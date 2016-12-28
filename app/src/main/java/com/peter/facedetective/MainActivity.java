package com.peter.facedetective;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Message;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.ShareActionProvider;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.NetworkResponse;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.crashlytics.android.Crashlytics;
import com.facepp.error.FaceppParseException;
import com.facepp.result.FaceppResult;
import com.peter.facedetective.models.Photo;
import com.peter.facedetective.models.VolleyMultipartRequest;
import com.peter.facedetective.utils.FacePlusPlus;

import io.fabric.sdk.android.Fabric;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.text.SimpleDateFormat;

import static com.peter.facedetective.models.VolleyMultipartRequest.*;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private ImageButton detect;
    private Button getImage;
    private ImageView photoView;

    private FrameLayout waiting;
    private ProgressBar ring;
    private Button saveImage;

    private String currentPhotoStr;
    private boolean hasAnalysedPhoto;

    private Bitmap photoImage;          // current
    private Paint paint;

    private long lastPressedTime;

    static final int PERMISSION_CAMERA_REQUEST_CODE             = 0x101;
    static final int PERMISSION_READ_WRITE_REQUEST_CODE         = 0x102;
    static final int PERMISSION_NETWORK_STATE_REQUEST_CODE      = 0x103;

    private static final int PICK_CODE                          = 0x233;
    private static final int REQUEST_TAKE_PHOTO                 = 0x232;

    private RelativeLayout relativeLayout;
    private ShareActionProvider shareActionProvider;

    private Photo currentPhoto;
    private boolean analysingPhoto = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initView();

        initEvent();

        // 确认暂时还没有照片可以保存
        hasAnalysedPhoto = false;

        paint = new Paint();
        setTitle(R.string.app_name);
        lastPressedTime = System.currentTimeMillis() - 2000;

        // Fabric
        Fabric.with(this, /*new Twitter(authConfig),*/ new Crashlytics());
    }

    private void initView() {
        detect = (ImageButton)findViewById(R.id.detect);
        getImage = (Button)findViewById(R.id.getImage);
        photoView = (ImageView)findViewById(R.id.photo);
        waiting = (FrameLayout)findViewById(R.id.waiting);
        saveImage = (Button)findViewById(R.id.saveImage);
        ring = (ProgressBar)findViewById(R.id.ring);

        relativeLayout = (RelativeLayout) findViewById(R.id.layout);
    }

    private void initEvent() {
        // fade in and out animation for the activity
        AlphaAnimation animation = new AlphaAnimation(0.0f, 1.0f);
        animation.setFillAfter(true);
        animation.setDuration(1800);
        relativeLayout.startAnimation(animation);

        // initialise currentPhoto object
        currentPhoto = new Photo();

        getImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setTitle(R.string.select_image);

                Intent getIntent = new Intent(Intent.ACTION_GET_CONTENT);
                getIntent.setType("image/*");

                Intent pickIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                pickIntent.setType("image/*");

                // allows picking from other galleries as well
                Intent chooserIntent = Intent.createChooser(getIntent, "Select Photo");
                chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, pickIntent);

                try {
                    startActivityForResult(chooserIntent, PICK_CODE);
                } catch (Exception e) {
                    Crashlytics.logException(e);
                    e.printStackTrace();
                    Toast.makeText(MainActivity.this, getString(R.string.no_app_to_pick_photo), Toast.LENGTH_SHORT).show();
                }

            }
        });
        
        detect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!isNetworkConnected()) {                // no internet
                    Toast.makeText(MainActivity.this, getString(R.string.no_internet_connection), Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!currentPhoto.hasPhoto()) {
                    Toast.makeText(MainActivity.this, getString(R.string.no_photo_selected), Toast.LENGTH_SHORT).show();
                    return;
                }

                analysingPhoto = true;
                waiting.setVisibility(View.VISIBLE);
                ring.setVisibility(View.VISIBLE);

                Map<String, String> params = new HashMap<>();
                params.put("api_key", Constants.FACEPP_API_KEY);
                params.put("api_secret", Constants.FACEPP_API_SECRET);
                params.put("return_attributes", "gender,age");

                Map<String, VolleyMultipartRequest.DataPart> data = new HashMap<>();
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                Bitmap resizedPhoto = resizePhoto(currentPhoto.getImageBitmap());
                resizedPhoto.compress(Bitmap.CompressFormat.JPEG, 100, bos);
                VolleyMultipartRequest.DataPart dataPart = new VolleyMultipartRequest.DataPart("detect.jpg", bos.toByteArray(), "image/jpeg");
                data.put("image_file", dataPart);

                // sent the post request
                FacePlusPlus.detectFace(MainActivity.this, params, data, new Response.Listener<NetworkResponse>() {
                    @Override
                    public void onResponse(NetworkResponse response) {
                        setTitle(getString(R.string.app_name));
                        analysingPhoto = false;
                        waiting.setVisibility(View.GONE);
                        ring.setVisibility(View.INVISIBLE);

                        String detectionResult = new String(response.data);
                        try {
                            JSONObject result = new JSONObject(detectionResult);
                            JSONArray faces = result.getJSONArray("faces");

                            if (response.statusCode == 200) {
                                Log.d("DetectResult", result.toString());
                                if (faces.length() > 0) {
                                    currentPhoto.setImageBitmap(prepareResultBitmap(currentPhoto.getImageBitmap(), faces));
                                    photoView.setImageBitmap(currentPhoto.getImageBitmap());
                                }
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        setTitle(getString(R.string.app_name));
                        analysingPhoto = false;
                        waiting.setVisibility(View.GONE);
                        ring.setVisibility(View.INVISIBLE);

                        error.printStackTrace();

                        Toast.makeText(MainActivity.this, "Failed to analyse the photoView", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

        saveImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO:
            }
        });
    }

    /***
     * Prepare for adding ages and stuff
     * @param originalBitmap    original bitmap
     * @param faces             list of JSONObjects that are faces
     * @return preparedBitmap
     */
    private Bitmap prepareResultBitmap(Bitmap originalBitmap, JSONArray faces) {
        if (originalBitmap == null)
            return originalBitmap;

        // 根据原图创建一个新的空画板
        Bitmap newBitmap = Bitmap.createBitmap(originalBitmap.getWidth(), originalBitmap.getHeight(), originalBitmap.getConfig());              // 此bitmap为最终的用来呈现给用户的图像
        Canvas canvas = new Canvas(newBitmap);                // 把bitmap当作一幅canvas作画

        //将原图画入
        canvas.drawBitmap(originalBitmap, 0, 0, null);

        setTitle(R.string.app_name);

        try {
            for (int i = 0; i < faces.length(); i++) {
                // 拿到单独的face对象
                JSONObject face = faces.getJSONObject(i);
                JSONObject rect = face.getJSONObject("face_rectangle");

                Log.i("Face", face.toString());

                float top = (float) rect.getDouble("top");                 // y-coord of top left
                float left = (float) rect.getDouble("left");               // x-coord of top left
                float width = (float) rect.getDouble("width");             // width
                float height = (float) rect.getDouble("height");           // height

                // 百分比转换为实际像素值
                paint.setColor(Color.WHITE);
                paint.setStrokeWidth(5);

                // 画脸部区域的box
                // 开始点的横纵坐标，结束点的横纵坐标
                canvas.drawLine(left, top, left + width, top, paint);                   // top line
                canvas.drawLine(left + width, top, left + width, top + height, paint);  // right line
                canvas.drawLine(left, top, left, top + height, paint);                  // left line
                canvas.drawLine(left, top + height, left + width, top + height, paint); // bottom line

                // 年龄和性别
                int age = face.getJSONObject("attributes").getJSONObject("age").getInt("value");
                String gender = face.getJSONObject("attributes").getJSONObject("gender").getString("value");

                // 生成合适的用TextView控件表示的年龄和性别框
                Bitmap ageBitmap = buildAgeBitmap(age, "Male".equals(gender));

                // 根据图像大小缩放
                double ratio = (double) ageBitmap.getHeight() / (double) ageBitmap.getWidth();
                int ageWidth = 200;
                int ageHeight = (int) (ageWidth * ratio);

                if (ageWidth < newBitmap.getWidth() && ageHeight < newBitmap.getHeight()) {
                    ageBitmap = Bitmap.createScaledBitmap(ageBitmap, ageWidth, ageHeight, false);
                }

                // 将年龄与性别框绘制到指定的位置
                canvas.drawBitmap(ageBitmap, left + width / 2 - ageWidth / 2, top - ageHeight, null);

                setTitle(R.string.app_name);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return newBitmap;
    }

    private Bitmap buildAgeBitmap(int age, boolean isMale) {
        TextView ageAndGender = (TextView)waiting.findViewById(R.id.ageAndGender);
        ageAndGender.setText(String.valueOf(age));
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main_activity, menu);

        MenuItem item = menu.findItem(R.id.action_share);

        shareActionProvider = (ShareActionProvider) MenuItemCompat.getActionProvider(item);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        switch (id) {
            case R.id.action_camera:

                if (Build.VERSION.SDK_INT >= 23) {
                    int cameraPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
                    if (cameraPermission == PackageManager.PERMISSION_DENIED) {
                        Toast.makeText(MainActivity.this, getString(R.string.no_camera_permission), Toast.LENGTH_SHORT).show();
                        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_CAMERA_REQUEST_CODE);
                        return true;
                    }
                }

                try {
                    takePhoto();
                } catch (Exception e){
                    e.printStackTrace();
                }

                return true;

            case R.id.action_share:
                if (hasAnalysedPhoto) {
                    File fileToShare;
                    try {
                        fileToShare = new File(currentPhotoStr);
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(this, getString(R.string.photo_not_found), Toast.LENGTH_SHORT).show();
                        return true;
                    }
                    OutputStream os = null;
                    try {
                        os = new BufferedOutputStream(new FileOutputStream(fileToShare));
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }

                    if (photoImage != null) {
                        Bitmap bitmap = photoImage;
                        bitmap.compress(Bitmap.CompressFormat.PNG, 0, os);
                    } else {
                        Toast.makeText(MainActivity.this, getString(R.string.unable_to_share_photo), Toast.LENGTH_SHORT).show();
                        return true;
                    }

                    try {
                        assert os != null;
                        os.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    Uri imageUri = Uri.fromFile(fileToShare);
                    Intent shareIntent = new Intent(Intent.ACTION_SEND).setType("image/*");
                    shareIntent.putExtra(Intent.EXTRA_STREAM, imageUri);
                    shareIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.sharing_content));

                    if (shareActionProvider != null)
                        shareActionProvider.setShareIntent(shareIntent);

                    startActivity(shareIntent);
                } else {
                    Toast.makeText(MainActivity.this, R.string.no_available_photo_to_share, Toast.LENGTH_SHORT).show();
                }
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void takePhoto() throws IOException {
        Intent takePicture = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // ensure that there is a camera activity to handle the intent
        if (takePicture.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException e) {
                Toast.makeText(this, R.string.something_happened, Toast.LENGTH_SHORT).show();
            }

            // continue if successfully created
            if (photoFile != null) {
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
                if (Build.VERSION.SDK_INT >= 23) {
                    int readWritePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
                    if (readWritePermission == PackageManager.PERMISSION_DENIED) {
                        Toast.makeText(MainActivity.this, getString(R.string.no_read_write_permission), Toast.LENGTH_SHORT).show();
                        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_READ_WRITE_REQUEST_CODE);
                        return;
                    }
                }
                break;
        }

        switch (v.getId()) {
            case R.id.saveImage:
                if (!hasAnalysedPhoto)
                    Toast.makeText(this, R.string.no_available_photo_to_save, Toast.LENGTH_SHORT).show();
                else {
                    saveImageToGallery(MainActivity.this);
                }

                break;

            case R.id.detect:
                if (!isNetworkConnected()) {        // no internet
                    Toast.makeText(MainActivity.this, getString(R.string.no_internet_connection), Toast.LENGTH_SHORT).show();
                    return;
                }

                if (currentPhotoStr != null && !currentPhotoStr.trim().equals("")) {
                    setTitle(R.string.detecting);
                    waiting.setVisibility(View.VISIBLE);
                    ring.setVisibility(View.VISIBLE);
                } else {
                    Toast.makeText(this, R.string.no_photo_selected, Toast.LENGTH_SHORT).show();
                    break;
                }

                Bitmap resizedBitmap = resizePhoto(currentPhoto.getImageBitmap());

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

    private void saveImageToGallery(Context context) {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "FACEDET_" + timeStamp;
        Log.i("IMAGE", imageFileName);

        Bitmap forSave = photoImage;
        MediaStore.Images.Media.insertImage(context.getContentResolver(), forSave, imageFileName, timeStamp);

        Toast.makeText(context, R.string.picture_is_saved, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case PICK_CODE:
                if (data != null) {
                    if (resultCode == RESULT_CANCELED)
                        return;

                    try {
                        Log.d("OnActivityResult", "Pick image form gallery");

                        InputStream is = getContentResolver().openInputStream(data.getData());
                        currentPhoto.setImageBitmap(BitmapFactory.decodeStream(is));
                        photoView.setImageBitmap(currentPhoto.getImageBitmap());
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                }
                break;

            case REQUEST_TAKE_PHOTO:
                // TODO:

                if (resultCode == RESULT_CANCELED) {
                    Log.i("CAPTURE", "Failed");
                    currentPhotoStr = null;
                    return;
                }

                if (currentPhotoStr != null) {
                    Log.i("CAPTURE", currentPhotoStr);
                } else {
                    Toast.makeText(MainActivity.this, getString(R.string.fail_to_capture_image), Toast.LENGTH_SHORT).show();
                }

                photoView.setImageBitmap(photoImage);
                break;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    // 压缩图片，防止过大
    private Bitmap resizePhoto(Bitmap bitmap) {
        int originalWidth = bitmap.getWidth();
        int originalHeight = bitmap.getHeight();

        if (Math.max(originalWidth, originalHeight) <= 4096) {              // hardcoded limit from website
            return bitmap;
        }

        double ratio = 4096 / Math.max(originalWidth, originalHeight);
        int newWidth = (int) Math.floor(originalWidth * ratio);
        int newHeight = (int) Math.floor(originalHeight * ratio);

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, false);
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
                    FaceppResult result = (FaceppResult) msg.obj;

                    // 解析JsonObject，绘制脸部框
//                    prepareResultBitmap(result);

                    photoView.setImageBitmap(photoImage);

                    break;

                case MSG_ERROR:
                    waiting.setVisibility(View.GONE);
                    ring.setVisibility(View.INVISIBLE);
                    String errorMsg = msg.obj.toString();

                    if (TextUtils.isEmpty(errorMsg)) {
                        Toast.makeText(MainActivity.this, "Error", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                    }

                    break;
            }
            super.handleMessage(msg);
        }
    };

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        long currentPressedTime = System.currentTimeMillis();

        if(keyCode == KeyEvent.KEYCODE_BACK){
            if((currentPressedTime - lastPressedTime) > 2000){
                Toast.makeText(MainActivity.this, R.string.press_again_exit, Toast.LENGTH_SHORT).show();
                lastPressedTime = currentPressedTime;
            }
            else {
                this.finish();
            }
        }

        return true;

//        return super.onKeyDown(keyCode, event);
    }

    /***
     * Checks if connected to internet
     * @return
     */
    private boolean isNetworkConnected() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

        return connectivityManager.getActiveNetworkInfo() != null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_CAMERA_REQUEST_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // all good, do nothing
                } else
                    Toast.makeText(MainActivity.this, getString(R.string.need_camera_permission), Toast.LENGTH_SHORT).show();
                break;

            case PERMISSION_READ_WRITE_REQUEST_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // all good, do nothing
                } else
                    Toast.makeText(MainActivity.this, getString(R.string.need_read_write_permission), Toast.LENGTH_SHORT).show();
                break;

            case PERMISSION_NETWORK_STATE_REQUEST_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // all good, do nothing
                } else
                    Toast.makeText(MainActivity.this, getString(R.string.need_network_permission), Toast.LENGTH_SHORT).show();
                break;
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i("IMAGE", "Setting screen name...");
    }
}
