package com.wink.xgjhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 录制视频管理页（纯黑）：全部/视频/录制/下载 四标签 + 卡片列表 + ⋮操作菜单 */
public class DownloadManagerActivity extends Activity {

    private static final String[] TABS = {"全部", "视频", "录制", "下载"};
    private String currentTab = "全部";
    private LinearLayout listBox;
    private LinearLayout tabBar;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private DownloadManager dm;
    private long dlId = -1;
    private String dlUrl = null;
    private final Runnable refreshRun = new Runnable() {
        public void run() {
            refresh();
            handler.postDelayed(this, 2000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        String passthrough = getIntent().getStringExtra("url");
        if (passthrough != null && passthrough.length() > 0) {
            String t = getIntent().getStringExtra("type");
            RecEngine.add(this, t == null ? RecEngine.TYPE_DL : t, "资源嗅探", passthrough);
            if (RecEngine.TYPE_DL.equals(t)) enqueueDownload(passthrough);
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        root.addView(column, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // 顶部导航栏
        tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setGravity(Gravity.CENTER);
        tabBar.setPadding(8, 18, 8, 10);
        for (String tab : TABS) {
            TextView tv = new TextView(this);
            tv.setText(tab);
            tv.setTextSize(15);
            tv.setTag(tab);
            tv.setPadding(22, 6, 22, 10);
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    currentTab = (String) v.getTag();
                    refreshTabs();
                    refresh();
                }
            });
            tabBar.addView(tv);
        }
        column.addView(tabBar);

        // 列表区
        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(listBox, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        column.addView(sv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

        setContentView(root);
        refreshTabs();
        refresh();
        handler.postDelayed(refreshRun, 2000);
    }

    void refreshTabs() {
        for (int i = 0; i < tabBar.getChildCount(); i++) {
            TextView tv = (TextView) tabBar.getChildAt(i);
            boolean sel = currentTab.equals(tv.getTag());
            tv.setTextColor(sel ? 0xFF4D9AFF : 0xFFCCCCCC);
            tv.getPaint().setUnderlineText(sel);
        }
    }

    boolean match(JSONObject o) {
        String type = o.optString("type");
        String st = o.optString("status");
        boolean active = RecEngine.ST_REC.equals(st) || RecEngine.ST_DL.equals(st);
        boolean finished = RecEngine.ST_DONE.equals(st) || RecEngine.ST_OK.equals(st);
        if (currentTab.equals("全部")) return finished || active;
        // 下载栏：进行中的录制/下载
        if (currentTab.equals("下载")) return active;
        if (currentTab.equals("录制")) return RecEngine.TYPE_REC.equals(type) && finished;
        // 视频：已结束/已完成的成品
        return finished;
    }

    void refresh() {
        List<JSONObject> all = RecEngine.load(this);
        listBox.removeAllViews();
        boolean any = false;
        for (final JSONObject o : all) {
            if (!match(o)) continue;
            any = true;
            listBox.addView(buildCard(o));
        }
        if (!any) {
            TextView empty = new TextView(this);
            empty.setText("暂无记录");
            empty.setTextColor(0xFF666666);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, 80, 0, 0);
            listBox.addView(empty);
        }
    }

    LinearLayout buildCard(final JSONObject o) {
        final String url = o.optString("url");
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(20, 16, 20, 16);
        row.setBackgroundColor(0xFF151515);

        // 左侧缩略图
        TextView thumb = new TextView(this);
        thumb.setTextSize(24);
        thumb.setGravity(Gravity.CENTER);
        thumb.setTextColor(0xFF4D9AFF);
        thumb.setBackgroundColor(0xFF222222);
        String icon = RecEngine.TYPE_DL.equals(o.optString("type")) ? "⬇" :
                (RecEngine.ST_REC.equals(o.optString("status")) ? "⏺" : "▶");
        thumb.setText(icon);
        row.addView(thumb, new LinearLayout.LayoutParams(150, 150));

        // 中部信息
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(20, 4, 8, 4);
        TextView t1 = new TextView(this);
        t1.setText("时长 " + fmtDur(o.optLong("duration", 0)));
        t1.setTextColor(0xFFDDDDDD); t1.setTextSize(13);
        TextView t2 = new TextView(this);
        t2.setText("大小 " + fmtSize(o.optLong("size", 0)));
        t2.setTextColor(0xFFDDDDDD); t2.setTextSize(13);
        TextView t3 = new TextView(this);
        t3.setText("状态 " + o.optString("status", ""));
        t3.setTextColor(0xFF4D9AFF); t3.setTextSize(13);
        TextView t0 = new TextView(this);
        t0.setText(o.optString("title", "") + " · " + o.optString("type", ""));
        t0.setTextColor(0xFFFFFFFF); t0.setTextSize(13);
        info.addView(t0);
        info.addView(t1);
        info.addView(t2);
        info.addView(t3);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(info, ip);

        // 右侧 ⋮
        TextView menu = new TextView(this);
        menu.setText("⋮");
        menu.setTextColor(0xFFCCCCCC);
        menu.setTextSize(20);
        menu.setPadding(24, 0, 8, 0);
        menu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { showMenu(v, o); }
        });
        row.addView(menu, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.setMargins(16, 8, 16, 8);
        row.setLayoutParams(rp);
        return row;
    }

    void showMenu(View anchor, final JSONObject o) {
        final String url = o.optString("url");
        final String status = o.optString("status");
        android.widget.PopupMenu pm = new android.widget.PopupMenu(this, anchor);
        pm.getMenu().add("取消");
        pm.getMenu().add("开始");
        pm.getMenu().add("播放");
        pm.getMenu().add("结束录制");
        pm.setOnMenuItemClickListener(new android.widget.PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(android.view.MenuItem item) {
                String t = item.getTitle().toString();
                if (t.equals("取消")) {
                    if (RecEngine.ST_DL.equals(status) && dlId > 0 && url.equals(dlUrl)) {
                        dm.remove(dlId);
                    }
                    RecEngine.stopRecording(DownloadManagerActivity.this, url);
                    RecEngine.remove(DownloadManagerActivity.this, url);
                    refresh();
                    Toast.makeText(DownloadManagerActivity.this, "已取消", Toast.LENGTH_SHORT).show();
                } else if (t.equals("开始")) {
                    RecEngine.startRecording(DownloadManagerActivity.this, url, o.optString("title", "资源嗅探"));
                    refresh();
                } else if (t.equals("播放")) {
                    String path = o.optString("path", "");
                    String play = (path.length() > 0 && new java.io.File(path).exists()) ? path : url;
                    android.content.Intent i = new android.content.Intent(DownloadManagerActivity.this, NativePlayerActivity.class);
                    i.putExtra("url", play);
                    i.putExtra("title", o.optString("title", "资源嗅探"));
                    i.putExtra("kernel", play.contains("douyin") || play.endsWith(".flv") ? "ijk" : "media");
                    startActivity(i);
                } else if (t.equals("结束录制")) {
                    RecEngine.stopRecording(DownloadManagerActivity.this, url);
                    refresh();
                    Toast.makeText(DownloadManagerActivity.this, "已结束录制", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        });
        pm.show();
    }

    /** 下载：DownloadManager 入队 + 轮询完成状态 */
    void enqueueDownload(String url) {
        try {
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            if (url.contains("bilibili") || url.contains("bilivideo"))
                req.addRequestHeader("Referer", "https://www.bilibili.com/");
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_MOVIES,
                    "资源嗅探_" + System.currentTimeMillis() + ".ts");
            dlId = dm.enqueue(req);
            dlUrl = url;
            pollDownload();
        } catch (Throwable e) {
            Toast.makeText(this, "下载失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    void pollDownload() {
        if (dlId < 0) return;
        final long id = dlId; final String u = dlUrl;
        new Thread(new Runnable() { public void run() {
            while (true) {
                Cursor cur = dm.query(new DownloadManager.Query().setFilterById(id));
                boolean done = false; boolean failed = false;
                if (cur != null && cur.moveToFirst()) {
                    int st = cur.getInt(cur.getColumnIndex(DownloadManager.COLUMN_STATUS));
                    done = st == DownloadManager.STATUS_SUCCESSFUL;
                    failed = st == DownloadManager.STATUS_FAILED;
                }
                if (cur != null) cur.close();
                if (done || failed) {
                    RecEngine.update(DownloadManagerActivity.this, u, "status",
                            done ? RecEngine.ST_OK : RecEngine.ST_CANCEL);
                    dlId = -1;
                    return;
                }
                try { Thread.sleep(3000); } catch (InterruptedException e) { return; }
            }
        }}).start();
    }

    static String fmtDur(long s) {
        if (s < 60) return s + "秒";
        return String.format(java.util.Locale.US, "%d分%02d秒", s / 60, s % 60);
    }

    static String fmtSize(long b) {
        if (b <= 0) return "0MB";
        if (b < 1024 * 1024) return String.format(java.util.Locale.US, "%.1fKB", b / 1024f);
        return String.format(java.util.Locale.US, "%.1fMB", b / 1024f / 1024f);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(refreshRun);
        super.onDestroy();
    }
}
