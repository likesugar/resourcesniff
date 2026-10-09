package com.wink.xgjhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;

/** 网页转APK: 网站列表 + 夜间模式/启用JS/隐藏导航栏/隐藏状态栏 + 打包 */
public class Web2ApkActivity extends Activity {

    private static final int BG = 0xFF17191D;
    private static final int ROW = 0xFF212429;
    private static final int ACCENT = 0xFF25D0A5;
    private static final int TXT = 0xFFF2F3F5;
    private static final int SUB = 0xFF9AA0A6;
    private static final int DIV = 0xFF2C2F34;
    private static final int PILL = 0xFF1F3A33;

    private String appName = "xapk", pkg = "main";
    private LinearLayout content;
    private LinearLayout tabs;

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    private TextView glyph(String t, int color, int size) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextColor(color);
        tv.setTextSize(size);
        return tv;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String n = getIntent().getStringExtra("name");
        String k = getIntent().getStringExtra("pkg");
        if (n != null) appName = n;
        if (k != null) pkg = k;
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(18), dp(14), dp(18), dp(10));
        TextView menu = glyph("≡", TXT, 22);
        top.addView(menu);
        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, -2, 1f);
        mp.setMargins(dp(14), 0, dp(14), 0);
        mid.setLayoutParams(mp);
        TextView tvName = new TextView(this);
        tvName.setText(appName);
        tvName.setTextColor(TXT);
        tvName.setTextSize(18);
        tvName.setTypeface(Typeface.DEFAULT_BOLD);
        TextView tvSub = new TextView(this);
        tvSub.setText(pkg);
        tvSub.setTextColor(SUB);
        tvSub.setTextSize(12);
        mid.addView(tvName);
        mid.addView(tvSub);
        top.addView(mid);
        // 右上角: 抖音直播 开/关
        final TextView dyT = new TextView(this);
        boolean dyOn = "1".equals(getPref("抖音直播"));
        dyT.setText("抖音直播 " + (dyOn ? "开" : "关"));
        dyT.setTextColor(dyOn ? ACCENT : SUB);
        dyT.setTextSize(14);
        dyT.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable dg = new GradientDrawable();
        dg.setCornerRadius(dp(12));
        dg.setColor(dyOn ? PILL : 0xFF2A2E33);
        dyT.setBackground(dg);
        dyT.setPadding(dp(12), dp(6), dp(12), dp(6));
        dyT.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                boolean on = !"1".equals(getPref("抖音直播"));
                savePref("抖音直播", on ? "1" : "0");
                dyT.setText("抖音直播 " + (on ? "开" : "关"));
                dyT.setTextColor(on ? ACCENT : SUB);
                GradientDrawable g2 = new GradientDrawable();
                g2.setCornerRadius(dp(12));
                g2.setColor(on ? PILL : 0xFF2A2E33);
                dyT.setBackground(g2);
            }
        });
        top.addView(dyT);
        root.addView(top, new LinearLayout.LayoutParams(-1, -2));

        // 单一分组头: 网站
        tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        tabs.setPadding(dp(16), dp(10), dp(16), dp(8));
        TextView h = new TextView(this);
        h.setText("网站");
        h.setTextColor(ACCENT);
        h.setTextSize(14);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(0, -2, 1f);
        h.setLayoutParams(hp);
        tabs.addView(h);
        TextView add = glyph("＋ 添加网站", ACCENT, 15);
        add.setTypeface(Typeface.DEFAULT_BOLD);
        add.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { addPageDialog(); }
        });
        tabs.addView(add);
        root.addView(tabs, new LinearLayout.LayoutParams(-1, -2));
        View topDiv = new View(this);
        topDiv.setBackgroundColor(DIV);
        root.addView(topDiv, new LinearLayout.LayoutParams(-1, dp(1)));

        ScrollView sc = new ScrollView(this);
        sc.setBackgroundColor(BG);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        sc.addView(content, new ViewGroup.LayoutParams(-1, -2));
        root.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1f));

        // 底部打包按钮
        TextView build = new TextView(this);
        build.setText("打包成APK");
        build.setTextColor(0xFFFFFFFF);
        build.setTextSize(17);
        build.setTypeface(Typeface.DEFAULT_BOLD);
        build.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(0xFF1A73E8);
        build.setBackground(bg);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(52));
        bp.setMargins(dp(16), dp(12), dp(16), dp(16));
        build.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { doBuild(); }
        });
        root.addView(build, bp);

        setContentView(root);
        render();
    }

    private void render() {
        content.removeAllViews();
        java.util.List<String[]> pages = getPages();
        for (final String[] pg : pages) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundColor(ROW);
            row.setPadding(dp(16), dp(12), dp(16), dp(12));
            TextView ic = glyph("🌐", TXT, 18);
            row.addView(ic);
            LinearLayout mid = new LinearLayout(this);
            mid.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, -2, 1f);
            mp.setMargins(dp(12), 0, dp(10), 0);
            mid.setLayoutParams(mp);
            TextView t = new TextView(this);
            t.setText(pg[0]);
            t.setTextColor(TXT);
            t.setTextSize(16);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            TextView u = new TextView(this);
            u.setText(pg[1]);
            u.setTextColor(SUB);
            u.setTextSize(12);
            mid.addView(t);
            mid.addView(u);
            row.addView(mid);
            TextView del = glyph("🗑", SUB, 15);
            del.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { delPage(pg[0]); render(); }
            });
            row.addView(del);
            content.addView(row, new LinearLayout.LayoutParams(-1, -2));
            View dv = new View(this);
            dv.setBackgroundColor(DIV);
            content.addView(dv, new LinearLayout.LayoutParams(-1, dp(1)));
        }
        if (pages.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("还没有网站，点右上角「＋ 添加网站」");
            empty.setTextColor(SUB);
            empty.setTextSize(14);
            empty.setPadding(dp(16), dp(24), dp(16), dp(24));
            content.addView(empty);
        }
        // 四个开关
        toggleRow("🌙", "夜间模式");
        toggleRow("JS", "启用JavaScript");
        toggleRow("⬓", "隐藏导航栏");
        toggleRow("⬒", "隐藏状态栏");
    }

    private void toggleRow(String icon, String title) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(ROW);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout ic = new LinearLayout(this);
        ic.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(8));
        g.setColor(0xFF2A2E33);
        ic.setBackground(g);
        TextView iv = glyph(icon, TXT, 13);
        ic.addView(iv);
        row.addView(ic, new LinearLayout.LayoutParams(dp(34), dp(34)));
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(TXT);
        t.setTextSize(16);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1f);
        tp.setMargins(dp(14), 0, dp(10), 0);
        t.setLayoutParams(tp);
        row.addView(t);
        Switch sw = new Switch(this);
        sw.setChecked("1".equals(getPref(title)));
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) { savePref(title, on ? "1" : "0"); }
        });
        row.addView(sw);
        content.addView(row, new LinearLayout.LayoutParams(-1, -2));
        View div = new View(this);
        div.setBackgroundColor(DIV);
        content.addView(div, new LinearLayout.LayoutParams(-1, dp(1)));
    }

    private java.util.List<String[]> getPages() {
        java.util.List<String[]> out = new java.util.ArrayList<>();
        String l = getSharedPreferences("web2apk_" + pkg, MODE_PRIVATE).getString("pages", "");
        for (String e : l.split("\u0002")) {
            if (e.length() == 0) continue;
            String[] p2 = e.split("\u0001", -1);
            if (p2.length >= 2) out.add(new String[]{p2[0], p2[1]});
        }
        return out;
    }

    private void delPage(String name) {
        StringBuilder sb = new StringBuilder();
        for (String[] pg : getPages()) {
            if (pg[0].equals(name)) continue;
            if (sb.length() > 0) sb.append('\u0002');
            sb.append(pg[0]).append('\u0001').append(pg[1]);
        }
        getSharedPreferences("web2apk_" + pkg, MODE_PRIVATE).edit().putString("pages", sb.toString()).apply();
    }

    private void addPageDialog() {
        final EditText nI = new EditText(this);
        nI.setHint("名称");
        nI.setTextSize(15);
        final EditText uI = new EditText(this);
        uI.setHint("网址 https://...");
        uI.setTextSize(15);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p16 = dp(16);
        box.setPadding(p16, p16 / 2, p16, 0);
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, -2);
        ep.setMargins(0, p16 / 2, 0, p16 / 2);
        box.addView(nI, ep);
        box.addView(uI, ep);
        new AlertDialog.Builder(this)
                .setTitle("添加网站")
                .setView(box)
                .setPositiveButton("添加", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        String n = nI.getText().toString().trim();
                        String u = uI.getText().toString().trim();
                        if (n.length() == 0 || !u.startsWith("http")) {
                            Toast.makeText(Web2ApkActivity.this, "名称必填, 网址需 http(s) 开头", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        String cur = getSharedPreferences("web2apk_" + pkg, MODE_PRIVATE).getString("pages", "");
                        String add = (cur.length() > 0 ? cur + "\u0002" : "") + n + "\u0001" + u;
                        getSharedPreferences("web2apk_" + pkg, MODE_PRIVATE).edit().putString("pages", add).apply();
                        render();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String getPref(String k) {
        return getSharedPreferences("web2apk_" + pkg, MODE_PRIVATE).getString(k, null);
    }

    private void savePref(String k, String v) {
        getSharedPreferences("web2apk_" + pkg, MODE_PRIVATE).edit().putString(k, v).apply();
    }

    // 打包: 配置JSON + 模板壳改包 + v2/v3签名 -> 可安装APK
    private void doBuild() {
        java.util.List<String[]> pages = getPages();
        if (pages.isEmpty()) {
            Toast.makeText(this, "请先添加至少一个网站", Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder json = new StringBuilder();
        json.append("{\"app_name\":\"").append(appName.replace("\"", ""))
                .append("\",\"package\":\"").append(pkg.replace("\"", ""))
                .append("\",\"night\":").append("1".equals(getPref("夜间模式")) ? 1 : 0)
                .append(",\"js\":").append("1".equals(getPref("启用JavaScript")) ? 1 : 0)
                .append(",\"hide_nav\":").append("1".equals(getPref("隐藏导航栏")) ? 1 : 0)
                .append(",\"hide_status\":").append("1".equals(getPref("隐藏状态栏")) ? 1 : 0)
                .append(",\"douyin_live\":").append("1".equals(getPref("抖音直播")) ? 1 : 0)
                .append(",\"sites\":[");
        for (int i = 0; i < pages.size(); i++) {
            if (i > 0) json.append(",");
            json.append("{\"name\":\"").append(pages.get(i)[0].replace("\"", ""))
                    .append("\",\"url\":\"").append(pages.get(i)[1].replace("\"", "")).append("\"}");
        }
        json.append("]}");
        final String cfg = json.toString();
        Toast.makeText(this, "正在打包...", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() { public void run() {
            try {
                File dir = new File(getExternalFilesDir(null), "web2apk");
                if (!dir.exists()) dir.mkdirs();
                File out = new File(dir, appName + "_" + pkg + ".apk");
                RepackUtil.buildFromAssets(getApplicationContext(), cfg, pkg, appName, out);
                runOnUiThread(new Runnable() { public void run() {
                    Toast.makeText(Web2ApkActivity.this, "打包完成: " + out.getAbsolutePath(), Toast.LENGTH_LONG).show();
                    try {
                        android.net.Uri uri;
                        try {
                            uri = androidx.core.content.FileProvider.getUriForFile(Web2ApkActivity.this,
                                    getPackageName() + ".files", out);
                        } catch (Throwable t) {
                            uri = android.net.Uri.fromFile(out);
                        }
                        android.content.Intent it = new android.content.Intent(android.content.Intent.ACTION_VIEW);
                        it.setDataAndType(uri, "application/vnd.android.package-archive");
                        it.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION | android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(it);
                    } catch (Throwable t) {
                        Toast.makeText(Web2ApkActivity.this, "请用MT管理器安装: " + out.getAbsolutePath(), Toast.LENGTH_LONG).show();
                    }
                }});
            } catch (Throwable t) {
                runOnUiThread(new Runnable() { public void run() {
                    Toast.makeText(Web2ApkActivity.this, "打包失败: " + t, Toast.LENGTH_LONG).show();
                }});
            }
        }}).start();
    }
}
