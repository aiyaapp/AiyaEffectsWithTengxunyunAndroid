package com.tencent.liteav.demo.aiya;

import android.content.Context;
import android.opengl.GLES20;

import com.aiyaapp.aiya.filter.AyBigEyeFilter;
import com.aiyaapp.aiya.filter.AyThinFaceFilter;
import com.aiyaapp.aiya.filter.AyTrackFilter;
import com.aiyaapp.aiya.render.AiyaGiftFilter;
import com.tencent.rtmp.TXLivePusher;

/**
 * Created by Administrator on 2017/12/6 0006.
 */

public class AiyaVideoProcessListener implements TXLivePusher.VideoCustomProcessListener {


    private AiyaGiftFilter mGiftFilter;
    private Context mContext;

    private AyBigEyeFilter mBigEyeFilter;
    private AyTrackFilter mTrackFilter;
    private AyThinFaceFilter mThinFaceFilter;


    public AiyaVideoProcessListener(Context mContext) {
        this.mContext = mContext;
    }


    @Override
    public int onTextureCustomProcess(int i, int i1, int i2) {
        if (mGiftFilter == null) {
            mGiftFilter = new AiyaGiftFilter(mContext, null);
            mGiftFilter.create();
            mGiftFilter.sizeChanged(i1, i2);
            mGiftFilter.setEffect("assets/sticker/one/meta.json");
        }
        if (mTrackFilter == null) {
            mTrackFilter = new AyTrackFilter(mContext);
            mTrackFilter.create();
            mTrackFilter.sizeChanged(i1, i2);
        }

        if (mBigEyeFilter == null) {
            mBigEyeFilter = new AyBigEyeFilter();
            mBigEyeFilter.create();
            mBigEyeFilter.sizeChanged(i1, i2);
            mBigEyeFilter.setDegree(1.0f);
        }

        if (mThinFaceFilter == null) {
            mThinFaceFilter = new AyThinFaceFilter();
            mThinFaceFilter.create();
            mThinFaceFilter.sizeChanged(i1, i2);
            mThinFaceFilter.setDegree(1.0f);
        }
        mTrackFilter.drawToTexture(i);
        //贴图处理
        mGiftFilter.setFaceDataID(mTrackFilter.getFaceDataID());
        int out = mGiftFilter.drawToTexture(i);
        //大眼处理
        mBigEyeFilter.setFaceDataID(mTrackFilter.getFaceDataID());
        out = mBigEyeFilter.drawToTexture(out);
        //瘦脸处理
        mThinFaceFilter.setFaceDataID(mTrackFilter.getFaceDataID());
        out = mThinFaceFilter.drawToTexture(out);

        //开启深度测试
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        return out;
    }


    @Override
    public void onDetectFacePoints(float[] floats) {
    }


    @Override
    public void onTextureDestoryed() {

    }
}
