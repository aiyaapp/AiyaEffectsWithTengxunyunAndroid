package com.tencent.liteav.demo.linkmic;

import android.app.Activity;
import android.graphics.drawable.AnimationDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.tencent.rtmp.ITXLivePlayListener;
import com.tencent.rtmp.TXLiveConstants;
import com.tencent.rtmp.TXLivePlayConfig;
import com.tencent.rtmp.TXLivePlayer;
import com.tencent.liteav.demo.R;
import com.tencent.rtmp.ui.TXCloudVideoView;


public class LinkMicPlayItem implements ITXLivePlayListener {
    private boolean              mRunning = false;
    private String               mPlayUrl = "";

    private TXCloudVideoView    mVideoView;
    private ImageView           mLoadingImg;
    private FrameLayout         mLoadingBkg;
    private Button              mBtnClose;
    private LinkMicLogView      mLogView;

    private TXLivePlayer mTXLivePlayer;
    private TXLivePlayConfig mTXLivePlayConfig = new TXLivePlayConfig();

    public LinkMicPlayItem(Activity activity, int index) {
        int videoViewId = R.id.play_video_view1;
        int loadingImgId = R.id.loading_imageview1;
        int loadingBkgId = R.id.loading_background1;
        int closeId = R.id.btn_close1;
        if (index == 1) {
            videoViewId = R.id.play_video_view1;
            loadingImgId = R.id.loading_imageview1;
            loadingBkgId = R.id.loading_background1;
            closeId = R.id.btn_close1;
        }
        else if (index == 2) {
            videoViewId = R.id.play_video_view2;
            loadingImgId = R.id.loading_imageview2;
            loadingBkgId = R.id.loading_background2;
            closeId = R.id.btn_close2;
        }
        else if (index == 3) {
            videoViewId = R.id.play_video_view3;
            loadingImgId = R.id.loading_imageview3;
            loadingBkgId = R.id.loading_background3;
            closeId = R.id.btn_close3;
        }

        mVideoView = (TXCloudVideoView) activity.findViewById(videoViewId);
        mLoadingImg = (ImageView) activity.findViewById(loadingImgId);
        mLoadingBkg = (FrameLayout) activity.findViewById(loadingBkgId);
        mBtnClose = (Button) activity.findViewById(closeId);
        mBtnClose.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                stopPlay();
            }
        });

