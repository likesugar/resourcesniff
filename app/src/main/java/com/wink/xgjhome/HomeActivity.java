package com.wink.xgjhome;

import android.app.Activity;
import android.content.ClipboardManager;
import android.content.Intent;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.graphics.Typeface;
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

    private void restylePill(TextView[] segs, int sel) {
        float dm = getResources().getDisplayMetrics().density;
        for (int i = 0; i < segs.length; i++) {
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setCornerRadius(50 * dm);
            bg.setColor(i == sel ? 0xFFE7EDFD : 0xFFF2F4F8);
            segs[i].setBackground(bg);
            segs[i].setTextColor(i == sel ? 0xFF315CDE : 0xFF8A919E);
            segs[i].setTypeface(i == sel ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
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

        // 复刻壳：卡片 → 嗅探弹窗
        findViewById(R.id.cardPlayer).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showSniffDialog(); }
        });
        findViewById(R.id.cardDownload).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(HomeActivity.this, RecordActivity.class));
            }
        });
        // 底部胶囊导航（样式=资源下载页顶部chips）：全部/视频/录制/进行中/已完成 → 跳资源下载页
        try {
            final String[] pillTabs = {"全部", "视频", "录制", "进行中", "已完成"};
            final TextView[] pillSegs = new TextView[5];
            float dm = getResources().getDisplayMetrics().density;
            LinearLayout pill = new LinearLayout(this);
            pill.setOrientation(LinearLayout.HORIZONTAL);
            android.graphics.drawable.GradientDrawable pillBg = new android.graphics.drawable.GradientDrawable();
            pillBg.setCornerRadius(50 * dm);
            pillBg.setColor(0xFFFFFFFF);
            pill.setBackground(pillBg);
            pill.setPadding(12, 12, 12, 12);
            pill.setElevation(8f);
            android.widget.FrameLayout flRoot = (android.widget.FrameLayout) findViewById(R.id.toolRoot);
            android.widget.FrameLayout.LayoutParams plp = new android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT, android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                    android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);
            plp.bottomMargin = (int)(18 * dm);
            pill.setLayoutParams(plp);
            for (int i = 0; i < 5; i++) {
                final int idx = i;
                TextView tv = new TextView(this);
                tv.setText(pillTabs[i]);
                tv.setTextSize(14);
                tv.setGravity(android.view.Gravity.CENTER);
                tv.setPadding(0, 24, 0, 24);
                LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams((int)(76 * dm), (int)(44 * dm));
                if (i < 4) sp.rightMargin = (int)(6 * dm);
                tv.setLayoutParams(sp);
                tv.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        restylePill(pillSegs, idx);
                        Intent i2 = new Intent(HomeActivity.this, RecordActivity.class);
                        i2.putExtra("tab", idx);
                        startActivity(i2);
                    }
                });
                pillSegs[i] = tv;
                pill.addView(tv);
            }
            restylePill(pillSegs, 0);
            flRoot.addView(pill);
            android.view.View sv = findViewById(R.id.toolRoot).findViewWithTag("scroll");
            if (sv == null) {
                android.view.ViewGroup vg = (android.view.ViewGroup) findViewById(R.id.toolColumn);
                for (int i2 = 0; i2 < vg.getChildCount(); i2++) {
                    android.view.View ch = vg.getChildAt(i2);
                    if (ch instanceof android.widget.ScrollView) { sv = ch; break; }
                }
            }
            if (sv != null) sv.setPadding(sv.getPaddingLeft(), sv.getPaddingTop(), sv.getPaddingRight(), (int)(86 * dm));
        } catch (Throwable t) { }

        findViewById(R.id.cardVideoDl).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try { startActivity(new Intent(HomeActivity.this, com.daxiaamu.dbdown.MainActivity.class)); }
                catch (Throwable t) {
                    android.widget.Toast.makeText(HomeActivity.this, "打开失败: " + t, android.widget.Toast.LENGTH_LONG).show();
                }
            }
        });
        findViewById(R.id.cardSettings).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    Intent i = new Intent(HomeActivity.this, com.daxiaamu.dbdown.MainActivity.class);
                    i.putExtra("settings", true);
                    startActivity(i);
                } catch (Throwable t) {
                    android.widget.Toast.makeText(HomeActivity.this, "打开失败: " + t, android.widget.Toast.LENGTH_LONG).show();
                }
            }
        });
        findViewById(R.id.lanToggle).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showLanDialog(); }
        });
        findViewById(R.id.cardShortcut).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try { startActivity(new Intent(HomeActivity.this, ShortcutActivity.class)); }
                catch (Throwable t) {
                    android.widget.Toast.makeText(HomeActivity.this, "打开失败: " + t, android.widget.Toast.LENGTH_LONG).show();
                }
            }
        });

    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    /** 🗄️ 局域网共享弹窗：开关 + 电脑访问地址 */
    private void showLanDialog() {
        boolean on = LanShareServer.isRunning();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (22 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);

        TextView status = new TextView(this);
        status.setTextSize(14);
        status.setTextColor(0xFF444444);
        String url = "http://" + LanShareServer.localIp() + ":" + LanShareServer.getPort();
        if (on) {
            status.setText("已开启，电脑浏览器访问：\n" + url + "\n（可查看并打开记录中的链接）");
        } else {
            status.setText("已关闭。开启后，同一 WiFi 下\n电脑浏览器可访问并打开记录中的链接。");
        }
        box.addView(status);

        LinearLayout swRow = new LinearLayout(this);
        swRow.setOrientation(LinearLayout.HORIZONTAL);
        swRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        swRow.setPadding(0, pad / 2, 0, 0);
        TextView swLabel = new TextView(this);
        swLabel.setText("局域网共享");
        swLabel.setTextSize(16);
        swLabel.setTextColor(0xFF222222);
        swRow.addView(swLabel, new LinearLayout.LayoutParams(0, -2, 1f));
        final android.widget.Switch sw = new android.widget.Switch(this);
        sw.setChecked(on);
        swRow.addView(sw);
        box.addView(swRow);

        sw.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (sw.isChecked()) {
                    if (android.os.Build.VERSION.SDK_INT >= 33
                        && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED
                        && !getSharedPreferences("settings", MODE_PRIVATE).getBoolean("permAsked", false)) {
                        getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("permAsked", true).apply();
                        requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 900);
                    }
                    try { LiveProxy.start(); } catch (Throwable ignored) {}
                    LanShareServer.start();  // 同步绑定：返回时端口必就绪
                    startService(new Intent(HomeActivity.this, LanShareService.class));
                    status.setText("已开启，电脑浏览器访问：\nhttp://" + LanShareServer.localIp() + ":" + LanShareServer.getPort());
                } else {
                    stopService(new Intent(HomeActivity.this, LanShareService.class));
                    status.setText("已关闭。");
                }
            }
        });

        new android.app.AlertDialog.Builder(this)
            .setTitle("🗄️ 局域网共享")
            .setView(box)
            .setPositiveButton("完成", null)
            .show();
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
