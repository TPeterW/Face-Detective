package com.peter.facedetective;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Environment;
import android.os.Message;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.facebook.appevents.AppEventsLogger;
import com.facepp.error.FaceppParseException;
import com.facepp.result.FaceppResult;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import android.content.pm.Signature;
import java.util.*;
import java.text.SimpleDateFormat;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final int PICK_CODE = 5221;
    private ImageButton detect;
    private Button getImage;
    private ImageView photo;
//    private TextView count;
    private FrameLayout waiting;
    private ProgressBar ring;
    private Button saveImage;

    private String currentPhotoStr;
//    private String lastPhotoStr;
    private boolean hasAnalysedPhoto;

    private Bitmap photoImage;         // current
    private Bitmap editedImage;      // edited
    private Paint paint;

    private long lastPressedTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        initEvents();

        //确认暂时还没有照片可以保存
        hasAnalysedPhoto = false;

        paint = new Paint();
        setTitle(R.string.app_name);
        lastPressedTime = System.currentTimeMillis() - 2000;
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Logs 'install' and 'app activate' App Events.
        AppEventsLogger.activateApp(this);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Logs 'app deactivate' App Event.
        AppEventsLogger.deactivateApp(this);
    }

    private void initEvents() {
        getImage.setOnClickListener(this);
        detect.setOnClickListener(this);
        saveImage.setOnClickListener(this);
    }

    private void initViews() {
        detect = (ImageButton)findViewById(R.id.detect);
        getImage = (Button)findViewById(R.id.getImage);
        photo = (ImageView)findViewById(R.id.photo);
//        count = (TextView)findViewById(R.id.count);
        waiting = (FrameLayout)findViewById(R.id.waiting);
        ring = (ProgressBar)findViewById(R.id.ring);
        saveImage = (Button)findViewById(R.id.saveImage);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main_activity_action_bar, menu);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.saveImage:
                if (hasAnalysedPhoto == false)
                    Toast.makeText(this, R.string.noAvailablePhotoToSave, Toast.LENGTH_SHORT).show();
                else {
                    // TODO: 保存照片
                    saveImageToGallery(MainActivity.this);
                }

                break;

            case R.id.getImage:
                setTitle(R.string.selectImage);
                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType("image/*");
                startActivityForResult(intent, PICK_CODE);