//        mLogView = new LinkMicLogView(activity);
//        mVideoView.addView(mLogView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
//        mLogView.setVisibility(View.GONE);
    }

    private void initPlayer() {
        mTXLivePlayer = new TXLivePlayer(mVideoView.getContext().getApplicationContext());
        mTXLivePlayer.setPlayListener(this);
        mTXLivePlayer.enableHardwareDecode(true);
        mTXLivePlayer.setRenderMode(TXLiveConstants.RENDER_MODE_FULL_FILL_SCREEN);
        mTXLivePlayer.setConfig(mTXLivePlayConfig);
    }
    public boolean isRunning() {
        return mRunning;
    }

    public void startLoading() {
        mBtnClose.setVisibility(View.GONE);
        mLoadingBkg.setVisibility(View.VISIBLE);
        mLoadingImg.setVisibility(View.VISIBLE);
        mLoadingImg.setImageResource(R.drawable.loading_animation);
        AnimationDrawable ad = (AnimationDrawable) mLoadingImg.getDrawable();
        ad.start();
    }

    public void stopLoading(boolean showCloseButton) {
        mBtnClose.setVisibility(showCloseButton ? View.VISIBLE: View.GONE);
        mLoadingBkg.setVisibility(View.GONE);
        mLoadingImg.setVisibility(View.GONE);
        AnimationDrawable ad = (AnimationDrawable) mLoadingImg.getDrawable();
        if (ad != null) {
            ad.stop();
        }
    }

    public void startPlay(String playUrl) {
        clearLog();

        if (playUrl != null && playUrl.length() > 0) {
            if (mTXLivePlayer == null) {
                initPlayer();
            }

            int playType = -1;
            if ( playUrl.startsWith("rtmp://")) {
                playType = TXLivePlayer.PLAY_TYPE_LIVE_RTMP;
            } else if ((playUrl.startsWith("http://") || playUrl.startsWith("https://"))&& playUrl.contains(".flv")) {
                playType = TXLivePlayer.PLAY_TYPE_LIVE_FLV;
            }

            if (playType != -1) {
                mRunning = true;
                mPlayUrl = playUrl;
                startLoading();
                if (mTXLivePlayer != null) {
                    mTXLivePlayer.setPlayerView(mVideoView);
//                    if (getParamsFromStreamUrl("bizid", playUrl) != null && getParamsFromStreamUrl("txSecret", playUrl) != null && getParamsFromStreamUrl("txTime", playUrl) != null) {
//                        mTXLivePlayer.startPlay(playUrl, TXLivePlayer.PLAY_TYPE_LIVE_RTMP_ACC);
//                    }
//                    else
                    {
                        mTXLivePlayer.startPlay(playUrl, TXLivePlayer.PLAY_TYPE_LIVE_RTMP_ACC);
                    }

                    if (mLogView == null) {
                        mLogView = new LinkMicLogView(mVideoView.getContext());
                        mLogView.setVisibility(View.GONE);
                    }
                    mVideoView.removeView(mLogView);
                    mVideoView.addView(mLogView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
                }

            }
        }
    }

    public void stopPlay() {
        mRunning = false;
        mPlayUrl = "";
        stopLoading(false);
        if (mTXLivePlayer != null) {
            mTXLivePlayer.stopPlay(true);
        }
    }

    public void pausePlay() {
        stopLoading(false);
        if (mTXLivePlayer != null) {
            mTXLivePlayer.stopPlay(true);
        }
    }

    public void resumePlay() {
        startPlay(mPlayUrl);
    }

    public void setLogText(Bundle status, Bundle event, int eventId) {
        if (mLogView != null) {
            mLogView.setLogText(status, event, eventId);
        }
    }

    public void clearLog() {
        if (mLogView != null) {
            mLogView.clearLog();
        }
    }

    public void showLog(boolean show) {
        if (mRunning) {
            if (mLogView != null) {
                mLogView.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
            }
        }
        else {
            if (mLogView != null) {
                mLogView.setVisibility(View.INVISIBLE);
            }
        }
    }

    public void destroy() {
        stopPlay();

        if (mVideoView != null) {
            mVideoView.onDestroy();
        }
        if (mTXLivePlayer != null) {
            mTXLivePlayer.setPlayListener(null);
        }
        mTXLivePlayer = null;
    }

    @Override
    public void onPlayEvent(final int event, final Bundle param) {
        if (event == TXLiveConstants.PLAY_EVT_PLAY_BEGIN)
        {
            stopLoading(true);
        }
        else if (event == TXLiveConstants.PLAY_ERR_NET_DISCONNECT || event == TXLiveConstants.PLAY_EVT_PLAY_END /*|| event == TXLiveConstants.PLAY_ERR_GET_RTMP_ACC_URL_FAIL*/)
        {
            stopPlay();
        }

        setLogText(null, param, event);
    }

    @Override
    public void onNetStatus(final Bundle status) {
        setLogText(status, null, 0);
    }

    private String getParamsFromStreamUrl(String paramName, String streamUrl) {
        if (paramName == null || paramName.length() == 0 || streamUrl == null || streamUrl.length() == 0) {
            return null;
        }
        paramName = paramName.toLowerCase();
        String [] strArrays = streamUrl.split("[?&]");
        for (String strItem: strArrays) {
            if (strItem.indexOf("=") != -1) {
                String[] array = strItem.split("[=]");
                if (array.length == 2) {
                    String name = array[0];
                    String value = array[1];
                    if (name != null) {
                        name = name.toLowerCase();
                        if (name.equalsIgnoreCase(paramName)) {
                            return value;
                        }
                    }
                }
            }
        }

        return null;
    }
}
