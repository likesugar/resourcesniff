package com.wink.xgjhome;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 爬虫（图集岛）：WebView(Vue3+CSS) 界面 + OpenYspider 4.x 解析逻辑移植
 * 站点: www.sqmuying.com (图集岛现域名, 需登录看列表)
 * 列表: /u/?action=gengxin&page=N  hezi li -> id / biaoti / shuliang
 * 图片: https://tjg.gzhuibei.com/a/1/{albumId}/{i}.jpg 顺序下载到 404
 */
public class SpiderActivity extends Activity {

    private static final String SITE = "https://www.sqmuying.com";
    private static final String IMG_PREFIX = "https://tjg.gzhuibei.com/a/1/";
    private static final Pattern LI = Pattern.compile(
            "<li id=\"(\\d+)\"[\\s\\S]*?class=\"biaoti\"[^>]*>([\\s\\S]*?)</[a-z]+>[\\s\\S]*?class=\"shuliang\"[^>]*>(\\d+)\\s*P",
            Pattern.CASE_INSENSITIVE);

    private WebView web;
    private boolean dark;
    private final Set<Integer> cancels = new HashSet<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(3);

    private void applyImmersive() {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            android.view.WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(android.view.WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersive();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dark = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("dark", false);
        web = new WebView(this);
        setContentView(web);
        applyImmersive();

        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        ws.setUserAgentString("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.setBackgroundColor(dark ? Color.BLACK : 0xFFEEF4FF);
        web.addJavascriptInterface(new And(), "And");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, String u) { return false; }
        });
        web.loadUrl("file:///android_asset/spider.html");
    }

    private void fireEvent(String type, String json) {
        final String payload = json == null ? "" : json.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n");
        runOnUiThread(new Runnable() { public void run() {
            try {
                web.evaluateJavascript("window.__andEvent('" + type + "','" + payload + "')", null);
            } catch (Throwable ignored) {}
        }});
    }

    private class And {

        /** 主题: dark / light */
        @JavascriptInterface
        public String theme() { return dark ? "dark" : "light"; }

        /** 是否已登录(按 cookie 粗判) */
        @JavascriptInterface
        public String loginState() {
            try {
                String c = CookieManager.getInstance().getCookie(SITE);
                if (c != null && (c.contains("uid=") || c.contains("name="))) return "yes";
            } catch (Throwable ignored) {}
            return "no";
        }

        /** 打开网页登录 */
        @JavascriptInterface
        public void login() {
            runOnUiThread(new Runnable() { public void run() {
                Intent i = new Intent(SpiderActivity.this, LoginWebActivity.class);
                i.putExtra("url", SITE + "/?action=login");
                startActivity(i);
            }});
        }

        /** 抓列表页(OpenYspider doSyncRecords 逻辑): 返回 JSON 字符串 */
        @JavascriptInterface
        public String list(int page) {
            try {
                trustAll();
                String html = httpGet(SITE + "/u/?action=gengxin&page=" + page);
                if (html == null) return "{\"err\":\"网络请求失败\"}";
                if (html.contains("还没有登录") || html.contains("action=login"))
                    return "{\"err\":\"notlogin\"}";
                JSONArray arr = new JSONArray();
                Matcher m = LI.matcher(html);
                while (m.find()) {
                    JSONObject o = new JSONObject();
                    o.put("id", Integer.parseInt(m.group(1)));
                    o.put("name", rmIllegal(m.group(2).trim()));
                    o.put("total", Integer.parseInt(m.group(3)));
                    arr.put(o);
                }
                if (arr.length() == 0) return "{\"err\":\"页面无图集(可能未登录或结构变化)\"}";
                JSONObject r = new JSONObject();
                r.put("items", arr);
                return r.toString();
            } catch (Throwable e) {
                return "{\"err\":\"" + e.toString().replace("\"", "'") + "\"}";
            }
        }

        /** 下载整本图集 */
        @JavascriptInterface
        public void download(final int id, final String name, final int total) {
            cancels.remove(id);
            pool.submit(new Runnable() { public void run() {
                try { trustAll(); } catch (Throwable ignored) {}
                String safe = rmIllegal(name);
                String relPath = "Pictures/图集岛/" + id + "-" + safe;
                int done = 0;
                for (int i = 0; i < (total > 0 ? total : 2000); i++) {
                    if (cancels.contains(id)) { fireEvent("prog", "{\"id\":" + id + ",\"done\":" + done + ",\"total\":" + total + ",\"state\":\"cancel\"}"); return; }
                    String url = String.format(Locale.US, "%s%d/%d.jpg", IMG_PREFIX, id, i);
                    boolean ok = false;
                    for (int retry = 0; retry < 3 && !ok && !cancels.contains(id); retry++) ok = saveImage(url, relPath, id + "-" + safe + "/" + i + ".jpg");
                    if (!ok) break; // 404 => 本本结束
                    done++;
                    if (i % 2 == 0 || i == total - 1)
                        fireEvent("prog", "{\"id\":" + id + ",\"done\":" + done + ",\"total\":" + total + ",\"state\":\"run\"}");
                }
                fireEvent("prog", "{\"id\":" + id + ",\"done\":" + done + ",\"total\":" + total + ",\"state\":\"done\"}");
            }});
        }

        /** 停止下载 */
        @JavascriptInterface
        public void cancel(int id) { cancels.add(id); }

        /** 返回首页 */
        @JavascriptInterface
        public void exit() { runOnUiThread(new Runnable() { public void run() { finish(); }}); }
    }

    /** MediaStore 存图( Pictures/图集岛/... ) */
    private boolean saveImage(String url, String relPath, String fileName) {
        OutputStream os = null; InputStream is = null;
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(12000); c.setReadTimeout(20000);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) Chrome/120 Mobile");
            c.setRequestProperty("Referer", SITE + "/");
            if (c.getResponseCode() != 200) return false;
            is = c.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            if (bos.size() < 1024) return false; // 异常小图当作失败
            android.content.ContentValues cv = new android.content.ContentValues();
            cv.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, fileName);
            cv.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            if (Build.VERSION.SDK_INT >= 29) cv.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, relPath);
            android.net.Uri uri = getContentResolver().insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
            os = getContentResolver().openOutputStream(uri);
            os.write(bos.toByteArray());
            os.flush();
            return true;
        } catch (Throwable e) { return false; }
        finally { try { if (is != null) is.close(); } catch (Throwable ignored) {} try { if (os != null) os.close(); } catch (Throwable ignored) {} }
    }

    private String httpGet(String url) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(15000); c.setReadTimeout(20000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) Chrome/120 Mobile");
            String cookie = CookieManager.getInstance().getCookie(SITE);
            if (cookie != null) c.setRequestProperty("Cookie", cookie);
            if (c.getResponseCode() != 200) return null;
            InputStream is = c.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            is.close();
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        } catch (Throwable e) { return null; }
    }

    // 图集岛证书与域名不匹配, 全放行 (OpenYspider 同款做法)
    private static void trustAll() throws Exception {
        javax.net.ssl.TrustManager[] tm = new javax.net.ssl.TrustManager[]{ new javax.net.ssl.X509TrustManager() {
            public void checkClientTrusted(java.security.cert.X509Certificate[] a, String t) {}
            public void checkServerTrusted(java.security.cert.X509Certificate[] a, String t) {}
            public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }
        }};
        javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("SSL");
        sc.init(null, tm, new java.security.SecureRandom());
        javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier((h, s) -> true);
    }

    private static final Pattern FILE_BAD = Pattern.compile("[\\\\/:*?\"<>|]");
    private static String rmIllegal(String s) { return s == null ? "" : FILE_BAD.matcher(s).replaceAll("").trim(); }

    @Override
    public void onBackPressed() { if (web.canGoBack()) web.goBack(); else super.onBackPressed(); }

    @Override
    protected void onDestroy() {
        cancels.addAll(java.util.Arrays.asList(new Integer[]{ })); // 占位, 真正取消在页面关闭时
        try { pool.shutdownNow(); } catch (Throwable ignored) {}
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
