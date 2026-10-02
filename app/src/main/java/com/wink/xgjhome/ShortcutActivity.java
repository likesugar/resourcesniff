package com.wink.xgjhome;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** 创建快捷键页：小卡片网格（新建快捷键 + 已建快捷键），扁平化 */
public class ShortcutActivity extends Activity {

    private LinearLayout grid;
    private EditText scNameEt, scPkgEt, scClsEt;

    @Override
    protected void onCreate(Bundle b) {
        try {
        onCreateInner(b);
        } catch (Throwable t) {
            android.widget.ScrollView sv2 = new android.widget.ScrollView(this);
            android.widget.TextView tv2 = new android.widget.TextView(this);
            tv2.setTextColor(0xFFFF6B6B);
            tv2.setTextSize(12);
            java.io.StringWriter sw2 = new java.io.StringWriter();
            t.printStackTrace(new java.io.PrintWriter(sw2));
            tv2.setText("CRASH:\n" + sw2.toString());
            sv2.addView(tv2);
            setContentView(sv2);
            try {
                java.io.File f = new java.io.File(getExternalFilesDir(null), "诊断toast.txt");
                java.io.FileWriter fw = new java.io.FileWriter(f, true);
                fw.write(new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(new java.util.Date()) + "\n" + sw2.toString() + "\n----------\n");
                fw.close();
            } catch (Throwable ignored) {}
        }
    }

    private void onCreateInner(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color0.bg());

