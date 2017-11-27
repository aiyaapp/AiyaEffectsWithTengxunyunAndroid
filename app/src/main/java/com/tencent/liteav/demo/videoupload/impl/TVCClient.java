package com.tencent.liteav.demo.videoupload.impl;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.text.TextUtils;

import com.tencent.cos.COSClient;
import com.tencent.cos.COSConfig;
import com.tencent.cos.model.COSRequest;
import com.tencent.cos.model.COSResult;
import com.tencent.cos.model.PutObjectRequest;
import com.tencent.cos.model.PutObjectResult;
import com.tencent.cos.task.listener.IUploadTaskListener;
import com.tencent.rtmp.TXLog;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

/**
 * 视频上传客户端
 */
public class TVCClient {
    private final static String TAG = "TVC-Client";
    private Context context;
    private Handler mainHandler;
    private boolean busyFlag = false;

    private TVCUploadInfo uploadInfo;

    private UGCClient ugcClient;
    private COSClient cosClient;
    private TVCUploadListener tvcListener;

    private int cosAppId;
    private String cosBucket;
    private String uploadRegion = "gz";

    private String cosVideoPath;
    private String cosVideoSign;
    private String videoFileId;

    private String cosCoverPath;
    private String cosCoverSign;
    private String coverFileId;

    private String session;
    private String coverUrl = null;

    private int iTimeOut = 8;
    private String domain;
    private String vodSessionKey;

    private long coverSize = 0;
    private long coverUseTime = 0;
    private long videoSize = 0;
    private long videoUseTime = 0;

    // 断点重传session本地缓存
    // 以文件路径作为key值得，存储的内容是<session, expiredTime>
    private static final String LOCALFILENAME = "TVCSession";
    private SharedPreferences mSharedPreferences;
    private SharedPreferences.Editor mShareEditor;

    private int requestId = 0;
    private String mUserID = null;

    /**
     * 初始化上传实例
     *
     * @param signature 签名
     * @param iTimeOut  超时时间
     * @param userID    用户id
     */
    public TVCClient(Context context, String signature, int iTimeOut, String userID) {
        this.context = context.getApplicationContext();
        ugcClient = new UGCClient(context, signature, iTimeOut);
        mainHandler = new Handler(context.getMainLooper());
        mSharedPreferences = context.getSharedPreferences(LOCALFILENAME, Activity.MODE_PRIVATE);
        mShareEditor = mSharedPreferences.edit();
        mUserID = userID;
        clearLocalCache();
    }

