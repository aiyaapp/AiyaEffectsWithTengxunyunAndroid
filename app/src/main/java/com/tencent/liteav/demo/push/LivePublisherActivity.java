package com.tencent.liteav.demo.push;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Service;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.util.TypedValue;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.aiyaapp.aavt.gl.BaseFilter;
import com.aiyaapp.aiya.AiyaBeauty;
import com.aiyaapp.aiya.AiyaEffects;
import com.aiyaapp.aiya.AiyaTracker;
import com.aiyaapp.aiya.filter.AyBeautyFilter;
import com.aiyaapp.aiya.filter.AyBigEyeFilter;
import com.aiyaapp.aiya.filter.AyThinFaceFilter;
import com.aiyaapp.aiya.filter.AyTrackFilter;
import com.aiyaapp.aiya.render.AiyaGiftFilter;
import com.tencent.liteav.demo.R;
import com.tencent.liteav.demo.common.activity.QRCodeScanActivity;
import com.tencent.liteav.demo.common.widget.BeautySettingPannel;
import com.tencent.rtmp.ITXLivePushListener;
import com.tencent.rtmp.TXLiveBase;
import com.tencent.rtmp.TXLiveConstants;
import com.tencent.rtmp.TXLivePushConfig;
import com.tencent.rtmp.TXLivePusher;
import com.tencent.rtmp.ui.TXCloudVideoView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class LivePublisherActivity extends Activity implements View.OnClickListener , ITXLivePushListener, BeautySettingPannel.IOnBeautyParamsChangeListener/*, ImageReader.OnImageAvailableListener*/{
    private static final String TAG = LivePublisherActivity.class.getSimpleName();

    private TXLivePushConfig mLivePushConfig;
    private TXLivePusher mLivePusher;
    private TXCloudVideoView mCaptureView;

    private LinearLayout     mBitrateLayout;
    private BeautySettingPannel mBeautyPannelView;
    private ScrollView       mScrollView;
    private RadioGroup       mRadioGroupBitrate;
    private Button           mBtnBitrate;
    private Button           mBtnPlay;
    private Button           mBtnFaceBeauty;
    private Button           mBtnTouchFocus;
    private Button           mBtnHWEncode;
    private Button           mBtnOrientation;
    protected EditText       mRtmpUrlView;
    public TextView          mLogViewStatus;
    public TextView          mLogViewEvent;
    private boolean          mPortrait = true;         //手动切换，横竖屏推流
    private boolean          mFrontCamera = true;

    private boolean          mVideoPublish;

    private int              mVideoSrc = VIDEO_SRC_CAMERA;
    private boolean          mHWVideoEncode = true;
    private boolean          mTouchFocus  = true;
    private boolean          mHWListConfirmDialogResult = false;
    private int              mBeautyLevel = 5;
    private int              mWhiteningLevel = 3;
    private int              mRuddyLevel = 2;
    private int              mBeautyStyle = TXLiveConstants.BEAUTY_STYLE_SMOOTH;

    private Handler          mHandler = new Handler();

    private Bitmap           mBitmap;

    private static final int VIDEO_SRC_CAMERA = 0;
    private static final int VIDEO_SRC_SCREEN = 1;
    private int              mCurrentVideoResolution = TXLiveConstants.VIDEO_RESOLUTION_TYPE_360_640;

    protected StringBuffer          mLogMsg = new StringBuffer("");
    private final int mLogMsgLenLimit = 3000;
    private int mNetBusyCount = 0;
    private Handler mMainHandler;
    private TextView mNetBusyTips;
    LinearLayout mBottomLinear = null;

    private boolean     mIsRealTime = false;
    public static final int ACTIVITY_TYPE_PUBLISH      = 1;
    public static final int ACTIVITY_TYPE_LIVE_PLAY    = 2;
    public static final int ACTIVITY_TYPE_VOD_PLAY     = 3;
    public static final int ACTIVITY_TYPE_LINK_MIC     = 4;

//    private final int           REQUEST_CODE_CS = 10001;

//    private MediaProjectionManager      mProjectionManager;
//    private MediaProjection             mMediaProjection;
//    private VirtualDisplay mVirtualDisplay         = null;
//    private ImageReader mImageReader            = null;

    //获取推流地址
    private OkHttpClient mOkHttpClient = null;
    private boolean mFetching = false;

    // 关注系统设置项“自动旋转”的状态切换
    private RotationObserver mRotationObserver = null;

    private AyTrackFilter mTrackFilter;
    private AyBeautyFilter mAiyaBeautyFilter;
    private AyBigEyeFilter mAiyaBigEeyeFilter;
    private AyThinFaceFilter mAiyaThinFaceFilter;
    private AiyaGiftFilter mGiftFilter;

    // 临时的推流地址, 由哎吖科技提供, 方便测试
    private String RTMP_PUBLISH_URL = "rtmp://120.25.237.18:1935/live/824";

    private Bitmap decodeResource(Resources resources, int id) {
        TypedValue value = new TypedValue();
        resources.openRawResource(id, value);
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inTargetDensity = value.density;
        return BitmapFactory.decodeResource(resources, id, opts);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AiyaEffects.init(this,"477de67d19ba39fb656a4806c803b552");

        mLivePusher     = new TXLivePusher(this);
        mLivePushConfig = new TXLivePushConfig();
        mLivePusher.setBeautyFilter(mBeautyStyle, mBeautyLevel, mWhiteningLevel, mRuddyLevel);
        mLivePusher.setConfig(mLivePushConfig);

        mBitmap         = decodeResource(getResources(),R.drawable.watermark);

        mRotationObserver = new RotationObserver(new Handler());
        mRotationObserver.startObserver();

        mMainHandler = new Handler(Looper.getMainLooper());

        setContentView();

        LinearLayout backLL = (LinearLayout)findViewById(R.id.back_ll);
        backLL.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        TextView titleTV = (TextView) findViewById(R.id.title_tv);
        titleTV.setText(getIntent().getStringExtra("TITLE"));

        mBottomLinear = (LinearLayout)findViewById(R.id.btns_tests);

        checkPublishPermission();


        // TODO: 2017/12/25 0025 添加Aiya特效 

        mLivePusher.setVideoProcessListener(new TXLivePusher.VideoCustomProcessListener() {
            @Override
            public int onTextureCustomProcess(int texture, int width, int height) {
                if(mGiftFilter==null){
                    // 人脸跟踪
                    mTrackFilter = new AyTrackFilter(getBaseContext());
                    mTrackFilter.create();
                    mTrackFilter.sizeChanged(width, height);

                    // 美颜
                    mAiyaBeautyFilter = new AyBeautyFilter(AiyaBeauty.TYPE1);
                    mAiyaBeautyFilter.create();
                    mAiyaBeautyFilter.sizeChanged(width, height);
                    mAiyaBeautyFilter.setDegree(1.0f);

                    // 大眼
                    mAiyaBigEeyeFilter = new AyBigEyeFilter();
                    mAiyaBigEeyeFilter.create();
                    mAiyaBigEeyeFilter.sizeChanged(width, height);
                    mAiyaBigEeyeFilter.setDegree(0.5f);

                    // 瘦脸
                    mAiyaThinFaceFilter = new AyThinFaceFilter();
                    mAiyaThinFaceFilter.create();
                    mAiyaThinFaceFilter.sizeChanged(width, height);
                    mAiyaThinFaceFilter.setDegree(0.5f);

                    // 特效
                    mGiftFilter = new AiyaGiftFilter(getBaseContext(), new AiyaTracker(getBaseContext()));
                    mGiftFilter.create();
                    mGiftFilter.sizeChanged(width, height);
                    mGiftFilter.setEffect("assets/sticker/mogulin/meta.json");
                }

                // 进行人脸识别
                mTrackFilter.drawToTexture(texture);

                // 绘制美颜
                texture = mAiyaBeautyFilter.drawToTexture(texture);

                // 绘制礼物
                mGiftFilter.setFaceDataID(mTrackFilter.getFaceDataID());
                texture = mGiftFilter.drawToTexture(texture);

                // 绘制大眼
                mAiyaBigEeyeFilter.setFaceDataID(mTrackFilter.getFaceDataID());
                texture  = mAiyaBigEeyeFilter.drawToTexture(texture);

                // 绘制瘦脸
                mAiyaThinFaceFilter.setFaceDataID(mTrackFilter.getFaceDataID());
                texture = mAiyaThinFaceFilter.drawToTexture(texture);

                // 处理OpenGL状态
                GLES20.glDisable(GLES20.GL_BLEND);
                GLES20.glDisable(GLES20.GL_DEPTH_TEST);
                return texture;
            }

            @Override
            public void onDetectFacePoints(float[] floats) {

            }

            @Override
            public void onTextureDestoryed() {
                mGiftFilter.destroy();
                mAiyaBeautyFilter.destroy();
                mAiyaBigEeyeFilter.destroy();
                mAiyaThinFaceFilter.destroy();
            }
        });

        mPhoneListener = new TXPhoneStateListener(mLivePusher);
        TelephonyManager tm = (TelephonyManager) getApplicationContext().getSystemService(Service.TELEPHONY_SERVICE);
        tm.listen(mPhoneListener, PhoneStateListener.LISTEN_CALL_STATE);
    }

    static class TXPhoneStateListener extends PhoneStateListener {
        WeakReference<TXLivePusher> mPusher;
        public TXPhoneStateListener(TXLivePusher pusher) {
            mPusher = new WeakReference<TXLivePusher>(pusher);
        }
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            super.onCallStateChanged(state, incomingNumber);
            TXLivePusher pusher = mPusher.get();
            switch(state){
                //电话等待接听
                case TelephonyManager.CALL_STATE_RINGING:
                    if (pusher != null) pusher.pausePusher();
                    break;
                //电话接听
                case TelephonyManager.CALL_STATE_OFFHOOK:
                    if (pusher != null) pusher.pausePusher();
                    break;
                //电话挂机
                case TelephonyManager.CALL_STATE_IDLE:
                    if (pusher != null) pusher.resumePusher();
                    break;
            }
        }
    };
    private PhoneStateListener mPhoneListener = null;

    protected void initView() {
        mRtmpUrlView   = (EditText) findViewById(R.id.roomid);
        mLogViewEvent  = (TextView) findViewById(R.id.logViewEvent);
        mLogViewStatus = (TextView) findViewById(R.id.logViewStatus);

        Button scanBtn = (Button)findViewById(R.id.btnScan);
        scanBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(LivePublisherActivity.this, QRCodeScanActivity.class);
                startActivityForResult(intent, 100);
            }
        });
        scanBtn.setEnabled(true);
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)scanBtn.getLayoutParams();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            params.removeRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        }
        scanBtn.setLayoutParams(params);
    }

    public static void scroll2Bottom(final ScrollView scroll, final View inner) {
        if (scroll == null || inner == null) {
            return;
        }
        int offset = inner.getMeasuredHeight() - scroll.getMeasuredHeight();
        if (offset < 0) {
            offset = 0;
        }
        scroll.scrollTo(0, offset);
    }

    public void setContentView() {
        super.setContentView(R.layout.activity_publish);

        initView();

        mCaptureView = (TXCloudVideoView) findViewById(R.id.video_view);

        mNetBusyTips = (TextView) findViewById(R.id.netbusy_tv);
        mVideoPublish = false;
        mLogViewStatus.setVisibility(View.GONE);
        mLogViewStatus.setMovementMethod(new ScrollingMovementMethod());
        mLogViewEvent.setMovementMethod(new ScrollingMovementMethod());
        mScrollView = (ScrollView)findViewById(R.id.scrollview);
        mScrollView.setVisibility(View.GONE);

        mRtmpUrlView.setHint(" 请输入或扫二维码获取推流地址");
        mRtmpUrlView.setText(RTMP_PUBLISH_URL);

        Button btnNew = (Button)findViewById(R.id.btnNew);
        btnNew.setVisibility(View.VISIBLE);
        btnNew.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fetchPushUrl();
            }
        });

        //美颜p图部分
        mBeautyPannelView = (BeautySettingPannel) findViewById(R.id.layoutFaceBeauty);
        mBeautyPannelView.setBeautyParamsChangeListener(this);

        mBtnFaceBeauty = (Button)findViewById(R.id.btnFaceBeauty);
        mBtnFaceBeauty.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mBeautyPannelView.setVisibility(mBeautyPannelView.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                mBottomLinear.setVisibility(mBeautyPannelView.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            }
        });

        //播放部分
        mBtnPlay = (Button) findViewById(R.id.btnPlay);
        mBtnPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (mVideoPublish) {
                    stopPublishRtmp();
                } else {
                    if(mVideoSrc == VIDEO_SRC_CAMERA){
                        FixOrAdjustBitrate();  //根据设置确定是“固定”还是“自动”码率
                    }
                    else{
                        //录屏横竖屏采用两种分辨率，和摄像头推流逻辑不一样
                    }
                    mVideoPublish = startPublishRtmp();
                }
            }
        });


        //log部分
        final Button btnLog = (Button) findViewById(R.id.btnLog);
        btnLog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mLogViewStatus.getVisibility() == View.GONE) {
                    mLogViewStatus.setVisibility(View.VISIBLE);
                    mScrollView.setVisibility(View.VISIBLE);
                    mLogViewEvent.setText(mLogMsg);
                    scroll2Bottom(mScrollView, mLogViewEvent);
                    btnLog.setBackgroundResource(R.drawable.log_hidden);
                } else {
                    mLogViewStatus.setVisibility(View.GONE);
                    mScrollView.setVisibility(View.GONE);
                    btnLog.setBackgroundResource(R.drawable.log_show);
                }
            }
        });

        //切换前置后置摄像头
        final Button btnChangeCam = (Button) findViewById(R.id.btnCameraChange);
        btnChangeCam.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mFrontCamera = !mFrontCamera;

                if (mLivePusher.isPushing()) {
                    mLivePusher.switchCamera();
                }
                mLivePushConfig.setFrontCamera(mFrontCamera);
                btnChangeCam.setBackgroundResource(mFrontCamera ? R.drawable.camera_change : R.drawable.camera_change2);
            }
        });


        //开启硬件加速
        mBtnHWEncode = (Button) findViewById(R.id.btnHWEncode);
        mBtnHWEncode.getBackground().setAlpha(mHWVideoEncode ? 255 : 100);
        mBtnHWEncode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean HWVideoEncode = mHWVideoEncode;
                mHWVideoEncode = !mHWVideoEncode;
                mBtnHWEncode.getBackground().setAlpha(mHWVideoEncode ? 255 : 100);

                if (mHWVideoEncode){
                    if (mLivePushConfig != null) {
                        if(Build.VERSION.SDK_INT < 18){
                            Toast.makeText(getApplicationContext(), "硬件加速失败，当前手机API级别过低（最低18）", Toast.LENGTH_SHORT).show();
                            mHWVideoEncode = false;
                        }
                        }
                    }
                if(HWVideoEncode != mHWVideoEncode){
                    mLivePushConfig.setHardwareAcceleration(mHWVideoEncode ? TXLiveConstants.ENCODE_VIDEO_HARDWARE : TXLiveConstants.ENCODE_VIDEO_SOFTWARE);
                    if(mHWVideoEncode == false){
                        Toast.makeText(getApplicationContext(), "取消硬件加速", Toast.LENGTH_SHORT).show();
                    }
                    else{
                        Toast.makeText(getApplicationContext(), "开启硬件加速", Toast.LENGTH_SHORT).show();
                    }
                }
                if (mLivePusher != null) {
                    mLivePusher.setConfig(mLivePushConfig);
                }
            }
        });

        //码率自适应部分
        mBtnBitrate = (Button)findViewById(R.id.btnBitrate);
        mBitrateLayout = (LinearLayout)findViewById(R.id.layoutBitrate);
        mBtnBitrate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mBitrateLayout.setVisibility(mBitrateLayout.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            }
        });

        mRadioGroupBitrate = (RadioGroup)findViewById(R.id.resolutionRadioGroup);
        mRadioGroupBitrate.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                boolean oldMode = mIsRealTime;
                FixOrAdjustBitrate();
                if (oldMode != mIsRealTime && mLivePusher != null && mLivePusher.isPushing()) {
                    stopPublishRtmp();
                    startPublishRtmp();
                }
                mBitrateLayout.setVisibility(View.GONE);
            }
        });

        //手动对焦/自动对焦
        mBtnTouchFocus = (Button) findViewById(R.id.btnTouchFoucs);
        mBtnTouchFocus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mFrontCamera) {
                    return;
                }

                mTouchFocus = !mTouchFocus;
                mLivePushConfig.setTouchFocus(mTouchFocus);
                v.setBackgroundResource(mTouchFocus ? R.drawable.automatic : R.drawable.manual);

                if (mLivePusher.isPushing()) {
                    mLivePusher.stopCameraPreview(false);
                    mLivePusher.startCameraPreview(mCaptureView);
                }

                Toast.makeText(LivePublisherActivity.this, mTouchFocus ? "已开启手动对焦" : "已开启自动对焦", Toast.LENGTH_SHORT).show();
            }
        });

        //锁定Activity不旋转的情况下，才能进行横屏|竖屏推流切换
        mBtnOrientation = (Button) findViewById(R.id.btnPushOrientation);
        if (isActivityCanRotation()) {
            mBtnOrientation.setVisibility(View.GONE);
        }
        mBtnOrientation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mPortrait = ! mPortrait;
                int renderRotation = 0;
                int orientation = 0;
                boolean screenCaptureLandscape = false;
                if (mPortrait) {
                    mLivePushConfig.setHomeOrientation(TXLiveConstants.VIDEO_ANGLE_HOME_DOWN);
                    mBtnOrientation.setBackgroundResource(R.drawable.landscape);
                    orientation = TXLiveConstants.VIDEO_ANGLE_HOME_DOWN;
                    renderRotation = 0;
                } else {
                    mLivePushConfig.setHomeOrientation(TXLiveConstants.VIDEO_ANGLE_HOME_RIGHT);
                    mBtnOrientation.setBackgroundResource(R.drawable.portrait);
                    screenCaptureLandscape = true;
                    orientation = TXLiveConstants.VIDEO_ANGLE_HOME_RIGHT;
                    renderRotation = 90;
                }
                if(VIDEO_SRC_SCREEN == mVideoSrc){
                    //录屏横竖屏推流的判断条件是，视频分辨率取360*640还是640*360
                    switch (mCurrentVideoResolution){
                        case TXLiveConstants.VIDEO_RESOLUTION_TYPE_360_640:
                            if(screenCaptureLandscape)
                                mLivePushConfig.setVideoResolution(TXLiveConstants.VIDEO_RESOLUTION_TYPE_640_360);
                            else mLivePushConfig.setVideoResolution(TXLiveConstants.VIDEO_RESOLUTION_TYPE_360_640);
                            break;
                        case TXLiveConstants.VIDEO_RESOLUTION_TYPE_540_960:
                            if(screenCaptureLandscape)
                                mLivePushConfig.setVideoResolution(TXLiveConstants.VIDEO_RESOLUTION_TYPE_960_540);
                            else mLivePushConfig.setVideoResolution(TXLiveConstants.VIDEO_RESOLUTION_TYPE_540_960);
                            break;
                        case TXLiveConstants.VIDEO_RESOLUTION_TYPE_720_1280:
                            if(screenCaptureLandscape)
                                mLivePushConfig.setVideoResolution(TXLiveConstants.VIDEO_RESOLUTION_TYPE_1280_720);
                            else mLivePushConfig.setVideoResolution(TXLiveConstants.VIDEO_RESOLUTION_TYPE_720_1280);
                            break;
                    }

                }
                if (mLivePusher.isPushing()) {
                    if(VIDEO_SRC_CAMERA == mVideoSrc){
                        mLivePusher.setConfig(mLivePushConfig);
                    } else if(VIDEO_SRC_SCREEN == mVideoSrc){
                        mLivePusher.setConfig(mLivePushConfig);
                        mLivePusher.stopScreenCapture();
                        mLivePusher.startScreenCapture();
                    }
                }
                else mLivePusher.setConfig(mLivePushConfig);
                mLivePusher.setRenderRotation(renderRotation);
            }
        });

        View view = findViewById(android.R.id.content);
        view.setOnClickListener(this);
        mLogViewStatus.setText("Log File Path:"+ Environment.getExternalStorageDirectory().getAbsolutePath()+"/txRtmpLog");
    }

    protected void HWListConfirmDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(LivePublisherActivity.this);
        builder.setMessage("警告：当前机型不在白名单中,是否继续尝试硬编码？");
        builder.setPositiveButton("确认", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                mHWListConfirmDialogResult = true;
                throw new RuntimeException();
            }
        });
        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                mHWListConfirmDialogResult = false;
                throw new RuntimeException();
            }
        });
        builder.setCancelable(false);
        builder.create().show();
        try {
            Looper.loop();
        }catch (Exception e) {}
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            default:
                mBottomLinear.setVisibility(View.VISIBLE);
                mBeautyPannelView.setVisibility(View.GONE);
                mBitrateLayout.setVisibility(View.GONE);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mCaptureView != null) {
            mCaptureView.onResume();
        }

        if (mVideoPublish && mLivePusher != null && mVideoSrc == VIDEO_SRC_CAMERA) {
            mLivePusher.resumePusher();
            mLivePusher.resumeBGM();
        }
    }

    @Override
    public void onStop(){
        super.onStop();
        if (mCaptureView != null) {
            mCaptureView.onPause();
        }

        if (mVideoPublish && mLivePusher != null && mVideoSrc == VIDEO_SRC_CAMERA) {
            mLivePusher.pausePusher();
            mLivePusher.pauseBGM();
        }

    }

	@Override
	public void onDestroy() {
		super.onDestroy();
        stopPublishRtmp();
        if (mCaptureView != null) {
            mCaptureView.onDestroy();
        }

        mRotationObserver.stopObserver();
        TelephonyManager tm = (TelephonyManager) getApplicationContext().getSystemService(Service.TELEPHONY_SERVICE);
        tm.listen(mPhoneListener, PhoneStateListener.LISTEN_NONE);

    }

    // 重载android标准实现函数
    protected void enableQRCodeBtn(boolean bEnable) {
        //disable qrcode
        Button btnScan = (Button) findViewById(R.id.btnScan);
        if (btnScan != null) {
            btnScan.setEnabled(bEnable);
        }
    }

    //公用打印辅助函数
    protected void appendEventLog(int event, String message) {
        String str = "receive event: " + event + ", " + message;
        Log.d(TAG, str);
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
        String date = sdf.format(System.currentTimeMillis());
        while(mLogMsg.length() >mLogMsgLenLimit ){
            int idx = mLogMsg.indexOf("\n");
            if (idx == 0)
                idx = 1;
            mLogMsg = mLogMsg.delete(0,idx);
        }
        mLogMsg = mLogMsg.append("\n" + "["+date+"]" + message);
    }

    //公用打印辅助函数
    protected String getNetStatusString(Bundle status) {
        String str = String.format("%-14s %-14s %-12s\n%-14s %-14s %-12s\n%-14s %-14s %-12s\n%-14s %-12s",
                "CPU:"+status.getString(TXLiveConstants.NET_STATUS_CPU_USAGE),
                "RES:"+status.getInt(TXLiveConstants.NET_STATUS_VIDEO_WIDTH)+"*"+status.getInt(TXLiveConstants.NET_STATUS_VIDEO_HEIGHT),
                "SPD:"+status.getInt(TXLiveConstants.NET_STATUS_NET_SPEED)+"Kbps",
                "JIT:"+status.getInt(TXLiveConstants.NET_STATUS_NET_JITTER),
                "FPS:"+status.getInt(TXLiveConstants.NET_STATUS_VIDEO_FPS),
                "ARA:"+status.getInt(TXLiveConstants.NET_STATUS_AUDIO_BITRATE)+"Kbps",
                "QUE:"+status.getInt(TXLiveConstants.NET_STATUS_CODEC_CACHE)+"|"+status.getInt(TXLiveConstants.NET_STATUS_CACHE_SIZE),
                "DRP:"+status.getInt(TXLiveConstants.NET_STATUS_CODEC_DROP_CNT)+"|"+status.getInt(TXLiveConstants.NET_STATUS_DROP_SIZE),
                "VRA:"+status.getInt(TXLiveConstants.NET_STATUS_VIDEO_BITRATE)+"Kbps",
                "SVR:"+status.getString(TXLiveConstants.NET_STATUS_SERVER_IP),
                "AVRA:"+status.getInt(TXLiveConstants.NET_STATUS_SET_VIDEO_BITRATE));
        return str;
    }

    protected void clearLog() {
        mLogMsg.setLength(0);
        mLogViewEvent.setText("");
        mLogViewStatus.setText("");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 100:
                for (int ret : grantResults) {
                    if (ret != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }
                }
                break;
            default:
                break;
        }
    }

    private boolean checkPublishPermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            List<String> permissions = new ArrayList<>();
            if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
            if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)) {
                permissions.add(Manifest.permission.CAMERA);
            }
            if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)) {
                permissions.add(Manifest.permission.RECORD_AUDIO);
            }
            if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)) {
                permissions.add(Manifest.permission.READ_PHONE_STATE);
            }
            if (permissions.size() != 0) {
                ActivityCompat.requestPermissions(this,
                        permissions.toArray(new String[0]),
                        100);
                return false;
            }
        }

        return true;
    }

    private  boolean startPublishRtmp() {
        String rtmpUrl = "";
        String inputUrl = mRtmpUrlView.getText().toString();
        if (!TextUtils.isEmpty(inputUrl)) {
            String url[] = inputUrl.split("###");
            if (url.length > 0) {
                rtmpUrl = url[0];
            }
        }

        if (TextUtils.isEmpty(rtmpUrl) || (!rtmpUrl.trim().toLowerCase().startsWith("rtmp://"))) {
            Toast.makeText(getApplicationContext(), "推流地址不合法，目前支持rtmp推流!", Toast.LENGTH_SHORT).show();
            return false;
        }

        if(mVideoSrc != VIDEO_SRC_SCREEN){
            mCaptureView.setVisibility(View.VISIBLE);
        }
        // demo默认不加水印
        //mLivePushConfig.setWatermark(mBitmap, 0.02f, 0.05f, 0.2f);

        int customModeType = 0;

        //【示例代码1】设置自定义视频采集逻辑 （自定义视频采集逻辑不要调用startPreview）
//        customModeType |= TXLiveConstants.CUSTOM_MODE_VIDEO_CAPTURE;
//        mLivePushConfig.setVideoResolution(TXLiveConstants.VIDEO_RESOLUTION_TYPE_640_360);
//        mLivePushConfig.setAutoAdjustBitrate(false);
//        mLivePushConfig.setVideoBitrate(1300);
//        mLivePushConfig.setVideoEncodeGop(3);
//        new Thread() {  //视频采集线程
//            @Override
//            public void run() {
//                while (true) {
//                    try {
//                        FileInputStream in = new FileInputStream("/sdcard/dump_1280_720.yuv");
//                        int len = 1280 * 720 * 3 / 2;
//                        byte buffer[] = new byte[len];
//                        int count;
//                        while ((count = in.read(buffer)) != -1) {
//                            if (len == count) {
//                                mLivePusher.sendCustomVideoData(buffer, TXLivePusher.YUV_420SP);
//                            } else {
//                                break;
//                            }
//                            sleep(50, 0);
//                        }
//                        in.close();
//                    } catch (Exception e) {
//                        // TODO Auto-generated catch block
//                        e.printStackTrace();
//                    }
//                }
//            }
//        }.start();

        //【示例代码2】设置自定义音频采集逻辑（音频采样位宽必须是16）
//        mLivePushConfig.setAudioSampleRate(44100);
//        mLivePushConfig.setAudioChannels(1);
//        customModeType |= TXLiveConstants.CUSTOM_MODE_AUDIO_CAPTURE;
//        new Thread() {  //音频采集线程
//            @Override
//            public void run() {
//                while (true) {
//                    try {
//                        FileInputStream in = new FileInputStream("/sdcard/dump.pcm");
//                        int len = 2048;
//                        byte buffer[] = new byte[len];
//                        int count;
//                        while ((count = in.read(buffer)) != -1) {
//                            if (len == count) {
//                                mLivePusher.sendCustomPCMData(buffer);
//                            } else {
//                                break;
//                            }
//                            sleep(10, 0);
//                        }
//                        in.close();
//                    } catch (Exception e) {
//                        // TODO Auto-generated catch block
//                        e.printStackTrace();
//                    }
//                }
//            }
//        }.start();

        //【示例代码3】设置自定义视频预处理逻辑
//        customModeType |= TXLiveConstants.CUSTOM_MODE_VIDEO_PREPROCESS;
//        String path = this.getActivity().getApplicationInfo().dataDir + "/lib";
//        mLivePushConfig.setCustomVideoPreProcessLibrary(path +"/libvideo.so", "tx_video_process");

        //【示例代码4】设置自定义音频预处理逻辑
//        customModeType |= TXLiveConstants.CUSTOM_MODE_AUDIO_PREPROCESS;
//        String path = this.getActivity().getApplicationInfo().dataDir + "/lib";
//        mLivePushConfig.setCustomAudioPreProcessLibrary(path +"/libvideo.so", "tx_audio_process");

//        mLivePushConfig.setAutoAdjustBitrate(true);
//        mLivePushConfig.setMaxVideoBitrate(1200);
//        mLivePushConfig.setMinVideoBitrate(500);
//        mLivePushConfig.setVideoBitrate(600);
//        mLivePushConfig.enablePureAudioPush(true);
        //mLivePushConfig.enableHighResolutionCaptureMode(false);
        if (isActivityCanRotation()) {
            onActivityRotation();
        }
        mLivePushConfig.setCustomModeType(customModeType);
        mLivePusher.setPushListener(this);
        mLivePushConfig.setPauseImg(300,5);
        Bitmap bitmap = decodeResource(getResources(),R.drawable.pause_publish);
        mLivePushConfig.setPauseImg(bitmap);
        mLivePushConfig.setPauseFlag(TXLiveConstants.PAUSE_FLAG_PAUSE_VIDEO | TXLiveConstants.PAUSE_FLAG_PAUSE_AUDIO);
        if(mVideoSrc != VIDEO_SRC_SCREEN){
            mLivePushConfig.setFrontCamera(mFrontCamera);
            mLivePushConfig.setBeautyFilter(mBeautyLevel, mWhiteningLevel, mRuddyLevel);
            mLivePusher.setConfig(mLivePushConfig);
            mLivePusher.startCameraPreview(mCaptureView);
        }
        else{
            mLivePusher.setConfig(mLivePushConfig);
            mLivePusher.startScreenCapture();
        }

        mLivePusher.startPusher(rtmpUrl.trim());

        enableQRCodeBtn(false);
        clearLog();

        mLogMsg.append("liteav sdk version:"+ TXLiveBase.getSDKVersionStr());
        mLogViewEvent.setText(mLogMsg);

        mBtnPlay.setBackgroundResource(R.drawable.play_pause);

        appendEventLog(0, "点击推流按钮！");

        return true;
    }

    private void stopPublishRtmp() {
        mVideoPublish = false;
        mLivePusher.stopBGM();
        mLivePusher.stopCameraPreview(true);
        mLivePusher.stopScreenCapture();
        mLivePusher.setPushListener(null);
        mLivePusher.stopPusher();
        mCaptureView.setVisibility(View.GONE);

        if(mBtnHWEncode != null) {
            //mHWVideoEncode = true;
            mLivePushConfig.setHardwareAcceleration(mHWVideoEncode ? TXLiveConstants.ENCODE_VIDEO_HARDWARE : TXLiveConstants.ENCODE_VIDEO_SOFTWARE);
            mBtnHWEncode.setBackgroundResource(R.drawable.quick);
            mBtnHWEncode.getBackground().setAlpha(mHWVideoEncode ? 255 : 100);
        }

        enableQRCodeBtn(true);
        mBtnPlay.setBackgroundResource(R.drawable.play_start);

        if(mLivePushConfig != null) {
            mLivePushConfig.setPauseImg(null);
        }
    }

    public void FixOrAdjustBitrate() {
        if (mRadioGroupBitrate == null || mLivePushConfig == null || mLivePusher == null) {
            return;
        }

        RadioButton rb = (RadioButton) findViewById(mRadioGroupBitrate.getCheckedRadioButtonId());
        int mode = Integer.parseInt((String) rb.getTag());
        mIsRealTime = false;
        switch (mode) {
            case 5: /*720p*/
                if (mLivePusher != null) {
                    mLivePusher.setVideoQuality(TXLiveConstants.VIDEO_QUALITY_SUPER_DEFINITION, false, false);
                    mCurrentVideoResolution = TXLiveConstants.VIDEO_RESOLUTION_TYPE_720_1280;
                    //超清默认开启硬件加速
                    if (Build.VERSION.SDK_INT >= 18) {
                        mHWVideoEncode = true;
                    }
                    mBtnHWEncode.getBackground().setAlpha(255);
                }
                break;
            case 4: /*540p*/
                if (mLivePusher != null) {
                    mLivePusher.setVideoQuality(TXLiveConstants.VIDEO_QUALITY_HIGH_DEFINITION, false, false);
                    mCurrentVideoResolution = TXLiveConstants.VIDEO_RESOLUTION_TYPE_540_960;
                    mHWVideoEncode = false;
                    mBtnHWEncode.getBackground().setAlpha(100);
                }
                break;
            case 3: /*360p*/
                if (mLivePusher != null) {
                    mLivePusher.setVideoQuality(TXLiveConstants.VIDEO_QUALITY_STANDARD_DEFINITION, true, false);
                    mCurrentVideoResolution = TXLiveConstants.VIDEO_RESOLUTION_TYPE_360_640;
                    //标清默认开启了码率自适应，需要关闭码率自适应
                    mLivePushConfig.setAutoAdjustBitrate(false);
                    mLivePushConfig.setVideoBitrate(700);
                    mLivePusher.setConfig(mLivePushConfig);
                    //标清默认关闭硬件加速
                    mHWVideoEncode = false;
                    mBtnHWEncode.getBackground().setAlpha(100);
                }
                break;

            case 2: /*自动*/
                if (mLivePusher != null) {
                    mLivePusher.setVideoQuality(TXLiveConstants.VIDEO_QUALITY_STANDARD_DEFINITION, true, true);
                    mCurrentVideoResolution = TXLiveConstants.VIDEO_RESOLUTION_TYPE_360_640;
                    //标清默认关闭硬件加速
                    mHWVideoEncode = false;
                    mBtnHWEncode.getBackground().setAlpha(100);
                }
                break;
            case 1: /*实时*/
                if (mLivePusher != null) {
                    mLivePusher.setVideoQuality(TXLiveConstants.VIDEO_QUALITY_REALTIEM_VIDEOCHAT,  true, false);
                    mCurrentVideoResolution = TXLiveConstants.VIDEO_RESOLUTION_TYPE_360_640;
                    //超清默认开启硬件加速
                    if (Build.VERSION.SDK_INT >= 18) {
                        mHWVideoEncode = true;
                        mBtnHWEncode.getBackground().setAlpha(255);
                    }
                    mIsRealTime = true;
                }
                break;
            default:
                break;
        }
    }

    private void showNetBusyTips() {
        if (mNetBusyTips.isShown()) {
            return;
        }
        mNetBusyTips.setVisibility(View.VISIBLE);
        mMainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
               mNetBusyTips.setVisibility(View.GONE);
            }
        }, 5000);
    }

    private void fetchPushUrl() {
        if (mFetching) return;

        if (mOkHttpClient == null) {
            mOkHttpClient = new OkHttpClient().newBuilder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .build();
        }

        String reqUrl = "https://livedemo.tim.qq.com/interface.php";
        String body = "";
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("Action", "GetTestUrlPush");
            body = jsonObject.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        RequestBody requestBody = RequestBody.create(MediaType.parse("application/json"), body);
        Request request = new Request.Builder()
                .url(reqUrl)
                .post(requestBody)
                .build();
        Log.d(TAG, "start fetch push url");
        if (mFechCallback == null) {
            mFechCallback = new TXFechPushUrlCall(this);
        }
        mOkHttpClient.newCall(request).enqueue(mFechCallback);
        mFetching = true;
    }

    @Override
    public void onPushEvent(int event, Bundle param) {
        Log.e("NotifyCode","LivePublisherActivity :" + event);
        String msg = param.getString(TXLiveConstants.EVT_DESCRIPTION);
        appendEventLog(event, msg);
        if (mScrollView.getVisibility() == View.VISIBLE){
            mLogViewEvent.setText(mLogMsg);
            scroll2Bottom(mScrollView, mLogViewEvent);
        }
//        if (mLivePusher != null) {
//            mLivePusher.onLogRecord("[event:" + event + "]" + msg + "\n");
//        }
        //错误还是要明确的报一下
        if (event < 0) {
            Toast.makeText(getApplicationContext(), param.getString(TXLiveConstants.EVT_DESCRIPTION), Toast.LENGTH_SHORT).show();
            if(event == TXLiveConstants.PUSH_ERR_OPEN_CAMERA_FAIL || event == TXLiveConstants.PUSH_ERR_OPEN_MIC_FAIL){
                stopPublishRtmp();
            }
        }

        if (event == TXLiveConstants.PUSH_ERR_NET_DISCONNECT) {
            stopPublishRtmp();
        }
        else if (event == TXLiveConstants.PUSH_WARNING_HW_ACCELERATION_FAIL) {
            Toast.makeText(getApplicationContext(), param.getString(TXLiveConstants.EVT_DESCRIPTION), Toast.LENGTH_SHORT).show();
            mLivePushConfig.setHardwareAcceleration(TXLiveConstants.ENCODE_VIDEO_SOFTWARE);
            mBtnHWEncode.setBackgroundResource(R.drawable.quick2);
            mLivePusher.setConfig(mLivePushConfig);
            mHWVideoEncode = false;
        }
        else if (event == TXLiveConstants.PUSH_ERR_SCREEN_CAPTURE_UNSURPORT) {
            stopPublishRtmp();
        }
        else if (event == TXLiveConstants.PUSH_ERR_SCREEN_CAPTURE_START_FAILED) {
            stopPublishRtmp();
        } else if (event == TXLiveConstants.PUSH_EVT_CHANGE_RESOLUTION) {
            Log.d(TAG, "change resolution to " + param.getInt(TXLiveConstants.EVT_PARAM2) + ", bitrate to" + param.getInt(TXLiveConstants.EVT_PARAM1));
        } else if (event == TXLiveConstants.PUSH_EVT_CHANGE_BITRATE) {
            Log.d(TAG, "change bitrate to" + param.getInt(TXLiveConstants.EVT_PARAM1));
        } else if (event == TXLiveConstants.PUSH_WARNING_NET_BUSY) {
            ++mNetBusyCount;
            Log.d(TAG, "net busy. count=" + mNetBusyCount);
            showNetBusyTips();
        } else if (event == TXLiveConstants.PUSH_EVT_START_VIDEO_ENCODER) {
            int encType = param.getInt(TXLiveConstants.EVT_PARAM1);
            mHWVideoEncode = (encType == 1);
            mBtnHWEncode.getBackground().setAlpha(mHWVideoEncode ? 255 : 100);
        }
    }

    @Override
    public void onNetStatus(Bundle status) {
        String str = getNetStatusString(status);
        mLogViewStatus.setText(str);
        Log.d(TAG, "Current status, CPU:"+status.getString(TXLiveConstants.NET_STATUS_CPU_USAGE)+
                ", RES:"+status.getInt(TXLiveConstants.NET_STATUS_VIDEO_WIDTH)+"*"+status.getInt(TXLiveConstants.NET_STATUS_VIDEO_HEIGHT)+
                ", SPD:"+status.getInt(TXLiveConstants.NET_STATUS_NET_SPEED)+"Kbps"+
                ", FPS:"+status.getInt(TXLiveConstants.NET_STATUS_VIDEO_FPS)+
                ", ARA:"+status.getInt(TXLiveConstants.NET_STATUS_AUDIO_BITRATE)+"Kbps"+
                ", VRA:"+status.getInt(TXLiveConstants.NET_STATUS_VIDEO_BITRATE)+"Kbps");
//        if (mLivePusher != null){
//            mLivePusher.onLogRecord("[net state]:\n"+str+"\n");
//        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        onActivityRotation();
        super.onConfigurationChanged(newConfig);
    }

    protected void onActivityRotation()
    {
        // 自动旋转打开，Activity随手机方向旋转之后，需要改变推流方向
        int mobileRotation = this.getWindowManager().getDefaultDisplay().getRotation();
        int pushRotation = TXLiveConstants.VIDEO_ANGLE_HOME_DOWN;
        boolean screenCaptureLandscape = false;
        switch (mobileRotation) {
            case Surface.ROTATION_0:
                pushRotation = TXLiveConstants.VIDEO_ANGLE_HOME_DOWN;
                break;
            case Surface.ROTATION_90:
                pushRotation = TXLiveConstants.VIDEO_ANGLE_HOME_RIGHT;
                screenCaptureLandscape = true;
                break;
            case Surface.ROTATION_270:
                pushRotation = TXLiveConstants.VIDEO_ANGLE_HOME_LEFT;
                screenCaptureLandscape = true;
                break;
            default:
                break;
        }
        mLivePusher.setRenderRotation(0); //因为activity也旋转了，本地渲染相对正方向的角度为0。
        mLivePushConfig.setHomeOrientation(pushRotation);
        if (mLivePusher.isPushing()) {
            if(VIDEO_SRC_CAMERA == mVideoSrc){
                mLivePusher.setConfig(mLivePushConfig);
                mLivePusher.stopCameraPreview(true);
                mLivePusher.startCameraPreview(mCaptureView);
            }
            else if(VIDEO_SRC_SCREEN == mVideoSrc){
                //录屏横竖屏推流的判断条件是，视频分辨率取360*640还是640*360
                switch (mCurrentVideoResolution){
                    case TXLiveConstants.VIDEO_RESOLUTION_TYPE_360_640:
                        if(screenCaptureLandscape)
                            mLivePushConfig.setVideoResolution(TXLiveConstants.VIDEO_RESOLUTION_TYPE_640_360);
                        else mLivePushConfig.setVideoResolution(TXLiveConstants.VIDEO_RESOLUTION_TYPE_360_640);
                        break;
                    case TXLiveConstants.VIDEO_RESOLUTION_TYPE_540_960:
                        if(screenCaptureLandscape)
                            mLivePushConfig.setVideoResolution(TXLiveConstants.VIDEO_RESOLUTION_TYPE_960_540);
                        else mLivePushConfig.setVideoResolution(TXLiveConstants.VIDEO_RESOLUTION_TYPE_540_960);
                        break;
                    case TXLiveConstants.VIDEO_RESOLUTION_TYPE_720_1280:
                        if(screenCaptureLandscape)
                            mLivePushConfig.setVideoResolution(TXLiveConstants.VIDEO_RESOLUTION_TYPE_1280_720);
                        else mLivePushConfig.setVideoResolution(TXLiveConstants.VIDEO_RESOLUTION_TYPE_720_1280);
                        break;
                }
                mLivePusher.setConfig(mLivePushConfig);
                mLivePusher.stopScreenCapture();
                mLivePusher.startScreenCapture();
            }
        }
    }

    /**
     * 判断Activity是否可旋转。只有在满足以下条件的时候，Activity才是可根据重力感应自动旋转的。
     * 系统“自动旋转”设置项打开；
     * @return false---Activity可根据重力感应自动旋转
     */
    protected boolean isActivityCanRotation()
    {
        // 判断自动旋转是否打开
        int flag = Settings.System.getInt(this.getContentResolver(),Settings.System.ACCELEROMETER_ROTATION, 0);
        if (flag == 0) {
            return false;
        }
        return true;
    }

    @Override
    public void onBeautyParamsChange(BeautySettingPannel.BeautyParams params, int key) {
        switch (key) {
            case BeautySettingPannel.BEAUTYPARAM_EXPOSURE:
                if (mLivePusher != null) {
                    mLivePusher.setExposureCompensation(params.mExposure);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_BEAUTY:
                mBeautyLevel = params.mBeautyLevel;
                if (mLivePusher != null) {
                    mLivePusher.setBeautyFilter(mBeautyStyle, mBeautyLevel, mWhiteningLevel, mRuddyLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_WHITE:
                mWhiteningLevel = params.mWhiteLevel;
                if (mLivePusher != null) {
                    mLivePusher.setBeautyFilter(mBeautyStyle, mBeautyLevel, mWhiteningLevel, mRuddyLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_BIG_EYE:
                if (mLivePusher != null) {
                    mLivePusher.setEyeScaleLevel(params.mBigEyeLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_FACE_LIFT:
                if (mLivePusher != null) {
                    mLivePusher.setFaceSlimLevel(params.mFaceSlimLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_FILTER:
                if (mLivePusher != null) {
                    mLivePusher.setFilter(params.mFilterBmp);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_GREEN:
                if (mLivePusher != null) {
                    mLivePusher.setGreenScreenFile(params.mGreenFile);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_MOTION_TMPL:
                if (mLivePusher != null) {
                    mLivePusher.setMotionTmpl(params.mMotionTmplPath);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_RUDDY:
                mRuddyLevel = params.mRuddyLevel;
                if (mLivePusher != null) {
                    mLivePusher.setBeautyFilter(mBeautyStyle, mBeautyLevel, mWhiteningLevel, mRuddyLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_BEAUTY_STYLE:
                mBeautyStyle = params.mBeautyStyle;
                if (mLivePusher != null) {
                    mLivePusher.setBeautyFilter(mBeautyStyle, mBeautyLevel, mWhiteningLevel, mRuddyLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_FACEV:
                if (mLivePusher != null) {
                    mLivePusher.setFaceVLevel(params.mFaceVLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_FACESHORT:
                if (mLivePusher != null) {
                    mLivePusher.setFaceShortLevel(params.mFaceShortLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_CHINSLIME:
                if (mLivePusher != null) {
                    mLivePusher.setChinLevel(params.mChinSlimLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_NOSESCALE:
                if (mLivePusher != null) {
                    mLivePusher.setNoseSlimLevel(params.mNoseScaleLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_FILTER_MIX_LEVEL:
                if (mLivePusher != null) {
                    mLivePusher.setSpecialRatio(params.mFilterMixLevel/10.f);
                }
                break;
//            case BeautySettingPannel.BEAUTYPARAM_CAPTURE_MODE:
//                if (mLivePusher != null) {
//                    boolean bEnable = ( 0 == params.mCaptureMode ? false : true);
//                    mLivePusher.enableHighResolutionCapture(bEnable);
//                }
//                break;
//            case BeautySettingPannel.BEAUTYPARAM_SHARPEN:
//                if (mLivePusher != null) {
//                    mLivePusher.setSharpenLevel(params.mSharpenLevel);
//                }
//                break;
        }
    }

    //观察屏幕旋转设置变化，类似于注册动态广播监听变化机制
    private class RotationObserver extends ContentObserver
    {
        ContentResolver mResolver;

        public RotationObserver(Handler handler)
        {
            super(handler);
            mResolver = LivePublisherActivity.this.getContentResolver();
        }

        //屏幕旋转设置改变时调用
        @Override
        public void onChange(boolean selfChange)
        {
            super.onChange(selfChange);
            //更新按钮状态
            if (isActivityCanRotation()) {
                mBtnOrientation.setVisibility(View.GONE);
                onActivityRotation();
            } else {
                mBtnOrientation.setVisibility(View.VISIBLE);
                mPortrait = true;
                mLivePushConfig.setHomeOrientation(TXLiveConstants.VIDEO_ANGLE_HOME_DOWN);
                mBtnOrientation.setBackgroundResource(R.drawable.landscape);
                mLivePusher.setRenderRotation(0);
                mLivePusher.setConfig(mLivePushConfig);
            }

        }

        public void startObserver()
        {
            mResolver.registerContentObserver(Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION), false, this);
        }

        public void stopObserver()
        {
            mResolver.unregisterContentObserver(this);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != 100 || data ==null || data.getExtras() == null || TextUtils.isEmpty(data.getExtras().getString("result"))) {
            return;
        }
        String result = data.getExtras().getString("result");
        if (mRtmpUrlView != null) {
            mRtmpUrlView.setText(result);
        }
    }

    static class TXFechPushUrlCall implements Callback {
        WeakReference<LivePublisherActivity> mPusher;
        public TXFechPushUrlCall(LivePublisherActivity pusher) {
            mPusher = new WeakReference<LivePublisherActivity>(pusher);
        }

        @Override
        public void onFailure(Call call, IOException e) {
            LivePublisherActivity pusher = mPusher.get();
            if (pusher != null) {
                pusher.mFetching = false;
            }
            Log.e(TAG, "fetch push url failed ");
        }

        @Override
        public void onResponse(Call call, Response response) throws IOException {
            if (response.isSuccessful()) {
                String rspString = response.body().string();
                final LivePublisherActivity pusher = mPusher.get();
                if (pusher != null) {
                        try {
                            JSONObject jsonRsp = new JSONObject(rspString);
                            int code = jsonRsp.optInt("returnValue", -1);
                            String message = jsonRsp.optString("returnMsg", "");
                            JSONObject dataObj = jsonRsp.getJSONObject("returnData");
                            final String pushUrl = dataObj.optString("url_push");
                            final String rtmpPlayUrl = dataObj.optString("url_play_rtmp");
                            final String flvPlayUrl = dataObj.optString("url_play_flv");
                            final String hlsPlayUrl = dataObj.optString("url_play_hls");
                            final String realtimePlayUrl = dataObj.optString("url_play_acc");
                            pusher.mMainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    pusher.mRtmpUrlView.setText(pushUrl);
                                    Toast.makeText(pusher, "获取推流地址成功，对应播放地址已复制到剪贴板", Toast.LENGTH_LONG).show();

                                    String playUrl = String.format("rtmp 协议：%s\n", rtmpPlayUrl)
                                            + String.format("flv 协议：%s\n", flvPlayUrl)
                                            + String.format("hls 协议：%s\n", hlsPlayUrl)
                                            + String.format("低时延播放：%s", realtimePlayUrl);
                                    Log.d(TAG, "fetch play url : " + playUrl);
                                    try {
                                        ClipboardManager cmb = (ClipboardManager) pusher.getSystemService(Context.CLIPBOARD_SERVICE);
                                        cmb.setText(playUrl);
                                    } catch (Exception e) {

                                    }
                                }
                            });

                        Log.d(TAG, "fetch push url : " + pushUrl);

                    } catch(Exception e){
                        Log.e(TAG, "fetch push url error ");
                        Log.e(TAG, e.toString());
                    }

                } else {
                    pusher.mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(pusher, "获取推流地址失败", Toast.LENGTH_LONG).show();
                        }
                    });

                    Log.e(TAG, "fetch push url failed code: " + response.code());
                }
                pusher.mFetching = false;
            }
        }
    };
    private TXFechPushUrlCall mFechCallback = null;
}
