package com.wink.xgjhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebResourceResponse;
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.HashMap;

/**
 * 网页离线(浏览器式保存): 在线浏览自动把整页资源(CSS/JS/图片/字体)内联成自包含HTML快照;
 * 离线时同URL回放, 已保存页面之间的链接可互相跳转(链接套页面)。
 */
public class OfflineActivity extends Activity {

    private static final String PREF = "offline_sites";
    private static final String[] SITE_NAMES = {"秀人网"};
    private static final String[] SITE_URLS = {"https://axiuren.com/"};
    private static final int[] COLORS = {0xFFd65db1, 0xFF315CDE, 0xFF9c8e7d, 0xFFa56bce, 0xFF1FA855, 0xFFe67e22};

    private LinearLayout homeRoot;
    private LinearLayout cardsRow;
    private LinearLayout pill;
    private boolean dark;
    private WebView web;
    private String pendingSnapFor = "";
    private boolean snapBusy = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dark = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("dark", false);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        buildHome();
    }

    private int dip(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    // ---------------- 主页(图1复刻) ----------------

    private void buildHome() {
        if (web != null && web.getParent() != null) ((android.view.ViewGroup) web.getParent()).removeView(web);
        homeRoot = new LinearLayout(this);
        homeRoot.setOrientation(LinearLayout.VERTICAL);
        homeRoot.setBackgroundColor(dark ? 0xFF000000 : 0xFFFFFFFF);

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        cardsRow = new LinearLayout(this);
        cardsRow.setOrientation(LinearLayout.HORIZONTAL);
        cardsRow.setGravity(Gravity.CENTER_VERTICAL);
        int hpad = dip(20);
        cardsRow.setPadding(hpad, dip(28), hpad, dip(20));
        hs.addView(cardsRow);
        homeRoot.addView(hs, new LinearLayout.LayoutParams(-1, 0, 1f));

        pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable pbg = new GradientDrawable();
        pbg.setColor(dark ? 0xFF1C1F26 : 0xFFF2F3F5);
        pbg.setCornerRadius(dip(26));
        pill.setBackground(pbg);
        pill.setPadding(dip(16), dip(10), dip(16), dip(10));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, -2);
        plp.setMargins(dip(16), dip(10), dip(16), dip(24));
        TextView star = new TextView(this);
        star.setText("⭐");
        star.setTextSize(16);
        pill.addView(star);
        TextView label = new TextView(this);
        label.setText("  网页离线 · 点此添加网址");
        label.setTextSize(14);
        label.setTextColor(dark ? 0xFF9AA0A6 : 0xFF5F6368);
        pill.addView(label);
        pill.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showAddDialog(); } });
        homeRoot.addView(pill, plp);

        dotBtn = null;
        setContentView(homeRoot);
        refreshCards();
    }

    private void refreshCards() {
        cardsRow.removeAllViews();
        JSONArray a = load();
        int n = a.length();
        for (int i0 = 0; i0 < n; i0++) {
            final int i = i0;
            JSONObject o = a.optJSONObject(i);
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
        // "+" 上传
        LinearLayout add = new LinearLayout(this);
        add.setOrientation(LinearLayout.VERTICAL);
        add.setGravity(Gravity.CENTER_HORIZONTAL);
        add.setPadding(dip(2), 0, dip(8), 0);
        add.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { uploadHtml(); } });
        TextView av2 = new TextView(this);
        av2.setText("＋");
        av2.setTextSize(30);
        av2.setTextColor(0xFFFFFFFF);
        av2.setGravity(Gravity.CENTER);
        GradientDrawable c2 = new GradientDrawable();
        c2.setShape(GradientDrawable.OVAL);
        c2.setColor(dark ? 0xFF2A2F3A : 0xFFE8EAED);
        av2.setBackground(c2);
        add.addView(av2, new LinearLayout.LayoutParams(dip(74), dip(74)));
        TextView lb2 = new TextView(this);
        lb2.setText("上传");
        lb2.setTextSize(15);
        lb2.setTextColor(dark ? 0xFF9AA0A6 : 0xFF5F6368);
        lb2.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ll2 = new LinearLayout.LayoutParams(-2, -2);
        ll2.topMargin = dip(12);
        add.addView(lb2, ll2);
        cardsRow.addView(add, new LinearLayout.LayoutParams(-2, -2));
    }

    private void showAddDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dip(24), dip(10), dip(24), 0);
        final EditText et = new EditText(this);
        et.setHint("输入网址");
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

    private String hostOf(String u) {
        try { return new URL(u).getHost().replace("www.", ""); } catch (Throwable e) { return "站"; }
    }

    private void addSite(String name, String url) {
        JSONArray a = load();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && url.equals(o.optString("url"))) { toast("已存在"); browse(url); return; }
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
        browse(url);
    }

    private void uploadHtml() {
        android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
        i.setType("*/*");
        i.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        startActivityForResult(android.content.Intent.createChooser(i, "选择HTML/ZIP"), 9002);
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
            boolean isZip = name.toLowerCase().endsWith(".zip");
            if (isZip) { importZip(data.getData(), name); return; }
            File dir = snapDir();
            File f = new File(dir, md5(name + System.currentTimeMillis()) + ".html");
            InputStream is = getContentResolver().openInputStream(data.getData());
            FileOutputStream fos = new FileOutputStream(f);
            byte[] b = new byte[8192]; int n;
            while ((n = is.read(b)) > 0) fos.write(b, 0, n);
            is.close(); fos.close();
            JSONObject o = new JSONObject();
            o.put("name", name.replaceAll("(?i)\\.html?$", ""));
            o.put("url", "file://" + f.getAbsolutePath());
            o.put("color", COLORS[load().length() % COLORS.length]);
            JSONArray a = load();
            a.put(o);
            save(a);
            refreshCards();
            toast("已导入");
        } catch (Throwable e) { toast("导入失败: " + e); }
    }

    private void importZip(android.net.Uri uri, String zipName) {
        try {
            String site = "z" + System.currentTimeMillis();
            File dir = new File(new File(getFilesDir(), "offline_zip"), site);
            if (!dir.exists()) dir.mkdirs();
            java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(getContentResolver().openInputStream(uri));
            java.util.zip.ZipEntry e;
            int count = 0;
            File index = null;
            while ((e = zis.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String fn = e.getName();
                if (fn.contains("..")) continue;
                File out = new File(dir, fn);
                File parent = out.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                FileOutputStream fos = new FileOutputStream(out);
                byte[] b = new byte[8192]; int n;
                while ((n = zis.read(b)) > 0) fos.write(b, 0, n);
                fos.close();
                count++;
                if (index == null && (fn.endsWith("index.html") || fn.endsWith("index.htm"))) index = out;
                if (index == null && fn.endsWith(".html") && !fn.contains("/")) index = out;
            }
            zis.close();
            if (index == null) { toast("包里没找到html"); return; }
            final String vurl = "http://ziplocal." + site + "/" + site + "/";
            zipDirs.put(site, dir);
            JSONObject o = new JSONObject();
            o.put("name", zipName.replaceAll("(?i)\\.zip$", ""));
            o.put("url", "http://ziplocal." + site + "/" + (dir.toPath().relativize(index.toPath()).toString().replace('\\', '/')));
            o.put("zipdir", dir.getAbsolutePath());
            o.put("color", COLORS[load().length() % COLORS.length]);
            JSONArray a = load();
            a.put(o);
            save(a);
            refreshCards();
            toast("已导入 " + count + " 个文件");
        } catch (Throwable e) { toast("导入失败: " + e); }
    }

    // ---------------- 浏览 + 整页快照 ----------------

    private void browse(String url) {
        ensureWeb();
        showBrowser();
        if (url.startsWith("file://")) {
            String html = readSnapshot(url.substring("file://".length()));
            web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
            return;
        }
        if (url.startsWith("http://ziplocal.")) {
            offlineMode = false;
            web.loadUrl(url);
            return;
        }
        pendingSnapFor = url;
        offlineMode = false;
        if (netOk(url)) {
            web.loadUrl(url); // 在线: 加载完自动整页快照
        } else if (hasSnap(url)) {
            offlineMode = true;
            buildOfflineIndex();
            web.loadUrl(url);
            toast("离线浏览");
        } else {
            toast("无网络且无快照");
        }
    }

    private void ensureWeb() {
        if (web != null) return;
        web = new WebView(this);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setUserAgentString("Mozilla/5.0 (Linux; Android 13) Chrome/120 Mobile");
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        try { ws.setAllowFileAccess(true); } catch (Throwable ignored) {}
        web.setWebViewClient(new WebViewClient() {
            @Override public void onReceivedSslError(WebView v, android.webkit.SslErrorHandler h, android.net.http.SslError e) { h.proceed(); }
            @Override public boolean shouldOverrideUrlLoading(WebView v, String u) { return false; }
            @Override public android.webkit.WebResourceResponse shouldInterceptRequest(WebView v, android.webkit.WebResourceRequest req) {
                String u = req.getUrl().toString();
                if (u.startsWith("http://ziplocal.")) return serveZip(u);
                if (offlineMode && u.startsWith("http")) return serveOffline(u);
                return null;
            }
            @Override public void onReceivedError(WebView v, int code, String desc, String failing) {
                if (offlineMode || failing == null || !failing.startsWith("http")) return;
                buildOfflineIndex();
                if (offlineIndex.containsKey(failing) || offlineIndex.containsKey(pendingSnapFor)) {
                    offlineMode = true;
                    web.post(new Runnable() { public void run() { web.loadUrl(pendingSnapFor); } });
                    toast("已切换离线浏览");
                }
            }
            @Override public void onPageFinished(WebView v, String u) {
                if (!u.startsWith("http") || snapBusy || offlineMode) return;
                pendingSnapFor = u;
                v.postDelayed(new Runnable() { public void run() { snapshotPage(u); } }, 2500);
            }
        });
    }

    private void showBrowser() {
        if (web == null) ensureWeb();
        FrameLayout root = new FrameLayout(this);
        if (web.getParent() instanceof android.view.ViewGroup) ((android.view.ViewGroup) web.getParent()).removeView(web);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        // 右下角半透明"•"回主页: 触屏出现3秒后消失
        final TextView dot = new TextView(this);
        dot.setText("•");
        dot.setTextSize(30);
        dot.setTextColor(0x88FFFFFF);
        dot.setGravity(Gravity.CENTER);
        GradientDrawable dbg = new GradientDrawable();
        dbg.setShape(GradientDrawable.OVAL);
        dbg.setColor(0x33000000);
        dot.setBackground(dbg);
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(dip(44), dip(44), Gravity.BOTTOM | Gravity.RIGHT);
        dlp.setMargins(0, 0, dip(14), dip(24));
        dot.setVisibility(View.GONE);
        root.addView(dot, dlp);
        dot.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { dot.setVisibility(View.GONE); buildHome(); } });
        final Runnable hideDot = new Runnable() { public void run() { dot.setVisibility(View.GONE); } };
        dot.setTag(hideDot);
        dotBtn = dot;
        setContentView(root);
    }

    private View dotBtn;

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        if (ev.getAction() == android.view.MotionEvent.ACTION_DOWN && dotBtn != null && dotBtn.getParent() != null) {
            dotBtn.setVisibility(View.VISIBLE);
            Runnable h = (Runnable) dotBtn.getTag();
            if (h != null) { dotBtn.removeCallbacks(h); dotBtn.postDelayed(h, 3000); }
        }
        return super.dispatchTouchEvent(ev);
    }

    /** 整页自包含快照: 抓outerHTML → 内联全部资源 → 存站目录 */
    private void snapshotPage(String url) {
        if (snapBusy) return;
        snapBusy = true;
        final String pageUrl = url;
        new Thread(new Runnable() { public void run() {
            try {
                String html = fetchText(pageUrl, null);
                if (html == null || html.length() < 200) { snapBusy = false; return; }
                String out = processHtml(html, pageUrl);
                File d = siteDir(pageUrl);
                File f = new File(d, "page_" + md5(pageUrl).substring(0, 8) + ".html");
                FileOutputStream fos = new FileOutputStream(f);
                fos.write(out.getBytes(StandardCharsets.UTF_8));
                fos.close();
                JSONObject m = new JSONObject();
                File mf = new File(d, "map.json");
                if (mf.exists()) { try { m = new JSONObject(readSnapshot(mf.getAbsolutePath())); } catch (Throwable ignored) {} }
                m.put(pageUrl, f.getName());
                writeMap(mf, m); // 主页先落盘, 爬子页期间离线也能开主页
                // HtmlDown2 多页机制: 同域文章链接一并抓取(单站一次)
                File flag = new File(d, "crawled.flag");
                if (!flag.exists()) {
                    int added = crawlSubPages(html, pageUrl, d, m, mf);
                    if (added >= 0) { try { flag.createNewFile(); } catch (Throwable ignored) {} }
                }
            } catch (Throwable ignored) {}
            snapBusy = false;
        }}).start();
    }

    private void writeMap(File mf, JSONObject m) {
        try {
            FileOutputStream fm = new FileOutputStream(mf);
            fm.write(m.toString().getBytes(StandardCharsets.UTF_8));
            fm.close();
        } catch (Throwable ignored) {}
    }

    // 返回新增页数; -1=中途中断(已写入的部分仍有效)
    private int crawlSubPages(String html, String pageUrl, File d, JSONObject m, File mf) {
        int added = 0;
        try {
            String host0 = new URL(pageUrl).getHost();
            java.util.ArrayList<String> urls = new java.util.ArrayList<>();
            java.util.regex.Matcher mm = java.util.regex.Pattern
                .compile("<a[^>]+href=[\"']?([^\\s>\"'#]+)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(html);
            while (mm.find()) {
                String abs = absUrl(mm.group(1), pageUrl);
                if (abs == null || abs.equals(pageUrl)) continue;
                try { if (!new URL(abs).getHost().equals(host0)) continue; } catch (Throwable e) { continue; }
                String lp = abs.toLowerCase();
                if (!(lp.endsWith("/") || lp.contains(".html") || lp.contains(".htm") || lp.contains("?p="))) continue;
                if (!urls.contains(abs)) urls.add(abs);
                if (urls.size() >= 30) break;
            }
            for (String u : urls) {
                if (m.has(u)) continue;
                try {
                    String h = fetchText(u, pageUrl);
                    if (h == null || h.length() < 200) continue;
                    String out = processHtml(h, u);
                    File f2 = new File(d, "page_" + md5(u).substring(0, 8) + ".html");
                    FileOutputStream fo = new FileOutputStream(f2);
                    fo.write(out.getBytes(StandardCharsets.UTF_8));
                    fo.close();
                    m.put(u, f2.getName());
                    writeMap(mf, m); // 每存一篇立即落盘
                    added++;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable e) { return -1; }
        return added;
    }

    // HtmlDown2 机制: 资源落盘 images/js/css/videos 子目录, 属性改根相对路径
    private String processHtml(String html, String pageUrl) {
        final File d = siteDir(pageUrl);
        String[] tagAttrs = {"img\u0001src", "img\u0001data-src", "video\u0001src", "source\u0001src", "script\u0001src", "link\u0001href"};
        for (String ta : tagAttrs) {
            final int ui = ta.indexOf('\u0001');
            final String tag = ta.substring(0, ui), attr = ta.substring(ui + 1);
            java.util.regex.Matcher mm = java.util.regex.Pattern
                .compile("<" + tag + "[^>]+\\s" + attr + "=[\"']?[^\\s>\"']+", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(html);
            StringBuilder out = new StringBuilder();
            int last = 0;
            while (mm.find()) {
                String seg = mm.group();
                String val = seg.substring(seg.indexOf(attr + "=") + attr.length() + 1);
                if (val.startsWith("\"") || val.startsWith("'")) val = val.substring(1);
                String rep = seg;
                if (!val.startsWith("data:") && !val.startsWith("about:")) {
                    String abs = absUrl(val, pageUrl);
                    if (abs != null) {
                        String folder = attr.equals("href") ? "css" : (tag.equals("script") ? "js" : (tag.equals("img") ? "images" : "videos"));
                        String local = fetchRes(abs, pageUrl, d, folder, tag.equals("link") ? "text/css" : null);
                        if (local != null) rep = seg.substring(0, seg.indexOf(attr + "=")) + attr + "=\"" + local + "\"";
                    }
                }
                out.append(html, last, mm.start()).append(rep);
                last = mm.end();
            }
            out.append(html.substring(last));
            html = out.toString();
        }
        // link stylesheet 宽松兜底 (rel 可能在 href 之后)
        java.util.regex.Matcher m2 = java.util.regex.Pattern
            .compile("<link[^>]*href=[\"']([^\"']+)[\"'][^>]*>", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(html);
        StringBuilder o2 = new StringBuilder(); int l2 = 0;
        while (m2.find()) {
            String tg = m2.group(); String val = m2.group(1);
            String rep = tg;
            String rel = attr2(tg, "rel");
            if (rep.equals(tg) && rel != null && rel.toLowerCase().contains("stylesheet") && !val.startsWith("data:")) {
                String abs = absUrl(val, pageUrl);
                if (abs != null) {
                    String local = fetchRes(abs, pageUrl, d, "css", "text/css");
                    if (local != null) rep = tg.replace(val, local);
                }
            }
            o2.append(html, l2, m2.start()).append(rep); l2 = m2.end();
        }
        o2.append(html.substring(l2));
        return o2.toString();
    }

    private String attr2(String tag, String name) {
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile(name + "=[\"']?([^\\s>\"']+)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(tag);
        return m.find() ? m.group(1) : null;
    }

    // 下载资源到 d/folder/, 返回根相对路径; css 内 url() 一并落盘改写
    private String fetchRes(String abs, String referer, File d, String folder, String mimeHint) {
        try {
            byte[] b = fetchBytes2(abs, referer);
            if (b == null || b.length == 0) return null;
            String ext = extOf(abs);
            if (mimeHint != null && mimeHint.equals("text/css") && !ext.equals("css")) ext = "css";
            String name = md5(abs).substring(0, 16) + "." + ext;
            File dir = new File(d, folder);
            if (!dir.exists()) dir.mkdirs();
            if (folder.equals("css")) {
                String txt = new String(b, StandardCharsets.UTF_8);
                txt = inlineCssUrls2(txt, abs, d);
                b = txt.getBytes(StandardCharsets.UTF_8);
            }
            FileOutputStream fo = new FileOutputStream(new File(dir, name));
            fo.write(b); fo.close();
            return "/" + folder + "/" + name;
        } catch (Throwable e) { return null; }
    }

    private String inlineCssUrls2(String css, String cssUrl, File d) {
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("url\\([\"']?([^)\"']+)[\"']?\\)").matcher(css);
        StringBuilder out = new StringBuilder(); int last = 0;
        while (m.find()) {
            String val = m.group(1);
            String rep = m.group();
            if (!val.startsWith("data:") && !val.startsWith("about:")) {
                String abs = absUrl(val, cssUrl);
                if (abs != null) {
                    String local = fetchRes(abs, cssUrl, d, "images", null);
                    if (local != null) rep = "url(\"" + local + "\")";
                }
            }
            out.append(css, last, m.start()).append(rep); last = m.end();
        }
        out.append(css.substring(last));
        return out.toString();
    }

    private String extOf(String url) {
        try {
            String p = new URL(url).getPath();
            int q = p.lastIndexOf('.');
            if (q > 0 && p.length() - q <= 6) {
                String e = p.substring(q + 1).toLowerCase().replaceAll("[^a-z0-9]", "");
                if (e.length() >= 2 && e.length() <= 5) return e;
            }
        } catch (Throwable ignored) {}
        return "bin";
    }

    private byte[] fetchBytes(String url, String referer) {
        try {
            java.net.HttpURLConnection c = (java.net.HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(10000); c.setReadTimeout(15000);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) Chrome/120 Mobile");
            if (referer != null) c.setRequestProperty("Referer", referer);
            c.setInstanceFollowRedirects(true);
            if (c.getResponseCode() != 200) return null;
            java.io.InputStream is = c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[16384]; int n;
            while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
            is.close();
            return bo.toByteArray();
        } catch (Throwable e) { return null; }
    }

    private String replaceAttr(String html, String tag, String attr, String pageUrl, boolean inlineScript) {
        java.util.regex.Pattern p = java.util.regex.Pattern
            .compile("<" + tag + "[^>]+\\b" + attr + "=[\"']([^\"']+)[\"'][^>]*>", java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher m = p.matcher(html);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (m.find()) {
            String tagAll = m.group();
            String abs = absUrl(m.group(1), pageUrl);
            String rep = tagAll;
            if (abs != null) {
                if (inlineScript) {
                    String js = fetchText(abs, pageUrl);
                    if (js != null) {
                        String srcTag = tagAll;
                        rep = "<script>/*" + abs + "*/\n" + js + "\n</script>";
                        if (srcTag.contains("type=")) rep = rep; // 保留
                    }
                } else {
                    String data = fetchDataUri(abs, pageUrl);
                    if (data != null) rep = tagAll.replaceFirst(attr + "=[\"'][^\"']+[\"']", attr + "=\"" + java.util.regex.Matcher.quoteReplacement(data) + "\"");
                }
            }
            out.append(html, last, m.start()).append(rep);
            last = m.end();
        }
        out.append(html.substring(last));
        return out.toString();
    }

    private String attr(String tag, String name) {
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("\\b" + name + "=[\"']([^\"']+)[\"']", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(tag);
        return m.find() ? m.group(1) : null;
    }

    private String absUrl(String href, String base) {
        if (href == null) return null;
        href = href.trim();
        if (href.startsWith("data:") || href.startsWith("#") || href.startsWith("javascript:")) return null;
        try { return new URL(new URL(base), href).toString(); } catch (Throwable e) { return null; }
    }

    private String fetchText(String url, String referer) {
        byte[] d = fetchBytes(url, referer);
        if (d == null) return null;
        return new String(d, StandardCharsets.UTF_8);
    }

    private String fetchDataUri(String url, String referer) {
        byte[] d = fetchBytes(url, referer);
        if (d == null || d.length > 6 * 1024 * 1024) return null;
        String mime = guessType(url, d);
        return "data:" + mime + ";base64," + android.util.Base64.encodeToString(d, android.util.Base64.NO_WRAP);
    }

    private byte[] fetchBytes2(String url, String referer) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000); c.setReadTimeout(12000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) Chrome/120 Mobile");
            c.setRequestProperty("Referer", referer == null ? url : referer);
            if (c.getResponseCode() != 200) return null;
            InputStream is = c.getInputStream();
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while ((n = is.read(buf)) > 0) b.write(buf, 0, n);
            is.close();
            return b.toByteArray();
        } catch (Throwable e) { return null; }
    }

    /** css里的 url(...) 引用转 data URI */
    private String inlineCssUrls(String css, String cssUrl) {
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("url\\((['\"]?)([^)'\";]+)\\1\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(css);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (m.find()) {
            String ref = m.group(2);
            String rep = m.group();
            String abs = absUrl(ref, cssUrl);
            if (abs != null) {
                String data = fetchDataUri(abs, cssUrl);
                if (data != null) rep = "url(" + data + ")";
            }
            out.append(css, last, m.start()).append(rep);
            last = m.end();
        }
        out.append(css.substring(last));
        return out.toString();
    }

    private String guessType(String u, byte[] d) {
        String lu = u.toLowerCase();
        if (lu.endsWith(".png")) return "image/png";
        if (lu.endsWith(".gif")) return "image/gif";
        if (lu.endsWith(".webp")) return "image/webp";
        if (lu.endsWith(".svg")) return "image/svg+xml";
        if (lu.endsWith(".js")) return "application/javascript";
        if (lu.endsWith(".css")) return "text/css";
        if (d != null && d.length > 4) {
            if (d[0] == (byte) 0x89 && d[1] == 'P') return "image/png";
            if (d[0] == (byte) 0xFF && d[1] == (byte) 0xD8) return "image/jpeg";
        }
        if (lu.endsWith(".jpg") || lu.endsWith(".jpeg")) return "image/jpeg";
        return "image/jpeg";
    }

    // ---------------- 离线回放 + 链接套页面 ----------------

    private boolean offlineMode = false;
    private final Map<String, File> offlineIndex = new ConcurrentHashMap<>();
    private final Map<String, File> zipDirs = new ConcurrentHashMap<>();

    private void buildOfflineIndex() {
        offlineIndex.clear();
        File[] dirs = snapDir().listFiles();
        if (dirs != null) for (File d : dirs) {
            if (!d.isDirectory()) continue;
            File mf = new File(d, "map.json");
            if (!mf.exists()) continue;
            try {
                JSONObject m = new JSONObject(readSnapshot(mf.getAbsolutePath()));
                java.util.Iterator<?> it = m.keys();
                while (it.hasNext()) {
                    String u = (String) it.next();
                    File f = new File(d, m.optString(u));
                    if (f.exists()) offlineIndex.put(u, f);
                }
            } catch (Throwable ignored) {}
        }
    }

    private String normKey(String u) {
        u = u.replace("https://", "").replace("http://", "").replace("www.", "");
        if (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        int q = u.indexOf('?');
        if (q > 0) u = u.substring(0, q);
        return u;
    }

    private android.webkit.WebResourceResponse serveOffline(String u) {
        File f = offlineIndex.get(u);
        if (f == null || !f.exists()) f = offlineIndex.get(normKey(u));
        if (f == null || !f.exists()) {
            for (Map.Entry<String, File> e : offlineIndex.entrySet()) {
                if (normKey(e.getKey()).equals(normKey(u))) { f = e.getValue(); break; }
            }
        }
        if (f == null || !f.exists()) {
            // HtmlDown2 资源目录兜底: /images/.. /js/.. /css/.. /videos/.. 按站点目录找
            try {
                URL pu = new URL(u);
                File pd = siteDir(u);
                String path = pu.getPath();
                if (path != null && path.length() > 1) {
                    File rf = new File(pd, path.substring(1));
                    if (rf.exists() && rf.isFile()) return serveRes(rf);
                }
            } catch (Throwable ignored) {}
            toastOnce(snapBusy ? "正在离线保存中,稍后再试" : "该页面未离线保存");
            return new WebResourceResponse("text/plain", "utf-8", new java.io.ByteArrayInputStream(new byte[0]));
        }
        if (f.getName().endsWith(".mht")) {
            final File ff = f;
            runOnUiThread(new Runnable() { public void run() { web.loadUrl("file://" + ff.getAbsolutePath()); } });
            return new WebResourceResponse("text/plain", "utf-8", new java.io.ByteArrayInputStream(new byte[0]));
        }
        return serveRes(f);
    }

    private boolean zipToastShown = false;
    private void toastOnce(final String s) {
        if (zipToastShown) return;
        zipToastShown = true;
        runOnUiThread(new Runnable() { public void run() { toast(s); } });
    }

    private android.webkit.WebResourceResponse serveZip(String u) {
        try {
            // http://ziplocal.z123/rel/path
            int dot = u.indexOf('.', "http://ziplocal.".length());
            String site = u.substring("http://ziplocal.".length(), dot);
            String rel = u.substring(("http://ziplocal." + site + ".").length());
            if (rel.contains("?")) rel = rel.substring(0, rel.indexOf('?'));
            rel = java.net.URLDecoder.decode(rel, "UTF-8");
            File base = zipDirs.get(site);
            if (base == null) {
                // 重启后恢复zip目录
                JSONArray a = load();
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.optJSONObject(i);
                    if (o != null && o.optString("url", "").contains(site)) {
                        base = new File(o.optString("zipdir"));
                        zipDirs.put(site, base);
                        break;
                    }
                }
            }
            if (base == null) return empty();
            File f = new File(base, rel);
            if (!f.exists() || f.isDirectory()) return empty();
            return serveRes(f);
        } catch (Throwable e) { return null; }
    }

    private android.webkit.WebResourceResponse empty() {
        return new WebResourceResponse("text/plain", "utf-8", new java.io.ByteArrayInputStream(new byte[0]));
    }

    private android.webkit.WebResourceResponse serveRes(File f) {
        try { return new WebResourceResponse(sniffType(f), null, new FileInputStream(f)); }
        catch (Throwable e) { return null; }
    }

    private String sniffType(File f) {
        String nm = f.getName().toLowerCase();
        if (nm.endsWith(".css")) return "text/css";
        if (nm.endsWith(".js")) return "application/javascript";
        if (nm.endsWith(".mjs")) return "application/javascript";
        if (nm.endsWith(".png")) return "image/png";
        if (nm.endsWith(".jpg") || nm.endsWith(".jpeg")) return "image/jpeg";
        if (nm.endsWith(".gif")) return "image/gif";
        if (nm.endsWith(".webp")) return "image/webp";
        if (nm.endsWith(".svg")) return "image/svg+xml";
        if (nm.endsWith(".html") || nm.endsWith(".htm")) return "text/html";
        if (nm.endsWith(".json")) return "application/json";
        if (nm.endsWith(".woff2")) return "font/woff2";
        if (nm.endsWith(".woff")) return "font/woff";
        if (nm.endsWith(".ttf")) return "font/ttf";
        if (nm.endsWith(".otf")) return "font/otf";
        if (nm.endsWith(".mp4")) return "video/mp4";
        if (nm.endsWith(".webm")) return "video/webm";
        if (nm.endsWith(".mp3")) return "audio/mpeg";
        if (nm.endsWith(".pdf")) return "application/pdf";
        try {
            FileInputStream is = new FileInputStream(f);
            byte[] h = new byte[400];
            int n = is.read(h); is.close();
            if (n < 4) return "text/html";
            if (h[0] == (byte) 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G') return "image/png";
            if (h[0] == (byte) 0xFF && h[1] == (byte) 0xD8) return "image/jpeg";
            if (h[0] == 'G' && h[1] == 'I' && h[2] == 'F') return "image/gif";
            if (h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F') return "image/webp";
            if (h[0] == '%' && h[1] == 'P' && h[2] == 'D' && h[3] == 'F') return "application/pdf";
            String head = new String(h, 0, n, StandardCharsets.UTF_8).trim().toLowerCase();
            if (head.startsWith("<!doctype") || head.startsWith("<html") || head.startsWith("<head") || head.contains("<body")) return "text/html";
            if (head.startsWith("@charset") || head.startsWith("@media") || head.startsWith("body") || head.startsWith(".") || head.startsWith("#")) return "text/css";
            if (head.startsWith("function") || head.startsWith("var ") || head.startsWith("let ") || head.startsWith("const ") || head.startsWith("(function") || head.startsWith("import") || head.startsWith("!function")) return "application/javascript";
            if (head.startsWith("{") || head.startsWith("[")) return "application/json";
            return "text/html";
        } catch (Throwable e) { return "text/html"; }
    }

    // ---------------- 站点菜单 ----------------

    private void siteMenu(final String name, final String url) {
        final String[] items = {"🌐 在线打开", "📕 离线打开(已存页面)", "✏️ 重命名", "🗑️ 删除"};
        new AlertDialog.Builder(this)
            .setTitle(name)
            .setItems(items, new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) {
                    if (w == 0) browse(url);
                    else if (w == 1) { showBrowser(); offlineMode = true; buildOfflineIndex(); pendingSnapFor = url; web.loadUrl(url); toast("离线浏览(已存页面可跳)"); }
                    else if (w == 2) renameSite(name, url);
                    else delSite(url);
                }
            }).show();
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
        toast("已删除");
    }

    // ---------------- 存储 ----------------

    private File snapDir() {
        File d = new File(getExternalFilesDir(null), "离线快照");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private File siteDir(String url) {
        String host;
        try { host = new URL(url).getHost().replace("www.", ""); }
        catch (Throwable e) { host = md5(url); }
        host = host.replaceAll("[^a-zA-Z0-9.-]", "_");
        File d = new File(snapDir(), host);
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private boolean hasSnap(String url) {
        File mf = new File(siteDir(url), "map.json");
        if (!mf.exists()) mf = new File(snapDir(), "map.json");
        if (mf.exists()) {
            try {
                JSONObject m = new JSONObject(readSnapshot(mf.getAbsolutePath()));
                java.util.Iterator<?> it = m.keys();
                while (it.hasNext()) if (normKey((String) it.next()).equals(normKey(url))) return true;
            } catch (Throwable ignored) {}
        }
        return false;
    }

    private String readSnapshot(String path) {
        try {
            FileInputStream is = new FileInputStream(path);
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while ((n = is.read(buf)) > 0) b.write(buf, 0, n);
            is.close();
            return new String(b.toByteArray(), StandardCharsets.UTF_8);
        } catch (Throwable e) { return ""; }
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

    private JSONArray load() {
        try { return new JSONArray(getSharedPreferences(PREF, MODE_PRIVATE).getString("json", "[]")); }
        catch (Throwable e) { return new JSONArray(); }
    }

    private void save(JSONArray a) {
        getSharedPreferences(PREF, MODE_PRIVATE).edit().putString("json", a.toString()).apply();
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



    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) Immersive.hide(this);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.getParent() != null && web.canGoBack()) web.goBack();
        else if (web != null && web.getParent() != null) buildHome();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