        TextView title = new TextView(this);
        title.setText("创建快捷键");
        title.setTextSize(22);
        title.setTypeface(Typeface0.bold());
        title.setTextColor(Color0.text());
        title.setPadding(dp(18), dp(20), dp(18), dp(10));
        root.addView(title);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(dp(18), 0, dp(18), dp(18));
        sv.addView(grid);
        root.addView(sv);
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderGrid();
    }

    private void renderGrid() {
        grid.removeAllViews();
        LinearLayout row = null;
        java.util.ArrayList<View> cells = new java.util.ArrayList<View>();
        cells.add(smallCard("⚡", "新建快捷键", new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(ShortcutActivity.this, ShortcutEditActivity.class));
            }
        }, null));
        for (final String[] us : userShortcuts()) {
            final String key = us[9];
            String ic = us[0].length() > 0 ? us[0].substring(0, 1) : "?";
            cells.add(smallCard(ic, us[0], new View.OnClickListener() {
                public void onClick(View v) { launchShortcut(us); }
            }, new View.OnLongClickListener() {
                public boolean onLongClick(View v) { itemMenu(us, key); return true; }
            }));
        }
        for (int i = 0; i < cells.size(); i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                grid.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            if (i % 2 == 1) lp.leftMargin = dp(12);
            row.addView(cells.get(i), lp);
        }
    }

    private View smallCard(String icon, String name, View.OnClickListener click, View.OnLongClickListener longClick) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setBackgroundColor(Color0.card());
        int pad = dp(14);
        card.setPadding(pad, pad, pad, pad);
        TextView ic = new TextView(this);
        ic.setText(icon);
        ic.setTextSize(20);
        ic.setGravity(Gravity.CENTER);
        ic.setTextColor(Color0.text());
        ic.setBackgroundColor(Color0.btnBg());
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(40), dp(40));
        card.addView(ic, ilp);
        TextView tv = new TextView(this);
        tv.setText(name);
        tv.setTextSize(13);
        tv.setTextColor(Color0.text());
        tv.setGravity(Gravity.CENTER);
        tv.setMaxLines(1);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
        tlp.topMargin = dp(8);
        card.addView(tv, tlp);
        card.setOnClickListener(click);
        if (longClick != null) card.setOnLongClickListener(longClick);
        return card;
    }

    private void itemMenu(final String[] us, final String key) {
        new android.app.AlertDialog.Builder(this)
            .setTitle(us[0])
            .setItems(new String[]{"打开", "编辑", "删除"}, new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) {
                    if (w == 0) launchShortcut(us);
                    else if (w == 1) {
                        Intent i = new Intent(ShortcutActivity.this, ShortcutEditActivity.class);
                        i.putExtra("key", key);
                        startActivity(i);
                    } else {
                        deleteByKey(key);
                        renderGrid();
                    }
                }
            }).show();
    }

    private void launchShortcut(String[] us) {
        String pkg = us[1], cls = us[2];
        String data = us[3];
        String extras = us[4];
        int am2 = parseInt0(us[5]);
        String custom = us[6];
        boolean newTask = !us[7].equals("0");
        boolean root = us[8].equals("1");
        if (root) {
            try {
                String cmd = "am start -n " + pkg + (cls.length() > 0 ? "/" + cls : "");
                Process p2 = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
                if (p2.waitFor() == 0) return;
                Toast.makeText(this, "Root启动失败", Toast.LENGTH_SHORT).show();
            } catch (Throwable t) { Toast.makeText(this, "无Root权限", Toast.LENGTH_SHORT).show(); }
            return;
        }
        try {
            Intent i = new Intent();
            if (am2 == 1) { i.setAction(Intent.ACTION_MAIN); i.addCategory(Intent.CATEGORY_LAUNCHER); }
            else if (am2 == 2 && custom.length() > 0) i.setAction(custom);
            else i.setAction(Intent.ACTION_VIEW);
            if (data.length() > 0) { try { i.setData(android.net.Uri.parse(data)); } catch (Throwable ignored) {} }
            if (cls.length() > 0) i.setComponent(new android.content.ComponentName(pkg, cls));
            for (String ln : extras.split("\n")) {
                ln = ln.trim();
                int eq = ln.indexOf('=');
                if (eq <= 0) continue;
                String k = ln.substring(0, eq), v = ln.substring(eq + 1);
                try {
                    if (v.matches("-?\\d+")) i.putExtra(k, Long.parseLong(v));
                    else if (v.equals("true") || v.equals("false")) i.putExtra(k, Boolean.parseBoolean(v));
                    else i.putExtra(k, v);
                } catch (Throwable t) { i.putExtra(k, v); }
            }
            if (newTask) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            return;
        } catch (Throwable t) { }
        try {
            Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
            if (i != null) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); return; }
        } catch (Throwable ignored) {}
        Toast.makeText(this, "未能打开目标应用", Toast.LENGTH_LONG).show();
    }

    private int parseInt0(String s2) { try { return Integer.parseInt(s2); } catch (Throwable e) { return 0; } }

    private void deleteByKey(String key) {
        android.content.SharedPreferences sp = getSharedPreferences("home_shortcuts", MODE_PRIVATE);
        int n = sp.getInt("count", 0);
        java.util.ArrayList<String> all = new java.util.ArrayList<String>();
        for (int i = 0; i < n; i++) {
            String r = sp.getString("s" + i, null);
            if (r == null) continue;
            String[] p3 = r.split("\\u0001");
            String k = p3.length > 9 ? p3[9] : "";
            if (!k.equals(key)) all.add(r);
        }
        android.content.SharedPreferences.Editor e = sp.edit();
        e.putInt("count", all.size());
        for (int i = 0; i < all.size(); i++) e.putString("s" + i, all.get(i));
        for (int i = all.size(); i < n; i++) e.remove("s" + i);
        e.apply();
    }

    public static String[] findByKey(android.content.Context c, String key) {
        android.content.SharedPreferences sp = c.getSharedPreferences("home_shortcuts", android.content.Context.MODE_PRIVATE);
        String raw = sp.getString("k_" + key, null);
        if (raw == null) return null;
        String[] p3 = raw.split("\\u0001");
        String[] full = new String[10];
        for (int j2 = 0; j2 < 10; j2++) full[j2] = j2 < p3.length ? p3[j2] : "";
        if (full[7].length() == 0) full[7] = "1";
        return full;
    }

    public static void saveByKey(android.content.Context c, String key, String rec) {
        android.content.SharedPreferences sp = c.getSharedPreferences("home_shortcuts", android.content.Context.MODE_PRIVATE);
        int n = sp.getInt("count", 0);
        java.util.ArrayList<String> all = new java.util.ArrayList<String>();
        for (int i = 0; i < n; i++) { String r = sp.getString("s" + i, null); if (r != null) all.add(r); }
        for (int i = 0; i < all.size(); i++) {
            String[] p3 = all.get(i).split("\\u0001");
            String k = p3.length > 9 ? p3[9] : "";
            if (k.equals(key)) { all.set(i, rec + "\\u0001" + key); rec = null; break; }
        }
        if (rec != null) all.add(rec + "\\u0001" + key);
        android.content.SharedPreferences.Editor e = sp.edit();
        e.putInt("count", all.size());
        for (int i = 0; i < all.size(); i++) e.putString("s" + i, all.get(i));
        e.putString("k_" + key, rec == null ? sp.getString("k_" + key, rec) : rec);
        e.apply();
    }

    private java.util.ArrayList<String[]> userShortcuts() {
        java.util.ArrayList<String[]> out = new java.util.ArrayList<String[]>();
        try {
            android.content.SharedPreferences sp = getSharedPreferences("home_shortcuts", MODE_PRIVATE);
            int n = sp.getInt("count", 0);
            for (int i = 0; i < n; i++) {
                String raw = sp.getString("s" + i, null);
                if (raw == null) continue;
                String[] p2 = raw.split("\\u0001");
                if (p2.length >= 3) {
                    String[] full = new String[10];
                    for (int j2 = 0; j2 < 10; j2++) full[j2] = j2 < p2.length ? p2[j2] : (j2 == 7 ? "1" : "");
                    if (full[9].length() == 0) full[9] = "s" + i;
                    out.add(full);
                }
            }
        } catch (Throwable ignored) {}
        return out;
    }

    private void saveUserShortcuts(java.util.ArrayList<String[]> list) {
        try {
            android.content.SharedPreferences.Editor e = getSharedPreferences("home_shortcuts", MODE_PRIVATE).edit();
            e.putInt("count", list.size());
            for (int i = 0; i < list.size(); i++) {
                String[] it = list.get(i);
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j < 10; j++) {
                    if (j > 0) sb.append("\\u0001");
                    String v = (j < it.length && it[j] != null) ? it[j] : "";
                    if (v.length() == 0 && j == 7) v = "1";
                    if (v.length() == 0 && j == 9) v = "s" + System.currentTimeMillis() + "_" + i;
                    sb.append(v);
                }
                e.putString("s" + i, sb.toString());
                e.putString("k_" + it[9], sb.toString());
            }
            e.apply();
        } catch (Throwable ignored) {}
    }

    /** 颜色/样式小助手（扁平化深色） */
    private static class Color0 {
        static int bg() { return 0xFF0B0D10; }
        static int card() { return 0xFF161A20; }
        static int text() { return 0xFFE8ECF2; }
        static int btnBg() { return 0xFF242B34; }
    }

    private static class Typeface0 {
        static android.graphics.Typeface bold() { return android.graphics.Typeface.DEFAULT_BOLD; }
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}
