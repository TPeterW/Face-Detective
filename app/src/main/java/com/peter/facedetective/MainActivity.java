package com.peter.facedetective;

import android.Manifest;
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
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
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
import com.peter.facedetective.models.Photo;
import com.peter.facedetective.models.VolleyMultipartRequest;
import com.peter.facedetective.utils.FacePlusPlus;

import io.fabric.sdk.android.Fabric;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.text.SimpleDateFormat;

public class MainActivity extends AppCompatActivity {

    private ImageButton detect;
    private Button getImage;
    private ImageView photoView;

    private FrameLayout waiting;
    private ProgressBar ring;
    private Button saveImage;

    private Paint paint;

    private long lastPressedTime;

    static final int PERMISSION_CAMERA_REQUEST_CODE             = 0x101;
    static final int PERMISSION_READ_WRITE_REQUEST_CODE         = 0x102;
    static final int PERMISSION_NETWORK_STATE_REQUEST_CODE      = 0x103;

    private static final int PICK_CODE                          = 0x233;
    private static final int CAMERA_CODE                        = 0x232;

    private RelativeLayout relativeLayout;

    private Photo currentPhoto;
    private boolean analysingPhoto = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initView();

        initEvent();

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
                if (Build.VERSION.SDK_INT >= 23) {
                    int readWritePermission = ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
                    if (readWritePermission == PackageManager.PERMISSION_DENIED) {
                        Toast.makeText(MainActivity.this, getString(R.string.no_read_write_permission), Toast.LENGTH_SHORT).show();
                        ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_READ_WRITE_REQUEST_CODE);
                        return;
                    }
                }

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

                if (analysingPhoto)
                    return;

                analysingPhoto = true;
                waiting.setVisibility(View.VISIBLE);
                ring.setVisibility(View.VISIBLE);

                Map<String, String> params = new HashMap<>();
                params.put("api_key", Constants.FACEPP_API_KEY);
                params.put("api_secret", Constants.FACEPP_API_SECRET);
                params.put("return_attributes", "gender,age");

                Map<String, VolleyMultipartRequest.DataPart> data = new HashMap<>();
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                // resize to fit in the API
                Bitmap resizedPhoto = resizePhoto(currentPhoto.getImageBitmap());
                if (resizedPhoto == null) {
                    Toast.makeText(MainActivity.this, getString(R.string.fail_to_parse_photo), Toast.LENGTH_SHORT).show();
                    return;
                }
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
                        currentPhoto.setAnalysed(true);

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
                if (Build.VERSION.SDK_INT >= 23) {
                    int readWritePermission = ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
                    if (readWritePermission == PackageManager.PERMISSION_DENIED) {
                        Toast.makeText(MainActivity.this, getString(R.string.no_read_write_permission), Toast.LENGTH_SHORT).show();
                        ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_READ_WRITE_REQUEST_CODE);
                        return;
                    }
                }

                if (!currentPhoto.hasPhoto() || !currentPhoto.isAnalysed()) {
                    Toast.makeText(MainActivity.this, getString(R.string.no_available_photo_to_save), Toast.LENGTH_SHORT).show();
                    return;
                }

                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                String imageFileName = "FACEDET_" + timeStamp;
                MediaStore.Images.Media.insertImage(getContentResolver(), currentPhoto.getImageBitmap(), imageFileName, timeStamp);
                Toast.makeText(MainActivity.this, getString(R.string.picture_is_saved), Toast.LENGTH_SHORT).show();
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
            return null;

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

                float rectTop = (float) rect.getDouble("top");                 // y-coord of top left
                float rectLeft = (float) rect.getDouble("left");               // x-coord of top left
                float rectWidth = (float) rect.getDouble("width");             // width
                float rectHeight = (float) rect.getDouble("height");           // height

                // 百分比转换为实际像素值
                paint.setColor(Color.WHITE);
                paint.setStrokeWidth(5);

                // 画脸部区域的box
                // 开始点的横纵坐标，结束点的横纵坐标
                canvas.drawLine(rectLeft, rectTop, rectLeft + rectWidth, rectTop, paint);                   // top line
                canvas.drawLine(rectLeft + rectWidth, rectTop, rectLeft + rectWidth, rectTop + rectHeight, paint);  // right line
                canvas.drawLine(rectLeft, rectTop, rectLeft, rectTop + rectHeight, paint);                  // left line
                canvas.drawLine(rectLeft, rectTop + rectHeight, rectLeft + rectWidth, rectTop + rectHeight, paint); // bottom line

                // 年龄和性别
                int age = face.getJSONObject("attributes").getJSONObject("age").getInt("value");
                String gender = face.getJSONObject("attributes").getJSONObject("gender").getString("value");

                // 生成合适的用TextView控件表示的年龄和性别框
                Bitmap ageBitmap = buildAgeBitmap(age, "Male".equals(gender));

                // 根据图像大小缩放
                double ratio = (double) ageBitmap.getHeight() / (double) ageBitmap.getWidth();
                int ageWidth = 200;
                if (ageWidth > rectWidth / 2.0f)                // in case image is small
                    ageWidth = (int) (rectWidth / 2.0f);
                int ageHeight = (int) (ageWidth * ratio);

                if (ageWidth < newBitmap.getWidth() && ageHeight < newBitmap.getHeight()) {
                    ageBitmap = Bitmap.createScaledBitmap(ageBitmap, ageWidth, ageHeight, false);
                }

                // 将年龄与性别框绘制到指定的位置
                canvas.drawBitmap(ageBitmap, rectLeft + rectWidth / 2 - ageWidth / 2, rectTop - ageHeight, null);

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
                    Intent takePhotoIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    if (takePhotoIntent.resolveActivity(getPackageManager()) != null) {
                        File photoFile = createImageFile();

                        // for Android 7.0 and higher, now provides FileUriExposedException, suckers
                        Uri photoUri = FileProvider.getUriForFile(this,
                                "com.peter.facedetective.fileprovider",
                                photoFile);
                        takePhotoIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                        startActivityForResult(takePhotoIntent, CAMERA_CODE);
                    }
                } catch (Exception e){
                    e.printStackTrace();
                }

                return true;

            case R.id.action_share:
                if (currentPhoto.hasPhoto() && currentPhoto.isAnalysed()) {
                    try {
                        // save it first
                        File shareFile = createImageFile();
                        Uri shareUri = FileProvider.getUriForFile(this,
                                "com.peter.facedetective.fileprovider",
                                shareFile);
                        FileOutputStream fos = new FileOutputStream(shareFile);
                        currentPhoto.getImageBitmap().compress(Bitmap.CompressFormat.JPEG, 100, fos);
                        fos.close();

                        // and then share it
                        if (shareUri != null) {
                            Intent shareIntent = new Intent();
                            shareIntent.setAction(Intent.ACTION_SEND);
                            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            shareIntent.putExtra(Intent.EXTRA_STREAM, shareUri);
                            shareIntent.setType("image/jpeg");
                            startActivity(Intent.createChooser(shareIntent, getString(R.string.sharing_content)));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    Toast.makeText(MainActivity.this, R.string.picture_not_analysed, Toast.LENGTH_SHORT).show();
                }
                return true;
        }

        return super.onOptionsItemSelected(item);
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
                        currentPhoto.setAnalysed(false);
                        photoView.setImageBitmap(currentPhoto.getImageBitmap());
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                }
                break;

            case CAMERA_CODE:
                if (resultCode == RESULT_OK) {
                    int targetWidth = photoView.getWidth();
                    int targetHeight = photoView.getHeight();

                    // Get the dimensions of the bitmap
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(currentPhoto.getPhotoPath(), options);
                    int photoWidth = options.outWidth;
                    int photoHeight = options.outHeight;

                    // Determine how much to scale down the image
                    int scaleFactor = Math.min(photoWidth / targetWidth > 0 ? targetWidth : 1,
                            photoHeight / targetHeight > 0 ? targetHeight : 1);

                    // Decode the image file into a Bitmap sized to fill the view
                    options.inJustDecodeBounds = false;
                    options.inSampleSize = scaleFactor;
                    options.inPurgeable = true;

                    currentPhoto.setImageBitmap(BitmapFactory.decodeFile(currentPhoto.getPhotoPath(), options));
                    currentPhoto.setAnalysed(false);
                    photoView.setImageBitmap(currentPhoto.getImageBitmap());
                }
                break;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "FACEDET_" + timeStamp + "ORIGINAL";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        currentPhoto.setPhotoPath(image.getAbsolutePath());
        return image;
    }

    // 压缩图片，防止过大
    private Bitmap resizePhoto(Bitmap bitmap) {
        int originalWidth = bitmap.getWidth();
        int originalHeight = bitmap.getHeight();

        if (Math.max(originalWidth, originalHeight) <= 4096) {              // hardcoded limit from website
            return bitmap;
        }

        double ratio = 4096 / (double) Math.max(originalWidth, originalHeight);
        int newWidth = (int) Math.floor(originalWidth * ratio);
        int newHeight = (int) Math.floor(originalHeight * ratio);

        if (newWidth <= 0 || newHeight <= 0)
            return null;
        else
            return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, false);
    }

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
    }

    /***
     * Checks if connected to internet
     * @return connectedOrNot
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
