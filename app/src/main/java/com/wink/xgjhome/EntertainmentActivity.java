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
        setContentView(web);
        web.loadUrl("file:///android_asset/lottery/ent.html");
    }

    @Override
    public void onBackPressed() { finish(); }

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
