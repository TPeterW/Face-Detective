package com.peter.facedetective;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.facepp.error.FaceppParseException;
import com.facepp.http.HttpRequests;
import com.facepp.http.PostParameters;
import com.facepp.result.FaceppResult;

import java.io.ByteArrayOutputStream;

/**
 * Created by 韬 on 2015/8/20 0020.
 *
 */
public class FaceppDetect {

    private static final String FACEPP_KEY = "70109ce8b5d3ad5fb398beeebf618dbc";
    private static final String FACEPP_SECRET = "KRyFl11nFFBKDrN4yE6Go5bBWCY-vCme";

    public interface Callback{
        void success(FaceppResult result);

        void error(FaceppParseException exception);

    }

    public static void detect(final Bitmap bm, final Callback callback, final Context context) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();
                try {
                    // 创建请求
                    HttpRequests requests = new HttpRequests(FACEPP_KEY, FACEPP_SECRET, true);    // 不行就改成false

                    Bitmap bmSmall = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight());
                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    bmSmall.compress(Bitmap.CompressFormat.JPEG, 100, stream);

                    byte[] arrays = stream.toByteArray();

                    PostParameters params = new PostParameters();
                    params.setImg(arrays);
                    FaceppResult jsonObject = requests.detectionDetect(params);
                    
//                    Log.i("TAG", "This is to see whether it works or not");
                    Log.i("TAG", jsonObject.toString());

                    if (callback != null) {
                        callback.success(jsonObject);
                    }
                } catch (FaceppParseException e) {
                    e.printStackTrace();
                    if (callback == null) {
                        callback.error(e);
                    }

                    Toast.makeText(context, R.string.foundException, Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(context, R.string.unknownException, Toast.LENGTH_SHORT).show();
                }

            }
        }).start();
    }
}
