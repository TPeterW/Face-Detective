package com.peter.facedetective.utils;

import android.content.Context;
import android.util.Log;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.Volley;
import com.peter.facedetective.models.VolleyMultipartRequest;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Map;

/**
 * Created by Peter on 12/27/16.
 * Middleware for handling Face++ requests
 */

public class FacePlusPlus {

    private static final String DETECT_API_URL = "https://api-cn.faceplusplus.com/facepp/v3/detect";
    private static RequestQueue requestQueue;

    /***
     * Sends a POST request to Face++ server and parse result
     */
    public static void detectFace(Context context, final Map<String, String> params, final Map<String, VolleyMultipartRequest.DataPart> data, Response.Listener<NetworkResponse> listener,
                                  Response.ErrorListener errorListener) {
        if (requestQueue == null) {
            requestQueue = Volley.newRequestQueue(context);
        }

        VolleyMultipartRequest multipartRequest = new VolleyMultipartRequest(Request.Method.POST,
                DETECT_API_URL, listener, errorListener) {
            @Override
            protected Map<String, String> getParams() throws AuthFailureError {
                Log.i("POST", params.toString());
                return params;
            }

            @Override
            protected Map<String, DataPart> getByteData() throws AuthFailureError {
                Log.i("POST", data.toString());
                return data;
            }
        };

        requestQueue.add(multipartRequest);
    }

}
