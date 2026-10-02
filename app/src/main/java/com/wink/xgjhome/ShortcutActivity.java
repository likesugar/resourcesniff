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
            public void onClick(View v) { showDialog(); }
        }, null));
        for (final String[] us : userShortcuts()) {
            String ic = us[0].length() > 0 ? us[0].substring(0, 1) : "?";
            cells.add(smallCard(ic, us[0], new View.OnClickListener() {
                public void onClick(View v) { launchShortcut(us[1], us[2]); }
            }, new View.OnLongClickListener() {
                public boolean onLongClick(View v) { confirmDelete(us); return true; }
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
        ic.setBackgroundResource(Color0.btnBg());
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

    private void showDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(24);
        box.setPadding(pad, pad / 2, pad, 0);

        scNameEt = input("名字（桌面显示）");
        box.addView(scNameEt);
        scPkgEt = input("包名（可留空从列表选）");
        box.addView(scPkgEt);
        scClsEt = input("活动（可留空自动取入口）");
        box.addView(scClsEt);

        TextView pick = new TextView(this);
        pick.setText("从已装应用选择 ›");
        pick.setTextColor(0xFF3D7BFF);
        pick.setTextSize(14);
        pick.setPadding(0, pad / 2, 0, 0);
        pick.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showAppPicker(); }
        });
        box.addView(pick);

        new android.app.AlertDialog.Builder(this)
            .setTitle("新建快捷键").setView(box)
            .setPositiveButton("确认", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) { createShortcut(); }
            })
            .setNegativeButton("取消", null).show();
    }

    private EditText input(String hint) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setBackground(null);
        et.setTextColor(0xFFE8ECF2);
        et.setHintTextColor(0xFF7A828E);
        et.setTextSize(15);
        return et;
    }

    private void showAppPicker() {
        try {
            android.content.pm.PackageManager pm = getPackageManager();
            Intent li = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            java.util.List<android.content.pm.ResolveInfo> apps = pm.queryIntentActivities(li, 0);
            java.util.Collections.sort(apps, new java.util.Comparator<android.content.pm.ResolveInfo>() {
                public int compare(android.content.pm.ResolveInfo a, android.content.pm.ResolveInfo b) {
                    return String.valueOf(a.loadLabel(pm)).compareToIgnoreCase(String.valueOf(b.loadLabel(pm)));
                }
            });
            final android.content.pm.ResolveInfo[] arr = apps.toArray(new android.content.pm.ResolveInfo[0]);
            String[] names = new String[arr.length];
            for (int i = 0; i < arr.length; i++)
                names[i] = arr[i].loadLabel(pm) + " (" + arr[i].activityInfo.packageName + ")";
            new android.app.AlertDialog.Builder(this)
                .setTitle("选择应用")
                .setItems(names, new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        scPkgEt.setText(arr[w].activityInfo.packageName);
                        scClsEt.setText(arr[w].activityInfo.name);
                        if (scNameEt.getText().length() == 0) scNameEt.setText(String.valueOf(arr[w].loadLabel(pm)));
                    }
                }).show();
        } catch (Throwable e) {
            Toast.makeText(this, "获取应用列表失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void createShortcut() {
        String name = scNameEt.getText().toString().trim();
        String pkg = scPkgEt.getText().toString().trim();
        String cls = scClsEt.getText().toString().trim();
        if (name.length() == 0 || pkg.length() == 0) {
            Toast.makeText(this, "名字和包名不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        java.util.ArrayList<String[]> list = userShortcuts();
        list.add(new String[]{name, pkg, cls});
        saveUserShortcuts(list);
        renderGrid();  // 新卡片出现在"新建快捷键"后面
        try {
            android.content.pm.PackageManager pm = getPackageManager();
            Intent launch = null;
            if (cls.length() > 0) {
                launch = new Intent(Intent.ACTION_VIEW).setComponent(new android.content.ComponentName(pkg, cls));
                try { launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(launch); }
                catch (Throwable t) {
                    launch = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                        .setComponent(new android.content.ComponentName(pkg, cls));
                }
            }
            if (launch == null) launch = pm.getLaunchIntentForPackage(pkg);
            if (launch == null) { Toast.makeText(this, "应用入口无效，仅保存了卡片", Toast.LENGTH_LONG).show(); return; }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            android.content.pm.ShortcutManager sm = (android.content.pm.ShortcutManager) getSystemService(SHORTCUT_SERVICE);
            if (sm != null && sm.isRequestPinShortcutSupported()) {
                android.content.pm.ShortcutInfo si = new android.content.pm.ShortcutInfo.Builder(this, "us_" + System.currentTimeMillis())
                    .setShortLabel(name).setLongLabel(name).setIntent(launch).build();
                sm.requestPinShortcut(si, null);
            }
        } catch (Throwable ignored) {}
    }

    private void launchShortcut(String pkg, String cls) {
        try {
            android.content.pm.PackageManager pm = getPackageManager();
            if (cls.length() > 0) {
                Intent i = new Intent(Intent.ACTION_VIEW).setComponent(new android.content.ComponentName(pkg, cls));
                try { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); return; } catch (Throwable t) { }
                i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(new android.content.ComponentName(pkg, cls));
                try { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); return; } catch (Throwable t) { }
            }
            Intent i = pm.getLaunchIntentForPackage(pkg);
            if (i != null) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); return; }
            Toast.makeText(this, "未能打开目标应用", Toast.LENGTH_LONG).show();
        } catch (Throwable e) {
            Toast.makeText(this, "未能打开目标应用", Toast.LENGTH_LONG).show();
        }
    }

    private void confirmDelete(final String[] us) {
        new android.app.AlertDialog.Builder(this)
            .setTitle("删除快捷键卡片").setMessage(us[0])
            .setPositiveButton("删除", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) {
                    java.util.ArrayList<String[]> list = userShortcuts();
                    for (int i = 0; i < list.size(); i++) {
                        String[] it = list.get(i);
                        if (it[0].equals(us[0]) && it[1].equals(us[1]) && it[2].equals(us[2])) { list.remove(i); break; }
                    }
                    saveUserShortcuts(list);
                    renderGrid();
                }
            })
            .setNegativeButton("取消", null).show();
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
                if (p2.length >= 3) out.add(new String[]{p2[0], p2[1], p2[2]});
            }
        } catch (Throwable ignored) {}
        return out;
    }

    private void saveUserShortcuts(java.util.ArrayList<String[]> list) {
        try {
            android.content.SharedPreferences.Editor e = getSharedPreferences("home_shortcuts", MODE_PRIVATE).edit();
            e.putInt("count", list.size());
            for (int i = 0; i < list.size(); i++)
                e.putString("s" + i, list.get(i)[0] + "\\u0001" + list.get(i)[1] + "\\u0001" + list.get(i)[2]);
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
