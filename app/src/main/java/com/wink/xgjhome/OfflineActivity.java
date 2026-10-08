package com.wink.xgjhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** 网页工程: 卡片=HTML工程(多文件: index.html+css+js+图片), 文件页签编辑, 本地拦截服务运行(相对路径互引) */
public class OfflineActivity extends Activity {

    private static final String PREF = "webpages";
    private static final String ENTRY = "index.html";
    private static final String TEMPLATE =
        "<!DOCTYPE html>\n<html>\n<head>\n<meta charset=\"utf-8\">\n<meta name=\"viewport\" content=\"width=device-width\">\n<title>My Page</title>\n<link rel=\"stylesheet\" href=\"style.css\">\n</head>\n<body>\n<h1>Hello</h1>\n<script src=\"script.js\"></script>\n</body>\n</html>";
    private static final String CSS_T = "body{font-family:sans-serif;background:#fff;color:#222;padding:16px}\nh1{color:#315CDE}";
    private static final String JS_T = "console.log('ready');";
    private static final int[] COLORS = {0xFFd65db1, 0xFF315CDE, 0xFF9c8e7d, 0xFFa56bce, 0xFF1FA855, 0xFFe67e22};

    private LinearLayout cardsRow;
    private LinearLayout editorRoot;
    private HorizontalScrollView tabsScroll;
    private LinearLayout tabsRow;
    private EditText code;
    private TextView runBtn;
    private WebView preview;
    private LinearLayout previewBox;

    private String projId = "";      // 当前工程目录名
    private String projName = "";
    private String curFile = ENTRY;  // 当前编辑的文件