//                setTitle("Detect Faces");
                break;

            case R.id.detect:
                if(currentPhotoStr != null && !currentPhotoStr.trim().equals("")){
                    setTitle(R.string.detecting);
                    waiting.setVisibility(View.VISIBLE);
                    ring.setVisibility(View.VISIBLE);
//                    count.setVisibility(View.INVISIBLE);
                }
                else {
                    Toast.makeText(this, R.string.noPhotoSelected, Toast.LENGTH_SHORT).show();
                    writeHashKey();
                    break;
                }

                resizePhoto();

                FaceppDetect.detect(photoImage, new FaceppDetect.Callback() {
                    @Override
                    public void success(FaceppResult result) {
                        Message msg = Message.obtain();
                        msg.what = MSG_SUCCESS;
                        msg.obj = result;
                        handler.sendMessage(msg);
                    }

                    @Override
                    public void error(FaceppParseException exception) {
                        Message msg = Message.obtain();
                        msg.what = MSG_ERROR;
                        msg.obj = exception.getErrorMessage();
                        handler.sendMessage(msg);
//                        setTitle(R.string.app_name);
                    }
                }, MainActivity.this);

                hasAnalysedPhoto = true;
                break;
        }
    }

    private void saveImageToGallery(Context context){
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "FACEDET_" + timeStamp;
        Log.i("TAG", imageFileName);

        /*
        方法一
         */
//        File sd = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
//        Log.i("TAG", sd.toString());
//
//        Intent saveImage = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
//        File outputImage = new File(currentPhotoStr);
//        Uri contentUri = Uri.fromFile(outputImage);
//
//        saveImage.setData(contentUri);
//        this.sendBroadcast(saveImage);

        /*
        方法二
         */
        Bitmap forSave = photoImage;
        MediaStore.Images.Media.insertImage(context.getContentResolver(), forSave, imageFileName,timeStamp);

        Toast.makeText(context, R.string.pictureIsSaved, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == PICK_CODE){
            if (data != null){
                Uri uri = data.getData();
                Cursor cursor = getContentResolver().query(uri, null, null, null, null);
                cursor.moveToFirst();

                int idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
                currentPhotoStr = cursor.getString(idx);
                cursor.close();

                resizePhoto();

                photo.setImageBitmap(photoImage);
//                count.setText(R.string.detectWithArrow);
            }
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    // 压缩图片，防止过大
    private void resizePhoto(){
        BitmapFactory.Options option = new BitmapFactory.Options();
        option.inJustDecodeBounds = true;

        BitmapFactory.decodeFile(currentPhotoStr, option);

        double ratio = Math.max(option.outWidth * 1.0d / 1024f, option.outHeight * 1.0d / 1024f);

        option.inSampleSize = (int)Math.ceil(ratio);
        option.inJustDecodeBounds = false;

        // 第一次将从Gallery中获取的图片加给photoImage
        photoImage = BitmapFactory.decodeFile(currentPhotoStr, option);
    }

    private static final int MSG_SUCCESS = 0x111;
    private static final int MSG_ERROR = 0x112;

    android.os.Handler handler = new android.os.Handler(){
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what){
                case MSG_SUCCESS:
                    waiting.setVisibility(View.INVISIBLE);           // INVISIBLE 还是 GONE 要注意
                    ring.setVisibility(View.INVISIBLE);
//                    count.setVisibility(View.VISIBLE);
                    FaceppResult result = (FaceppResult)msg.obj;

                    // 解析JsonObject，绘制脸部框
                    prepareResultBitmap(result);

                    photo.setImageBitmap(photoImage);
                    editedImage = photoImage;                   // 将加了框的图像传入“已修改图像中”

                    break;

                case MSG_ERROR:
                    waiting.setVisibility(View.GONE);
                    ring.setVisibility(View.INVISIBLE);
//                    count.setVisibility(View.VISIBLE);
                    String errorMsg = msg.obj.toString();

                    if(TextUtils.isEmpty(errorMsg)){
                        Toast.makeText(MainActivity.this, "Error", Toast.LENGTH_SHORT).show();
//                        count.setText("Error");
                    }
                    else {
                        Toast.makeText(MainActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
//                        count.setText(errorMsg);
                    }

                    break;
            }
            super.handleMessage(msg);
        }
    };

    private void prepareResultBitmap(FaceppResult result) {
        // 根据原图创建一个新的空画板
        Bitmap bitmap = Bitmap.createBitmap(photoImage.getWidth(), photoImage.getHeight(), photoImage.getConfig());              // 此bitmap为最终的用来呈现给用户的图像
        Canvas canvas = new Canvas(bitmap);                // 把bitmap当作一幅canvas作画

        //将原图画入
        canvas.drawBitmap(photoImage, 0, 0, null);

        try{
            // 转换成JSONObject
            JSONObject rs = new JSONObject(result.toString());
            JSONArray faces = rs.getJSONArray("face");

            setTitle(R.string.app_name);

            int faceCount = faces.length();
//            count.setText("No: " + faceCount);

            for(int i = 0; i < faceCount; i++){
                // 拿到单独的face对象
                JSONObject face = faces.getJSONObject(i);
                JSONObject posObj = face.getJSONObject("position");

                /*
                 代表图片高度/宽度的百分比
                 中心点的位置
                  */
                float x = (float) posObj.getJSONObject("center").getDouble("x");
                float y = (float) posObj.getJSONObject("center").getDouble("y");

                float w = (float) posObj.getDouble("width");
                float h = (float) posObj.getDouble("height");

                // 百分比转换为实际像素值
                x = x / 100 * bitmap.getWidth();
                y = y / 100 * bitmap.getHeight();
                w = w / 100 * bitmap.getWidth();
                h = h / 100 * bitmap.getHeight();

                paint.setColor(0xffffffff);
                paint.setStrokeWidth(5);

                // 画脸部区域的box
                // 开始点的横纵坐标，结束点的横纵坐标
                canvas.drawLine(x - w/2, y - h/2, x - w/2, y + h/2, paint);
                canvas.drawLine(x - w/2, y + h/2, x + w/2, y + h/2, paint);
                canvas.drawLine(x + w/2, y - h/2, x + w/2, y + h/2, paint);
                canvas.drawLine(x - w/2, y - h/2, x + w/2, y - h/2, paint);

                // 年龄和性别
                int age = face.getJSONObject("attribute").getJSONObject("age").getInt("value");
                String gender = face.getJSONObject("attribute").getJSONObject("gender").getString("value");

                // 生成合适的用TextView控件表示的年龄和性别框
                Bitmap ageBitmap = buildAgeBitmap(age, "Male".equals(gender));

                // 根据图像大小缩放
                int ageWidth = ageBitmap.getWidth();
                int ageHeight = ageBitmap.getHeight();

                if(bitmap.getWidth() < photoImage.getWidth() && bitmap.getHeight() < photoImage.getHeight()){
                    float ratio = Math.max(bitmap.getWidth() * 1.0f / photo.getWidth(), bitmap.getHeight() * 1.0f / photo.getHeight());
                    ageBitmap = Bitmap.createScaledBitmap(ageBitmap, (int)(ageWidth * ratio), (int)(ageHeight * ratio), false);
                }

                // 将年龄与性别框绘制到指定的位置
                canvas.drawBitmap(ageBitmap, x - ageBitmap.getWidth() / 2, y - h/2 - ageBitmap.getHeight(), null);

                photoImage = bitmap;

                setTitle(R.string.app_name);
            }
        }
        catch (Exception e){

        }

    }

    private Bitmap buildAgeBitmap(int age, boolean isMale) {
//        TextView ageAndGender = (TextView)findViewById(R.id.ageAndGender);
        TextView ageAndGender = (TextView)waiting.findViewById(R.id.ageAndGender);
        ageAndGender.setText(age + "");
        if (isMale){
            ageAndGender.setCompoundDrawablesWithIntrinsicBounds(getResources().getDrawable(R.drawable.male), null, null, null);           // 左上右下
        }
        else {
            ageAndGender.setCompoundDrawablesWithIntrinsicBounds(getResources().getDrawable(R.drawable.female), null, null, null);
        }
        ageAndGender.setDrawingCacheEnabled(true);

        /*
        此段代码是防止DrawingCache为空
         */
        ageAndGender.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        ageAndGender.layout(0, 0, ageAndGender.getMeasuredWidth(),
                ageAndGender.getMeasuredHeight());
        ageAndGender.buildDrawingCache();

        Bitmap bitmap = Bitmap.createBitmap(ageAndGender.getDrawingCache());
        ageAndGender.destroyDrawingCache();

        return bitmap;
    }

    private void writeHashKey(){
        PackageInfo info;
        try {
            info = getPackageManager().getPackageInfo("com.peter.facedetective", PackageManager.GET_SIGNATURES);
            for (Signature signature : info.signatures) {
                MessageDigest md;
                md = MessageDigest.getInstance("SHA");
                md.update(signature.toByteArray());
                String something = new String(Base64.encode(md.digest(), 0));
                //String something = new String(Base64.encodeBytes(md.digest()));
                Log.i("TAG", something);
            }
        } catch (PackageManager.NameNotFoundException e1) {
            Log.e("name not found", e1.toString());
        } catch (NoSuchAlgorithmException e) {
            Log.e("no such an algorithm", e.toString());
        } catch (Exception e) {
            Log.e("exception", e.toString());
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        long currentPressedTime = System.currentTimeMillis();

        if(keyCode == KeyEvent.KEYCODE_BACK){
            if((currentPressedTime - lastPressedTime) > 2000){
                Toast.makeText(MainActivity.this, R.string.pressAgainExit, Toast.LENGTH_SHORT).show();
                lastPressedTime = currentPressedTime;
            }
            else {
                this.finish();
            }
        }

        return true;

//        return super.onKeyDown(keyCode, event);
    }
}
