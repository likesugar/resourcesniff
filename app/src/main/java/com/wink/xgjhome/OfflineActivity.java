package com.wink.xgjhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** 网页离线: 圆形站点卡主页(图1复刻), 点击浏览并自动存HTML快照, 断网自动回退快照; 也可上传html文件 */
public class OfflineActivity extends Activity {

    private static final String PREF = "offline_sites";
    private static final int[] COLORS = {0xFFd65db1, 0xFF9c8e7d, 0xFFa56bce, 0xFF315CDE, 0xFF1FA855, 0xFFe67e22};

    private LinearLayout homeRoot;
    private LinearLayout cardsRow;
    private WebView web;
    private boolean dark;
    private String pendingSnapFor = "";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dark = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("dark", false);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        buildHome();
    }

    // ---------------- 主页(图1复刻) ----------------

    private void buildHome() {
        if (web != null && web.getParent() != null) ((android.view.ViewGroup) web.getParent()).removeView(web);
        homeRoot = new LinearLayout(this);
        homeRoot.setOrientation(LinearLayout.VERTICAL);
        homeRoot.setBackgroundColor(dark ? 0xFF000000 : 0xFFFFFFFF);

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        cardsRow = new LinearLayout(this);
        cardsRow.setGravity(Gravity.CENTER_VERTICAL);
        cardsRow.setPadding(dip(24), dip(40), dip(24), 0);
        hs.addView(cardsRow);
        homeRoot.addView(hs);

        TextView hint = new TextView(this);
        hint.setText("  点开自动保存快照 · 断网可看最近版本\n  底部输入新网址 · 右上＋上传HTML");
        hint.setTextSize(12);
        hint.setTextColor(0xFF999999);
        hint.setPadding(dip(28), dip(30), 0, 0);
        homeRoot.addView(hint);

        FrameLayout fl = new FrameLayout(this);
        fl.addView(homeRoot, new FrameLayout.LayoutParams(-1, -1));

        // 右上 + (上传HTML)
        TextView plus = new TextView(this);
        plus.setText("＋");
        plus.setTextSize(22);
        plus.setTextColor(dark ? 0xFFEEEEEE : 0xFF1F2329);
        plus.setPadding(dip(18), dip(14), dip(18), dip(14));
        plus.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { uploadHtml(); } });
        fl.addView(plus, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.RIGHT));

        // 底部胶囊
        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable pbg = new GradientDrawable();
        pbg.setColor(dark ? 0xFF1C1F26 : 0xFFF2F3F5);
        pbg.setCornerRadius(dip(28));
        pill.setBackground(pbg);
        pill.setPadding(dip(22), dip(14), dip(22), dip(14));
        TextView mag = new TextView(this);
        mag.setText("🔍");
        mag.setTextSize(17);
        pill.addView(mag);
        TextView lab = new TextView(this);
        lab.setText("  网页离线");
        lab.setTextSize(17);
        lab.setTextColor(dark ? 0xFFEEEEEE : 0xFF1F2329);
        lab.setTypeface(null, android.graphics.Typeface.BOLD);
        pill.addView(lab);
        pill.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showAddDialog(); } });
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        plp.setMargins(dip(16), 0, dip(16), dip(24));
        fl.addView(pill, plp);

        setContentView(fl);
        refreshCards();
    }

    private void refreshCards() {
        cardsRow.removeAllViews();
        JSONArray a = load();
        for (int i = 0; i < a.length(); i++) {
            final JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            final String name = o.optString("name", "站");
            final String url = o.optString("url", "");
            if (url.isEmpty()) continue;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            card.setPadding(dip(2), 0, dip(24), 0);
            card.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { browse(url); } });
            card.setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View v) { siteMenu(name, url); return true; } });
            TextView av = new TextView(this);
            av.setText(name.length() > 2 ? name.substring(0, 2) : name);
            av.setTextColor(0xFFFFFFFF);
            av.setTextSize(22);
            av.setGravity(Gravity.CENTER);
            GradientDrawable c = new GradientDrawable();
            c.setShape(GradientDrawable.OVAL);
            c.setColor(o.has("color") ? o.optInt("color", COLORS[0]) : COLORS[i % COLORS.length]);
            av.setBackground(c);
            card.addView(av, new LinearLayout.LayoutParams(dip(74), dip(74)));
            TextView lb = new TextView(this);
            lb.setText(name);
            lb.setTextSize(15);
            lb.setTextColor(dark ? 0xFFEEEEEE : 0xFF000000);
            lb.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-2, -2);
            llp.topMargin = dip(12);
            card.addView(lb, llp);
            cardsRow.addView(card, new LinearLayout.LayoutParams(-2, -2));
        }
        // 上传卡
        LinearLayout up = new LinearLayout(this);
        up.setOrientation(LinearLayout.VERTICAL);
        up.setGravity(Gravity.CENTER_HORIZONTAL);
        up.setPadding(dip(2), 0, dip(24), 0);
        up.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { uploadHtml(); } });
        TextView ua = new TextView(this);
        ua.setText("＋");
        ua.setTextColor(0xFFFFFFFF);
        ua.setTextSize(26);
        ua.setGravity(Gravity.CENTER);
        GradientDrawable uc = new GradientDrawable();
        uc.setShape(GradientDrawable.OVAL);
        uc.setColor(0xFF666666);
        ua.setBackground(uc);
        up.addView(ua, new LinearLayout.LayoutParams(dip(74), dip(74)));
        TextView ul = new TextView(this);
        ul.setText("上传HTML");
        ul.setTextSize(12);
        ul.setTextColor(dark ? 0xFFCCCCCC : 0xFF444444);
        ul.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(-2, -2);
        ulp.topMargin = dip(12);
        up.addView(ul, ulp);
        cardsRow.addView(up, new LinearLayout.LayoutParams(-2, -2));
    }

    private void showAddDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dip(24), dip(10), dip(24), 0);
        final EditText et = new EditText(this);
        et.setHint("输入网址，如 tulpa.cn");
        et.setInputType(EditorInfo.TYPE_TEXT_VARIATION_URI);
        box.addView(et);
        new AlertDialog.Builder(this)
            .setTitle("添加离线网页")
            .setView(box)
            .setPositiveButton("添加", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) {
                    String u = et.getText().toString().trim();
                    if (u.isEmpty()) return;
                    if (!u.startsWith("http")) u = "https://" + u;
                    addSite(hostOf(u), u);
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void uploadHtml() {
        android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
        i.setType("*/*");
        i.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        startActivityForResult(android.content.Intent.createChooser(i, "选择HTML文件"), 9002);
    }

    @Override
    protected void onActivityResult(int req, int res, android.content.Intent data) {
        super.onActivityResult(req, res, data);
        if (req != 9002 || res != RESULT_OK || data == null || data.getData() == null) return;
        try {
            String name = "page.html";
            android.database.Cursor c = getContentResolver().query(data.getData(), null, null, null, null);
            if (c != null) { try { int ni = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (ni >= 0 && c.moveToFirst()) name = c.getString(ni); } finally { c.close(); } }
            File dir = snapDir();
            File f = new File(dir, md5(name + System.currentTimeMillis()) + ".html");
            InputStream is = getContentResolver().openInputStream(data.getData());
            FileOutputStream fos = new FileOutputStream(f);
            byte[] b = new byte[8192]; int n;
            while ((n = is.read(b)) > 0) fos.write(b, 0, n);
            is.close(); fos.close();
            JSONArray a = load();
            JSONObject o = new JSONObject();
            o.put("name", name.replaceAll("\\.html?$", ""));
            o.put("url", "file://" + f.getAbsolutePath());
            o.put("snap", f.getName());
            o.put("color", COLORS[a.length() % COLORS.length]);
            a.put(o);
            save(a);
            refreshCards();
            toast("已导入");
        } catch (Throwable e) { toast("导入失败: " + e); }
    }

    // ---------------- 浏览 + 快照 ----------------

    private void browse(String url) {
        if (web == null) {
            web = new WebView(this);
            WebSettings ws = web.getSettings();
            ws.setJavaScriptEnabled(true);
            ws.setDomStorageEnabled(true);
            ws.setUserAgentString("Mozilla/5.0 (Linux; Android 13) Chrome/120 Mobile");
            ws.setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            web.setWebViewClient(new WebViewClient() {
                @Override public void onReceivedSslError(WebView v, android.webkit.SslErrorHandler h, android.net.http.SslError e) { h.proceed(); }
                @Override public boolean shouldOverrideUrlLoading(WebView v, String u) { return false; }
                // 站内跳页: 每页独立快照集(已抓过跳过); 离线模式点未缓存链接→自动转在线补抓
                @Override public void onPageStarted(WebView v, String u, android.graphics.Bitmap fav) {
                    if (!u.startsWith("http") || u.equals(pendingSnapFor)) return;
                    if (hasFullSnap(u)) {
                        boolean wasOffline = offlineMode;
                        pendingSnapFor = u;
                        if (wasOffline) { offlineMode = true; captureMode = false; }
                        return;
                    }
                    if (captureMode || offlineMode) {
                        pendingSnapFor = u;
                        offlineMode = false; captureMode = true;
                        beginCapture(u);
                    }
                }
                // Via式整站快照: 拦截资源落地 / 离线回放
                @Override public android.webkit.WebResourceResponse shouldInterceptRequest(WebView v, android.webkit.WebResourceRequest req) {
                    String u = req.getUrl().toString();
                    if (!u.startsWith("http")) return null;
                    if (captureMode) return captureRes(u, pendingSnapFor);
                    if (offlineMode) return serveOffline(u);
                    return null;
                }
                @Override public void onReceivedError(WebView v, int code, String desc, String failing) {
                    // 加载失败 → 整站离线回放(Via式) → 兜底单页
                    if (failing != null && failing.contains(pendingSnapFor) && hasFullSnap(pendingSnapFor)) {
                        openOffline(pendingSnapFor);
                    } else if (failing != null && failing.contains(pendingSnapFor)) {
                        String snap = snapshotFor(pendingSnapFor);
                        String html = snap.isEmpty() ? "" : readSnapshot(snap);
                        if (!html.isEmpty()) {
                            v.loadDataWithBaseURL(pendingSnapFor, html, "text/html", "utf-8", null);
                            toast("离线快照(单页)");
                        }
                    }
                }
                @Override public void onPageFinished(WebView v, String u) {
                    if (captureMode) v.postDelayed(new Runnable() { public void run() { flushCapture(); } }, 4000);
                }
            });
        }
        pendingSnapFor = url.startsWith("http") ? url : "";
        FrameLayout root = new FrameLayout(this);
        if (web.getParent() instanceof android.view.ViewGroup) ((android.view.ViewGroup) web.getParent()).removeView(web);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        TextView back = new TextView(this);
        back.setText("🏠 离线主页");
        back.setTextColor(0xFFFFFFFF);
        back.setTextSize(13);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x66000000);
        bg.setCornerRadius(dip(18));
        back.setBackground(bg);
        back.setPadding(dip(14), dip(8), dip(14), dip(8));
        back.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { buildHome(); } });
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.LEFT);
        blp.setMargins(dip(10), dip(50), 0, 0);
        root.addView(back, blp);
        setContentView(root);
        if (url.startsWith("file://")) {
            captureMode = false; offlineMode = false;
            String html = readSnapshot(url.substring("file://".length()));
            web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
            return;
        }
        // 纯在线模式: 不抓快照不拦截 (抓取/离线走长按菜单)
        pendingSnapFor = url;
        captureMode = false; offlineMode = false;
        web.loadUrl(url);
    }

    /** 浏览器界面装配(供各模式复用) */
    private void showBrowser() {
        if (web == null) { browse("about:blank"); }
        FrameLayout root = new FrameLayout(this);
        if (web.getParent() instanceof android.view.ViewGroup) ((android.view.ViewGroup) web.getParent()).removeView(web);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        TextView back = new TextView(this);
        back.setText("🏠 离线主页");
        back.setTextColor(0xFFFFFFFF);
        back.setTextSize(13);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x66000000);
        bg.setCornerRadius(dip(18));
        back.setBackground(bg);
        back.setPadding(dip(14), dip(8), dip(14), dip(8));
        back.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { buildHome(); } });
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.LEFT);
        blp.setMargins(dip(10), dip(50), 0, 0);
        root.addView(back, blp);
        setContentView(root);
    }

    // ---------------- Via式整站快照 ----------------

    private boolean captureMode = false;
    private boolean offlineMode = false;
    private File capDir = null;
    private final java.util.Map<String, File> capMap = new java.util.concurrent.ConcurrentHashMap<>();

    /** 一个网站一个文件夹(按域名): 离线快照/tulpa.cn/ */
    private File fullDir(String siteUrl) {
        String host;
        try { host = new URL(siteUrl).getHost().replace("www.", ""); }
        catch (Throwable e) { host = md5(siteUrl); }
        host = host.replaceAll("[^a-zA-Z0-9.-]", "_");
        File d = new File(snapDir(), host);
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private boolean hasFullSnap(String url) { return new File(fullDir(url), "map.json").exists(); }

    private void beginCapture(String url) {
        capDir = fullDir(url);
        // 载入该站已有映射, 资源与页面累积不重抓
        try {
            File mf = new File(capDir, "map.json");
            if (mf.exists()) {
                JSONObject m = new JSONObject(readSnapshot(mf.getAbsolutePath()));
                java.util.Iterator<?> it = m.keys();
                while (it.hasNext()) {
                    String u = (String) it.next();
                    File f = new File(capDir, m.optString(u));
                    if (f.exists()) capMap.put(u, f);
                }
            }
        } catch (Throwable ignored) {}
    }

    /** 落盘当前页的URL映射(不关抓取, 跳页继续) */
    private void flushCapture() {
        if (!captureMode || capMap.isEmpty() || capDir == null) return;
        try {
            JSONObject m = new JSONObject();
            for (java.util.Map.Entry<String, File> e : capMap.entrySet()) m.put(e.getKey(), e.getValue().getName());
            FileOutputStream fos = new FileOutputStream(new File(capDir, "map.json"));
            fos.write(m.toString().getBytes(StandardCharsets.UTF_8));
            fos.close();
        } catch (Throwable ignored) {}
        capMap.clear();
    }

    /** 拦截下载: 自拉资源→落盘→回流; 主文档保存但放行走网络(防整页白屏) */
    private android.webkit.WebResourceResponse captureRes(String u, String referer) {
        boolean isMain = u.equals(referer);
        File out = new File(capDir, md5(u) + ".res");
        if (out.exists()) { capMap.put(u, out); return isMain ? null : serveRes(out, guessType(u)); }
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
            c.setConnectTimeout(8000); c.setReadTimeout(10000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) Chrome/120 Mobile");
            c.setRequestProperty("Referer", referer == null || referer.isEmpty() ? u : referer);
            if (c.getResponseCode() != 200) return null;
            InputStream is = c.getInputStream();
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while ((n = is.read(buf)) > 0) b.write(buf, 0, n);
            is.close();
            byte[] data = b.toByteArray();
            if (data.length > 8 * 1024 * 1024) return null;
            FileOutputStream fos = new FileOutputStream(out);
            fos.write(data); fos.close();
            capMap.put(u, out);
            flushCapture(); // 即时刷写, 中途退出不丢映射
            return isMain ? null : new android.webkit.WebResourceResponse(guessType(u), null, new java.io.ByteArrayInputStream(data));
        } catch (Throwable e) { return null; }
    }

    /** 离线打开时, 一次载入全部站点快照映射(子页面在任何目录都能命中) */
    private final java.util.Map<String, File> offlineIndex = new java.util.concurrent.ConcurrentHashMap<>();

    private void buildOfflineIndex() {
        offlineIndex.clear();
        File base = snapDir();
        File[] dirs = base.listFiles();
        if (dirs == null) return;
        for (File d : dirs) {
            if (!d.isDirectory() || !d.getName().startsWith("site_")) continue;
            File mf = new File(d, "map.json");
            if (!mf.exists()) continue;
            try {
                JSONObject m = new JSONObject(readSnapshot(mf.getAbsolutePath()));
                java.util.Iterator<?> it = m.keys();
                while (it.hasNext()) {
                    String u = (String) it.next();
                    offlineIndex.put(u, new File(d, m.optString(u)));
                }
            } catch (Throwable ignored) {}
        }
    }

    private android.webkit.WebResourceResponse serveOffline(String u) {
        File f = offlineIndex.get(u);
        if (f == null || !f.exists()) return new android.webkit.WebResourceResponse("text/plain", "utf-8", new java.io.ByteArrayInputStream(new byte[0]));
        return serveRes(f, guessType(u));
    }

    private android.webkit.WebResourceResponse serveRes(File f, String type) {
        try { return new android.webkit.WebResourceResponse(type, null, new java.io.FileInputStream(f)); }
        catch (Throwable e) { return null; }
    }

    private String guessType(String u) {
        String lu = u.toLowerCase();
        if (lu.endsWith(".css")) return "text/css";
        if (lu.endsWith(".js")) return "application/javascript";
        if (lu.endsWith(".png")) return "image/png";
        if (lu.endsWith(".gif")) return "image/gif";
        if (lu.endsWith(".webp")) return "image/webp";
        if (lu.endsWith(".svg")) return "image/svg+xml";
        if (lu.endsWith(".woff2")) return "font/woff2";
        if (lu.endsWith(".woff")) return "font/woff";
        if (lu.endsWith(".mp4")) return "video/mp4";
        return "text/html";
    }

    /** 离线整站回放: 先建全量索引, 子页面跨目录命中 */
    private void openOffline(String url) {
        showBrowser();
        offlineMode = true; captureMode = false;
        pendingSnapFor = url;
        buildOfflineIndex();
        web.loadUrl(url);
        toast("离线模式(整站)");
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.getParent() != null && web.canGoBack()) web.goBack();
        else if (web != null && web.getParent() != null) buildHome();
        else super.onBackPressed();
    }

    private boolean netOk(final String url) {
        final java.util.concurrent.atomic.AtomicBoolean ok = new java.util.concurrent.atomic.AtomicBoolean(false);
        Thread t = new Thread(new Runnable() { public void run() {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(4000); c.setReadTimeout(4000);
                c.setRequestMethod("GET");
                c.setRequestProperty("Range", "bytes=0-1023");
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) Chrome/120 Mobile");
                int code = c.getResponseCode();
                ok.set(code >= 200 && code < 500 && code != 403);
            } catch (Throwable e) { ok.set(false); }
        }});
        t.start();
        try { t.join(6000); } catch (InterruptedException ignored) {}
        return ok.get();
    }

    // ---------------- 数据 ----------------

    private File snapDir() {
        // 公共可访问目录: /storage/emulated/0/Android/data/com.wink.xgjhome/files/离线快照/
        File d = new File(getExternalFilesDir(null), "离线快照");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private String readSnapshot(String path) {
        try {
            java.io.FileInputStream fis = new java.io.FileInputStream(path);
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while ((n = fis.read(buf)) > 0) b.write(buf, 0, n);
            fis.close();
            return new String(b.toByteArray(), StandardCharsets.UTF_8);
        } catch (Throwable e) { return ""; }
    }

    private String snapshotFor(String url) {
        if (url == null || url.isEmpty()) return "";
        JSONArray a = load();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && url.equals(o.optString("url"))) {
                String s = o.optString("snap", "");
                if (!s.isEmpty()) return new File(snapDir(), s).getAbsolutePath();
            }
        }
        return "";
    }

    private void markSnap(String url, String file) {
        JSONArray a = load();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && url.equals(o.optString("url"))) { try { o.put("snap", file); } catch (Throwable ignored) {} }
        }
        save(a);
    }

    private void addSite(String name, String url) {
        JSONArray a = load();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && url.equals(o.optString("url"))) { toast("已存在"); return; }
        }
        try {
            JSONObject o = new JSONObject();
            o.put("name", name);
            o.put("url", url);
            o.put("color", COLORS[a.length() % COLORS.length]);
            a.put(o);
            save(a);
            refreshCards();
        } catch (Throwable ignored) {}
    }

    /** 卡片长按菜单: 在线/离线/抓快照/重命名/删除 各走各的 */
    private void siteMenu(final String name, final String url) {
        final String[] items = {"🌐 在线打开", "📥 抓取快照(在线)", "📕 离线打开(整站)", "✏️ 重命名", "🗑️ 删除"};
        new AlertDialog.Builder(this)
            .setTitle(name)
            .setItems(items, new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) {
                    if (w == 0) { browse(url); }
                    else if (w == 1) { captureBrowse(url); }
                    else if (w == 2) { openOffline(url); }
                    else if (w == 3) { renameSite(name, url); }
                    else delSite(url);
                }
            }).show();
    }

    /** 抓取快照模式: 在线浏览+全资源落地 */
    private void captureBrowse(String url) {
        showBrowser();
        pendingSnapFor = url;
        offlineMode = false; captureMode = true;
        beginCapture(url);
        web.loadUrl(url);
        toast("快照抓取中…浏览要离线的页面");
    }

    private void renameSite(final String oldName, final String url) {
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dip(24), dip(10), dip(24), 0);
        final EditText et = new EditText(this);
        et.setText(oldName);
        box.addView(et);
        new AlertDialog.Builder(this).setTitle("重命名").setView(box)
            .setPositiveButton("保存", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface dg, int w) {
                    JSONArray a = load();
                    for (int i = 0; i < a.length(); i++) {
                        JSONObject o = a.optJSONObject(i);
                        if (o != null && url.equals(o.optString("url"))) {
                            try { o.put("name", et.getText().toString().trim()); } catch (Throwable ignored) {}
                        }
                    }
                    save(a);
                    refreshCards();
                }
            }).setNegativeButton("取消", null).show();
    }

    private void delSite(String url) {
        JSONArray src = load();
        JSONArray out = new JSONArray();
        for (int i = 0; i < src.length(); i++) {
            JSONObject o = src.optJSONObject(i);
            if (o == null || !url.equals(o.optString("url"))) out.put(o);
        }
        save(out);
        refreshCards();
        toast("已删除(长按卡片)");
    }

    private JSONArray load() {
        try { return new JSONArray(getSharedPreferences(PREF, MODE_PRIVATE).getString("json", "[]")); }
        catch (Throwable e) { return new JSONArray(); }
    }

    private void save(JSONArray a) {
        getSharedPreferences(PREF, MODE_PRIVATE).edit().putString("json", a.toString()).apply();
    }

    private String hostOf(String u) {
        try { return new URL(u).getHost().replace("www.", ""); } catch (Throwable e) { return "站"; }
    }

    private String md5(String s) {
        try {
            MessageDigest m = MessageDigest.getInstance("MD5");
            byte[] d = m.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte x : d) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Throwable e) { return String.valueOf(s.hashCode()); }
    }

    private int dip(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private void toast(String s) {
        runOnUiThread(new Runnable() { public void run() {
            Toast.makeText(this2(), s, Toast.LENGTH_SHORT).show();
        }});
    }
    private OfflineActivity this2() { return this; }

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
