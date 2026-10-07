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
import android.widget.Toast;

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

        /** 诊断2: WebView渲染图集页抓DOM + 探测gl25路径规则 */
        @JavascriptInterface
        public String probe2(final int id) {
            try { trustAll(); } catch (Throwable ignored) {}
            final StringBuilder sb = new StringBuilder("== probe2 id=").append(id).append("\n");
            // 路径规则探测
            String[] cands = {
                "https://qwevyimg.gl25.cn/t/" + id + "/1.jpg",
                "https://qwevyimg.gl25.cn/t/" + id + "/0.jpg",
                "https://qwevyimg.gl25.cn/t/" + id + "_1.jpg",
                "https://qwevyimg.gl25.cn/a/1/" + id + "/1.jpg",
                "https://qwevyimg.gl25.cn/t/1/" + id + ".jpg",
                "https://qwevyimg.gl25.cn/u/" + id + "/1.jpg"
            };
            for (String u : cands) {
                HttpURLConnection c = null;
                try {
                    c = (HttpURLConnection) new URL(u).openConnection();
                    c.setConnectTimeout(8000); c.setReadTimeout(8000);
                    c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) Chrome/120 Mobile");
                    c.setRequestProperty("Referer", SITE + "/");
                    sb.append("TRY ").append(u.replace("https://qwevyimg.gl25.cn", "")).append(" -> ").append(c.getResponseCode())
                      .append(" ").append(c.getContentType()).append(" ").append(c.getContentLength()).append("\n");
                } catch (Throwable e) { sb.append("TRY ").append(u).append(" -> ERR ").append(e).append("\n"); }
                finally { if (c != null) try { c.disconnect(); } catch (Throwable ignored) {} }
            }
            // WebView 渲染: 拦截所有资源请求 + 抓DOM
            try {
                final Object[] box = new Object[]{ "" };
                final Object lock = new Object();
                final java.util.Set<String> hits = java.util.Collections.synchronizedSet(new java.util.LinkedHashSet<String>());
                runOnUiThread(new Runnable() { public void run() {
                    final WebView wv = new WebView(SpiderActivity.this);
                    WebSettings ws = wv.getSettings();
                    ws.setJavaScriptEnabled(true);
                    ws.setUserAgentString("Mozilla/5.0 (Linux; Android 13) Chrome/120 Mobile");
                    ws.setDomStorageEnabled(true);
                    ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
                    wv.setWebViewClient(new WebViewClient() {
                        @Override public void onReceivedSslError(WebView v, android.webkit.SslErrorHandler h, android.net.http.SslError e) { h.proceed(); }
                        @Override public android.webkit.WebResourceResponse shouldInterceptRequest(WebView v, android.webkit.WebResourceRequest req) {
                            try {
                                String u = req.getUrl().toString();
                                if (!u.contains("sqmuying") && (u.contains(".jpg") || u.contains(".jpeg") || u.contains(".png") || u.contains(".webp") || u.contains("gl25") || u.contains("img")))
                                    hits.add(u);
                            } catch (Throwable ignored) {}
                            return null;
                        }
                        @Override public void onPageFinished(WebView v, String u) {
                            sb.append("[pageFinished:").append(u).append("]");
                        }
                    });
                    wv.loadUrl(SITE + "/t/?id=" + id);
                    // 不依赖onPageFinished: 固定延时自动滚屏+提取
                    final android.os.Handler h2 = new android.os.Handler();
                    for (int k = 0; k < 6; k++) h2.postDelayed(new Runnable() { public void run() {
                        try { wv.evaluateJavascript("window.scrollTo(0,document.body.scrollHeight)", null); } catch (Throwable ignored) {}
                    } }, 1500L * (k + 1));
                    h2.postDelayed(new Runnable() { public void run() {
                        String js = "JSON.stringify({href:location.href,rs:document.readyState,"
                            + "imgs:[].map.call(document.images,function(i){return i.src+(i.getAttribute('data-original')?' DO='+i.getAttribute('data-original'):'')}).slice(0,40),"
                            + "pages:(document.querySelector('#pages')||{innerHTML:''}).innerHTML,"
                            + "hezi:[].map.call(document.querySelectorAll('.hezi'),function(e){return e.innerHTML}).join('|||').substring(0,4000),"
                            + "links:[].map.call(document.querySelectorAll('a'),function(a){return a.href}).filter(function(h){return h.indexOf('page')>=0||h.indexOf('t/?id')>=0}).slice(0,20)})";
                        wv.evaluateJavascript(js, new android.webkit.ValueCallback<String>() {
                            public void onReceiveValue(String val) { box[0] = val == null ? "NULL" : val; synchronized (lock) { lock.notify(); } }
                        });
                    } }, 12000);
                }});
                synchronized (lock) { try { lock.wait(18000); } catch (InterruptedException ignored) {} }
                sb.append("---- img requests(").append(hits.size()).append(") ----\n");
                int i = 0;
                for (String h2 : hits) { sb.append(h2).append("\n"); if (++i > 25) break; }
                sb.append("---- rendered DOM ----\n").append(box[0]);
                try { web.post(new Runnable() { public void run() { } }); } catch (Throwable ignored) {}
            } catch (Throwable e) { sb.append("\nwv err: ").append(e); }
            return finishProbe(sb);
        }

        /** 诊断3: 可见WebView打开图集页, 记录全部图片请求, 30秒后汇总复制 */
        @JavascriptInterface
        public String probe3(final int id) {
            try { trustAll(); } catch (Throwable ignored) {}
            final java.util.Set<String> hits = java.util.Collections.synchronizedSet(new java.util.LinkedHashSet<String>());
            runOnUiThread(new Runnable() { public void run() {
                final android.widget.FrameLayout root = new android.widget.FrameLayout(SpiderActivity.this);
                final WebView wv = new WebView(SpiderActivity.this);
                WebSettings ws = wv.getSettings();
                ws.setJavaScriptEnabled(true);
                ws.setUserAgentString("Mozilla/5.0 (Linux; Android 13) Chrome/120 Mobile");
                ws.setDomStorageEnabled(true);
                ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
                wv.setWebViewClient(new WebViewClient() {
                    @Override public void onReceivedSslError(WebView v, android.webkit.SslErrorHandler h, android.net.http.SslError e) { h.proceed(); }
                    @Override public android.webkit.WebResourceResponse shouldInterceptRequest(WebView v, android.webkit.WebResourceRequest req) {
                        String u = req.getUrl().toString();
                        if (u.contains(".jpg") || u.contains(".jpeg") || u.contains(".png") || u.contains(".webp")) hits.add(u);
                        return null;
                    }
                });
                root.addView(wv, new android.widget.FrameLayout.LayoutParams(-1, -1));
                android.widget.Button btn = new android.widget.Button(SpiderActivity.this);
                btn.setText("完成并复制诊断(" + id + ")");
                root.addView(btn, new android.widget.FrameLayout.LayoutParams(-2, -2));
                btn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                    StringBuilder sb = new StringBuilder("== probe3 id=").append(id).append("\n");
                    sb.append("---- img requests(").append(hits.size()).append(") ----\n");
                    for (String u : hits) sb.append(u).append("\n");
                    finishProbe(sb);
                    ((android.view.ViewGroup) root.getParent()).removeView(root);
                    wv.destroy();
                }});
                setContentView(root);
                wv.loadUrl(SITE + "/t/?id=" + id);
            }});
            return "ok-see-screen";
        }

        /** 诊断: 抓图集详情页原文+探测图床候选URL, 结果复制到剪贴板 */
        @JavascriptInterface
        public String probe(final int id) {
            final StringBuilder sb = new StringBuilder();
            try { trustAll(); } catch (Throwable ignored) {}
            try {
                String ck = CookieManager.getInstance().getCookie(SITE);
                String page = httpGet(SITE + "/t/?id=" + id, ck);
                sb.append("== album page /t/?id=").append(id)
                  .append(" len=").append(page == null ? -1 : page.length()).append("\n");
                if (page != null) {
                    // 抽页面里所有 gl25/图片域字符串
                    java.util.regex.Matcher gm = Pattern.compile("[A-Za-z0-9:/._\\-]*(?:gl25|img|tjg)[A-Za-z0-9:/._\\-]*").matcher(page);
                    java.util.LinkedHashSet<String> gs = new java.util.LinkedHashSet<>();
                    while (gm.find() && gs.size() < 25) gs.add(gm.group());
                    sb.append("---- domains ----\n");
                    for (String g : gs) sb.append(g).append("\n");
                    // 尾部原文(正文图片区在后面)
                    int from = Math.max(0, page.length() - 5000);
                    sb.append("---- tail ----\n").append(page.substring(from));
                }
            } catch (Throwable e) { sb.append("page err: ").append(e).append("\n"); }
            // 探测封面: 裸/带Referer/带Cookie+Referer
            HttpURLConnection c0 = null;
            String[] probes = {
                "https://qwevyimg.gl25.cn/t/" + id + ".jpg|none",
                "https://qwevyimg.gl25.cn/t/" + id + ".jpg|ref",
                "https://qwevyimg.gl25.cn/t/" + id + ".jpg|ck"
            };
            for (String entry : probes) {
                int bar = entry.lastIndexOf('|');
                String u = entry.substring(0, bar), mode = entry.substring(bar + 1);
                try {
                    c0 = (HttpURLConnection) new URL(u).openConnection();
                    c0.setConnectTimeout(8000); c0.setReadTimeout(8000);
                    c0.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) Chrome/120 Mobile");
                    if (mode.equals("ref") || mode.equals("ck")) c0.setRequestProperty("Referer", SITE + "/");
                    if (mode.equals("ck")) { String ck = CookieManager.getInstance().getCookie(SITE); if (ck != null) c0.setRequestProperty("Cookie", ck); }
                    sb.append("\nPROBE[").append(mode).append("] ").append(u).append(" -> ").append(c0.getResponseCode())
                      .append(" ").append(c0.getContentType()).append(" ").append(c0.getContentLength());
                } catch (Throwable e) { sb.append("\nPROBE[").append(mode).append("] ERR ").append(e); }
                finally { if (c0 != null) try { c0.disconnect(); } catch (Throwable ignored) {} }
            }
            return finishProbe(sb);
        }

        private String finishProbe(final StringBuilder sb) {
            final String out = sb.toString();
            runOnUiThread(new Runnable() { public void run() {
                try {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("probe", out));
                    Toast.makeText(SpiderActivity.this, "诊断信息已复制，粘贴给比特", Toast.LENGTH_LONG).show();
                } catch (Throwable ignored) {}
            }});
            return "ok";
        }

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

    private String httpGet(String url) { return httpGet(url, null); }

    private String httpGet(String url, String cookieOverride) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(15000); c.setReadTimeout(20000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) Chrome/120 Mobile");
            String cookie = cookieOverride != null ? cookieOverride : CookieManager.getInstance().getCookie(SITE);
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