    // 清理一下本地缓存，过期的删掉
    private void clearLocalCache() {
        if (mSharedPreferences != null) {
            try {
                Map<String, ?> allContent = mSharedPreferences.getAll();
                //注意遍历map的方法
                for(Map.Entry<String, ?>  entry : allContent.entrySet()){
                    JSONObject json = new JSONObject((String) entry.getValue());
                    long expiredTime = json.optLong("expiredTime", 0);
                    // 过期了清空key
                    if (expiredTime < System.currentTimeMillis() / 1000) {
                        session = "";
                        mShareEditor.remove(entry.getKey());
                        mShareEditor.commit();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 初始化上传实例
     *
     * @param signature 签名
     * @param iTimeOut  超时时间
     */
    public TVCClient(Context context, String signature, int iTimeOut) {
        this(context, signature, iTimeOut, null);
    }

    /**
     * 初始化上传实例
     *
     * @param signature 签名
     */
    public TVCClient(Context context, String signature) {
        this(context, signature, 8);
    }

    // 通知上层上传成功
    private void notifyUploadSuccess(final String fileId, final String playUrl, final String coverUrl) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                tvcListener.onSucess(fileId, playUrl, coverUrl);
            }
        });
    }

    // 通知上层上传失败
    private void notifyUploadFailed(final int errCode, final String errMsg) {
        if (uploadInfo.getIsShouldRetry()) {
            uploadInfo.setIsShouldRetry(false);
            getCosUploadInfo(uploadInfo, null);
        } else {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    tvcListener.onFailed(errCode, errMsg);
                }
            });
        }
    }

    // 通知上层上传进度
    private void notifyUploadProgress(final long currentSize, final long totalSize) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                tvcListener.onProgress(currentSize, totalSize);
            }
        });
    }

    private boolean isVideoFileExist(String path) {
        File file = new File(path);
        try {
            if (file.exists()) {
                return true;
            }
        } catch (Exception e) {
            TXLog.e("getFileSize", "getFileSize: " + e);
            return false;
        }
        return false;
    }

    /**
     * 上传视频文件
     *
     * @param info     视频文件信息
     * @param listener 上传回调
     * @return
     */
    public int uploadVideo(TVCUploadInfo info, TVCUploadListener listener) {
        if (busyFlag) {     // 避免一个对象传输多个文件
            return TVCConstants.ERR_CLIENT_BUSY;
        }
        busyFlag = true;
        this.uploadInfo = info;
        this.tvcListener = listener;

        if (!isVideoFileExist(info.getFilePath())) { //视频文件不存在 直接返回
            tvcListener.onFailed(TVCConstants.ERR_UGC_REQUEST_FAILED, "file could not find");

            //txReport(TXCDRDef.UPLOAD_EVENT_ID_REQUEST_UPLOAD, TVCConstants.ERR_UGC_REQUEST_FAILED, "file could not find", 0, 0);

            return -1;

        }

        String fileName = info.getFileName();
        TXLog.d(TAG, "fileName = " + fileName);
        if (fileName != null && fileName.getBytes().length > 40) { //视频文件名太长 直接返回
            tvcListener.onFailed(TVCConstants.ERR_UGC_FILE_NAME, "file name too long");

            //txReport(TXCDRDef.UPLOAD_EVENT_ID_REQUEST_UPLOAD, TVCConstants.ERR_UGC_FILE_NAME, "file name too long", 0, 0);

            return TVCConstants.ERR_UGC_FILE_NAME;
        }

        if (info.isContainSpecialCharacters(fileName)) {//视频文件名包含特殊字符 直接返回
            tvcListener.onFailed(TVCConstants.ERR_UGC_FILE_NAME, "file name contains special character / : * ? \" < >");

            //txReport(TXCDRDef.UPLOAD_EVENT_ID_REQUEST_UPLOAD, TVCConstants.ERR_UGC_FILE_NAME, "file name contains special character / : * ? \" < >", 0, 0);

            return TVCConstants.ERR_UGC_FILE_NAME;
        }

        getCosUploadInfo(info, getSessionFromFilepath(info.getFilePath()));
        return TVCConstants.NO_ERROR;
    }

    /**
     * 取消（中断）上传。中断之后恢复上传再用相同的参数调用uploadVideo即可。
     * @return 成功或者失败
     */
    public boolean cancleUploadVideo() {
        if (cosClient != null)
            return cosClient.cancelTask(requestId);
        return false;
    }

    private void getCosUploadInfo(TVCUploadInfo info, String vodSessionKey) {
        // 第一步 向UGC请求上传(获取COS认证信息)
        ugcClient.initUploadUGC(info, vodSessionKey, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                TXLog.e(TAG, "initUploadUGC->onFailure: " + e.toString());
                notifyUploadFailed(TVCConstants.ERR_UGC_REQUEST_FAILED, e.toString());

                //txReport(TXCDRDef.UPLOAD_EVENT_ID_REQUEST_UPLOAD, TVCConstants.ERR_UGC_REQUEST_FAILED, e.toString(), 0, 0);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    notifyUploadFailed(TVCConstants.ERR_UGC_REQUEST_FAILED, "HTTP Code:" + response.code());

                    //txReport(TXCDRDef.UPLOAD_EVENT_ID_REQUEST_UPLOAD, TVCConstants.ERR_UGC_REQUEST_FAILED, "HTTP Code:" + response.code(), 0, 0);

                    setSession(uploadInfo.getFilePath(), null);

                    TXLog.e(TAG, "initUploadUGC->http code: " + response.code());
                    throw new IOException("" + response);
                } else {
                    parseInitRsp(response.body().string());
                }
            }
        });
    }

    // 解析上传请求返回信息
    private void parseInitRsp(String rspString) {
        TXLog.i(TAG, "parseInitRsp: " + rspString);
        if (TextUtils.isEmpty(rspString)) {
            TXLog.e(TAG, "parseInitRsp->response is empty!");
            notifyUploadFailed(TVCConstants.ERR_UGC_PARSE_FAILED, "init response is empty");

            //txReport(TXCDRDef.UPLOAD_EVENT_ID_REQUEST_UPLOAD, TVCConstants.ERR_UGC_PARSE_FAILED, "init response is empty", 0, 0);

            setSession(uploadInfo.getFilePath(), null);

            return;
        }

        try {
            JSONObject jsonRsp = new JSONObject(rspString);
            int code = jsonRsp.optInt("code", -1);
            TXLog.i(TAG, "parseInitRsp: " + code);

            String message = jsonRsp.optString("message", "");
            JSONObject dataObj = jsonRsp.getJSONObject("data");

            if (0 != code) {
                notifyUploadFailed(TVCConstants.ERR_UGC_PARSE_FAILED, code + "|" + message);

                //txReport(TXCDRDef.UPLOAD_EVENT_ID_REQUEST_UPLOAD, TVCConstants.ERR_UGC_PARSE_FAILED, code + "|" + message, 0, 0);

                setSession(uploadInfo.getFilePath(), null);

                return;
            }
            JSONObject videoObj = dataObj.getJSONObject("video");
            cosVideoSign = videoObj.getString("storageSignature");
            cosVideoPath = videoObj.getString("storagePath");

            TXLog.d(TAG, "isNeedCover:" + uploadInfo.isNeedCover());
            if (uploadInfo.isNeedCover()) {
                JSONObject coverObj = dataObj.getJSONObject("cover");
                cosCoverSign = coverObj.getString("storageSignature");
                cosCoverPath = coverObj.getString("storagePath");
            }
            cosAppId = dataObj.getInt("storageAppId");
            cosBucket = dataObj.getString("storageBucket");
            uploadRegion = dataObj.getString("storageRegion");
            domain = dataObj.getString("domain");
            vodSessionKey = dataObj.getString("vodSessionKey");
            setSession(uploadInfo.getFilePath(), vodSessionKey);

            TXLog.d(TAG, "cosVideoSign=" + cosVideoSign);
            TXLog.d(TAG, "cosVideoPath=" + cosVideoPath);
            TXLog.d(TAG, "cosCoverSign=" + cosCoverSign);
            TXLog.d(TAG, "cosCoverPath=" + cosCoverPath);
            TXLog.d(TAG, "cosAppId=" + cosAppId);
            TXLog.d(TAG, "cosBucket=" + cosBucket);
            TXLog.d(TAG, "uploadRegion=" + uploadRegion);
            TXLog.d(TAG, "domain=" + domain);
            TXLog.d(TAG, "vodSessionKey=" + vodSessionKey);
        } catch (JSONException e) {
            TXLog.e(TAG, e.toString());
            notifyUploadFailed(TVCConstants.ERR_UGC_PARSE_FAILED, e.toString());
        }

        COSConfig cosConfig = new COSConfig();
        cosConfig.setEndPoint(uploadRegion);
        TXLog.d(TAG, "config end point: " + uploadRegion);
        cosClient = new COSClient(context, "" + cosAppId, cosConfig, null);

        //txReport(TXCDRDef.UPLOAD_EVENT_ID_REQUEST_UPLOAD, 0, "", 0, 0);

        boolean isResumeUpload = false;
        if (vodSessionKey != null && !vodSessionKey.isEmpty()) {
            isResumeUpload = true;
        }

        // 第二步 通过COS上传视频
        uploadCosVideo(isResumeUpload);
    }

    // 通过COS上传封面
    private void uploadCosCover(final boolean isResumeUpload) {

        coverUseTime = System.currentTimeMillis();

        PutObjectRequest putRequest = new PutObjectRequest();
        putRequest.setBucket(cosBucket);
        putRequest.setCosPath(cosCoverPath);
        putRequest.setSrcPath(uploadInfo.getCoverPath());
        putRequest.setSign(cosCoverSign);
        putRequest.setInsertOnly("0"); // 封面图片直接覆盖上传
        putRequest.setListener(new IUploadTaskListener() {
            @Override
            public void onProgress(COSRequest cosRequest, long currentSize, long totalSize) {
                TXLog.d(TAG, "uploadCosCover->progress: " + currentSize + "/" + totalSize);
                // 上传封面无进度
                //tvcListener.onProgress(currentSize, totalSize);

                coverSize = currentSize;
            }

            @Override
            public void onCancel(COSRequest cosRequest, COSResult cosResult) {
                notifyUploadFailed(TVCConstants.ERR_USER_CANCEL, cosResult.code + "|" + cosResult.msg);

                coverUseTime = System.currentTimeMillis() - coverUseTime;

                //txReport(TXCDRDef.UPLOAD_EVENT_ID_UPLOAD, TVCConstants.ERR_USER_CANCEL, cosResult.code + "|" + cosResult.msg, coverSize, coverUseTime);
            }

            @Override
            public void onSuccess(COSRequest cosRequest, COSResult cosResult) {
                startFinishUploadUGC((PutObjectResult) cosResult);
            }

            @Override
            public void onFailed(COSRequest cosRequest, COSResult cosResult) {
                String errMsg;
                if (cosResult.code == -1) {
                    errMsg = "upload cover failed!";
                } else {
                    errMsg = cosResult.code + "|" + cosResult.msg;
                }
                notifyUploadFailed(TVCConstants.ERR_UPLOAD_COVER_FAILED, errMsg);

                coverUseTime = System.currentTimeMillis() - coverUseTime;

                //txReport(TXCDRDef.UPLOAD_EVENT_ID_UPLOAD, TVCConstants.ERR_UPLOAD_COVER_FAILED, errMsg, coverSize, coverUseTime);

                // 删除session
                if (isResumeUpload) {
                    setSession(uploadInfo.getFilePath(), null);
                }
            }
        });
        requestId = putRequest.getRequestId();
        cosClient.putObject(putRequest);
    }


    // 解析cos上传视频返回信息
    private void startUploadCoverFile(PutObjectResult result, boolean isResumeUpload) {
//        coverUrl = result.access_url;

        // 第三步 通过COS上传封面
        if (uploadInfo.isNeedCover()) {
            uploadCosCover(isResumeUpload);
        } else {
            startFinishUploadUGC(result);
        }
    }


    // 通过COS上传视频
    private void uploadCosVideo(final boolean isResumeUpload) {
        new Thread() {
            @Override
            public void run() {

                videoUseTime = System.currentTimeMillis();

                TXLog.i(TAG, "uploadCosVideo:  cosBucket " + cosBucket + " cosVideoPath: " + cosVideoPath + "  path " + uploadInfo.getFilePath() + " Sign " + cosVideoSign);
                PutObjectRequest putRequest = new PutObjectRequest();
                putRequest.setBucket(cosBucket);
                putRequest.setCosPath(cosVideoPath);
                putRequest.setSrcPath(uploadInfo.getFilePath());
                putRequest.setSign(cosVideoSign);
                putRequest.setSliceFlag(true);
                if (isResumeUpload) {
                    putRequest.setInsertOnly("1");
                } else {
                    putRequest.setInsertOnly("0");
                }
                //putRequest.setSlice_size(2*1024*1024);md
                putRequest.setListener(new IUploadTaskListener() {
                    @Override
                    public void onProgress(COSRequest cosRequest, long currentSize, long totalSize) {
                        notifyUploadProgress(currentSize, totalSize);

                        videoSize = currentSize;
                    }

                    @Override
                    public void onCancel(COSRequest cosRequest, COSResult cosResult) {
                        notifyUploadFailed(TVCConstants.ERR_USER_CANCEL, "Err:" + cosResult.code + "|" + cosResult.msg);

                        videoUseTime = System.currentTimeMillis() - videoUseTime;

                        //txReport(TXCDRDef.UPLOAD_EVENT_ID_UPLOAD, TVCConstants.ERR_USER_CANCEL, "Err:" + cosResult.code + "|" + cosResult.msg, videoSize + coverSize, videoUseTime + coverUseTime);
                    }

                    @Override
                    public void onSuccess(COSRequest cosRequest, COSResult cosResult) {
                        TXLog.i(TAG, "uploadCosVideo path onSuccess  ");

                        videoUseTime = System.currentTimeMillis() - videoUseTime;

                        //txReport(TXCDRDef.UPLOAD_EVENT_ID_UPLOAD, 0, "", videoSize + coverSize, videoUseTime + coverUseTime);

                        startUploadCoverFile((PutObjectResult) cosResult, isResumeUpload);
                    }

                    @Override
                    public void onFailed(COSRequest cosRequest, COSResult cosResult) {
                        TXLog.i(TAG, "uploadCosVideo onFailed: " + cosResult.code + "    " + cosResult.msg);
                        String errMsg;
                        if (cosResult.code == -1) {
                            errMsg = "upload video failed!";
                        } else {
                            errMsg = cosResult.code + "|" + cosResult.msg;
                        }

                        // -20002任务取消，不清空session
                        if (cosResult.code != -20002 && isResumeUpload) {
                            setSession(uploadInfo.getFilePath(), null);
                        }

                        /************************************************************************
                         * 需要忽略本地存储的vodsession，重启上传流程的几种情况
                         * -177：  相同文件名的文件已存在
                         * -197：  文件不存在
                         * -4016： 请求的分片SHA-1值不正确的情况
                         * -4020： 文件尚未上传完成  新申请上传文件的sha值跟cos残余存在的文件sha值不一致
                         * **********************************************************************/
                        if (cosResult.code == -177 || cosResult.code == -197 || cosResult.code == -4016|| cosResult.code == -4020) {
                            uploadInfo.setIsShouldRetry(true);
                        }

                        notifyUploadFailed(TVCConstants.ERR_UPLOAD_VIDEO_FAILED, errMsg);

                        videoUseTime = System.currentTimeMillis() - videoUseTime;

                        //txReport(TXCDRDef.UPLOAD_EVENT_ID_UPLOAD, TVCConstants.ERR_UPLOAD_VIDEO_FAILED, errMsg, videoSize + coverSize, videoUseTime + coverUseTime);
                    }
                });
                requestId = putRequest.getRequestId();
                cosClient.putObject(putRequest);


            }
        }.start();
    }

    // 解析cos上传视频返回信息
    private void startFinishUploadUGC(PutObjectResult result) {
        String strAccessUrl = result.access_url;
        String strSrcUrl = result.source_url;
        TXLog.i(TAG, "startFinishUploadUGC: " + strAccessUrl + "  source: " + strSrcUrl);
        UGCFinishUploadInfo finishInfo = new UGCFinishUploadInfo();
        finishInfo.setFileName(uploadInfo.getFileName());
        finishInfo.setFileType(uploadInfo.getFileType());
        finishInfo.setFileSize(uploadInfo.getFileSize());
        finishInfo.setCverImgType(uploadInfo.getCoverImgType());
        finishInfo.setVideoFileId(videoFileId);
        finishInfo.setImgFieldId(coverFileId);
        finishInfo.setUploadSession(session);
        finishInfo.setDomain(domain);
        finishInfo.setVodSessionKey(vodSessionKey);


        // 第三步 上传结束
        ugcClient.finishUploadUGC(finishInfo, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                TXLog.i(TAG, "FinishUploadUGC: fail" + e.toString());
                notifyUploadFailed(TVCConstants.ERR_UGC_FINISH_REQUEST_FAILED, e.toString());

                //txReport(TXCDRDef.UPLOAD_EVENT_ID_UPLOAD_RESULT, TVCConstants.ERR_UGC_FINISH_REQUEST_FAILED, e.toString(), 0, 0);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    notifyUploadFailed(TVCConstants.ERR_UGC_FINISH_REQUEST_FAILED, "HTTP Code:" + response.code());
                    TXLog.e(TAG, "FinishUploadUGC->http code: " + response.code());

                    //txReport(TXCDRDef.UPLOAD_EVENT_ID_UPLOAD_RESULT, TVCConstants.ERR_UGC_FINISH_REQUEST_FAILED, "HTTP Code:" + response.code(), 0, 0);

                    throw new IOException("" + response);
                } else {
                    TXLog.i(TAG, "FinishUploadUGC Suc onResponse body : " + response.body().toString());
                    parseFinishRsp(response.body().string());
                }
            }
        });
    }


    // 解析结束上传返回信息
    private void parseFinishRsp(String rspString) {
        TXLog.i(TAG, "parseFinishRsp: " + rspString);
        if (TextUtils.isEmpty(rspString)) {
            TXLog.e(TAG, "parseFinishRsp->response is empty!");
            notifyUploadFailed(TVCConstants.ERR_UGC_FINISH_RESPONSE_FAILED, "finish response is empty");

            //txReport(TXCDRDef.UPLOAD_EVENT_ID_UPLOAD_RESULT, TVCConstants.ERR_UGC_FINISH_RESPONSE_FAILED, "finish response is empty", 0, 0);

            return;
        }
        try {
            JSONObject jsonRsp = new JSONObject(rspString);
            int code = jsonRsp.optInt("code", -1);
            String message = jsonRsp.optString("message", "");
            if (0 != code) {
                notifyUploadFailed(TVCConstants.ERR_UGC_FINISH_RESPONSE_FAILED, code + "|" + message);

                //txReport(TXCDRDef.UPLOAD_EVENT_ID_UPLOAD_RESULT, TVCConstants.ERR_UGC_FINISH_RESPONSE_FAILED, code + "|" + message, 0, 0);

                return;
            }
            JSONObject dataRsp = jsonRsp.getJSONObject("data");
            String coverUrl = "";
            if (uploadInfo.isNeedCover()) {
                JSONObject coverObj = dataRsp.getJSONObject("cover");
                coverUrl = coverObj.getString("url");
            }
            JSONObject videoObj = dataRsp.getJSONObject("video");
            String playUrl = videoObj.getString("url");
            videoFileId = dataRsp.getString("fileId");
            notifyUploadSuccess(videoFileId, playUrl, coverUrl);

            //txReport(TXCDRDef.UPLOAD_EVENT_ID_UPLOAD_RESULT, 0, "", 0, 0);

            TXLog.d(TAG, "playUrl:" + playUrl);
            TXLog.d(TAG, "coverUrl: " + coverUrl);
            TXLog.d(TAG, "videoFileId: " + videoFileId);
        } catch (JSONException e) {
            notifyUploadFailed(TVCConstants.ERR_UGC_FINISH_RESPONSE_FAILED, e.toString());

            //txReport(TXCDRDef.UPLOAD_EVENT_ID_UPLOAD_RESULT, TVCConstants.ERR_UGC_FINISH_RESPONSE_FAILED, e.toString(), 0, 0);
        }
    }

