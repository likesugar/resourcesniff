package com.wink.xgjhome;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;

/** 设置页：原生 View/XML 复刻（平台账号网页登录 / 剪贴板开关 / 下载通知） */
public class SettingsActivity extends Activity {

    private static final String[][] PLATFORMS = {
            {"哔哩哔哩", "https://passport.bilibili.com/h5-app/login", "SESSDATA="},
            {"抖音", "https://www.douyin.com/", "sessionid="},
            {"YouTube", "https://m.youtube.com/", "SAPISID="},
    };

    private boolean dark;
    private TextView[] states = new TextView[3];
    private TextView[] actions = new TextView[3];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        dark = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("dark", false);
        applyTheme();

        states[0] = findViewById(R.id.biliState); states[1] = findViewById(R.id.dyState); states[2] = findViewById(R.id.ytState);
        actions[0] = findViewById(R.id.biliAction); actions[1] = findViewById(R.id.dyAction); actions[2] = findViewById(R.id.ytAction);

        for (int i = 0; i < 3; i++) {
            final int idx = i;
            actions[i].setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (loggedIn(idx)) {   // 已登录 → 管理登录（再次打开网页可改号/退出）
                        openWeb(idx);
                    } else {
                        openWeb(idx);
                    }
                }
            });
        }
        findViewById(R.id.btnClearAll).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    android.webkit.CookieManager cm = android.webkit.CookieManager.getInstance();
                    cm.setAcceptCookie(true);
                    cm.removeAllCookies(null);
                    cm.flush();
                    refresh();
                    Toast.makeText(SettingsActivity.this, "已清除全部网页登录", Toast.LENGTH_SHORT).show();
                } catch (Throwable t) {
                    Toast.makeText(SettingsActivity.this, "清除失败: " + t, Toast.LENGTH_SHORT).show();
                }
            }
        });
        android.widget.Switch sw = findViewById(R.id.swClipboard);
        sw.setChecked(getSharedPreferences("settings", MODE_PRIVATE).getBoolean("clipboard", true));
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) {
                getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("clipboard", on).apply();
            }
        });
        findViewById(R.id.cardNotify).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    Intent i = new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName());
                    startActivity(i);
                } catch (Throwable t) {
                    Toast.makeText(SettingsActivity.this, "打开失败", Toast.LENGTH_SHORT).show();
                }
            }
        });
        findViewById(R.id.btnBack).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { finish(); }
        });
        if (Build.VERSION.SDK_INT >= 21) getWindow().setStatusBarColor(dark ? 0xFF11151D : 0xFFEEF4FF);
    }

    private void openWeb(int idx) {
        Intent i = new Intent(this, LoginWebActivity.class);
        i.putExtra("url", PLATFORMS[idx][1]);
        i.putExtra("domain", platformDomain(idx));
        startActivity(i);
    }

    private static String platformDomain(int idx) {
        if (idx == 0) return "https://www.bilibili.com";
        if (idx == 1) return "https://www.douyin.com";
        return "https://www.youtube.com";
    }

    private boolean loggedIn(int idx) {
        try {
            String c = android.webkit.CookieManager.getInstance().getCookie(platformDomain(idx));
            return c != null && c.contains(PLATFORMS[idx][2]);
        } catch (Throwable t) { return false; }
    }

    private void refresh() {
        for (int i = 0; i < 3; i++) {
            boolean in = loggedIn(i);
            states[i].setText(in ? "已登录" : "未登录");
            actions[i].setText(in ? "管理登录" : "网页登录");
        }
    }

    @Override
    protected void onResume() { super.onResume(); refresh(); }

    private int color(int darkC, int lightC) { return dark ? darkC : lightC; }

    private void applyTheme() {
        int bg = color(0xFF11151D, 0xFFEEF4FF);
        int card = color(0xFF191F2E, 0xFFFFFFFF);
        int title = color(0xFFE8ECF4, 0xFF1F2329);
        int sub = color(0xFF8A94A6, 0xFF8A94A6);
        int accent = color(0xFFB4C5FF, 0xFF315CDE);
        int div = color(0xFF2A3142, 0xFFEDEFF3);
        findViewById(R.id.settingsRoot).setBackgroundColor(bg);
        int[] titles = {R.id.tvTitle, R.id.t1, R.id.t2, R.id.t3, R.id.t4, R.id.t5, R.id.t6};
        for (int id : titles) ((TextView) findViewById(id)).setTextColor(title);
        int[] subs = {R.id.label1, R.id.label2, R.id.biliState, R.id.dyState, R.id.ytState,
                R.id.desc1, R.id.desc2, R.id.desc3, R.id.desc4, R.id.desc5, R.id.desc6};
        for (int id : subs) ((TextView) findViewById(id)).setTextColor(sub);
        int[] accents = {R.id.biliAction, R.id.dyAction, R.id.ytAction, R.id.btnClearAll, R.id.arrow, R.id.btnBack};
        for (int id : accents) ((TextView) findViewById(id)).setTextColor(accent);
        int[] divs = {R.id.div1, R.id.div2, R.id.div3};
        for (int id : divs) findViewById(id).setBackgroundColor(div);
        if (Build.VERSION.SDK_INT >= 23) {
            getWindow().getDecorView().setSystemUiVisibility(
                    dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }
}
