package com.tencent.liteav.demo.videoupload.impl;

import android.content.Context;
import android.os.Handler;

import com.tencent.liteav.basic.log.TXCLog;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;


/**
 * UGC Client
 */
public class UGCClient {
    private final static String TAG = "TVC-UGCClient";
    private Context context;
    private String signature;
    private OkHttpClient okHttpClient;
    private Handler mainHandler;
//    private static String TEST_SERVER = "http://112.90.82.173/v3/index.php?Action=";
    private static String SERVER = "https://vod2.qcloud.com/v3/index.php?Action=";


    public UGCClient(Context context, String signature, int iTimeOut) {
        this.context = context;
        this.signature = signature;

        okHttpClient = new OkHttpClient().newBuilder()
                .connectTimeout(iTimeOut, TimeUnit.SECONDS)    // 设置超时时间
                .readTimeout(iTimeOut, TimeUnit.SECONDS)       // 设置读取超时时间
                .writeTimeout(iTimeOut, TimeUnit.SECONDS)      // 设置写入超时时间
                .build();

        mainHandler = new Handler(context.getMainLooper());
    }

//    private String getCommonReqPath(String interfaceName) {
//        return SERVER + interfaceName
//                + "&Region=gz"
//                + "&Timestamp=" + String.valueOf(System.currentTimeMillis() / 1000)
//                + "&Nonce=" + String.valueOf((int) Math.random() * 65535 + 1)
//                + "&SecretId=" + URLEncoder.encode(scretId)
//                + "&Signature=" + URLEncoder.encode(signature);
//    }

    /**
     * 申请上传（UGC接口）
     *
     * @param info     文件信息
     * @param callback 回调
     * @return
     */
    public int initUploadUGC(TVCUploadInfo info, String vodSessionKey, Callback callback) {
        String reqUrl = SERVER + "ApplyUploadUGC";
//        String reqUrl =  TEST_SERVER + "ApplyUploadUGC";
        TXCLog.d(TAG, "initUploadUGC->request url:" + reqUrl);

        String body = "";
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("signature", signature);
            jsonObject.put("videoName", info.getFileName());
            jsonObject.put("videoType", info.getFileType());
            // 判断是否需要上传封面
            if (info.isNeedCover()) {
                jsonObject.put("coverName",info.getCoverName());
                jsonObject.put("coverType",info.getCoverImgType());
            }
            if (vodSessionKey != null && !vodSessionKey.isEmpty()) {
                jsonObject.put("vodSessionKey", vodSessionKey);
            }
            body = jsonObject.toString();
            TXCLog.d(TAG, body);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        RequestBody requestBody = RequestBody.create(MediaType.parse("application/json"), body);
        Request request = new Request.Builder()
                .url(reqUrl)
                .post(requestBody)
                .build();

        okHttpClient.newCall(request).enqueue(callback);

        return TVCConstants.NO_ERROR;
    }

    /**
     * 上传结束(UGC接口)
     *
     * @param info     视频信息
     * @param callback 回调
     * @return
     */
    public int finishUploadUGC(UGCFinishUploadInfo info, final Callback callback) {
        String reqUrl = "https://" + info.getDomain() + "/v3/index.php?Action=CommitUploadUGC";
//        String reqUrl = "http://112.90.82.173/v3/index.php?Action=CommitUploadUGC";
        TXCLog.d(TAG, "finishUploadUGC->request url:" + reqUrl);
        String body = "";
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("signature", signature);
            jsonObject.put("vodSessionKey", info.getVodSessionKey());
            body = jsonObject.toString();
            TXCLog.d(TAG, body);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        RequestBody requestBody = RequestBody.create(MediaType.parse("application/json"), body);
        Request request = new Request.Builder()
                .url(reqUrl)
                .post(requestBody)
                .build();

        okHttpClient.newCall(request).enqueue(callback);

        return TVCConstants.NO_ERROR;
    }
}
