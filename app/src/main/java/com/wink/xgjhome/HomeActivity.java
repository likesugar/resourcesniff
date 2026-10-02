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
    @Override
    protected void onDestroy() {
        if (isFinishing()) {   // 应用退出：局域网共享随进程结束
            LanShareServer.stop();
            android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.cancel(1001);
        }
        super.onDestroy();
    }

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

        // 复刻壳：卡片 → 嗅探弹窗
        findViewById(R.id.cardPlayer).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showSniffDialog(); }
        });
        findViewById(R.id.cardDownload).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(HomeActivity.this, RecordActivity.class));
            }
        });
        findViewById(R.id.lanToggle).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (LanShareServer.isRunning()) {
                    LanShareServer.stop();
                    android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                    nm.cancel(1001);
                    android.widget.Toast.makeText(HomeActivity.this, "局域网共享已关闭", android.widget.Toast.LENGTH_SHORT).show();
                } else {
                    if (android.os.Build.VERSION.SDK_INT >= 33 &&
                        checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 900);
                    }
                    LanShareServer.start();
                    String url = "http://" + LanShareServer.localIp() + ":" + LanShareServer.getPort();
                    android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                    if (android.os.Build.VERSION.SDK_INT >= 26) {
                        nm.createNotificationChannel(new android.app.NotificationChannel("lan", "局域网共享", android.app.NotificationManager.IMPORTANCE_LOW));
                    }
                    android.app.Notification.Builder nb = android.os.Build.VERSION.SDK_INT >= 26
                        ? new android.app.Notification.Builder(HomeActivity.this, "lan")
                        : new android.app.Notification.Builder(HomeActivity.this);
                    nb.setSmallIcon(android.R.drawable.ic_menu_share)
                      .setContentTitle("局域网共享已开启")
                      .setContentText("电脑打开 " + url)
                      .setStyle(new android.app.Notification.BigTextStyle().bigText("电脑浏览器打开 " + url + " 可查看并打开记录中的链接"))
                      .setOngoing(true);
                    nm.notify(1001, nb.build());
                }
            }
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
