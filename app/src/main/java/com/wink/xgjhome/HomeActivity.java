package com.wink.xgjhome;

import android.app.Activity;
import android.content.ClipboardManager;
import android.content.Intent;
import android.widget.EditText;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;


/** 首页：工具箱（纯黑/冰蓝主题切换 · 全屏）——小工具首页复刻版 */
public class HomeActivity extends Activity {

    // ---------- 主题（纯黑 / 冰蓝） ----------
    private void applyTheme() {
        boolean dark = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("dark", false);
        findViewById(R.id.toolRoot).setBackgroundColor(dark ? 0xFF000000 : 0xFFEEF4FF);
        findViewById(R.id.toolColumn).setBackgroundColor(dark ? 0xFF000000 : 0xFFEEF4FF);
        ((TextView) findViewById(R.id.themeToggle)).setText(dark ? "☀️" : "🌙");
        applyTraversal((android.view.ViewGroup) findViewById(R.id.toolColumn), dark);
    }

    private void applyTraversal(android.view.ViewGroup vg, boolean dark) {
        for (int i = 0; i < vg.getChildCount(); i++) {
            View c = vg.getChildAt(i);
            String t = c.getTag() == null ? "" : c.getTag().toString();
            if (c instanceof TextView) {
                if (t.equals("title")) ((TextView) c).setTextColor(dark ? 0xFFFFFFFF : 0xFF1F2329);
                else if (t.equals("sub")) ((TextView) c).setTextColor(dark ? 0xFF9AA3AE : 0xFF8A94A6);
                else if (t.equals("chip")) {
                    ((TextView) c).setBackgroundResource(dark ? R.drawable.bg_chip_dark : R.drawable.bg_chip_off);
                    ((TextView) c).setTextColor(dark ? 0xFF9AA3AE : 0xFF8A94A6);
                }
            }
            if (t.equals("card")) c.setBackgroundResource(dark ? R.drawable.bg_card_dark : R.drawable.bg_card);
            if (c instanceof android.view.ViewGroup) applyTraversal((android.view.ViewGroup) c, dark);
        }
    }

