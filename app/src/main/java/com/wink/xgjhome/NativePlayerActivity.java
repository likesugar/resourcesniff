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
        usingWeb = "web".equals(getIntent().getStringExtra("kernel"));

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        // DKVideoPlayer：MediaPlayer 内核 + 标准控制层
        videoView = new VideoView(this);
        if (url != null && (url.contains("douyincdn") || url.contains("douyin"))) {
            java.util.Map<String, String> hdrs = new java.util.HashMap<String, String>();
            hdrs.put("Referer", "https://live.douyin.com/");
            hdrs.put("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
            videoView.setUrl(url, hdrs);
        } else {
            videoView.setUrl(url);
        }
        controller = new StandardVideoController(this);
        controller.addDefaultControlComponent(title != null ? title : "资源嗅探", url != null && url.contains(".m3u8"));
        videoView.setVideoController(controller);
        controller.setOnKernelSwitchListener(new StandardVideoController.OnKernelSwitchListener() {
            @Override
            public void onKernelSwitch() { toggleKernel(); }
        });

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
        wv.setWebViewClient(new android.webkit.WebViewClient() {
            @Override
            public android.webkit.WebResourceResponse shouldInterceptRequest(WebView view, android.webkit.WebResourceRequest request) {
                return serveAppAsset(request);
            }
        });
        wv.addJavascriptInterface(new Object() {
            @android.webkit.JavascriptInterface
            public String getUrl() { return url; }
            @android.webkit.JavascriptInterface
            public void onNative() { }
            @android.webkit.JavascriptInterface
            public void openNative(final String u, final String t) {
                runOnUiThread(new Runnable() { public void run() {
                    if (usingWeb) { switchToNativeKernel(); }
                    else { videoView.setUrl(u); videoView.start(); }
                }});
            }
        }, "AndroidPlayer");
        root.addView(wv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));


        setContentView(root);
        if (url == null || url.length() == 0) {
            Toast.makeText(this, "无播放地址", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (usingWeb) {
            // 抖音：网页内核先行，失败 player.html 自动调 openNative 切原生
            videoView.setVisibility(View.GONE);
            wv.setVisibility(View.VISIBLE);
            wv.loadUrl("https://appassets.local/player.html");
        } else {
            videoView.start();
        }
    }

    /** 虚拟域名：https://appassets.local/xxx → assets/xxx（file:// 下 Worker 被禁，需 https 同源） */
    android.webkit.WebResourceResponse serveAppAsset(android.webkit.WebResourceRequest request) {
        Uri u = request.getUrl();
        if (!"appassets.local".equals(u.getHost())) return null;
        String path = u.getPath();
        if (path == null || path.startsWith("/player.html")) path = "/player.html";
        try {
            java.io.InputStream is = getAssets().open(path.substring(1));
            String mime = path.endsWith(".wasm") ? "application/wasm"
                    : path.endsWith(".js") ? "text/javascript"
                    : path.endsWith(".html") ? "text/html" : "application/octet-stream";
            return new android.webkit.WebResourceResponse(mime, path.endsWith(".html") ? "utf-8" : null, is);
        } catch (Throwable e) {
            return null;
        }
    }

    /** 内核切换：MediaPlayer(VideoView) ↔ jessibuca(WebView) */
    void toggleKernel() {
        usingWeb = !usingWeb;
        if (usingWeb) {
            try { videoView.pause(); } catch (Throwable e) { }
            videoView.setVisibility(View.GONE);
            wv.loadUrl("https://appassets.local/player.html");
            wv.setVisibility(View.VISIBLE);
            Toast.makeText(this, "已切换：jessibuca 网页内核", Toast.LENGTH_SHORT).show();
        } else {
            switchToNativeKernel();
        }
    }

    void switchToNativeKernel() {
        try { wv.loadUrl("about:blank"); } catch (Throwable e) { }
        wv.setVisibility(View.GONE);
        videoView.setVisibility(View.VISIBLE);
        usingWeb = false;
        videoView.setUrl(url);
        videoView.start();
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
