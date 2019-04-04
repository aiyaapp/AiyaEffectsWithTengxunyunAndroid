package com.tencent.liteav.demo;

import android.app.Application;
import android.util.Log;

import com.aiyaapp.aiya.AYLicenseManager;
import com.aiyaapp.aiya.AyCore;
import com.aiyaapp.aiya.AyFaceTrack;
import com.tencent.bugly.crashreport.CrashReport;
import com.tencent.rtmp.TXLiveBase;

import java.io.File;

//import com.squareup.leakcanary.LeakCanary;
//import com.squareup.leakcanary.RefWatcher;


public class DemoApplication extends Application {

//    private RefWatcher mRefWatcher;
    private static DemoApplication instance;

    @Override
    public void onCreate() {

        super.onCreate();

        instance = this;

        TXLiveBase.setConsoleEnabled(true);
        CrashReport.UserStrategy strategy = new CrashReport.UserStrategy(getApplicationContext());
        strategy.setAppVersion(TXLiveBase.getSDKVersionStr());
        CrashReport.initCrashReport(getApplicationContext(),strategy);

        // ---------- 哎吖科技添加 开始----------
        AYLicenseManager.initLicense(getApplicationContext(), "477de67d19ba39fb656a4806c803b552", new AyCore.OnResultCallback() {
            @Override
            public void onResult(int ret) {
                Log.d("哎吖科技", "License初始化结果"+ret);
            }
        });

        // copy数据
        new Thread(new Runnable() {
            @Override
            public void run() {
                String dstPath = getExternalCacheDir() + "/aiya/effect";
                if (!new File(dstPath).exists()) {
                    AyFaceTrack.deleteFile(new File(dstPath));
                    AyFaceTrack.copyFileFromAssets("modelsticker", dstPath, getAssets());
                }
            }
        }).start();
        // ---------- 哎吖科技添加 结束----------
    }

//    public static RefWatcher getRefWatcher(Context context) {
//        DemoApplication application = (DemoApplication) context.getApplicationContext();
//        return application.mRefWatcher;
//    }

    public static DemoApplication getApplication() {
        return instance;
    }

}
