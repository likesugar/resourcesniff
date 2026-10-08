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
        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        root.addView(web, new android.widget.FrameLayout.LayoutParams(-1, -1));
        android.widget.TextView ai = new android.widget.TextView(this);
        ai.setText("🤖 AI");
        ai.setTextSize(13); ai.setTextColor(0xFFFFFFFF); ai.setTypeface(null, android.graphics.Typeface.BOLD);
        ai.setGravity(android.view.Gravity.CENTER);
        android.graphics.drawable.GradientDrawable ag = new android.graphics.drawable.GradientDrawable();
        ag.setColor(0xCC315CDE); ag.setCornerRadius(dip2(18));
        ai.setBackground(ag);
        android.widget.FrameLayout.LayoutParams alp = new android.widget.FrameLayout.LayoutParams(-2, -2, android.view.Gravity.BOTTOM | android.view.Gravity.LEFT);
        alp.setMargins(dip2(12), 0, 0, dip2(20));
        root.addView(ai, alp);
        ai.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            startActivity(new android.content.Intent(EntertainmentActivity.this, AiChatActivity.class));
        }});
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
