package com.wink.xgjhome;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;

import xyz.doikki.videoplayer.ijk.IjkPlayerFactory;
import xyz.doikki.videoplayer.player.VideoView;
import xyz.doikki.videocontroller.StandardVideoController;

/** 双内核播放器：MediaPlayer ↔ IjkPlayer（FireflyVideo 同款内核），DK 控制层，↹ 切换 */
public class NativePlayerActivity extends Activity {

    private static final int KERNEL_MEDIA = 0;
    private static final int KERNEL_IJK = 1;

    private VideoView videoView;
    private StandardVideoController controller;
    private String url;
    private String title;
    private int kernel = KERNEL_MEDIA;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            initPlayerPage(savedInstanceState);
        } catch (Throwable e) {
            showCrash(e);
        }
    }

    void showCrash(final Throwable e) {
        try {
            java.io.File dir = getExternalFilesDir(null);
            if (dir == null) dir = getFilesDir();
            java.io.File f = new java.io.File(dir, "crash.txt");
            java.io.FileWriter fw = new java.io.FileWriter(f, true);
            fw.append("\n==== " + new java.util.Date().toString() + " (player) ====\n");
            fw.append(android.util.Log.getStackTraceString(e));
            fw.close();
        } catch (Throwable e2) { }
        new android.app.AlertDialog.Builder(this)
                .setTitle("播放页闪退原因")
                .setCancelable(false)
                .setMessage(android.util.Log.getStackTraceString(e))
                .setPositiveButton("好", null)
                .show();
    }

    @SuppressLint("SetJavaScriptEnabled")
    void initPlayerPage(Bundle savedInstanceState) {
        url = getIntent().getStringExtra("url");
        title = getIntent().getStringExtra("title");
        // DK 对抖音直播用 Ijk（firefly）内核：MediaPlayer 播 m3u8/flv 会报"出了点小问题"
        kernel = (url != null && (url.contains("douyin") || url.contains(".m3u8") || url.contains(".flv")))
                ? KERNEL_IJK : KERNEL_MEDIA;

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        videoView = new VideoView(this);
        controller = new StandardVideoController(this);
        controller.addDefaultControlComponent(title != null ? title : "资源嗅探", url != null && url.contains(".m3u8"));
        videoView.setVideoController(controller);
        controller.setOnKernelSwitchListener(new StandardVideoController.OnKernelSwitchListener() {
            @Override
            public void onKernelSwitch() { toggleKernel(); }
        });
        // 控制层左上角：width: height:（随控制条显隐）
        controller.addControlComponent(new SizeComponent(videoView));

        root.addView(videoView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
        if (url == null || url.length() == 0) {
            Toast.makeText(this, "无播放地址", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        applyKernel();
        videoView.setUrl(url, douyinHeaders());
        videoView.start();

    }

    void applyKernel() {
        if (kernel == KERNEL_IJK) {
            videoView.setPlayerFactory(IjkPlayerFactory.create());
        } else {
            videoView.setPlayerFactory(xyz.doikki.videoplayer.player.AndroidMediaPlayerFactory.create());
        }
    }

    void toggleKernel() {
        try { videoView.release(); } catch (Throwable e) { }
        kernel = (kernel == KERNEL_MEDIA) ? KERNEL_IJK : KERNEL_MEDIA;
        applyKernel();
        videoView.setUrl(url, douyinHeaders());
        videoView.start();
        Toast.makeText(this, kernel == KERNEL_IJK ? "已切换：IjkPlayer 内核" : "已切换：MediaPlayer 内核", Toast.LENGTH_SHORT).show();
    }

    Map<String, String> douyinHeaders() {
        Map<String, String> hdrs = null;
        if (url != null && (url.contains("douyincdn") || url.contains("douyin"))) {
            hdrs = new HashMap<String, String>();
            hdrs.put("Referer", "https://live.douyin.com/");
            hdrs.put("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
        }
        return hdrs;
    }

    @Override
    protected void onPause() {
        super.onPause();
        videoView.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        videoView.resume();
    }

    @Override
    protected void onDestroy() {
        try { videoView.release(); } catch (Throwable e) { }
        super.onDestroy();
    }
}
