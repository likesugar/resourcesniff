package com.wink.xgjhome;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.ClipboardManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 嗅探页：复刻抖音直播解析主页面（WebView + 顶栏 + 右下角半透明FAB + 记录面板） */
public class SniffActivity extends Activity {

    private static final Pattern MEDIA = Pattern.compile(
        "\\.(m3u8|mp4|flv|mkv|avi|ts|webm|mp3|m4a|aac|flac|mov)(\\?|$)|\\.ts\\?|/stream/|media-worker", Pattern.CASE_INSENSITIVE);
    private static final Pattern URL_IN_TEXT = Pattern.compile(
        "(https?://|www\\.)[\\w\\-./?:#=&%+~@!$'*;,\\[\\]]+", Pattern.CASE_INSENSITIVE);

    private WebView webView;
    private EditText etUrl;
    private LinearLayout topBar;
    private LinearLayout bottomPanel;
    private LinearLayout layoutRecords;
    private View fab;
    private Button btnSwitchUa;

    private boolean isMobileUa = false;
    private boolean isRecordsVisible = false;
    private boolean isTopBarVisible = true;

    private final ArrayList<String> foundUrls = new ArrayList<>();
    private final LinkedHashSet<String> recordKeys = new LinkedHashSet<>();

    @SuppressLint({"SetJavaScriptEnabled", "ClickableViewAccessibility"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sniff);

        webView = findViewById(R.id.webview);
        etUrl = findViewById(R.id.et_url);
        topBar = findViewById(R.id.top_bar);
        bottomPanel = findViewById(R.id.bottom_panel);
        layoutRecords = findViewById(R.id.layout_records);
        fab = findViewById(R.id.btn_toggle_top);
        btnSwitchUa = findViewById(R.id.btn_switch_ua);
        Button btnGo = findViewById(R.id.btn_go);
        Button btnRecords = findViewById(R.id.btn_records);

        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        ws.setCacheMode(WebSettings.LOAD_DEFAULT);
        ws.setDatabaseEnabled(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        webView.addJavascriptInterface(new Object() {
            @android.webkit.JavascriptInterface
            public String getUrl() { return playerUrl; }
        }, "AndroidPlayer");

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase();
                if ("http".equals(scheme) || "https".equals(scheme)) return false;
                // 非 http(s) 协议：不拉起外部、不询问；剩余部分带域名才转 https，否则忽略
                String s2 = u.toString();
                String rest = s2.replaceAll("^[a-zA-Z][a-zA-Z0-9+.-]*://", "");
                int slash = rest.indexOf('/');
                String host = slash >= 0 ? rest.substring(0, slash) : rest;
                if (host.indexOf('.') >= 0) {
                    view.loadUrl("https://" + rest);
                }
                return true;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                try {
                    if (!"GET".equalsIgnoreCase(request.getMethod())) return null;
                    Uri u = request.getUrl();
                    if (!"http".equals(u.getScheme()) && !"https".equals(u.getScheme())) return null;
                    String url = u.toString();
                    if (isMediaUrl(url) && recordKeys.add(url)) {
                        foundUrls.add(url);
                        final String page = webView.getTitle();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                addRecord(url, page == null ? "" : page);
                                if (!isRecordsVisible) toggleRecords();
                            }
                        });
                    }
                } catch (Exception ignored) {
                }
                return null;
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                if (url != null && url.contains("douyin.com")) injectDouyinScript();
            }

            public void onPageFinished(WebView view, String url) {
                BiliResolver.tryParse(SniffActivity.this, url);
                etUrl.setText(url);
            }
        });

        // 回车按钮（及键盘 Go）：打开网址（B站/抖音/其他网站都能进）
        btnGo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openInputUrl();
            }
        });
        etUrl.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent event) {
                openInputUrl();
                return true;
            }
        });

        btnSwitchUa.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchUa();
            }
        });
        btnRecords.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleRecords();
            }
        });

        // FAB：切换顶部栏
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleTopBar();
            }
        });

        // 点屏幕（非FAB区域）→ FAB 出现；再点一下 → 消失
        webView.setOnTouchListener(new View.OnTouchListener() {
            float x0, y0;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    x0 = event.getX(); y0 = event.getY();
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    if (Math.abs(event.getX() - x0) < 12 && Math.abs(event.getY() - y0) < 12) {
                        fab.setVisibility(fab.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                    }
                }
                return false;
            }
        });

        // 弹窗带入的输入
        String pre = getIntent().getStringExtra("input");
        if (pre != null && pre.length() > 0) {
            etUrl.setText(pre);
            openInputUrl();
        } else {
            webView.loadUrl("https://live.douyin.com/");
        }
    }

    void hideBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideBars();
    }

    void openInputUrl() {
        String raw = etUrl.getText().toString().trim();
        if (raw.length() == 0) return;

        String url = extractUrl(raw);
        if (url == null) {
            if (raw.matches("\\d+")) {
                url = "https://live.douyin.com/" + raw;
            } else {
                return;
            }
        }
        if (!url.startsWith("http")) url = "https://" + url;
        webView.loadUrl(url);
    }

    static String extractUrl(String text) {
        Matcher m = URL_IN_TEXT.matcher(text);
        if (m.find()) {
            String u = m.group();
            if (u.startsWith("www.")) u = "https://" + u;
            return u;
        }
        if (text.matches("[\\w\\-./?:#=&%+~@!$'*;,\\[\\]]+")) return text;
        return null;
    }

    /** B站解析（cookie 版）：登录后 SESSDATA 走 view+playurl API，DASH 标画质，durl 合并流可直接播/下 */
    static class BiliResolver {
        static final java.util.Set<String> parsed = new java.util.HashSet<String>();

        static String storedCookies(android.content.Context c) {
            return c.getSharedPreferences("bili", android.content.Context.MODE_PRIVATE)
                    .getString("cookies", null);
        }

        static void tryParse(final SniffActivity act, String pageUrl) {
            try {
                if (pageUrl == null) return;
                Uri u = Uri.parse(pageUrl);
                String host = u.getHost() == null ? "" : u.getHost();
                if (!host.contains("bilibili.com")) return;
                if (!u.getPath().contains("/video/")) return;
                Matcher m = Pattern.compile("(BV[0-9A-Za-z]{10})").matcher(pageUrl);
                if (!m.find()) return;
                final String bvid = m.group(1);
                int p = 1;
                try { p = Integer.parseInt(u.getQueryParameter("p")); } catch (Exception e) { }
                final int pIdx = p;
                if (!parsed.add(bvid + "#p" + pIdx)) return;

                final String cookies = storedCookies(act);
                if (cookies == null || !cookies.contains("SESSDATA=")) {
                    // 首次：弹窗引导登录
                    act.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            new android.app.AlertDialog.Builder(act)
                                    .setTitle("需要登录B站")
                                    .setMessage("登录后才能解析并下载/播放最高画质，是否前往登录？")
                                    .setNegativeButton("取消", null)
                                    .setPositiveButton("去登录", new android.content.DialogInterface.OnClickListener() {
                                        public void onClick(android.content.DialogInterface d, int w) {
                                            act.startActivity(new android.content.Intent(act, BiliLoginActivity.class));
                                        }
                                    })
                                    .show();
                        }
                    });
                    parsed.remove(bvid + "#p" + pIdx); // 登录后重试
                    return;
                }

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            String vjson = httpGet("https://api.bilibili.com/x/web-interface/view?bvid=" + bvid, cookies);
                            org.json.JSONObject vroot = new org.json.JSONObject(vjson);
                            if (vroot.optInt("code", -1) != 0) return;
                            org.json.JSONArray pages = vroot.getJSONObject("data").getJSONArray("pages");
                            long cid = pages.getJSONObject(Math.min(pIdx - 1, pages.length() - 1)).getLong("cid");
                            final String title = vroot.getJSONObject("data").optString("title", "B站视频");

                            // DASH：只用来取最高画质标签
                            String djson = httpGet("https://api.bilibili.com/x/player/playurl?bvid=" + bvid
                                    + "&cid=" + cid + "&qn=127&fnval=16&fourk=1", cookies);
                            org.json.JSONObject dd = new org.json.JSONObject(djson).getJSONObject("data");
                            int qn = dd.optInt("quality", 0);
                            final String qname = qnName(qn);

                            // durl：合并了音轨的完整流，可直接播放/下载
                            String mjson = httpGet("https://api.bilibili.com/x/player/playurl?bvid=" + bvid
                                    + "&cid=" + cid + "&qn=127&fnval=0&platform=html5", cookies);
                            org.json.JSONObject md = new org.json.JSONObject(mjson).getJSONObject("data");
                            String merged = null;
                            org.json.JSONArray durls = md.optJSONArray("durl");
                            if (durls != null && durls.length() > 0) {
                                merged = durls.getJSONObject(0).optString("url",
                                        durls.getJSONObject(0).optString("url"));
                            }
                            final String fmerged = merged;
                            act.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    String key = "bili#" + bvid + "#p" + pIdx;
                                    if (act.recordKeys.contains(key)) return;
                                    act.recordKeys.add(key);
                                    String label = "▶" + qname + "·" + title;
                                    act.addRecord(fmerged != null ? fmerged : ("RESOLVE:" + bvid + ":" + pIdx), label);
                                    if (!act.isRecordsVisible) act.toggleRecords();
                                }
                            });
                        } catch (Exception e) { }
                    }
                }).start();
            } catch (Exception e) { }
        }

        static String qnName(int qn) {
            switch (qn) {
                case 127: return "8K"; case 126: return "杜比"; case 125: return "HDR";
                case 120: return "4K"; case 116: return "1080P60"; case 112: return "1080P高码率";
                case 80: return "1080P"; case 74: return "720P60"; case 64: return "720P";
                case 32: return "480P"; case 16: return "360P";
                default: return "qn" + qn;
            }
        }

        static String httpGet(String url, String cookies) throws Exception {
            java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            c.setConnectTimeout(8000); c.setReadTimeout(8000);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
            c.setRequestProperty("Referer", "https://www.bilibili.com/");
            if (cookies != null && cookies.length() > 0) c.setRequestProperty("Cookie", cookies);
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(c.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            return sb.toString();
        }
    }

    static boolean isMediaUrl(String url) {
        return MEDIA.matcher(url.toLowerCase()).find();
    }

    /** 油猴脚本离线内置：抖音网页版全能优化（assets/douyin.user.js） */
    private String jsDouyin = null;

    void injectDouyinScript() {
        if (jsDouyin == null) jsDouyin = readAsset("douyin.user.js");
        if (jsDouyin == null) return;
        String shim = "if(typeof window.GM_addStyle=='undefined'){window.GM_addStyle=function(c){var s=document.createElement('style');s.textContent=c;document.head.appendChild(s);};}"
            + "if(typeof window.GM_getValue=='undefined'){window.GM_getValue=function(k,d){var v=localStorage.getItem('gm_'+k);return v===null?d:v;};window.GM_setValue=function(k,v){localStorage.setItem('gm_'+k,v);};window.GM_deleteValue=function(k){localStorage.removeItem('gm_'+k);};}"
            + "if(typeof window.GM_xmlhttpRequest=='undefined'){window.GM_xmlhttpRequest=function(d){fetch(d.url).then(function(r){return r.text();}).then(function(t){if(d.onload)d.onload({responseText:t,status:200});});};}"
            + "if(typeof window.GM_info=='undefined'){window.GM_info={script:{version:'offline'}};}"
            + "if(typeof window.unsafeWindow=='undefined'){window.unsafeWindow=window;}";
        webView.evaluateJavascript(shim, null);
        final String escaped = jsDouyin.replace("\\", "\\\\").replace("'", "\'").replace("\n", "\\n").replace("\r", "");
        webView.evaluateJavascript("(function(){try{eval('" + escaped + "');}catch(e){console.log('userscript error',e);}})();", null);
    }

    String readAsset(String name) {
        try {
            java.io.InputStream is = getAssets().open(name);
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[8192]; int n;
            while ((n = is.read(b)) > 0) bo.write(b, 0, n);
            is.close();
            return bo.toString("UTF-8");
        } catch (Throwable e) { return null; }
    }

    void switchUa() {
        if (isMobileUa) {
            webView.getSettings().setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            btnSwitchUa.setText("切手机UA");
            btnSwitchUa.setBackgroundColor(0x4D3742fa);
            isMobileUa = false;
        } else {
            webView.getSettings().setUserAgentString("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
            btnSwitchUa.setText("切电脑UA");
            btnSwitchUa.setBackgroundColor(0x4D2ed573);
            isMobileUa = true;
        }
        webView.reload();
    }

    void toggleRecords() {
        if (isRecordsVisible) {
            bottomPanel.setVisibility(View.GONE);
            ((Button) findViewById(R.id.btn_records)).setBackgroundColor(0x4Dff4757);
            isRecordsVisible = false;
        } else {
            bottomPanel.setVisibility(View.VISIBLE);
            ((Button) findViewById(R.id.btn_records)).setBackgroundColor(0x4Dff4757);
            isRecordsVisible = true;
        }
    }

    void toggleTopBar() {
        isTopBarVisible = !isTopBarVisible;
        topBar.setVisibility(isTopBarVisible ? View.VISIBLE : View.GONE);
    }

    private volatile String playerUrl = null;

    /** 内置 jessibuca 播放器：player.html 通过 AndroidPlayer 桥取地址 */
    void playUrl(String url) {
        try {
            playerUrl = url;
            android.content.Intent i = new android.content.Intent(this, NativePlayerActivity.class);
            i.putExtra("url", url);
            i.putExtra("title", "资源嗅探");
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "打不开: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    void addRecord(final String url, String title) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(16, 12, 16, 12);
        row.setBackgroundColor(0x33222244);

        TextView tv = new TextView(this);
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(13);
        String label = (title == null || title.length() == 0) ? "嗅探 " + (foundUrls.size()) : title;
        tv.setText(label);
        tv.setSingleLine(true);

        TextView menuBtn = new TextView(this);
        menuBtn.setText("\u22EE");
        menuBtn.setBackgroundColor(0x338899AA);
        menuBtn.setTextColor(0xFFFFFFFF);
        menuBtn.setTextSize(16);
        menuBtn.setPadding(12, 4, 12, 4);
        menuBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showRecordMenu(v, url, row);
            }
        });
        LinearLayout headRow = new LinearLayout(this);
        headRow.setOrientation(LinearLayout.HORIZONTAL);
        headRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        headRow.addView(tv, new LinearLayout.LayoutParams(0,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        headRow.addView(menuBtn);
        row.addView(headRow);

        TextView tvUrl = new TextView(this);
        tvUrl.setTextColor(0xFF8AB4F8);
        tvUrl.setTextSize(11);
        tvUrl.setText(url);
        tvUrl.setSingleLine(true);
        row.addView(tvUrl);

        // 点行复制（不删除）
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("url", url));
                Toast.makeText(SniffActivity.this, "已复制", Toast.LENGTH_SHORT).show();
            }
        });

        layoutRecords.addView(row, 0);
    }

    void showRecordMenu(final View anchor, final String url, final LinearLayout row) {
        android.widget.PopupMenu pm = new android.widget.PopupMenu(this, anchor);
        pm.getMenu().add("下载");
        pm.getMenu().add("播放");
        final String label = recThreads.containsKey(url) ? "停止录制" : "直播录制";
        pm.getMenu().add(label);
        pm.getMenu().add("删除");
        pm.setOnMenuItemClickListener(new android.widget.PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(android.view.MenuItem item) {
                String t = item.getTitle().toString();
                if (t.equals("下载")) downloadUrl(url);
                else if (t.equals("播放")) playUrl(url);
                else if (t.equals("直播录制")) startRecord(url);
                else if (t.equals("停止录制")) stopRecord(url);
                else if (t.equals("删除")) {
                    ViewGroup p = (ViewGroup) row.getParent();
                    if (p != null) p.removeView(row);
                    recordKeys.remove(url);
                    foundUrls.remove(url);
                    Toast.makeText(SniffActivity.this, "已删除", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        });
        pm.show();
    }

    final java.util.Map<String, Thread> recThreads = new java.util.HashMap<>();

        String refererFor(String url) {
        return url.contains("bilibili.com") || url.contains("bilivideo") ? "https://www.bilibili.com/" : null;
    }

    void downloadUrl(String url) {
        try {
            android.app.DownloadManager.Request req = new android.app.DownloadManager.Request(android.net.Uri.parse(url));
            String rf = refererFor(url);
            if (rf != null) req.addRequestHeader("Referer", rf);
            req.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_MOVIES,
                    "资源嗅探_" + System.currentTimeMillis() + ".ts");
            ((android.app.DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(req);
            Toast.makeText(this, "已加入下载（Movies）", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "下载失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    void startRecord(final String url) {
        if (recThreads.containsKey(url)) return;
        Toast.makeText(this, "开始录制", Toast.LENGTH_SHORT).show();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                java.io.File out = new java.io.File(
                        android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES),
                        "资源嗅探_直播_" + System.currentTimeMillis() + ".ts");
                java.io.InputStream in = null;
                java.io.FileOutputStream fos = null;
                try {
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                    c.setConnectTimeout(8000); c.setReadTimeout(8000);
                    c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
                    String rf = refererFor(url);
                    if (rf != null) c.setRequestProperty("Referer", rf);
                    in = c.getInputStream();
                    fos = new java.io.FileOutputStream(out);
                    byte[] buf = new byte[65536]; int n; long total = 0;
                    while ((n = in.read(buf)) > 0) {
                        fos.write(buf, 0, n);
                        total += n;
                    }
                    final String msg = "录制完成: " + (total / 1048576) + "MB " + out.getName();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() { Toast.makeText(SniffActivity.this, msg, Toast.LENGTH_LONG).show(); }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() { Toast.makeText(SniffActivity.this, "录制中断: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
                    });
                } finally {
                    try { if (in != null) in.close(); } catch (Exception e) { }
                    try { if (fos != null) fos.close(); } catch (Exception e) { }
                    recThreads.remove(url);
                }
            }
        });
        recThreads.put(url, t);
        t.start();
    }

    void stopRecord(String url) {
        Thread t = recThreads.remove(url);
        if (t != null) t.interrupt();
        Toast.makeText(this, "已停止", Toast.LENGTH_SHORT).show();
    }    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
