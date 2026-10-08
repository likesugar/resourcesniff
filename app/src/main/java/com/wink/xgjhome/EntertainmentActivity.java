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
        setContentView(web);
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
