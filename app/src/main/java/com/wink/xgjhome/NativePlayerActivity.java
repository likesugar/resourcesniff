package com.wink.xgjhome;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.webkit.WebSettings;
import android.webkit.WebView;

import xyz.doikki.videoplayer.player.VideoView;
import xyz.doikki.videocontroller.StandardVideoController;

/** 双内核播放器：DKVideoPlayer 控制层（默认 MediaPlayer 内核）+ jessibuca 网页内核切换（控制层按钮） */
public class NativePlayerActivity extends Activity {

    private VideoView videoView;
    private StandardVideoController controller;
    private WebView wv;
    private String url;
    private String title;
    private boolean usingWeb = false;
    private Button kernelBtn;

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

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        // DKVideoPlayer：MediaPlayer 内核 + 标准控制层
        videoView = new VideoView(this);
        videoView.setUrl(url);
        controller = new StandardVideoController(this);
        controller.addDefaultControlComponent(title != null ? title : "资源嗅探", url != null && url.contains(".m3u8"));
        videoView.setVideoController(controller);
        root.addView(videoView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // jessibuca 网页内核（隐藏，控制层"内核"按钮切换）
        wv = new WebView(this);
        wv.setVisibility(View.GONE);
        WebSettings ws = wv.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        wv.addJavascriptInterface(new Object() {
            @android.webkit.JavascriptInterface
            public String getUrl() { return url; }
            @android.webkit.JavascriptInterface
            public void onNative() { }
        }, "AndroidPlayer");
        root.addView(wv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 控制层右上角悬浮的内核切换按钮（覆盖层，跟随控制层显隐简化为常驻小按钮）
        kernelBtn = new Button(this);
        kernelBtn.setText("🌐内核");
        kernelBtn.setTextSize(11);
        kernelBtn.setBackgroundResource(R.drawable.bg_btn_deep);
        FrameLayout.LayoutParams kp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        kp.setMargins(0, 12, 12, 0);
        kernelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { toggleKernel(); }
        });
        root.addView(kernelBtn, kp);

        setContentView(root);
        if (url == null || url.length() == 0) {
            Toast.makeText(this, "无播放地址", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        videoView.start();
    }

    /** 内核切换：MediaPlayer(VideoView) ↔ jessibuca(WebView) */
    void toggleKernel() {
        usingWeb = !usingWeb;
        if (usingWeb) {
            try { videoView.pause(); } catch (Throwable e) { }
            videoView.setVisibility(View.GONE);
            wv.loadUrl("file:///android_asset/player.html");
            wv.setVisibility(View.VISIBLE);
            Toast.makeText(this, "已切换：jessibuca 网页内核", Toast.LENGTH_SHORT).show();
        } else {
            try { wv.loadUrl("about:blank"); } catch (Throwable e) { }
            wv.setVisibility(View.GONE);
            videoView.setVisibility(View.VISIBLE);
            videoView.start();
            Toast.makeText(this, "已切换：MediaPlayer 内核", Toast.LENGTH_SHORT).show();
        }
        kernelBtn.setText(usingWeb ? "🌐原生" : "🌐网页");
    }

    @Override
    protected void onPause() {
        super.onPause();
        videoView.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!usingWeb) videoView.resume();
    }

    @Override
    protected void onDestroy() {
        try { videoView.release(); } catch (Throwable e) { }
        try {
            ViewGroup p = (ViewGroup) wv.getParent();
            if (p != null) p.removeView(wv);
            wv.destroy();
        } catch (Throwable e) { }
        super.onDestroy();
    }

}