    private LinearLayout cardsRowRef;
    private boolean dark;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dark = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("dark", false);
        buildHome();
    }

    // ---------------- 主页 ----------------

    private void buildHome() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(dark ? 0xFF000000 : 0xFFEEF4FF);
        root.setPadding(dip(20), dip(60), 0, dip(20));

        TextView head = new TextView(this);
        head.setText("网页工程");
        head.setTextSize(22);
        head.setTypeface(null, Typeface.BOLD);
        head.setTextColor(dark ? 0xFFEEEEEE : 0xFF1F2329);
        root.addView(head, new LinearLayout.LayoutParams(-2, -2));

        cardsRow = new LinearLayout(this);
        cardsRow.setOrientation(LinearLayout.HORIZONTAL);
        cardsRow.setGravity(Gravity.CENTER_VERTICAL);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(cardsRow);
        root.addView(hs, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout spacer = new LinearLayout(this);
        root.addView(spacer, new LinearLayout.LayoutParams(-1, 0, 1f));

        // 底部胶囊: ＋新建工程
        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable pg = new GradientDrawable();
        pg.setColor(dark ? 0xFF1C1F26 : 0xFFF2F3F5);
        pg.setCornerRadius(dip(24));
        pill.setBackground(pg);
        pill.setPadding(dip(18), dip(12), dip(18), dip(12));
        TextView plus = new TextView(this);
        plus.setText("＋");
        plus.setTextSize(16);
        plus.setTextColor(0xFF888888);
        pill.addView(plus);
        TextView label = new TextView(this);
        label.setText("  新建工程");
        label.setTextSize(15);
        label.setTextColor(dark ? 0xFFEEEEEE : 0xFF1F2329);
        pill.addView(label);
        pill.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { newProject(null, null); } });
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, -2);
        plp.setMargins(0, dip(10), 0, dip(10));
        root.addView(pill, plp);

        setContentView(root);
        cardsRowRef = cardsRow;
        refreshCards();
    }

    private void refreshCards() {
        cardsRow.removeAllViews();
        JSONArray a = a();
        int n = a.length();
        for (int i = 0; i < n; i++) {
            final int idx = i;
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            final String name = o.optString("name", "工程");
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            card.setPadding(dip(2), 0, dip(24), 0);
            card.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { openProject(idx); } });
            card.setOnLongClickListener(new View.OnLongClickListener() {
                public boolean onLongClick(View v) { editProject(idx, name); return true; }
            });
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
        // 末尾 + 上传zip/文件
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dip(2), 0, dip(24), 0);
        card.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showImportDialog(); } });
        TextView av = new TextView(this);
        av.setText("＋");
        av.setTextColor(0xFF888888);
        av.setTextSize(30);
        av.setGravity(Gravity.CENTER);
        GradientDrawable c = new GradientDrawable();
        c.setShape(GradientDrawable.OVAL);
        c.setColor(dark ? 0xFF1C1F26 : 0xFFE8E8E8);
        av.setBackground(c);
        card.addView(av, new LinearLayout.LayoutParams(dip(74), dip(74)));
        TextView lb = new TextView(this);
        lb.setText("导入");
        lb.setTextSize(15);
        lb.setTextColor(dark ? 0xFFEEEEEE : 0xFF000000);
        lb.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-2, -2);
        llp.topMargin = dip(12);
        card.addView(lb, llp);
        cardsRow.addView(card, new LinearLayout.LayoutParams(-2, -2));
    }

    private void editProject(final int idx, final String name) {
        final EditText et = new EditText(this);
        et.setText(name);
        new AlertDialog.Builder(this)
            .setTitle("工程操作")
            .setView(et)
            .setPositiveButton("重命名", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) {
                    JSONArray a = a();
                    JSONObject o = a.optJSONObject(idx);
                    if (o == null) return;
                    try { o.put("name", et.getText().toString().trim()); } catch (Throwable ignored) {}
                    save(a); refreshCards();
                }
            })
            .setNeutralButton("删除", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) {
                    JSONArray src = a();
                    JSONArray out = new JSONArray();
                    for (int i = 0; i < src.length(); i++) {
                        JSONObject o = src.optJSONObject(i);
                        if (o == null || i != idx) out.put(o);
                    }
                    save(out);
                    refreshCards();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void showImportDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView h = new TextView(this);
        h.setText("📄 导入单个HTML(自动成工程)");
        h.setTextSize(15);
        h.setTextColor(dark ? 0xFFEEEEEE : 0xFF1F2329);
        h.setPadding(dip(30), dip(14), dip(10), dip(14));
        h.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
            i.setType("text/*");
            startActivityForResult(android.content.Intent.createChooser(i, "选择HTML"), 9002);
            dlgRef.dismiss();
        }});
        TextView z = new TextView(this);
        z.setText("🗜️ 导入ZIP工程(多文件互引)");
        z.setTextSize(15);
        z.setTextColor(0xFF315CDE);
        z.setTypeface(null, Typeface.BOLD);
        z.setPadding(dip(30), dip(14), dip(10), dip(14));
        z.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
            i.setType("*/*");
            startActivityForResult(android.content.Intent.createChooser(i, "选择ZIP"), 9003);
            dlgRef.dismiss();
        }});
        box.addView(h);
        box.addView(z);
        dlgRef = new AlertDialog.Builder(this).setTitle("导入工程").setView(box).show();
    }
    private AlertDialog dlgRef;

    // ---------------- 工程编辑器 ----------------

    private void openProject(int idx) {
        JSONObject o = a().optJSONObject(idx);
        if (o == null) return;
        projId = o.optString("id", "");
        projName = o.optString("name", "工程");
        String dir = o.optString("dir", "");
        File entry = new File(dir, ENTRY);
        if (!entry.exists()) { try { write(new File(dir, ENTRY), TEMPLATE); write(new File(dir, "style.css"), CSS_T); write(new File(dir, "script.js"), JS_T); } catch (Throwable ignored) {} }
        curFile = ENTRY;
        buildEditor(dir);
    }

    private void newProject(String presetName, String htmlContent) {
        String id = "p" + System.currentTimeMillis();
        File dir = new File(new File(getFilesDir(), "webpages"), id);
        if (!dir.exists()) dir.mkdirs();
        try {
            write(new File(dir, ENTRY), htmlContent != null ? htmlContent : TEMPLATE);
            write(new File(dir, "style.css"), CSS_T);
            write(new File(dir, "script.js"), JS_T);
        } catch (Throwable ignored) {}
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("name", presetName != null ? presetName : "工程" + (a().length() + 1));
            o.put("dir", dir.getAbsolutePath());
            o.put("color", COLORS[a().length() % COLORS.length]);
        } catch (Throwable ignored) {}
        JSONArray arr = a();
        arr.put(o);
        save(arr);
        refreshCards();
    }

    private void buildEditor(final String dir) {
        editorRoot = new LinearLayout(this);
        editorRoot.setOrientation(LinearLayout.VERTICAL);
        editorRoot.setBackgroundColor(dark ? 0xFF0C0E12 : 0xFFFFFFFF);

        // 顶栏: 返回 | 工程名 | 运行
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dip(12), dip(40), dip(12), dip(10));
        TextView back = new TextView(this);
        back.setText("‹ 返回");
        back.setTextSize(14);
        back.setTextColor(dark ? 0xFFEEEEEE : 0xFF1F2329);
        back.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { buildHome(); } });
        top.addView(back);
        TextView tn = new TextView(this);
        tn.setText("  " + projName);
        tn.setTextSize(16);
        tn.setTypeface(null, Typeface.BOLD);
        tn.setTextColor(dark ? 0xFFEEEEEE : 0xFF1F2329);
        LinearLayout.LayoutParams tnp = new LinearLayout.LayoutParams(0, -2, 1f);
        top.addView(tn, tnp);
        runBtn = new TextView(this);
        runBtn.setText("▶ 运行");
        runBtn.setTextSize(14);
        runBtn.setTextColor(0xFFFFFFFF);
        runBtn.setGravity(Gravity.CENTER);
        GradientDrawable rg = new GradientDrawable();
        rg.setColor(0xFF1FA855);
        rg.setCornerRadius(dip(16));
        runBtn.setBackground(rg);
        runBtn.setPadding(dip(16), dip(8), dip(16), dip(8));
        runBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { saveCur(); runPreview(dir); } });
        top.addView(runBtn);
        editorRoot.addView(top, new LinearLayout.LayoutParams(-1, -2));

        // 文件页签
        tabsScroll = new HorizontalScrollView(this);
        tabsScroll.setHorizontalScrollBarEnabled(false);
        tabsRow = new LinearLayout(this);
        tabsRow.setOrientation(LinearLayout.HORIZONTAL);
        tabsRow.setPadding(dip(10), 0, dip(10), dip(6));
        tabsScroll.addView(tabsRow);
        editorRoot.addView(tabsScroll, new LinearLayout.LayoutParams(-1, -2));

        // 代码区
        code = new EditText(this);
        code.setTypeface(Typeface.MONOSPACE);
        code.setTextSize(13);
        code.setTextColor(dark ? 0xFFEEEEEE : 0xFF1F2329);
        code.setBackgroundColor(dark ? 0xFF0C0E12 : 0xFFF7F8FA);
        code.setGravity(Gravity.TOP);
        code.setMinimumHeight(dip(300));
        editorRoot.addView(code, new LinearLayout.LayoutParams(-1, 0, 1f));

        // 预览层(隐藏)
        previewBox = new LinearLayout(this);
        previewBox.setOrientation(LinearLayout.VERTICAL);
        previewBox.setBackgroundColor(0xFFFFFFFF);
        preview = new WebView(this);
        WebSettings ws = preview.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setUserAgentString("Mozilla/5.0 (Linux; Android 13) Chrome/120 Mobile");
        try { ws.setAllowFileAccess(true); } catch (Throwable ignored) {}
        preview.setWebViewClient(new WebViewClient() {
            @Override public void onReceivedSslError(WebView v, android.webkit.SslErrorHandler h, android.net.http.SslError e) { h.proceed(); }
            @Override public boolean shouldOverrideUrlLoading(WebView v, String u) { return false; }
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, android.webkit.WebResourceRequest req) {
                return serveProj(req.getUrl().toString());
            }
        });
        previewBox.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1f));
        previewBox.setVisibility(View.GONE);
        editorRoot.addView(previewBox, new LinearLayout.LayoutParams(-1, 0, 1f));

        setContentView(editorRoot);
        refreshTabs(dir);
    }

    private void refreshTabs(final String dir) {
        tabsRow.removeAllViews();
        File[] files = new File(dir).listFiles();
        if (files == null) return;
        java.util.Arrays.sort(files);
        for (final File f : files) {
            if (f.isDirectory()) continue;
            TextView t = new TextView(this);
            boolean cur = f.getName().equals(curFile);
            t.setText(f.getName());
            t.setTextSize(13);
            t.setTextColor(cur ? 0xFFFFFFFF : (dark ? 0xFFBBBBBB : 0xFF555555));
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dip(14));
            g.setColor(cur ? 0xFF315CDE : (dark ? 0xFF1C1F26 : 0xFFF2F3F5));
            t.setBackground(g);
            t.setPadding(dip(14), dip(7), dip(14), dip(7));
            t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                saveCur();
                curFile = f.getName();
                refreshTabs(dir);
                loadCur();
            }});
            tabsRow.addView(t, new LinearLayout.LayoutParams(-2, -2));
        }
        // ＋新建文件
        TextView plus = new TextView(this);
        plus.setText("＋");
        plus.setTextSize(15);
        plus.setTextColor(0xFF315CDE);
        plus.setPadding(dip(14), dip(7), dip(14), dip(7));
        plus.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            final EditText et = new EditText(OfflineActivity.this);
            et.setHint("如 about.html / app.js / style2.css");
            new AlertDialog.Builder(OfflineActivity.this)
                .setTitle("新建文件")
                .setView(et)
                .setPositiveButton("创建", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        String fn = et.getText().toString().trim();
                        if (fn.isEmpty()) return;
                        File nf = new File(dir, fn);
                        if (!nf.exists()) try { write(nf, fn.endsWith(".css") ? "" : fn.endsWith(".js") ? "" : TEMPLATE); } catch (Throwable ignored) {}
                        saveCur();
                        curFile = fn;
                        refreshTabs(dir);
                        loadCur();
                    }
                })
                .setNegativeButton("取消", null).show();
        }});
        tabsRow.addView(plus);
    }

    private void loadCur() {
        try { code.setText(read(new File(projDir(), curFile))); } catch (Throwable ignored) {}
    }

    private void saveCur() {
        try { write(new File(projDir(), curFile), code.getText().toString()); } catch (Throwable ignored) {}
    }

    private void runPreview(String dir) {
        if (previewBox.getVisibility() == View.VISIBLE) {
            preview.loadUrl("javascript:location.reload()");
            return;
        }
        code.setVisibility(View.GONE);
        tabsScroll.setVisibility(View.GONE);
        previewBox.setVisibility(View.VISIBLE);
        runBtn.setText("⟲ 重运行");
        preview.loadUrl("http://projectlocal." + projId + "/" + ENTRY);
    }

    /** 工程本地服务: projectlocal.{id}/路径 → 工程目录文件 (相对路径互引全通) */
    private WebResourceResponse serveProj(String u) {
        try {
            String prefix = "http://projectlocal." + projId + "/";
            if (!u.startsWith(prefix)) return null;
            String rel = u.substring(prefix.length());
            if (rel.contains("?")) rel = rel.substring(0, rel.indexOf('?'));
            File f = new File(projDir(), rel);
            if (!f.exists() || f.isDirectory()) return new WebResourceResponse("text/plain", "utf-8", new java.io.ByteArrayInputStream(new byte[0]));
            return new WebResourceResponse(guessType(rel), null, new FileInputStream(f));
        } catch (Throwable e) { return null; }
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
        if (lu.endsWith(".json")) return "application/json";
        if (lu.endsWith(".mp4")) return "video/mp4";
        return "text/html";
    }

    // ---------------- 存储 ----------------

    private JSONArray a() {
        try { return new JSONArray(getSharedPreferences(PREF, MODE_PRIVATE).getString("json", "[]")); }
        catch (Throwable e) { return new JSONArray(); }
    }

    private void save(JSONArray a) {
        getSharedPreferences(PREF, MODE_PRIVATE).edit().putString("json", a.toString()).apply();
    }

    private File projDir() { return new File(projId == null || projId.isEmpty() ? getFilesDir() : new File(getFilesDir(), "webpages"), projId.isEmpty() ? "" : projId); }

    private String read(File f) {
        try {
            FileInputStream fis = new FileInputStream(f);
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while ((n = fis.read(buf)) > 0) b.write(buf, 0, n);
            fis.close();
            return new String(b.toByteArray(), StandardCharsets.UTF_8);
        } catch (Throwable e) { return ""; }
    }

    private void write(File f, String s) throws Exception {
        FileOutputStream fos = new FileOutputStream(f);
        fos.write(s.getBytes(StandardCharsets.UTF_8));
        fos.close();
    }

    private String hostOf(String u) {
        try { return new java.net.URL(u).getHost().replace("www.", ""); } catch (Throwable e) { return "站"; }
    }

    private int dip(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    // ---------------- 导入 ----------------

    @Override
    protected void onActivityResult(int req, int res, android.content.Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        if (req == 9002) {
            try {
                String name = "page.html";
                android.database.Cursor c = getContentResolver().query(data.getData(), null, null, null, null);
                if (c != null) { try { int ni = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (ni >= 0 && c.moveToFirst()) name = c.getString(ni); } finally { c.close(); } }
                String html = readFromUri(data.getData());
                newProject(name.replaceAll("(?i)\\.html?$", ""), html);
                toast("已导入为工程");
            } catch (Throwable e) { toast("导入失败: " + e); }
        } else if (req == 9003) {
            try {
                String zipName = "site.zip";
                android.database.Cursor c = getContentResolver().query(data.getData(), null, null, null, null);
                if (c != null) { try { int ni = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (ni >= 0 && c.moveToFirst()) zipName = c.getString(ni); } finally { c.close(); } }
                String id = "p" + System.currentTimeMillis();
                File dir = new File(new File(getFilesDir(), "webpages"), id);
                if (!dir.exists()) dir.mkdirs();
                java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(getContentResolver().openInputStream(data.getData()));
                java.util.zip.ZipEntry e;
                int count = 0;
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
                }
                zis.close();
                JSONObject o = new JSONObject();
                o.put("id", id);
                o.put("name", zipName.replaceAll("(?i)\\.zip$", ""));
                o.put("dir", dir.getAbsolutePath());
                o.put("color", COLORS[a().length() % COLORS.length]);
                JSONArray arr = a();
                arr.put(o);
                save(arr);
                refreshCards();
                toast("已导入 " + count + " 个文件");
            } catch (Throwable e) { toast("导入失败: " + e); }
        }
    }

    private String readFromUri(android.net.Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while ((n = is.read(buf)) > 0) b.write(buf, 0, n);
            is.close();
            return new String(b.toByteArray(), StandardCharsets.UTF_8);
        } catch (Throwable e) { return ""; }
    }

    @Override
    public void onBackPressed() {
        if (previewBox != null && previewBox.getVisibility() == View.VISIBLE) {
            previewBox.setVisibility(View.GONE);
            code.setVisibility(View.VISIBLE);
            tabsScroll.setVisibility(View.VISIBLE);
            runBtn.setText("▶ 运行");
            return;
        }
        if (editorRoot != null && editorRoot.getParent() != null) { buildHome(); return; }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (preview != null) preview.destroy();
        super.onDestroy();
    }
}
