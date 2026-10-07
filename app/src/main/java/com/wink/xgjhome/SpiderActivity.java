package com.wink.xgjhome;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 爬虫2(beauty_spider移植): 内置浏览器浏览 win4000.com,
 * 在系列详情页注入悬浮"下载全部"按钮, 点击后整本下载到 Pictures/美女/
 * WebView 天然通过该站 JS cookie 反爬挑战.
 */
public class SpiderActivity extends Activity {

    private static final String START = "http://www.win4000.com/zt/xinggan.html";
    private WebView web;
    private boolean dark;
    private final ExecutorService pool = Executors.newFixedThreadPool(3);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dark = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("dark", false);
        if (Build.VERSION.SDK_INT >= 28) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        web = new WebView(this);
        setContentView(web);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setUserAgentString("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        ws.setUseWideViewPort(true);
        ws.setLoadWithOverviewMode(true);
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new And(), "And");
        web.setWebViewClient(new WebViewClient() {
            @Override public void onReceivedSslError(WebView v, android.webkit.SslErrorHandler h, android.net.http.SslError e) { h.proceed(); }
            @Override public boolean shouldOverrideUrlLoading(WebView v, String u) { return false; }
            @Override public void onPageFinished(WebView v, String u) {
                inject(v, u);
            }
        });
        web.loadUrl(START);
    }

    private void inject(WebView v, String u) {
        if (u == null) return;
        boolean axDetail = u.contains("axiuren.com") && u.matches(".*axiuren\\.com/\\d+\\.html.*");
        if (axDetail) injectAxiuren(v);
        // 每页注入站点切换按钮(除目标详情页已注入的那个外)
        String other = u.contains("axiuren.com")
            ? "http://www.win4000.com/zt/xinggan.html|甜|315CDE"
            : "https://axiuren.com/|秀|c0392b";
        String[] p = other.split("\\|");
        String js2 =
            "(function(){" +
            "try{" +
            "if(window.__sw)return;" +
            "var b=document.createElement('div'); b.textContent='" + p[1] + "';" +
            "b.style.cssText='position:fixed;left:12px;bottom:60px;z-index:99999;background:#" + p[2] + ";color:#fff;padding:8px 14px;border-radius:20px;font-size:13px;font-weight:bold;opacity:.8';" +
            "b.onclick=function(){And.goto2('" + p[0] + "')};" +
            "document.body.appendChild(b); window.__sw=b;" +
            "}catch(e){}}" +
            ")();";
        v.evaluateJavascript(js2, null);
        if (axDetail || !u.contains("win4000.com")) return;
        // 仅系列详情页注入下载按钮
        if (!u.contains("wallpaper_detail") && !u.contains("meinvxiaoguotu") && !u.contains("mobile_detail")) return;
        String js =
            "(function(){" +
            "try{" +
            "window.__collect=function(){" +
            " var out=[];" +
            " [].forEach.call(document.querySelectorAll('.scroll-img-cont li img'),function(i){" +
            "  var s=i.getAttribute('data-original')||i.src; if(!s)return; if(s.indexOf('//')==0)s='http:'+s;" +
            "  var big=s.substring(0,s.indexOf('_'))+'.jpg'; out.push(big);" +
            " });" +
            " return JSON.stringify({name:(document.querySelector('h1')||{textContent:document.title}).textContent.trim(),imgs:out});" +
            "};" +
            "var old=window.__dlbtn; if(old)old.remove();" +
            "var b=document.createElement('div');" +
            "b.id='dlbtn'; b.textContent='⬇ 下载全部';" +
            "b.style.cssText='position:fixed;right:12px;bottom:60px;z-index:99999;background:#315CDE;color:#fff;padding:10px 16px;border-radius:24px;font-size:14px;font-weight:bold;box-shadow:0 4px 12px rgba(0,0,0,.4);opacity:.92';" +
            "b.onclick=function(){var d=window.__collect();And.download(d)};" +
            "document.body.appendChild(b); window.__dlbtn=b;" +
            "}catch(e){}}" +
            ")();";
        v.evaluateJavascript(js, null);
    }

    /** axiuren.com(秀人网镜像): 从页面抽图床文件夹+标题里的张数, 枚举 0001.webp..N.webp */
    private void injectAxiuren(WebView v) {
        String js =
            "(function(){" +
            "try{" +
            "window.__collect=function(){" +
            " var h=document.documentElement.innerHTML;" +
            " var m=h.match(/https:\\/\\/img\\.ecmm\\.cc\\/new\\/[^\"']+\\//);" +
            " if(!m)return JSON.stringify({name:'未找到图床',imgs:[]});" +
            " var folder=m[0];" +
            " var t=(document.querySelector('h1')||{textContent:document.title}).textContent;" +
            " var pm=t.match(/(\\d+)\\s*P/i); var n=pm?parseInt(pm[1]):0;" +
            " if(n<=0)n=120;" +
            " var urls=[];" +
            " for(var i=1;i<=n;i++){var s='0000'+i; s=s.substring(s.length-4); urls.push(folder+s+'.webp');}" +
            " return JSON.stringify({name:t.trim().substring(0,60),imgs:urls});" +
            "};" +
            "var old=window.__dlbtn; if(old)old.remove();" +
            "var b=document.createElement('div');" +
            "b.id='dlbtn'; b.textContent='⬇ 下载全部';" +
            "b.style.cssText='position:fixed;right:12px;bottom:60px;z-index:99999;background:#c0392b;color:#fff;padding:10px 16px;border-radius:24px;font-size:14px;font-weight:bold;box-shadow:0 4px 12px rgba(0,0,0,.4);opacity:.92';" +
            "b.onclick=function(){var d=window.__collect();And.download(d)};" +
            "document.body.appendChild(b); window.__dlbtn=b;" +
            "}catch(e){}}" +
            ")();";
        v.evaluateJavascript(js, null);
    }

    private class And {

        /** 站点切换 */
        @JavascriptInterface
        public void goto2(String url) {
            String u = url == null ? "" : url.trim();
            if (u.startsWith("http")) { runOnUiThread(new Runnable() { public void run() { web.loadUrl(u); }}); }
            else toast("无效地址");
        }

        /** 下载整本: payload={name, imgs:[url...]} */
        @JavascriptInterface
        public void download(String payload) {
            try {
                JSONObject o = new JSONObject(payload);
                String name = o.optString("name", "图集").replace('/', '_').replace(':', '_');
                JSONArray arr = o.optJSONArray("imgs");
                if (arr == null || arr.length() == 0) { toast("没抓到图片链接"); return; }
                final String fname = name;
                final int total = arr.length();
                toast("开始下载 " + total + " 张 → Pictures/美女/" + fname);
                pool.execute(new Runnable() { public void run() {
                    int ok = 0;
                    for (int i = 0; i < total; i++) {
                        String url;
                        try { url = arr.getString(i); } catch (Throwable e) { continue; }
                        String ext = url.toLowerCase().contains(".webp") ? "webp" : "jpg";
                        String fn = String.format(java.util.Locale.US, "%s_%03d.%s", fname, i + 1, ext);
                        if (saveImage(url, "Pictures/美女/" + fname, fn)) ok++;
                        if (i % 5 == 4) toast("进度 " + (ok) + "/" + total);
                    }
                    toast("完成: 成功 " + ok + "/" + total + " (Pictures/美女/" + fname + ")");
                }});
            } catch (Throwable e) { toast("解析失败: " + e); }
        }

        @JavascriptInterface
        public void toast(final String s) {
            runOnUiThread(new Runnable() { public void run() {
                Toast.makeText(SpiderActivity.this, s, Toast.LENGTH_SHORT).show();
            }});
        }
    }

    private void toast(String s) {
        runOnUiThread(new Runnable() { public void run() {
            Toast.makeText(SpiderActivity.this, s, Toast.LENGTH_SHORT).show();
        }});
    }

    /** 下载到 MediaStore */
    private boolean saveImage(String url, String relPath, String fileName) {
        InputStream is = null; OutputStream os = null;
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(15000); c.setReadTimeout(20000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) Chrome/120 Mobile");
            c.setRequestProperty("Referer", "http://www.win4000.com/");
            String ck = CookieManager.getInstance().getCookie("http://www.win4000.com");
            if (ck != null) c.setRequestProperty("Cookie", ck);
            if (c.getResponseCode() != 200) return false;
            is = c.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[16384]; int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            if (bos.size() < 5000) return false; // 太小=错误页
            android.content.ContentValues cv = new android.content.ContentValues();
            cv.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, fileName);
            cv.put(android.provider.MediaStore.Images.Media.MIME_TYPE, relPath != null && fileName.endsWith(".webp") ? "image/webp" : "image/jpeg");
            if (Build.VERSION.SDK_INT >= 29) cv.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, relPath);
            android.net.Uri uri = getContentResolver().insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
            os = getContentResolver().openOutputStream(uri);
            os.write(bos.toByteArray());
            os.flush();
            return true;
        } catch (Throwable e) { return false; }
        finally { try { if (is != null) is.close(); } catch (Throwable ignored) {} try { if (os != null) os.close(); } catch (Throwable ignored) {} }
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        try { pool.shutdownNow(); } catch (Throwable ignored) {}
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
