package com.peter.facedetective;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Message;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
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

import com.facepp.error.FaceppParseException;
import com.facepp.result.FaceppResult;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;

import com.peter.facedetective.utils.AnalyticsApplication;
import com.twitter.sdk.android.Twitter;
import com.twitter.sdk.android.core.TwitterAuthConfig;
import com.twitter.sdk.android.tweetcomposer.TweetComposer;

import io.fabric.sdk.android.Fabric;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.text.SimpleDateFormat;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    // TODO: check other permission

    // Note: Your consumer key and secret should be obfuscated in your source code before shipping.
    private static final String TWITTER_KEY = "teGWfhaat5woIR9ZqqOTELPXX";
    private static final String TWITTER_SECRET = "Cz5bMeSfmDAsjvdSnnkHmHM4d5OdwOqb2gkcr6pbFxeIgrvCzI";

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

    private Bitmap photoImage;          // current
    private Bitmap editedImage;         // edited
    private Paint paint;

    // for capturing image
    private Uri preinsertedUri;
    private File photoFile;

    private long lastPressedTime;

    static final int REQUEST_TAKE_PHOTO = 1;
    static final int PERMISSION_CAMERA_REQUEST_CODE = 101;
    static final int PERMISSION_READ_WRITE_REQUEST_CODE = 102;

    private Tracker mTracker;

    //TODO: change the order of create image and take picture

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initView();
        initEvent();

        //确认暂时还没有照片可以保存
        hasAnalysedPhoto = false;

        paint = new Paint();
        setTitle(R.string.app_name);
        lastPressedTime = System.currentTimeMillis() - 2000;

        // Google Analytics
        AnalyticsApplication application = (AnalyticsApplication) getApplication();
        mTracker = application.getDefaultTracker();

        // Twitter Sharing
        TwitterAuthConfig authConfig =  new TwitterAuthConfig(TWITTER_KEY, TWITTER_SECRET);
        Fabric.with(this, new TweetComposer(), new Twitter(authConfig));
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i("TAG", "Setting screen name: ");
        mTracker.setScreenName("MainScreen");
        mTracker.send(new HitBuilders.ScreenViewBuilder().build());
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    private void initEvent() {
        getImage.setOnClickListener(this);
        detect.setOnClickListener(this);
        saveImage.setOnClickListener(this);
    }

    private void initView() {
        detect = (ImageButton)findViewById(R.id.detect);
        getImage = (Button)findViewById(R.id.getImage);
        photo = (ImageView)findViewById(R.id.photo);
        waiting = (FrameLayout)findViewById(R.id.waiting);
        saveImage = (Button)findViewById(R.id.saveImage);
        ring = (ProgressBar)findViewById(R.id.ring);
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
        switch (id){
            case R.id.action_camera:
                if(Build.VERSION.SDK_INT >= 23){
                    int cameraPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
                    if (cameraPermission == PackageManager.PERMISSION_DENIED){
                        Toast.makeText(MainActivity.this, getString(R.string.noCameraPermission), Toast.LENGTH_SHORT).show();
                        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_CAMERA_REQUEST_CODE);
                        break;
                    }
                }

                try{
                    takePhoto();
                }
                catch (Exception e){
                    // do nothing
                }
//                Toast.makeText(this, "Capture Photo", Toast.LENGTH_SHORT).show();
                mTracker.send(new HitBuilders.EventBuilder()
                .setCategory("Action")
                .setAction("Take Photo")
                .build());

                return true;

            case R.id.action_share_twitter:
                if(hasAnalysedPhoto) {
                    File fileToShare = new File(currentPhotoStr);
                    OutputStream os = null;
                    try {
                        os = new BufferedOutputStream(new FileOutputStream(fileToShare));
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                    Bitmap bitmap = photoImage;
                    bitmap.compress(Bitmap.CompressFormat.PNG, 0, os);
                    try {
                        os.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    Uri imageUri = Uri.fromFile(fileToShare);
                    TweetComposer.Builder builder = new TweetComposer.Builder(this)
                            .text(getResources().getString(R.string.twitter_sharing))
                            .image(imageUri);
                    builder.show();
                }
                else {
                    Toast.makeText(MainActivity.this, R.string.noAvailablePhotoToShare, Toast.LENGTH_SHORT).show();
                }
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    private void takePhoto() throws IOException{
        Intent takePicture = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // ensure that there is a camera activity to handle the intent
        if(takePicture.resolveActivity(getPackageManager()) != null){
            photoFile = null;
            try {
                photoFile = createImageFile();
            }
            catch (IOException e){
                Toast.makeText(this, R.string.somethingHappened, Toast.LENGTH_SHORT).show();
            }

            // continue if successfully created
            if(photoFile != null){
                // TODO: save photo to gallery first
                takePicture.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(photoFile));
                startActivityForResult(takePicture, REQUEST_TAKE_PHOTO);
            }
        }

    }

    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "FACEDET_" + timeStamp;
        File storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
//        currentPhotoStr = "file:" + image.getAbsolutePath();
        currentPhotoStr = image.getAbsolutePath();
        return image;
    }


    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.saveImage:
            case R.id.getImage:
                if(Build.VERSION.SDK_INT >= 23){
                    int readWritePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
                    if (readWritePermission == PackageManager.PERMISSION_DENIED){
                        Toast.makeText(MainActivity.this, getString(R.string.noReadWritePermission), Toast.LENGTH_SHORT).show();
                        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_READ_WRITE_REQUEST_CODE);
                        return;
                    }
                }
                break;
        }

        switch (v.getId()){
            case R.id.saveImage:
                if (!hasAnalysedPhoto)
                    Toast.makeText(this, R.string.noAvailablePhotoToSave, Toast.LENGTH_SHORT).show();
                else {
                    saveImageToGallery(MainActivity.this);
                }

                mTracker.send(new HitBuilders.EventBuilder()
                        .setCategory("Action")
                        .setAction("Save Photo")
                        .build());

                break;

            case R.id.getImage:
                setTitle(R.string.selectImage);
                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType("image/*");
                startActivityForResult(intent, PICK_CODE);
//                setTitle("Detect Faces");

                mTracker.send(new HitBuilders.EventBuilder()
                        .setCategory("Action")
                        .setAction("Pick Photo From Gallery")
                        .build());
                break;

            case R.id.detect:
                if(currentPhotoStr != null && !currentPhotoStr.trim().equals("")){
                    setTitle(R.string.detecting);
                    waiting.setVisibility(View.VISIBLE);
                    ring.setVisibility(View.VISIBLE);
                }
                else {
                    Toast.makeText(this, R.string.noPhotoSelected, Toast.LENGTH_SHORT).show();
//                    writeHashKey();
                    break;
                }

                mTracker.send(new HitBuilders.EventBuilder()
                        .setCategory("Action")
                        .setAction("Detect Photo")
                        .build());

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
        MediaStore.Images.Media.insertImage(context.getContentResolver(), forSave, imageFileName, timeStamp);

        Toast.makeText(context, R.string.pictureIsSaved, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == PICK_CODE){
            if (data != null){
                Uri uri = data.getData();
                Cursor cursor = getContentResolver().query(uri, null, null, null, null);
                if(cursor != null)
                    cursor.moveToFirst();

                int idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
                currentPhotoStr = cursor.getString(idx);
                cursor.close();

                resizePhoto();

                photo.setImageBitmap(photoImage);
//                count.setText(R.string.detectWithArrow);
            }
        }

        if(requestCode == REQUEST_TAKE_PHOTO && resultCode == RESULT_OK){
//            Bundle extras = data.getExtras();
//            Bitmap imageBitmap = (Bitmap) extras.get("data");
//            photo.setImageBitmap(imageBitmap);

            Log.i("CAPTURE", currentPhotoStr);
            resizePhoto();

            photo.setImageBitmap(photoImage);
        }
        if(requestCode == REQUEST_TAKE_PHOTO && resultCode == RESULT_CANCELED){
            Log.i("CAPTURE", "Failed");
            currentPhotoStr = null;
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

        android.os.Handler handler = new android.os.Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_SUCCESS:
                        waiting.setVisibility(View.INVISIBLE);           // INVISIBLE 还是 GONE 要注意
                        ring.setVisibility(View.INVISIBLE);
//                    count.setVisibility(View.VISIBLE);
                        FaceppResult result = (FaceppResult) msg.obj;

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

                        if (TextUtils.isEmpty(errorMsg)) {
                            Toast.makeText(MainActivity.this, "Error", Toast.LENGTH_SHORT).show();
//                        count.setText("Error");
                        } else {
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
            ageAndGender.setCompoundDrawablesWithIntrinsicBounds(ContextCompat.getDrawable(this, R.drawable.male), null, null, null);           // 左上右下
        }
        else {
            ageAndGender.setCompoundDrawablesWithIntrinsicBounds(ContextCompat.getDrawable(this, R.drawable.female), null, null, null);
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

    /*
    Hash key (for Facebook Plug-in)
     */
//    private void writeHashKey(){
//        PackageInfo info;
//        try {
//            info = getPackageManager().getPackageInfo("com.peter.facedetective", PackageManager.GET_SIGNATURES);
//            for (Signature signature : info.signatures) {
//                MessageDigest md;
//                md = MessageDigest.getInstance("SHA");
//                md.update(signature.toByteArray());
//                String something = new String(Base64.encode(md.digest(), 0));
//                //String something = new String(Base64.encodeBytes(md.digest()));
//                Log.i("TAG", something);
//            }
//        } catch (PackageManager.NameNotFoundException e1) {
//            Log.e("name not found", e1.toString());
//        } catch (NoSuchAlgorithmException e) {
//            Log.e("no such an algorithm", e.toString());
//        } catch (Exception e) {
//            Log.e("exception", e.toString());
//        }
//    }

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

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode){
            case PERMISSION_CAMERA_REQUEST_CODE:
                if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    // all good, do nothing
                }
                else {
                    Toast.makeText(MainActivity.this, getString(R.string.needCameraPermission), Toast.LENGTH_SHORT).show();
                }
                break;

            case PERMISSION_READ_WRITE_REQUEST_CODE:
                if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    // all good, do nothing
                }
                else {
                    Toast.makeText(MainActivity.this, getString(R.string.needReadWritePermission), Toast.LENGTH_SHORT).show();
                }
                break;
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}
