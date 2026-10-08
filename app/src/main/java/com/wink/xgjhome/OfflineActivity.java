package com.wink.xgjhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebSettings;
import android.webkit.WebView;
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

/** 网页编辑运行: 图1卡片主页(卡片=HTML项目) → 编辑器写代码 → 一键运行预览; 支持上传html/新建 */
public class OfflineActivity extends Activity {

    private static final String PREF = "webpages";
    private static final String TEMPLATE =
        "<!DOCTYPE html>\n<html>\n<head>\n<meta charset=\"utf-8\">\n"
        + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
        + "<title>我的页面</title>\n<style>\nbody{font-family:sans-serif;padding:16px}\n"
        + "</style>\n</head>\n<body>\n<h1>Hello!</h1>\n<p>开始编辑你的网页…</p>\n"
        + "<script>\n// JS 在这里\n</script>\n</body>\n</html>";

    private LinearLayout cardsRow;
    private LinearLayout homeRoot;
    private boolean dark;
    private int editingIdx = -1;
    private WebView preview;
    private EditText code;
    private LinearLayout editorRoot;
    private TextView runBtn;

    private static final int[] COLORS = {0xFFd65db1, 0xFF315CDE, 0xFF9c8e7d, 0xFFa56bce, 0xFF1FA855, 0xFFe67e22};

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dark = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("dark", false);
        buildHome();
    }

    // ---------------- 主页 (图1) ----------------

    private void buildHome() {
        homeRoot = new LinearLayout(this);
        homeRoot.setOrientation(LinearLayout.VERTICAL);
        homeRoot.setBackgroundColor(dark ? 0xFF000000 : 0xFFFFFFFF);
        homeRoot.setPadding(dip(20), dip(60), 0, 0);

        TextView header = new TextView(this);
        header.setText("网页编辑");
        header.setTextSize(22);
        header.setTypeface(null, Typeface.BOLD);
        header.setTextColor(dark ? 0xFFEEEEEE : 0xFF000000);
        header.setPadding(0, 0, 0, dip(20));
        homeRoot.addView(header);

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        cardsRow = new LinearLayout(this);
        cardsRow.setOrientation(LinearLayout.HORIZONTAL);
        hs.addView(cardsRow);
        homeRoot.addView(hs, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout spacer = new LinearLayout(this);
        spacer.setOrientation(LinearLayout.VERTICAL);
        spacer.setGravity(Gravity.BOTTOM);
        LinearLayout pad = new LinearLayout(this);
        pad.setOrientation(LinearLayout.VERTICAL);
        pad.setGravity(Gravity.BOTTOM);
        homeRoot.addView(pad, new LinearLayout.LayoutParams(0, 0, 1f));

        // 底部胶囊: ＋新建
        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable pbg = new GradientDrawable();
        pbg.setColor(dark ? 0xFF1C1F26 : 0xFFF2F3F5);
        pbg.setCornerRadius(dip(24));
        pill.setBackground(pbg);
        pill.setPadding(dip(18), dip(12), dip(18), dip(12));
        TextView ic = new TextView(this);
        ic.setText("＋");
        ic.setTextSize(16);
        ic.setTextColor(0xFF315CDE);
        pill.addView(ic);
        TextView ph = new TextView(this);
        ph.setText("  新建网页 / 上传HTML");
        ph.setTextSize(14);
        ph.setTextColor(0xFF888888);
        pill.addView(ph);
        pill.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showNewDialog(); } });
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, -2);
        plp.setMargins(0, dip(20), dip(20), dip(24));
        homeRoot.addView(pill, plp);

        refreshCards();
        setContentView(homeRoot);
    }

    private void refreshCards() {
        cardsRow.removeAllViews();
        JSONArray a = a();
        int n = a.length();
        for (int i = 0; i < n; i++) {
            final int idx = i;
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            final String name = o.optString("name", "页");
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            card.setPadding(dip(2), 0, dip(24), 0);
            card.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { openEditor(idx); } });
            card.setOnLongClickListener(new View.OnLongClickListener() {
                public boolean onLongClick(View v) { delPage(idx); return true; }
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
        // 末尾常驻 ＋ 卡
        LinearLayout add = new LinearLayout(this);
        add.setOrientation(LinearLayout.VERTICAL);
        add.setGravity(Gravity.CENTER_HORIZONTAL);
        add.setPadding(dip(2), 0, dip(24), 0);
        add.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showNewDialog(); } });
        TextView ap = new TextView(this);
        ap.setText("＋");
        ap.setTextSize(30);
        ap.setTextColor(0xFF888888);
        ap.setGravity(Gravity.CENTER);
        GradientDrawable ac = new GradientDrawable();
        ac.setShape(GradientDrawable.OVAL);
        ac.setColor(dark ? 0xFF1C1F26 : 0xFFF2F3F5);
        ap.setBackground(ac);
        add.addView(ap, new LinearLayout.LayoutParams(dip(74), dip(74)));
        TextView al = new TextView(this);
        al.setText("新建");
        al.setTextSize(15);
        al.setTextColor(dark ? 0xFFEEEEEE : 0xFF000000);
        al.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-2, -2);
        alp.topMargin = dip(12);
        add.addView(al, alp);
        cardsRow.addView(add, new LinearLayout.LayoutParams(-2, -2));
    }

    private void showNewDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dip(24), dip(10), dip(24), 0);
        final EditText et = new EditText(this);
        et.setHint("页面名称");
        et.setInputType(EditorInfo.TYPE_CLASS_TEXT);
        box.addView(et);
        TextView up = new TextView(this);
        up.setText("📂 或从手机导入HTML文件");
        up.setTextSize(14);
        up.setTextColor(0xFF315CDE);
        up.setPadding(0, dip(16), 0, dip(4));
        up.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
            i.setType("text/*");
            i.addCategory(android.content.Intent.CATEGORY_OPENABLE);
            startActivityForResult(android.content.Intent.createChooser(i, "选择HTML文件"), 9002);
        }});
        box.addView(up);
        new AlertDialog.Builder(this)
            .setTitle("新建网页")
            .setView(box)
            .setPositiveButton("创建", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) {
                    String n = et.getText().toString().trim();
                    if (n.isEmpty()) n = "页面" + (a().length() + 1);
                    try {
                        JSONObject o = new JSONObject();
                        o.put("name", n);
                        String f = "p" + System.currentTimeMillis() + ".html";
                        o.put("file", f);
                        o.put("color", COLORS[a().length() % COLORS.length]);
                        FileOutputStream fos = new FileOutputStream(pageFile(f));
                        fos.write(TEMPLATE.getBytes("UTF-8"));
                        fos.close();
                        JSONArray arr = a();
                        arr.put(o);
                        save(arr);
                        openEditor(arr.length() - 1);
                    } catch (Throwable e) { toast("创建失败"); }
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    // ---------------- 编辑器 + 运行 ----------------

    private void openEditor(int idx) {
        editingIdx = idx;
        JSONObject o = a().optJSONObject(idx);
        if (o == null) return;
        editorRoot = new LinearLayout(this);
        editorRoot.setOrientation(LinearLayout.VERTICAL);
        editorRoot.setBackgroundColor(dark ? 0xFF000000 : 0xFFFFFFFF);

        // 顶栏: 返回 | 名称 | 保存 | 运行
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dip(12), dip(40), dip(12), dip(8));
        TextView back = new TextView(this);
        back.setText("‹ 主页");
        back.setTextSize(14);
        back.setTextColor(0xFF315CDE);
        back.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { buildHome(); } });
        top.addView(back);
        TextView name = new TextView(this);
        name.setText("  " + o.optString("name", ""));
        name.setTextSize(16);
        name.setTypeface(null, Typeface.BOLD);
        name.setTextColor(dark ? 0xFFEEEEEE : 0xFF1F2329);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0, -2, 1f);
        top.addView(name, nlp);
        runBtn = new TextView(this);
        runBtn.setText("▶ 运行");
        runBtn.setTextSize(14);
        runBtn.setTextColor(0xFFFFFFFF);
        runBtn.setTypeface(null, Typeface.BOLD);
        GradientDrawable rg = new GradientDrawable();
        rg.setColors(new int[]{0xFF315CDE, 0xFF7B4FD8});
        rg.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
        rg.setCornerRadius(dip(16));
        runBtn.setBackground(rg);
        runBtn.setPadding(dip(16), dip(8), dip(16), dip(8));
        runBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { runPage(); } });
        top.addView(runBtn);
        editorRoot.addView(top, new LinearLayout.LayoutParams(-1, -2));

        // 代码区
        code = new EditText(this);
        code.setText(readPage(o.optString("file", "")));
        code.setTextSize(13);
        code.setTypeface(Typeface.MONOSPACE);
        code.setTextColor(dark ? 0xFFEEEEEE : 0xFF1F2329);
        code.setBackgroundColor(dark ? 0xFF0C0E12 : 0xFFF7F8FA);
        code.setGravity(Gravity.TOP);
        code.setMinimumHeight(dip(200));
        editorRoot.addView(code, new LinearLayout.LayoutParams(-1, 0, 1f));

        // 预览区(默认隐藏, 运行时显示)
        preview = new WebView(this);
        WebSettings ws = preview.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        preview.setBackgroundColor(0xFFFFFFFF);
        preview.setVisibility(View.GONE);
        editorRoot.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1f));

        setContentView(editorRoot);
        toast("编辑代码, 点▶运行预览");
    }

    private void runPage() {
        try {
            // 先保存
            JSONObject o = a().optJSONObject(editingIdx);
            if (o == null) return;
            FileOutputStream fos = new FileOutputStream(pageFile(o.optString("file", "")));
            fos.write(code.getText().toString().getBytes("UTF-8"));
            fos.close();
            // 运行: 代码区隐藏, 预览显示; 再点运行=刷新
            if (preview.getVisibility() == View.VISIBLE) {
                code.setVisibility(View.VISIBLE);
                preview.setVisibility(View.GONE);
                runBtn.setText("▶ 运行");
            } else {
                preview.loadDataWithBaseURL(null, code.getText().toString(), "text/html", "utf-8", null);
                code.setVisibility(View.GONE);
                preview.setVisibility(View.VISIBLE);
                runBtn.setText("✎ 编辑");
            }
        } catch (Throwable e) { toast("运行失败: " + e); }
    }

    private void delPage(int idx) {
        try {
            JSONArray arr = a();
            arr.remove(idx);
            save(arr);
            refreshCards();
            toast("已删除");
        } catch (Throwable ignored) {}
    }

    // ---------------- 数据 ----------------

    private JSONArray a() {
        try { return new JSONArray(getSharedPreferences(PREF, MODE_PRIVATE).getString("json", "[]")); }
        catch (Throwable e) { return new JSONArray(); }
    }

    private void save(JSONArray arr) {
        getSharedPreferences(PREF, MODE_PRIVATE).edit().putString("json", arr.toString()).apply();
    }

    private File pageDir() {
        File d = new File(getFilesDir(), "webpages");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private File pageFile(String f) { return new File(pageDir(), f); }

    private String readPage(String f) {
        try {
            FileInputStream fis = new FileInputStream(pageFile(f));
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while ((n = fis.read(buf)) > 0) b.write(buf, 0, n);
            fis.close();
            return new String(b.toByteArray(), "UTF-8");
        } catch (Throwable e) { return TEMPLATE; }
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
            String f = "p" + System.currentTimeMillis() + ".html";
            InputStream is = getContentResolver().openInputStream(data.getData());
            FileOutputStream fos = new FileOutputStream(pageFile(f));
            byte[] b = new byte[8192]; int n;
            while ((n = is.read(b)) > 0) fos.write(b, 0, n);
            is.close(); fos.close();
            JSONObject o = new JSONObject();
            o.put("name", name.replaceAll("(?i)\\.html?$", ""));
            o.put("file", f);
            o.put("color", COLORS[a().length() % COLORS.length]);
            JSONArray arr = a();
            arr.put(o);
            save(arr);
            refreshCards();
            toast("已导入");
        } catch (Throwable e) { toast("导入失败: " + e); }
    }

    private int dip(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackPressed() {
        if (editorRoot != null && editorRoot.getParent() != null) buildHome();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (preview != null) preview.destroy();
        super.onDestroy();
    }
}
