package com.tencent.liteav.demo.linkmic;

import android.Manifest;
import android.app.Activity;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.tencent.rtmp.ITXLivePushListener;
import com.tencent.rtmp.TXLiveBase;
import com.tencent.rtmp.TXLiveConstants;
import com.tencent.rtmp.TXLivePlayer;
import com.tencent.rtmp.TXLivePushConfig;
import com.tencent.rtmp.TXLivePusher;
import com.tencent.liteav.demo.R;
import com.tencent.liteav.demo.common.activity.QRCodeScanActivity;
import com.tencent.liteav.demo.common.widget.BeautySettingPannel;
import com.tencent.rtmp.ui.TXCloudVideoView;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public class LinkMicActivity extends Activity implements View.OnClickListener , ITXLivePushListener, BeautySettingPannel.IOnBeautyParamsChangeListener/*, ImageReader.OnImageAvailableListener*/{
    private static final String TAG = LinkMicActivity.class.getSimpleName();

    private TXLivePushConfig mLivePushConfig;
    private TXLivePusher     mLivePusher;
    private TXCloudVideoView mCaptureView;

    protected EditText      mRtmpUrlView;
    public TextView         mLogViewStatus;
    public TextView         mLogViewEvent;
    protected int           mActivityType;

    StringBuffer            mLogMsg = new StringBuffer("");
    private final int       mLogMsgLenLimit = 3000;

    private ScrollView       mScrollView;

    private Button           mBtnPushType;
    private Button           mBtnPlay;
    private Button           mBtnFaceBeauty;
    private BeautySettingPannel mBeautyPannel;
    private LinearLayout mBtnsTestsLinearLayout = null;

    private boolean          mVideoPublish;
    private boolean          mFrontCamera = true;

    private int              mBeautyLevel = 5;
    private int              mWhiteningLevel = 3;
    private int              mRuddyLevel = 2;
    private int              mBeautyStyle = TXLiveConstants.BEAUTY_STYLE_SMOOTH;

    private boolean          mMainPublish = true;

    private FrameLayout      mUrlScanLayout;

    private Vector<LinkMicPlayItem> mVecPlayItems = new Vector<>();

    private Bitmap           mBitmap;

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

    // 关注系统设置项“自动旋转”的状态切换
    private RotationObserver mRotationObserver = null;

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

        mVideoPublish = false;

        mLivePusher     = new TXLivePusher(getApplicationContext());
        mLivePusher.setBeautyFilter(mBeautyStyle, mBeautyLevel, mWhiteningLevel, mRuddyLevel);
        mLivePushConfig = new TXLivePushConfig();
        mLivePushConfig.enableHighResolutionCaptureMode(false);
        mLivePusher.setConfig(mLivePushConfig);

        mBitmap         = decodeResource(getResources(),R.drawable.watermark);

        mRotationObserver = new RotationObserver(new Handler());
        mRotationObserver.startObserver();
        
        onCreateView();

        checkPublishPermission();

        mPhoneListener = new TXPhoneStateListener(mLivePusher);
        TelephonyManager tm = (TelephonyManager) getApplicationContext().getSystemService(Service.TELEPHONY_SERVICE);
        tm.listen(mPhoneListener, PhoneStateListener.LISTEN_CALL_STATE);
    }
    
    protected void onCreateView() {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_linkmic);

        mBeautyPannel = (BeautySettingPannel) findViewById(R.id.beauty_pannel);
        mBeautyPannel.setBeautyParamsChangeListener(this);

        mBtnsTestsLinearLayout = (LinearLayout) findViewById(R.id.btns_tests);

        mCaptureView = (TXCloudVideoView) findViewById(R.id.video_view);
        mCaptureView.disableLog(true);

        mLogViewStatus = (TextView) findViewById(R.id.logViewStatus);
        mLogViewStatus.setVisibility(View.GONE);
        mLogViewStatus.setMovementMethod(new ScrollingMovementMethod());
        mLogViewStatus.setText("Log File Path:" + Environment.getExternalStorageDirectory().getAbsolutePath() + "/txRtmpLog");

        mLogViewEvent  = (TextView) findViewById(R.id.logViewEvent);
        mLogViewEvent.setMovementMethod(new ScrollingMovementMethod());

        mScrollView = (ScrollView)findViewById(R.id.scrollview);
        mScrollView.setVisibility(View.GONE);

        mRtmpUrlView   = (EditText) findViewById(R.id.roomid);
        mRtmpUrlView.setHint(" 请扫码输入推流地址...");
        mRtmpUrlView.setText("");

        //扫码推流
        Button btnScanPush = (Button) findViewById(R.id.btnScan);
        btnScanPush.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(LinkMicActivity.this, QRCodeScanActivity.class);
                startActivityForResult(intent, 100);
            }
        });
        btnScanPush.setEnabled(true);
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)btnScanPush.getLayoutParams();
        params.removeRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        btnScanPush.setLayoutParams(params);

        //初始化扫码拉流Layout
        initUrlScanLayout();

        //添加拉流
        Button btnAdd = (Button)findViewById(R.id.btnAdd);
        btnAdd.setVisibility(View.VISIBLE);
        btnAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean hasFreePlayer = false;
                for (LinkMicPlayItem item : mVecPlayItems) {
                    if (item.isRunning() == false) {
                        hasFreePlayer = true;
                        break;
                    }
                }
                if (hasFreePlayer == true) {
                    EditText editText = (EditText)findViewById(R.id.editText);
                    editText.setText("");
                    mUrlScanLayout.setVisibility(View.VISIBLE);
                } else {
                    Toast.makeText(getApplicationContext(), "播放器数目已达上限", Toast.LENGTH_SHORT).show();
                }
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
                    mVideoPublish = startPublishRtmp();
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
                } else {
                    mLivePushConfig.setFrontCamera(mFrontCamera);
                }
                btnChangeCam.setBackgroundResource(mFrontCamera ? R.drawable.camera_change : R.drawable.camera_change2);
            }
        });

        mBtnFaceBeauty = (Button)findViewById(R.id.btnFaceBeauty);
        mBtnFaceBeauty.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mBeautyPannel.setVisibility(mBeautyPannel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                mBtnsTestsLinearLayout.setVisibility(mBeautyPannel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
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

                for (LinkMicPlayItem item: mVecPlayItems) {
                    item.showLog(mLogViewStatus.getVisibility() == View.VISIBLE);
                }
            }
        });

        //推流类型
        mBtnPushType = (Button)findViewById(R.id.btnPushType);
        mBtnPushType.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mMainPublish = !mMainPublish;
                mBtnPushType.setBackgroundResource(mMainPublish ? R.drawable.mainpusher : R.drawable.subpusher);
                if (mVideoPublish) {
                    mLivePusher.setVideoQuality(mMainPublish ? TXLiveConstants.VIDEO_QUALITY_LINKMIC_MAIN_PUBLISHER : TXLiveConstants.VIDEO_QUALITY_LINKMIC_SUB_PUBLISHER, false, false);
                }
            }
        });

        //初始化播放Item
        mVecPlayItems.add(new LinkMicPlayItem(this, 1));
        mVecPlayItems.add(new LinkMicPlayItem(this, 2));
        mVecPlayItems.add(new LinkMicPlayItem(this, 3));

        View view = findViewById(android.R.id.content);
        view.setOnClickListener(this);
    }

    protected void initUrlScanLayout() {
        mUrlScanLayout = (FrameLayout)findViewById(R.id.url_scan_layout);
        mUrlScanLayout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        SpannableStringBuilder strNoticeText = new SpannableStringBuilder("注意：拉流地址必须添加防盗链key，请参考Android视频连麦方案");
        strNoticeText.setSpan(new ForegroundColorSpan(Color.BLUE), 21, 34, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        strNoticeText.setSpan(new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                Uri uri = Uri.parse("https://www.qcloud.com/document/product/454/9856");
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(intent);
            }
        }, 21, 34, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        TextView noticeText = (TextView)mUrlScanLayout.findViewById(R.id.noticeText);
        noticeText.setMovementMethod(LinkMovementMethod.getInstance());
        noticeText.setText(strNoticeText);

        Button btnScanPlay = (Button)mUrlScanLayout.findViewById(R.id.btnScan_play);
        btnScanPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(LinkMicActivity.this.getApplicationContext(), QRCodeScanActivity.class);
                startActivityForResult(intent, 200);
            }
        });

        Button btnConfirm = (Button)mUrlScanLayout.findViewById(R.id.btnConfirm);
        btnConfirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mUrlScanLayout.setVisibility(View.GONE);
                EditText editText = (EditText)findViewById(R.id.editText);
                String playUrl = editText.getText().toString();

                int playType = -1;
                if (TextUtils.isEmpty(playUrl) == false) {
                    if (playUrl.startsWith("rtmp://")) {
                        playType = TXLivePlayer.PLAY_TYPE_LIVE_RTMP;
                    } else if ((playUrl.startsWith("http://") || playUrl.startsWith("https://")) && playUrl.contains(".flv")) {
                        playType = TXLivePlayer.PLAY_TYPE_LIVE_FLV;
                    }
                }

                if (playType == -1) {
                    Toast.makeText(getApplicationContext(), "播放地址不合法，目前仅支持rtmp,flv播放方式!", Toast.LENGTH_SHORT).show();
                }
                else {
                    for (LinkMicPlayItem item: mVecPlayItems) {
                        if (item.isRunning() == false) {
                            item.startPlay(playUrl);
                            break;
                        }
                    }
                }
            }
        });

        Button btnCancel = (Button)mUrlScanLayout.findViewById(R.id.btnCancel);
        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mUrlScanLayout.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.back_tv:
                finish();
                break;
            default:
                break;
        }
        mBeautyPannel.setVisibility(View.GONE);
        mBtnsTestsLinearLayout.setVisibility(View.VISIBLE);
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.w(TAG, "vrender: activity onPause");

        if (mCaptureView != null) {
            mCaptureView.onPause();
        }

        if (mVideoPublish && mLivePusher != null) {
            mLivePusher.pausePusher();
        }

        for (LinkMicPlayItem item : mVecPlayItems) {
            item.pausePlay();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.w(TAG, "vrender: activity onResume");
        if (mCaptureView != null) {
            mCaptureView.onResume();
        }

        if (mVideoPublish && mLivePusher != null) {
            mLivePusher.resumePusher();
        }

        for (LinkMicPlayItem item : mVecPlayItems) {
            item.resumePlay();
        }
    }

    @Override
    public void onStop(){
        super.onStop();
        Log.w(TAG, "vrender: activity onStop");

        if (mCaptureView != null) {
            mCaptureView.onPause();
        }

        if (mVideoPublish && mLivePusher != null) {
            mLivePusher.pausePusher();
        }

        for (LinkMicPlayItem item : mVecPlayItems) {
            item.pausePlay();
        }
    }

	@Override
	public void onDestroy() {
		super.onDestroy();
        Log.w(TAG, "vrender: activity onDestroy");

        stopPublishRtmp();
        if (mCaptureView != null) {
            mCaptureView.onDestroy();
        }

        for (LinkMicPlayItem item : mVecPlayItems) {
            item.destroy();
        }
        mVecPlayItems.clear();

        mRotationObserver.stopObserver();

        TelephonyManager tm = (TelephonyManager) getApplicationContext().getSystemService(Service.TELEPHONY_SERVICE);
        tm.listen(mPhoneListener, PhoneStateListener.LISTEN_NONE);
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

        mCaptureView.setVisibility(View.VISIBLE);
        //mLivePushConfig.setWatermark(mBitmap, 10, 10);
        mLivePushConfig.setPauseImg(300,10);
        Bitmap bitmap = decodeResource(getResources(),R.drawable.pause_publish);
        mLivePushConfig.setPauseImg(bitmap);
        mLivePushConfig.setPauseFlag(TXLiveConstants.PAUSE_FLAG_PAUSE_VIDEO | TXLiveConstants.PAUSE_FLAG_PAUSE_AUDIO);
		mLivePushConfig.setConnectRetryCount(5);
        mLivePushConfig.setConnectRetryInterval(1);

        mLivePushConfig.setBeautyFilter(mBeautyLevel, mWhiteningLevel, mRuddyLevel);
        mLivePusher.setConfig(mLivePushConfig);
        mLivePusher.setPushListener(this);
        mLivePusher.startCameraPreview(mCaptureView);
        mLivePusher.setVideoQuality(mMainPublish ? TXLiveConstants.VIDEO_QUALITY_LINKMIC_MAIN_PUBLISHER : TXLiveConstants.VIDEO_QUALITY_LINKMIC_SUB_PUBLISHER,false,false);
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

        mLivePusher.stopCameraPreview(true);
        mLivePusher.stopScreenCapture();
        mLivePusher.setPushListener(null);
        mLivePusher.stopPusher();
        mCaptureView.setVisibility(View.GONE);

        enableQRCodeBtn(true);
        mBtnPlay.setBackgroundResource(R.drawable.play_start);

        if(mLivePushConfig != null) {
            mLivePushConfig.setPauseImg(null);
        }
    }

    @Override
    public void onPushEvent(int event, Bundle param) {
        String msg = param.getString(TXLiveConstants.EVT_DESCRIPTION);
        appendEventLog(event, msg);
        if (mScrollView.getVisibility() == View.VISIBLE){
            mLogViewEvent.setText(mLogMsg);
            scroll2Bottom(mScrollView, mLogViewEvent);
        }

        //错误还是要明确的报一下
        if (event < 0) {
            Toast.makeText(getApplicationContext(), param.getString(TXLiveConstants.EVT_DESCRIPTION), Toast.LENGTH_SHORT).show();
            if(event == TXLiveConstants.PUSH_ERR_OPEN_CAMERA_FAIL){
                stopPublishRtmp();
            }
        }

        if (event == TXLiveConstants.PUSH_ERR_NET_DISCONNECT) {
            stopPublishRtmp();
        }
        else if (event == TXLiveConstants.PUSH_WARNING_HW_ACCELERATION_FAIL) {
            Toast.makeText(getApplicationContext(), param.getString(TXLiveConstants.EVT_DESCRIPTION), Toast.LENGTH_SHORT).show();
            mLivePushConfig.setHardwareAcceleration(TXLiveConstants.ENCODE_VIDEO_SOFTWARE);
            mLivePusher.setConfig(mLivePushConfig);
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
    }


    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        onActivityRotation();
    }

    protected void onActivityRotation()
    {
        // 自动旋转打开，Activity随手机方向旋转之后，需要改变推流方向
        int mobileRotation = this.getWindowManager().getDefaultDisplay().getRotation();
        int pushRotation = TXLiveConstants.VIDEO_ANGLE_HOME_DOWN;
        switch (mobileRotation) {
            case Surface.ROTATION_0:
                pushRotation = TXLiveConstants.VIDEO_ANGLE_HOME_DOWN;
                break;
            case Surface.ROTATION_90:
                pushRotation = TXLiveConstants.VIDEO_ANGLE_HOME_RIGHT;
                break;
            case Surface.ROTATION_270:
                pushRotation = TXLiveConstants.VIDEO_ANGLE_HOME_LEFT;
                break;
            default:
                break;
        }
        mLivePusher.setRenderRotation(0); //因为activity也旋转了，本地渲染相对正方向的角度为0。
        mLivePushConfig.setHomeOrientation(pushRotation);
        mLivePusher.setConfig(mLivePushConfig);
    }

    /**
     * 判断Activity是否可旋转。只有在满足以下条件的时候，Activity才是可根据重力感应自动旋转的。
     * 系统“自动旋转”设置项打开；
     * @return false---Activity可根据重力感应自动旋转
     */
    protected boolean isActivityCanRotation()
    {
        // 判断自动旋转是否打开
        int flag = Settings.System.getInt(this.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0);
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
                    mLivePusher.setBeautyFilter(params.mBeautyStyle, mBeautyLevel, mWhiteningLevel, mRuddyLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_WHITE:
                mWhiteningLevel = params.mWhiteLevel;
                if (mLivePusher != null) {
                    mLivePusher.setBeautyFilter(params.mBeautyStyle, mBeautyLevel, mWhiteningLevel, mRuddyLevel);
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
                    mLivePusher.setBeautyFilter(params.mBeautyStyle, mBeautyLevel, mWhiteningLevel, mRuddyLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_BEAUTY_STYLE:
                if (mLivePusher != null) {
                    mLivePusher.setBeautyFilter(params.mBeautyStyle, mBeautyLevel, mWhiteningLevel, mRuddyLevel);
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
//            case BeautySettingPannel.BEAUTYPARAM_SHARPEN:
//                if (mLivePusher != null) {
//                    mLivePusher.setSharpenLevel(params.mSharpenLevel);
//                }
//                break;
//            case BeautySettingPannel.BEAUTYPARAM_CAPTURE_MODE:
//                if (mLivePusher != null) {
//                    boolean bEnable = ( 0 == params.mCaptureMode ? false : true);
//                    mLivePusher.enableHighResolutionCapture(bEnable);
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
            mResolver = LinkMicActivity.this.getContentResolver();
        }

        //屏幕旋转设置改变时调用
        @Override
        public void onChange(boolean selfChange)
        {
            super.onChange(selfChange);
            //更新按钮状态
            if (isActivityCanRotation()) {
                onActivityRotation();
            } else {
                mLivePushConfig.setHomeOrientation(TXLiveConstants.VIDEO_ANGLE_HOME_DOWN);
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
        if (data ==null || data.getExtras() == null || TextUtils.isEmpty(data.getExtras().getString("result"))) {
            return;
        }
        String result = data.getExtras().getString("result");
        if (requestCode == 200) {
            EditText editText = (EditText) findViewById(R.id.editText);
            editText.setText(result);
        }
        else if (requestCode == 100){
            mRtmpUrlView.setText(result);
        }
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

    /**
     * 实现EVENT VIEW的滚动显示
     * @param scroll
     * @param inner
     */
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

    public void setActivityType(int type) {
        mActivityType = type;
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
}