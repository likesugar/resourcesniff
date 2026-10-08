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
        ensureDefault();
        buildHome();
    }

    private void ensureDefault() {
        JSONArray a = load();
        if (a.length() == 0) {
            try {
                JSONObject o = new JSONObject();
                o.put("name", "Tulpa");
                o.put("url", "https://tulpa.cn/");
                o.put("color", COLORS[0]);
                a.put(o);
                save(a);
            } catch (Throwable ignored) {}
        }
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
            card.setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View v) { delSite(url); return true; } });
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
                @Override public void onReceivedError(WebView v, int code, String desc, String failing) {
                    // 断网 → 回退快照
                    String snap = snapshotFor(pendingSnapFor);
                    if (!snap.isEmpty() && failing != null && failing.contains(pendingSnapFor)) {
                        v.loadUrl("file://" + snap);
                        toast("离线快照");
                    }
                }
                @Override public void onPageFinished(WebView v, String u) {
                    if (pendingSnapFor.isEmpty() || !u.contains("http")) return;
                    v.evaluateJavascript("document.documentElement.outerHTML", new android.webkit.ValueCallback<String>() {
                        public void onReceiveValue(String val) {
                            if (val == null || val.length() < 100) return;
                            String html = val;
                            if (html.length() > 1 && html.charAt(0) == '"') {
                                html = html.substring(1, html.length() - 1)
                                    .replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n");
                            }
                            try {
                                File f = new File(snapDir(), md5(pendingSnapFor) + ".html");
                                FileOutputStream fos = new FileOutputStream(f);
                                fos.write(html.getBytes(StandardCharsets.UTF_8));
                                fos.close();
                                markSnap(pendingSnapFor, f.getName());
                            } catch (Throwable ignored) {}
                        }
                    });
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
        // file:// 直接开, http 先试网络
        web.loadUrl(url);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.getParent() != null && web.canGoBack()) web.goBack();
        else if (web != null && web.getParent() != null) buildHome();
        else super.onBackPressed();
    }

    // ---------------- 数据 ----------------

    private File snapDir() {
        File d = new File(getFilesDir(), "offline_snap");
        if (!d.exists()) d.mkdirs();
        return d;
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
