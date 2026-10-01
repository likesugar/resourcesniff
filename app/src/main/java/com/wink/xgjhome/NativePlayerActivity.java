package com.wink.xgjhome;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.widget.VideoView;
import android.webkit.WebSettings;
import android.webkit.WebView;

/** 双内核播放器：默认系统 MediaPlayer（VideoView），可切 jessibuca 网页内核 */
public class NativePlayerActivity extends Activity {

    private VideoView vv;
    private WebView wv;
    private String url;
    private String title;
    private boolean usingWeb = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        url = getIntent().getStringExtra("url");
        title = getIntent().getStringExtra("title");

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        // 内核A：系统 MediaPlayer
        vv = new VideoView(this);
        root.addView(vv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // 内核B：jessibuca 网页内核
        wv = new WebView(this);
        WebSettings ws = wv.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setAllowFileAccess(true);
        wv.setBackgroundColor(0xFF000000);
        wv.addJavascriptInterface(new Object() {
            @android.webkit.JavascriptInterface
            public String getUrl() { return url; }
            @android.webkit.JavascriptInterface
            public void openNative(String u, String t) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() { switchToNative(); }
                });
            }
        }, "AndroidPlayer");
        wv.loadUrl("file:///android_asset/player.html");
        wv.setVisibility(View.GONE);
        root.addView(wv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // 顶栏按钮
        Button btn = new Button(this);
        btn.setText("🌐 切网页内核");
        btn.setTextSize(12);
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        bp.setMargins(16, 32, 16, 0);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { toggle(); }
        });
        root.addView(btn, bp);

        setContentView(root);

        if (url == null || url.length() == 0) {
            Toast.makeText(this, "无播放地址", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        vv.setVideoURI(Uri.parse(url));
        vv.start();
    }

    void toggle() {
        if (!usingWeb) {
            try { vv.pause(); } catch (Throwable e) { }
            vv.setVisibility(View.GONE);
            wv.setVisibility(View.VISIBLE);
            usingWeb = true;
            btnState("▶ 系统内核");
        } else {
            wv.setVisibility(View.GONE);
            vv.setVisibility(View.VISIBLE);
            usingWeb = false;
            btnState("🌐 切网页内核");
            try { vv.start(); } catch (Throwable e) { }
        }
    }

    void switchToNative() {
        if (!usingWeb) return;
        wv.setVisibility(View.GONE);
        vv.setVisibility(View.VISIBLE);
        usingWeb = false;
        btnState("🌐 切网页内核");
        try { vv.start(); } catch (Throwable e) { }
    }

    void btnState(String t) {
        View b = findViewById(0);
        // 简化：直接遍历顶层找 Button
        android.view.ViewGroup root = (android.view.ViewGroup) findViewById(android.R.id.content);
        for (int i = 0; i < root.getChildCount(); i++) {
            View c = root.getChildAt(i);
            if (c instanceof android.view.ViewGroup) {
                android.view.ViewGroup g = (android.view.ViewGroup) c;
                for (int j = 0; j < g.getChildCount(); j++) {
                    View cc = g.getChildAt(j);
                    if (cc instanceof Button) ((Button) cc).setText(t);
                }
            }
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }
}