//    void txReport(int eventId, int errCode, String errInfo, long FileSize, long useTime) {
//        TXCDRExtInfo extInfo = new TXCDRExtInfo();
////        extInfo.sdk_id = TXDRDef.DR_SDK_ID_RTMPSDK;
////        extInfo.sdk_version = "0.0.0.0";
//        extInfo.command_id_comment = TXCDRDef.COMMAND_ID_COMMENT_UGC_UPLOAD_40401;
//
//        TXCDRHelper txdrHelper = new TXCDRHelper(context, TXCDRDef.COMMAND_ID_UGC_UPLOAD, TXCDRDef.MODULE_PUSH_SDK, extInfo);
//        txdrHelper.setEventValue(TXCDRDef.DR_KEY_UPLAOD_EVENT_ID, "" + eventId);
//        txdrHelper.setEventValue(TXCDRDef.DR_KEY_UPLAOD_ERR_CODE, "" + errCode);
//        txdrHelper.setEventValue(TXCDRDef.DR_KEY_UPLAOD_ERR_INFO, errInfo);
//        txdrHelper.setEventValue(TXCDRDef.DR_KEY_UPLOAD_FILE_SIZE, "" + FileSize);
//        txdrHelper.setEventValue(TXCDRDef.DR_KEY_UPLOAD_UP_USE_TIME, "" + useTime);
//        txdrHelper.reportEvent();
//    }

    // 断点续传
    // 本地保存 filePath --> <session, expireTime> 的映射集合，格式为json
    // session的过期时间是1天，参考文档：http://tapd.oa.com/VideoCloud_Vod/markdown_wikis/view/#1010095581005975321
    private String getSessionFromFilepath(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return null;
        }

        String session = "";
        if (mSharedPreferences != null) {
            try {
                String itemPath = filePath;
                if (mUserID != null && !mUserID.isEmpty()) {
                    itemPath += mUserID;
                }
                JSONObject json = new JSONObject(mSharedPreferences.getString(itemPath, ""));
                session = json.optString("session", "");
                long expiredTime = json.optLong("expiredTime", 0);
                // 过期了清空key
                if (expiredTime < System.currentTimeMillis() / 1000) {
                    session = "";
                    mShareEditor.remove(itemPath);
                    mShareEditor.commit();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return session;
    }

    private void setSession(String filePath, String session) {
        if (filePath == null || filePath.isEmpty()) {
            return;
        }
        if (mSharedPreferences != null) {
            try {
                // vodSessionKey 为空就表示删掉该记录
                String itemPath = filePath;
                if (mUserID != null && !mUserID.isEmpty()) {
                    itemPath += mUserID;
                }
                if (session == null || session.isEmpty()) {
                    mShareEditor.remove(itemPath);
                    mShareEditor.commit();
                } else {
                    String comment = "";
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("session", session);
                    jsonObject.put("expiredTime", System.currentTimeMillis() / 1000 + 24 * 60 * 60);
                    comment = jsonObject.toString();
                    mShareEditor.putString(itemPath, comment);
                    mShareEditor.commit();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
