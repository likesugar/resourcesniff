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
            {"哔哩哔哩", "https://passport.bilibili.com/h5-app/passport/login", "SESSDATA="},
            {"抖音", "https://www.douyin.com/jingxuan", "sessionid="},
            {"YouTube", "https://www.youtube.com/signin?next=%2F&hl=zh-CN", "SAPISID="},
    };

    private boolean dark;
    private TextView[] states = new TextView[3];
    private TextView[] actions = new TextView[3];

    @Override
    private float swX, swY; private boolean swDone;

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case android.view.MotionEvent.ACTION_DOWN:
                swX = ev.getX(); swY = ev.getY(); swDone = false; break;
            case android.view.MotionEvent.ACTION_MOVE: {
                if (!swDone) {
                    float dx = ev.getX() - swX, dy = ev.getY() - swY;
                    if (dx > Math.abs(dy) * 1.4f && dx > 80 * getResources().getDisplayMetrics().density) {
                        swDone = true;
                        finish();
                        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
                    }
                }
                break;
            }
        }
        return super.dispatchTouchEvent(ev);
    }

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
        findViewById(R.id.cardAbout2).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                new android.app.AlertDialog.Builder(SettingsActivity.this)
                    .setTitle("关于 · 卡片功能")
                    .setMessage("🏠 资源嗅探：打开网页嗅探视频/直播流，支持剪贴板链接直进（抖音/B站/YouTube），底部胶囊“播放”可播网络流和本地文件。\n\n⬇️ 下载/记录：管理下载与后台录制任务，结束后合并进记录，可播放/删除。\n\n📡 局域网共享：开启后电脑浏览器可访问手机上的记录与文件。\n\n🔗 快捷方式：为任意应用的活动创建桌面快捷方式，支持从已装应用选择。\n\n⚙️ 本页（设置）：平台账号登录（哔哩哔哩/抖音/YouTube）、自动检测剪贴板开关、下载通知、主题切换。\n\n🌍 主题：右上角 🌙/☀️ 切换纯黑/冰蓝两套主题。")
                    .setPositiveButton("完成", null)
                    .show();
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

        // ---------- 自定义平台 ----------
        {
            String legacy = getSharedPreferences("settings", MODE_PRIVATE).getString("custom_site", "");
            if (legacy.length() > 0) {
                getSharedPreferences("settings", MODE_PRIVATE).edit()
                    .putString("custom_sites", legacy).remove("custom_site").apply();
            }
        }
        refreshCust();
        findViewById(R.id.custAction).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String conf = getSharedPreferences("settings", MODE_PRIVATE).getString("custom_site", "");
                if (conf.length() > 0) {
                    // 已配置：直接登录
                    try {
                        String[] p2 = conf.split("\\u0001", -1);
                        CookieHosts.add(SettingsActivity.this, p2[1]);
                        Intent i = new Intent(SettingsActivity.this, LoginWebActivity.class);
                        i.putExtra("url", p2[1]);
                        i.putExtra("uamode", p2.length > 2 ? p2[2] : "mobile");
                        startActivity(i);
                    } catch (Throwable t) { Toast.makeText(SettingsActivity.this, "打开失败", Toast.LENGTH_SHORT).show(); }
                } else {
                    showCustConfig();
                }
            }
        });
        findViewById(R.id.custState).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showCustConfig(); }
        });
        if (Build.VERSION.SDK_INT >= 21) getWindow().setStatusBarColor(dark ? 0xFF11151D : 0xFFEEF4FF);
    }

    private void refreshCust() {
        try {
            android.view.ViewGroup card1 = (android.view.ViewGroup) findViewById(R.id.card1);
            // 移除旧的自定义行
            for (int i = card1.getChildCount() - 1; i >= 0; i--) {
                View ch = card1.getChildAt(i);
                if (ch.getId() == 777001 + i && ch.getTag() != null && "custrow".equals(ch.getTag().toString())) { }
            }
            for (int i = card1.getChildCount() - 1; i >= 0; i--) {
                View ch = card1.getChildAt(i);
                Object tg = ch.getTag();
                if (tg != null && tg.toString().startsWith("custrow")) card1.removeViewAt(i);
            }
            String list = getSharedPreferences("settings", MODE_PRIVATE).getString("custom_sites", "");
            if (list.length() > 0) {
                int dp8 = (int) (8 * getResources().getDisplayMetrics().density);
                for (final String entry : list.split("\u0002")) {
                    if (entry.length() == 0) continue;
                    String[] p2 = entry.split("\u0001", -1);
                    if (p2.length < 3) continue;
                    android.widget.LinearLayout row = new android.widget.LinearLayout(this);
                    row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
                    row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                    row.setTag("custrow");
                    android.widget.LinearLayout mid = new android.widget.LinearLayout(this);
                    mid.setOrientation(android.widget.LinearLayout.VERTICAL);
                    android.widget.LinearLayout.LayoutParams mlp = new android.widget.LinearLayout.LayoutParams(0, -2, 1f);
                    mid.setLayoutParams(mlp);
                    android.widget.TextView name = new android.widget.TextView(this);
                    name.setText(p2[0]); name.setTextSize(18); name.setTypeface(name.getTypeface(), android.graphics.Typeface.BOLD);
                    name.setTextColor(color(0xFFE8ECF4, 0xFF1F2329));
                    mid.addView(name);
                    android.widget.TextView st = new android.widget.TextView(this);
                    st.setText(p2[2].equals("pc") ? "PC" : "手机"); st.setTextSize(14);
                    st.setTextColor(color(0xFF8A94A6, 0xFF8A94A6)); st.setPadding(0, dp8 / 2, 0, 0);
                    mid.addView(st);
                    row.addView(mid);
                    android.widget.TextView go = new android.widget.TextView(this);
                    go.setText("登录"); go.setTextSize(16); go.setPadding(dp8, dp8, dp8, dp8);
                    go.setTextColor(color(0xFFB4C5FF, 0xFF315CDE));
                    go.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) {
                            try {
                                String[] p3 = entry.split("\u0001", -1);
                                CookieHosts.add(SettingsActivity.this, p3[1]);
                                Intent i = new Intent(SettingsActivity.this, LoginWebActivity.class);
                                i.putExtra("url", p3[1]);
                                i.putExtra("uamode", p3[2]);
                                startActivity(i);
                            } catch (Throwable t) { Toast.makeText(SettingsActivity.this, "打开失败", Toast.LENGTH_SHORT).show(); }
                        }
                    });
                    row.addView(go);
                    android.widget.TextView del = new android.widget.TextView(this);
                    del.setText("删除"); del.setTextSize(16); del.setPadding(dp8, dp8, 0, dp8);
                    del.setTextColor(color(0xFF8A94A6, 0xFF8A94A6));
                    del.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) {
                            String list2 = getSharedPreferences("settings", MODE_PRIVATE).getString("custom_sites", "");
                            StringBuilder sb = new StringBuilder();
                            for (String e2 : list2.split("\u0002")) {
                                if (e2.length() > 0 && !e2.equals(entry)) { if (sb.length() > 0) sb.append('\u0002'); sb.append(e2); }
                            }
                            getSharedPreferences("settings", MODE_PRIVATE).edit().putString("custom_sites", sb.toString()).apply();
                            refreshCust();
                        }
                    });
                    row.addView(del);
                    // 插到 desc1 之前
                    int di = card1.indexOfChild(findViewById(R.id.desc1));
                    card1.addView(row, di > 0 ? di : card1.getChildCount());
                }
            }
            TextView st2 = (TextView) findViewById(R.id.custState);
            TextView ac = (TextView) findViewById(R.id.custAction);
            int n = 0;
            String listN = getSharedPreferences("settings", MODE_PRIVATE).getString("custom_sites", "");
            for (String e2 : listN.split("\u0002")) if (e2.length() > 0) n++;
            st2.setText(n > 0 ? "已配置 " + n + " 个" : "未配置");
            ac.setText("添加");
        } catch (Throwable ignored) {}
        refresh();
    }

    private void showCustConfig() {
        String conf = getSharedPreferences("settings", MODE_PRIVATE).getString("custom_site", "");
        String preName = "", preUrl = "", preMode = "mobile";
        if (conf.length() > 0) { String[] p2 = conf.split("\\u0001", -1); preName = p2[0]; preUrl = p2[1]; preMode = p2.length > 2 ? p2[2] : "mobile"; }
        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        final android.widget.EditText etName = new android.widget.EditText(this);
        etName.setHint("网站名字"); etName.setSingleLine(true); etName.setText(preName);
        box.addView(etName);
        final android.widget.EditText etUrl = new android.widget.EditText(this);
        etUrl.setHint("登录页 https://…"); etUrl.setSingleLine(true); etUrl.setText(preUrl);
        box.addView(etUrl);
        final android.widget.RadioGroup rg = new android.widget.RadioGroup(this);
        rg.setOrientation(android.widget.RadioGroup.HORIZONTAL);
        android.widget.RadioButton rbM = new android.widget.RadioButton(this);
        rbM.setText("手机版"); rbM.setId(101); rbM.setChecked(!preMode.equals("pc"));
        android.widget.RadioButton rbP = new android.widget.RadioButton(this);
        rbP.setText("PC版"); rbP.setId(102); rbP.setChecked(preMode.equals("pc"));
        rg.addView(rbM); rg.addView(rbP);
        box.addView(rg);
        new android.app.AlertDialog.Builder(this)
            .setTitle("自定义平台")
            .setView(box)
            .setPositiveButton("保存", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) {
                    String n = etName.getText().toString().trim();
                    String u = etUrl.getText().toString().trim();
                    if (n.length() == 0 || !u.startsWith("http")) {
                        Toast.makeText(SettingsActivity.this, "名字与登录页URL必填", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String mode = rg.getCheckedRadioButtonId() == 102 ? "pc" : "mobile";
                    String list = getSharedPreferences("settings", MODE_PRIVATE).getString("custom_sites", "");
                    String add = n + "\u0001" + u + "\u0001" + mode;
                    getSharedPreferences("settings", MODE_PRIVATE).edit()
                        .putString("custom_sites", list.length() > 0 ? list + "\u0002" + add : add).apply();
                    refreshCust();
                    Toast.makeText(SettingsActivity.this, "已保存，点「网页登录」登录", Toast.LENGTH_SHORT).show();
                }
            }).setNegativeButton("取消", null).show();
    }

    private void openWeb(int idx) {
        CookieHosts.add(this, PLATFORMS[idx][1]);
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
        int[] titles = {R.id.tvTitle, R.id.t1, R.id.t2, R.id.t3, R.id.t4, R.id.t5, R.id.t6, R.id.tvAboutTitle2};
        for (int id : titles) ((TextView) findViewById(id)).setTextColor(title);
        int[] subs = {R.id.label1, R.id.label2, R.id.biliState, R.id.dyState, R.id.ytState,
                R.id.desc1, R.id.desc2, R.id.desc3, R.id.desc4, R.id.desc5, R.id.desc6, R.id.tvAboutText2};
        for (int id : subs) ((TextView) findViewById(id)).setTextColor(sub);
        int[] accents = {R.id.biliAction, R.id.dyAction, R.id.ytAction, R.id.btnClearAll, R.id.arrow, R.id.btnBack, R.id.custAction};
        for (int id : accents) ((TextView) findViewById(id)).setTextColor(accent);
        int[] cards = {R.id.card1, R.id.card2, R.id.card3, R.id.cardNotify, R.id.cardAbout2};
        for (int id : cards) findViewById(id).setBackgroundColor(card);
        int[] divs = {R.id.div1, R.id.div2, R.id.div3};
        for (int id : divs) findViewById(id).setBackgroundColor(div);
        if (Build.VERSION.SDK_INT >= 23) {
            getWindow().getDecorView().setSystemUiVisibility(
                    dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }
}
