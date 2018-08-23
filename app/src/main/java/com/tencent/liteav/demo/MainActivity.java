package com.tencent.liteav.demo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;

import com.tencent.liteav.demo.common.widget.ModuleEntryItemView;
import com.tencent.liteav.demo.play.LivePlayerActivity;
import com.tencent.liteav.demo.play.VodPlayerActivity;
import com.tencent.liteav.demo.push.LivePublisherActivity;

import java.util.ArrayList;
import com.aiyaapp.aiya.AiyaEffects;

public class MainActivity extends Activity {

    private static final String TAG = MainActivity.class.getName();
    private ListView mListView;
    private int mSelectedModuleId = 0;
    private ModuleEntryItemView mSelectedView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AiyaEffects.init(this,"477de67d19ba39fb656a4806c803b552");

        if ((getIntent().getFlags() & Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) != 0) {
            Log.d(TAG, "brought to front");
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        mListView = (ListView) findViewById(R.id.entry_lv);
        EntryAdapter adapter = new EntryAdapter();
        mListView.setAdapter(adapter);
    }

    private class EntryAdapter extends BaseAdapter {

        public class ItemInfo {
            String mName;
            int mIconId;
            Class mClass;

            public ItemInfo(String name, int iconId, Class c) {
                mName = name;
                mIconId = iconId;
                mClass = c;
            }
        }

        private ArrayList<ItemInfo> mData = new ArrayList<>();

        public EntryAdapter() {
            super();
            createData();
        }

        private void createData() {

            mData.add(new ItemInfo("RTMP 推流", R.drawable.push, LivePublisherActivity.class));
            mData.add(new ItemInfo("直播播放器", R.drawable.live, LivePlayerActivity.class));
            mData.add(new ItemInfo("低延时播放", R.drawable.realtime_play,  LivePlayerActivity.class));
            mData.add(new ItemInfo("点播播放器", R.drawable.play, VodPlayerActivity.class));
//            mData.add(new ItemInfo("双人音视频", R.drawable.mic,  LinkMicActivity.class));
        }

        @Override
        public int getCount() {
            return mData.size();
        }

        @Override
        public Object getItem(int position) {
            return mData.get(position);
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (null == convertView) {
                convertView = new ModuleEntryItemView(MainActivity.this);
            }
            ItemInfo info = (ItemInfo) getItem(position);
            ModuleEntryItemView v = (ModuleEntryItemView) convertView;
            v.setContent(info.mName, info.mIconId);
            v.setTag(info);
            v.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ItemInfo itemInfo = (ItemInfo) v.getTag();
                    Intent intent = new Intent(MainActivity.this, itemInfo.mClass);
                    intent.putExtra("TITLE", itemInfo.mName);
                    if (itemInfo.mIconId == R.drawable.play) {
                        intent.putExtra("PLAY_TYPE", LivePlayerActivity.ACTIVITY_TYPE_VOD_PLAY);
                    } else if (itemInfo.mIconId == R.drawable.live) {
                        intent.putExtra("PLAY_TYPE", LivePlayerActivity.ACTIVITY_TYPE_LIVE_PLAY);
                    } else if (itemInfo.mIconId == R.drawable.mic) {
                        intent.putExtra("PLAY_TYPE", LivePlayerActivity.ACTIVITY_TYPE_LINK_MIC);
                    } else if (itemInfo.mIconId == R.drawable.cut) {
                    } else if (itemInfo.mIconId == R.drawable.composite) {
                    } else if (itemInfo.mIconId == R.drawable.conf_icon) {
                    } else if (itemInfo.mIconId == R.drawable.realtime_play) {
                        intent.putExtra("PLAY_TYPE", LivePlayerActivity.ACTIVITY_TYPE_REALTIME_PLAY);
                    }
                    if (mSelectedView != null) {
                        mSelectedView.setBackgroudId(R.drawable.block_normal);
                    }
                    mSelectedModuleId = itemInfo.mIconId;
                    mSelectedView = (ModuleEntryItemView)v;
                    mSelectedView.setBackgroudId(R.drawable.block_pressed);
                    MainActivity.this.startActivity(intent);
                }
            });
            if (mSelectedModuleId == info.mIconId) {
                mSelectedView = v;
                mSelectedView.setBackgroudId(R.drawable.block_pressed);
            }

            return convertView;
        }
    }
}