    // ---------- 沉浸全屏 ----------
    private void applyImmersive() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            android.view.WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(android.view.WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
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
        RecManager.init(getApplicationContext());
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                try {
                    java.io.File dir = getExternalFilesDir(null);
                    if (dir == null) dir = getFilesDir();
                    java.io.File f = new java.io.File(dir, "crash.txt");
                    java.io.FileWriter fw = new java.io.FileWriter(f, true);
                    fw.append("\n==== " + new java.util.Date().toString() + " thread=" + t.getName() + " ====\n");
                    fw.append(android.util.Log.getStackTraceString(e));
                    fw.close();
                } catch (Throwable e2) { }
                Thread.setDefaultUncaughtExceptionHandler(null);
                throw new RuntimeException(e);
            }
        });
        setContentView(R.layout.activity_toolbox);
        applyTheme();
        applyImmersive();

        findViewById(R.id.themeToggle).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                boolean dark = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("dark", false);
                getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("dark", !dark).apply();
                applyTheme();
            }
        });

        renderHomeGrid();

    }

    @Override
    protected void onResume() {
        super.onResume();
        renderHomeGrid();
    }

    // ---------- 小卡片网格：资源嗅探/下载/新建快捷键/用户快捷方式（两列排布） ----------
    private static final String EMOJI_SNIFF = "🪩", EMOJI_DL = "➼", EMOJI_SC = "⚡";

    private java.util.ArrayList<String[]> userShortcuts() {
        java.util.ArrayList<String[]> out = new java.util.ArrayList<String[]>();
        try {
            android.content.SharedPreferences sp = getSharedPreferences("home_shortcuts", MODE_PRIVATE);
            int n = sp.getInt("count", 0);
            for (int i = 0; i < n; i++) {
                String raw = sp.getString("s" + i, null);
                if (raw == null) continue;
                String[] p2 = raw.split("\u0001");
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
                e.putString("s" + i, list.get(i)[0] + "\u0001" + list.get(i)[1] + "\u0001" + list.get(i)[2]);
            e.apply();
        } catch (Throwable ignored) {}
    }

    private void renderHomeGrid() {
        android.view.ViewGroup grid = (android.view.ViewGroup) findViewById(R.id.homeGrid);
        if (grid == null) return;
        grid.removeAllViews();
        java.util.ArrayList<View> cells = new java.util.ArrayList<View>();
        cells.add(makeSmallCard(EMOJI_SNIFF, "资源嗅探", new View.OnClickListener() {
            public void onClick(View v) { showSniffDialog(); }
        }));
        cells.add(makeSmallCard(EMOJI_DL, "下载", new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(HomeActivity.this, RecordActivity.class)); }
        }));
        cells.add(makeSmallCard(EMOJI_SC, "新建快捷键", new View.OnClickListener() {
            public void onClick(View v) { showShortcutDialog(); }
        }));
        for (final String[] us : userShortcuts()) {
            String initial = us[0].length() > 0 ? us[0].substring(0, 1) : "?";
            cells.add(makeSmallCard(initial, us[0], new View.OnClickListener() {
                public void onClick(View v) { launchShortcut(us[1], us[2]); }
            }, new View.OnLongClickListener() {
                public boolean onLongClick(View v) {
                    confirmDeleteShortcut(us);
                    return true;
                }
            }));
        }
        // 两列排布
        android.widget.LinearLayout row = null;
        for (int i = 0; i < cells.size(); i++) {
            if (i % 2 == 0) {
                android.widget.LinearLayout r2 = new android.widget.LinearLayout(this);
                r2.setOrientation(android.widget.LinearLayout.HORIZONTAL);
                grid.addView(r2, new android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
                row = r2;
            }
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(0,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i % 2 == 1) lp.leftMargin = (int) (12 * getResources().getDisplayMetrics().density);
            row.addView(cells.get(i), lp);
        }
    }

    private View makeSmallCard(String icon, String name, View.OnClickListener click) {
        return makeSmallCard(icon, name, click, null);
    }

    private View makeSmallCard(String icon, String name, View.OnClickListener click, View.OnLongClickListener longClick) {
        android.widget.LinearLayout card = new android.widget.LinearLayout(this);
        card.setOrientation(android.widget.LinearLayout.VERTICAL);
        card.setGravity(android.view.Gravity.CENTER);
        card.setTag("card");
        int pad = (int) (14 * getResources().getDisplayMetrics().density);
        card.setPadding(pad, pad, pad, pad);
        android.widget.LinearLayout.LayoutParams clp = new android.widget.LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        card.setLayoutParams(clp);
        TextView ic = new TextView(this);
        ic.setText(icon);
        ic.setTextSize(20);
        ic.setGravity(android.view.Gravity.CENTER);
        ic.setBackgroundResource(R.drawable.bg_btn);
        android.widget.LinearLayout.LayoutParams ilp = new android.widget.LinearLayout.LayoutParams(
            (int) (40 * getResources().getDisplayMetrics().density),
            (int) (40 * getResources().getDisplayMetrics().density));
        ic.setLayoutParams(ilp);
        card.addView(ic);
        TextView tv = new TextView(this);
        tv.setText(name);
        tv.setTextSize(13);
        tv.setTag("title");
        tv.setGravity(android.view.Gravity.CENTER);
        tv.setMaxLines(1);
        android.widget.LinearLayout.LayoutParams tlp = new android.widget.LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = (int) (8 * getResources().getDisplayMetrics().density);
        tv.setLayoutParams(tlp);
        card.addView(tv);
        card.setOnClickListener(click);
        if (longClick != null) card.setOnLongClickListener(longClick);
        return card;
    }

    // ---------- 新建快捷键：扁平对话框 ----------
    private EditText scNameEt, scPkgEt, scClsEt;

    private void showShortcutDialog() {
        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);

        scNameEt = new EditText(this);
        scNameEt.setHint("名字（桌面显示）");
        scNameEt.setBackground(null);
        scNameEt.setTextColor(0xFF1F2329);
        scNameEt.setHintTextColor(0xFF9AA3AE);
        box.addView(scNameEt);
        scPkgEt = new EditText(this);
        scPkgEt.setHint("包名（可留空从列表选）");
        scPkgEt.setBackground(null);
        scPkgEt.setTextColor(0xFF1F2329);
        scPkgEt.setHintTextColor(0xFF9AA3AE);
        box.addView(scPkgEt);
        scClsEt = new EditText(this);
        scClsEt.setHint("活动（可留空自动取入口）");
        scClsEt.setBackground(null);
        scClsEt.setTextColor(0xFF1F2329);
        scClsEt.setHintTextColor(0xFF9AA3AE);
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

        android.app.AlertDialog.Builder ab = new android.app.AlertDialog.Builder(this);
        ab.setTitle("新建快捷键").setView(box)
          .setPositiveButton("确认", new android.content.DialogInterface.OnClickListener() {
              public void onClick(android.content.DialogInterface d, int w) { createShortcut(); }
          })
          .setNegativeButton("取消", null).show();
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
            android.widget.Toast.makeText(this, "获取应用列表失败", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void createShortcut() {
        String name = scNameEt.getText().toString().trim();
        String pkg = scPkgEt.getText().toString().trim();
        String cls = scClsEt.getText().toString().trim();
        if (name.length() == 0 || pkg.length() == 0) {
            android.widget.Toast.makeText(this, "名字和包名不能为空", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        // 持久化卡片（出现在"新建快捷键"后面）
        java.util.ArrayList<String[]> list = userShortcuts();
        list.add(new String[]{name, pkg, cls});
        saveUserShortcuts(list);
        renderHomeGrid();
        // 同时申请桌面固定快捷方式（bilux 三级退路同款 Intent）
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
            if (launch == null) { android.widget.Toast.makeText(this, "应用入口无效，仅保存了卡片", android.widget.Toast.LENGTH_LONG).show(); return; }
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
            Intent i = null;
            if (cls.length() > 0) {
                i = new Intent(Intent.ACTION_VIEW).setComponent(new android.content.ComponentName(pkg, cls));
                try { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); return; } catch (Throwable t) { }
                i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(new android.content.ComponentName(pkg, cls));
                try { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); return; } catch (Throwable t) { }
            }
            i = pm.getLaunchIntentForPackage(pkg);
            if (i != null) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); return; }
            android.widget.Toast.makeText(this, "未能打开目标应用", android.widget.Toast.LENGTH_LONG).show();
        } catch (Throwable e) {
            android.widget.Toast.makeText(this, "未能打开目标应用", android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private void confirmDeleteShortcut(final String[] us) {
        new android.app.AlertDialog.Builder(this)
            .setTitle("删除快捷键卡片")
            .setMessage(us[0])
            .setPositiveButton("删除", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) {
                    java.util.ArrayList<String[]> list = userShortcuts();
                    for (int i = 0; i < list.size(); i++) {
                        String[] it = list.get(i);
                        if (it[0].equals(us[0]) && it[1].equals(us[1]) && it[2].equals(us[2])) { list.remove(i); break; }
                    }
                    saveUserShortcuts(list);
                    renderHomeGrid();
                }
            })
            .setNegativeButton("取消", null).show();
    }

    private void showSniffDialog() {
        android.app.Dialog d = new android.app.Dialog(this);
        d.setContentView(R.layout.dialog_sniff);
        android.view.Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        final EditText et = d.findViewById(R.id.et_dialog_url);
        d.findViewById(R.id.btn_paste).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (cm != null && cm.getPrimaryClip() != null
                            && cm.getPrimaryClip().getItemAt(0) != null
                            && cm.getPrimaryClip().getItemAt(0).getText() != null) {
                        et.setText(cm.getPrimaryClip().getItemAt(0).getText().toString().trim());
                    }
                } catch (Exception e) { }
            }
        });
        d.findViewById(R.id.btn_cancel).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { d.dismiss(); }
        });
        d.findViewById(R.id.btn_go).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(HomeActivity.this, SniffActivity.class);
                i.putExtra("input", et.getText().toString().trim());
                startActivity(i);
                d.dismiss();
            }
        });
        d.show();
    }
}
