package com.wink.xgjhome;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** 娱乐: 刮刮卡+大转盘 (lottery插件移植, WebView本地运行, 奖项可自定义) */
public class EntertainmentActivity extends Activity {

    private WebView web;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true); // localStorage存自定义奖项
        ws.setUserAgentString("Mozilla/5.0 (Linux; Android 13) Chrome/120 Mobile");
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new android.webkit.WebChromeClient()); // 没有它 alert() 会被静默吞掉(积分不足提示/错误提示全部看不到)
        web.addJavascriptInterface(new Object() {
            @android.webkit.JavascriptInterface
            public void close() { runOnUiThread(new Runnable() { public void run() { finish(); } }); }
        }, "app");
        // 悬浮模式切换: 刮刮卡/转盘 ↔ 幸运水果机
        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        root.addView(web, new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        android.widget.TextView modeBtn = new android.widget.TextView(this);
        modeBtn.setText("🎰 水果机");
        modeBtn.setTextSize(13);
        modeBtn.setTextColor(0xFFFFFFFF);
        modeBtn.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        modeBtn.setGravity(android.view.Gravity.CENTER);
        modeBtn.setPadding(dip2(14), dip2(8), dip2(14), dip2(8));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(dip2(18));
        bg.setColor(0xCC8A1FA2);
        modeBtn.setBackground(bg);
        modeBtn.setElevation(dip2(6));
        android.widget.FrameLayout.LayoutParams mlp = new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);
        mlp.bottomMargin = dip2(64);
        modeBtn.setOnClickListener(new View.OnClickListener() {
            boolean fruit = false;
            public void onClick(View v) {
                fruit = !fruit;
                modeBtn.setText(fruit ? "🎡 娱乐" : "🎰 水果机");
                web.loadUrl("file:///android_asset/lottery/" + (fruit ? "fruit.html" : "ent.html"));
            }
        });
        root.addView(modeBtn, mlp);
        setContentView(root);
        web.loadUrl("file:///android_asset/lottery/ent.html");
    }

    private int dip2(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override
    public void onBackPressed() { finish(); }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) Immersive.hide(this);
    }
    @Override
    protected void onResume() { super.onResume(); Immersive.hide(this); }

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
