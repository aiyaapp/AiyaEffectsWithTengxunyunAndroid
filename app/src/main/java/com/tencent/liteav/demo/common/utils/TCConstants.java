package com.tencent.liteav.demo.common.utils;

/**
 * 静态函数
 */
public class TCConstants {

    /**
     * UGC小视频录制信息
     */
    public static final String VIDEO_RECORD_TYPE        = "type";
    public static final String VIDEO_RECORD_RESULT      = "result";
    public static final String VIDEO_RECORD_DESCMSG     = "descmsg";
    public static final String VIDEO_RECORD_VIDEPATH    = "path";
    public static final String VIDEO_RECORD_COVERPATH   = "coverpath";
    public static final String VIDEO_RECORD_DURATION    = "duration";
    public static final String VIDEO_RECORD_RESOLUTION  = "resolution";

    public static final int VIDEO_RECORD_TYPE_PUBLISH   = 1;   // 推流端录制
    public static final int VIDEO_RECORD_TYPE_PLAY      = 2;   // 播放端录制
    public static final int VIDEO_RECORD_TYPE_UGC_RECORD = 3;   // 短视频录制
    public static final int VIDEO_RECORD_TYPE_EDIT      = 4;   // 短视频编辑


    /**
     * 用户可见的错误提示语
     */
    public static final String ERROR_MSG_NET_DISCONNECTED    = "网络异常，请检查网络";

    // UGCEditer
    public static final String ACTION_UGC_SINGLE_CHOOSE  = "com.tencent.qcloud.xiaozhibo.single";
    public static final String ACTION_UGC_MULTI_CHOOSE   = "com.tencent.qcloud.xiaozhibo.multi";

    public static final String INTENT_KEY_SINGLE_CHOOSE  = "single_video";
    public static final String INTENT_KEY_MULTI_CHOOSE   = "multi_video";

    public static final String DEFAULT_MEDIA_PACK_FOLDER = "txrtmp";      // UGC编辑器输出目录
    public static final int THUMB_COUNT = 10;
}
