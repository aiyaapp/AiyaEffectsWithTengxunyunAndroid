package com.tencent.liteav.demo.linkmic;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.tencent.rtmp.TXLiveConstants;

import java.text.SimpleDateFormat;

/**
 * 用于先显示SDK log,上半部分显示状态,下半部分显示事件
 */
public class LinkMicLogView extends LinearLayout{
    private TextView      mStatusTextView;
    private TextView      mEventTextView;
    private ScrollView    mStatusScrollView;
    private ScrollView    mEventScrollView;

    StringBuffer          mLogMsg = new StringBuffer("");
    private final int     mLogMsgLenLimit = 3000;

    private boolean       mDisableLog = false;

    public LinkMicLogView(Context context) {
        this(context, null);
    }

    public LinkMicLogView(Context context, AttributeSet attrs) {
        super(context, attrs);

        setOrientation(VERTICAL);

        mStatusTextView = new TextView(context);
        mEventTextView  = new TextView(context);
        mStatusScrollView = new ScrollView(context);
        mEventScrollView  = new ScrollView(context);

        LinearLayout.LayoutParams statusScrollViewParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0);
        statusScrollViewParams.weight = 1.0f;
        mStatusScrollView.setLayoutParams(statusScrollViewParams);
        mStatusScrollView.setBackgroundColor(0x60ffffff);
        mStatusScrollView.setVerticalScrollBarEnabled(true);
        mStatusScrollView.setScrollbarFadingEnabled(true);

        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        mStatusTextView.setLayoutParams(statusParams);
        mStatusTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        mStatusTextView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        mStatusTextView.setPadding(dip2px(context, 2.0f), dip2px(context, 2.0f), dip2px(context, 2.0f), dip2px(context, 2.0f));
        mStatusTextView.setTextColor(0XFF333333);
        mStatusTextView.setTextSize(11);

        mStatusScrollView.addView(mStatusTextView);

        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0);
        scrollParams.weight = 1.0f;
        mEventScrollView.setLayoutParams(scrollParams);
        mEventScrollView.setBackgroundColor(0x60ffffff);
        mEventScrollView.setVerticalScrollBarEnabled(true);
        mEventScrollView.setScrollbarFadingEnabled(true);

        FrameLayout.LayoutParams eventParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        mEventTextView.setLayoutParams(eventParams);
        mEventTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        mEventTextView.setPadding(dip2px(context, 2.0f), dip2px(context, 2.0f), dip2px(context, 2.0f), dip2px(context, 2.0f));
        mEventTextView.setTextColor(0XFF333333);
        mStatusTextView.setTextSize(12);

        mEventScrollView.addView(mEventTextView);

        addView(mStatusScrollView);
        addView(mEventScrollView);
    }

    private int dip2px(Context context, float dpValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }

    public void setLogText(Bundle status, Bundle event, int eventId) {
        if (mDisableLog/* || getVisibility() == GONE*/) {
            return;
        }

        if (status != null) {
            mStatusTextView.setText(getNetStatusString(status));
        }

        if (event != null) {
            String message = event.getString(TXLiveConstants.EVT_DESCRIPTION);
            if (message != null) {
                appendEventLog(eventId, message);
                mEventTextView.setText(mLogMsg.toString());
                scroll2Bottom(mEventScrollView, mEventTextView);
            }
        }
    }

    //公用打印辅助函数
    protected void appendEventLog(int event, String message) {
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
        String str = String.format("%-14s %-14s %-14s\n%-14s %-14s %-14s\n%-14s %-14s %-14s\n%-14s",
                "CPU:"+status.getString(TXLiveConstants.NET_STATUS_CPU_USAGE),
                "RES:"+status.getInt(TXLiveConstants.NET_STATUS_VIDEO_WIDTH)+"*"+status.getInt(TXLiveConstants.NET_STATUS_VIDEO_HEIGHT),
                "SPD:"+status.getInt(TXLiveConstants.NET_STATUS_NET_SPEED)+"Kbps",
                "JIT:"+status.getInt(TXLiveConstants.NET_STATUS_NET_JITTER),
                "FPS:"+status.getInt(TXLiveConstants.NET_STATUS_VIDEO_FPS),
                "ARA:"+status.getInt(TXLiveConstants.NET_STATUS_AUDIO_BITRATE)+"Kbps",
                "QUE:"+status.getInt(TXLiveConstants.NET_STATUS_CODEC_CACHE)+"|"+status.getInt(TXLiveConstants.NET_STATUS_CACHE_SIZE),
                "DRP:"+status.getInt(TXLiveConstants.NET_STATUS_CODEC_DROP_CNT)+"|"+status.getInt(TXLiveConstants.NET_STATUS_DROP_SIZE),
                "VRA:"+status.getInt(TXLiveConstants.NET_STATUS_VIDEO_BITRATE)+"Kbps",
                "SVR:"+status.getString(TXLiveConstants.NET_STATUS_SERVER_IP));
        return str;
    }

    public void clearLog() {
        mLogMsg.setLength(0);
        mStatusTextView.setText("");
        mEventTextView.setText("");
    }

    public void show(boolean enable) {
        setVisibility(enable ? View.VISIBLE : View.GONE);
    }

    public void disableLog(boolean disable) {
        mDisableLog = disable;
    }

    private void scroll2Bottom(final ScrollView scroll, final View inner) {
        if (scroll == null || inner == null) {
            return;
        }
        int offset = inner.getMeasuredHeight() - scroll.getMeasuredHeight();
        if (offset < 0) {
            offset = 0;
        }
        scroll.scrollTo(0, offset);
    }
}
