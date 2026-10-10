package com.wink.xgjhome;

import android.app.Activity;
import android.content.ClipboardManager;
import android.widget.Toast;
import android.content.DialogInterface;
import android.content.Intent;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;


/** 首页：工具箱（纯黑/冰蓝主题切换 · 全屏）——小工具首页复刻版 */
public class HomeActivity extends Activity {

    // ---------- 主题（纯黑 / 冰蓝） ----------
    private void updateMedStatus() {
        try {
            TextView st = findViewById(R.id.medStatus);
            if (st == null) return;
            org.json.JSONArray plans = MedPlanActivity.load(this);
            if (plans.length() == 0) {
                st.setText("🌱 还没有计划，点进去添加");
                return;
            }
            String next = null;
            String now = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(new java.util.Date());
            for (int i = 0; i < plans.length(); i++) {
                org.json.JSONObject o = plans.optJSONObject(i);
                if (o == null) continue;
                org.json.JSONArray ts = o.optJSONArray("times");
                if (ts == null) continue;
                for (int t = 0; t < ts.length(); t++) {
                    String tt = ts.optString(t);
                    if (tt.compareTo(now) >= 0 && (next == null || tt.compareTo(next) < 0)) next = tt;
                }
            }
            if (next == null) {
                // 今天都过了, 找全天最早
                for (int i = 0; i < plans.length(); i++) {
                    org.json.JSONObject o = plans.optJSONObject(i);
                    org.json.JSONArray ts = o == null ? null : o.optJSONArray("times");
                    if (ts == null) continue;
                    for (int t = 0; t < ts.length(); t++) {
                        String tt = ts.optString(t);
                        if (next == null || tt.compareTo(next) < 0) next = tt;
                    }
                }
                if (next != null) { st.setText("💊 " + plans.length() + " 个计划 · 明天 " + next); return; }
            }
            st.setText("💊 " + plans.length() + " 个计划 · 下次 " + (next == null ? "--:--" : next));
        } catch (Throwable ignored) {}
    }

    private void setupCalendar() {
        findViewById(R.id.calendarCard).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(HomeActivity.this, CalendarActivity.class)); }
        });
        findViewById(R.id.cardMed).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(HomeActivity.this, MedPlanActivity.class)); }
        });
    }

    private final android.os.Handler calTick = new android.os.Handler();
    private final Runnable calTickRun = new Runnable() { public void run() {
        calTick.postDelayed(this, 30_000);
    }};

    private float swX, swY; private boolean swDone;


    private float dp2(float v) { return v * getResources().getDisplayMetrics().density; }

    private boolean mediaPage = false;
    private CapsuleBottomBar bottomBar;
    private android.view.GestureDetector pageGesture;
    private static final int[] MEDIA_CARDS = {R.id.cardPlayer, R.id.cardDownload, R.id.cardVideoDl, R.id.cardXhs};

    /** 首页/媒体管理 胶囊页切换 */
    private void switchPage(boolean media) {
        if (mediaPage == media) return;
        applyPageVisibility(media);
    }

    /** 无守卫的页面可见性应用(启动初始化也走这里) */
    private void applyPageVisibility(boolean media) {
        mediaPage = media;
        if (bottomBar != null) bottomBar.setActive(media ? 1 : 0);
        java.util.List<View> cards = new java.util.ArrayList<View>();
        collectCards((android.view.ViewGroup) findViewById(R.id.toolColumn), cards);
        for (View c : cards) {
            boolean isMedia = false;
            for (int id : MEDIA_CARDS) if (c.getId() == id) { isMedia = true; break; }
            c.setVisibility(isMedia == media ? View.VISIBLE : View.GONE);
        }
    }

    private void collectCards(android.view.ViewGroup vg, java.util.List<View> out) {
        for (int i = 0; i < vg.getChildCount(); i++) {
            View c = vg.getChildAt(i);
            if (c instanceof android.view.ViewGroup) collectCards((android.view.ViewGroup) c, out);
            if (c.getTag() != null && "card".equals(c.getTag().toString())) out.add(c);
        }
    }

    private void applyTheme() {
        boolean dark = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("dark", false);
        findViewById(R.id.toolRoot).setBackgroundColor(dark ? 0xFF000000 : 0xFFEEF4FF);
        // 底部胶囊导航（SmoothBottomBar 风格）
        android.view.ViewGroup root = (android.view.ViewGroup) findViewById(R.id.toolRoot);
        for (int i = root.getChildCount() - 1; i >= 0; i--) {
            if (root.getChildAt(i) instanceof CapsuleBottomBar) root.removeViewAt(i);
        }
        bottomBar = new CapsuleBottomBar(this, dark, new CapsuleBottomBar.OnItem() {
                public void onItem(int idx) {
                    if (idx == 1) switchPage(true);
                    else if (idx == 2) startActivity(new Intent(HomeActivity.this, SettingsActivity.class));
                    else if (idx == 3) showPlayChoice();
                    else switchPage(false);
                }
            });
        bottomBar.setTag("bottombar");
        root.addView(bottomBar);
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
                else if (t.equals("icon")) c.setBackgroundResource(dark ? R.drawable.bg_btn_md3_dark : R.drawable.bg_btn_md3);
                else if (t.equals("chip")) {
                    ((TextView) c).setBackgroundResource(dark ? R.drawable.bg_chip_dark : R.drawable.bg_chip_off);
                    ((TextView) c).setTextColor(dark ? 0xFF9AA3AE : 0xFF8A94A6);
                }
            }
            if (t.equals("card")) {
                boolean oled = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("oled", true);
                c.setBackgroundResource(dark ? (oled ? R.drawable.bg_card_oled : R.drawable.bg_card_md3_dark) : R.drawable.bg_card_md3);
            }
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
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        if (pageGesture != null) pageGesture.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        RecManager.init(getApplicationContext());
        pageGesture = new android.view.GestureDetector(this, new android.view.GestureDetector.SimpleOnGestureListener() {
            public boolean onFling(android.view.MotionEvent e1, android.view.MotionEvent e2, float vx, float vy) {
                if (e1 == null || e2 == null) return false;
                float dx = e2.getX() - e1.getX(), dy = e2.getY() - e1.getY();
                if (Math.abs(dx) > 150 && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                    switchPage(dx < 0);
                    return true;
                }
                return false;
            }
        });
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
        Immersive.hide(this);

        // 启动即创建通知渠道 + 申请通知权限(Android 13+)
        try { MedPlanActivity.ensureChannel(this); } catch (Throwable ignored) {}
        try { MedWatchService.ensure(this); } catch (Throwable ignored) {}
        if (android.os.Build.VERSION.SDK_INT >= 33
            && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 990);
        }

        if (getIntent() != null && getIntent().getBooleanExtra("goto_play", false)) showPlayChoice();
        setupCalendar();
        applyTheme();
        applyImmersive();

        applyPageVisibility(false); // 初始: 首页态, 隐藏媒体卡

        findViewById(R.id.themeToggle).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                boolean dark = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("dark", false);
                getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("dark", !dark).apply();
                applyTheme();
            }
        });

        // 复刻壳：卡片 → 嗅探弹窗；剪贴板有链接则直接进嗅探
        findViewById(R.id.cardPlayer).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String clip = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("clipboard", true) ? clipUrl() : null;
                if (clip != null && !supportedSite(clip)) clip = null;
                if (clip != null) {
                    Intent i = new Intent(HomeActivity.this, SniffActivity.class);
                    i.putExtra("input", clip);
                    startActivity(i);
                } else {
                    showSniffDialog();
                }
            }
        });
        findViewById(R.id.cardDownload).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(HomeActivity.this, RecordActivity.class)); }
        });
        findViewById(R.id.cardVideoDl).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showVdlDialog(); }
        });
        findViewById(R.id.cardXhs).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(HomeActivity.this, XhsActivity.class)); }
        });
        findViewById(R.id.cardSpider).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(HomeActivity.this, SpiderActivity.class)); }
        });
        findViewById(R.id.cardChat).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(HomeActivity.this, ChatActivity.class)); }
        });
        findViewById(R.id.cardWeb2Apk).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(HomeActivity.this, Web2ApkActivity.class)); }
        });
        findViewById(R.id.cardEnt).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(HomeActivity.this, EntertainmentActivity.class)); }
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
        calTick.post(calTickRun);
        updateMedStatus();
        try { MedPlanActivity.checkAndNotifyDue(this); } catch (Throwable ignored) {}
    }

    @Override
    protected void onPause() {
        super.onPause();
        calTick.removeCallbacks(calTickRun);
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
        status.setTextColor(0xFFFFFFFF);
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
        swLabel.setTextColor(0xFFFFFFFF);
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

    /** 只允许抖音/B站/YouTube 直进 */
    private boolean supportedSite(String u) {
        if (u == null) return false;
        return u.contains("douyin.com") || u.contains("b23.tv") || u.contains("bilibili.com")
                || u.contains("youtube.com") || u.contains("youtu.be");
    }

    /** 播放入口选择：网络流 / 本地文件 */
    private void showPlayChoice() {
        new android.app.AlertDialog.Builder(this)
            .setTitle("播放")
            .setItems(new String[]{"网络流（输入链接）", "本地文件（选择视频）"}, new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) {
                    if (w == 0) showUrlInput();
                    else pickLocal();
                }
            }).show();
    }

    private void showUrlInput() {
        final android.widget.EditText et = new android.widget.EditText(this);
        et.setHint("http(s)://");
        et.setSingleLine(true);
        new android.app.AlertDialog.Builder(this)
            .setTitle("网络流")
            .setView(et)
            .setPositiveButton("播放", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) {
                    playAny(et.getText().toString().trim());
                }
            }).setNegativeButton("取消", null).show();
    }

    private static final int REQ_PICK_LOCAL = 9911;

    private void pickLocal() {
        try {
            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
            i.setType("video/*");
            i.addCategory(android.content.Intent.CATEGORY_OPENABLE);
            startActivityForResult(android.content.Intent.createChooser(i, "选择视频"), REQ_PICK_LOCAL);
        } catch (Throwable e) {
            Toast.makeText(this, "无法打开文件选择器", Toast.LENGTH_SHORT).show();
        }
    }

    private void playAny(String url) {
        if (url == null || url.length() == 0) return;
        Intent i = new Intent(this, NativePlayerActivity.class);
        i.putExtra("url", url);
        i.putExtra("title", "播放");
        i.putExtra("kernel", "native");
        startActivity(i);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        if (requestCode == REQ_PICK_LOCAL && resultCode == RESULT_OK && data != null && data.getData() != null) {
            android.net.Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Throwable ignored) {}
            Intent i = new Intent(this, NativePlayerActivity.class);
            i.putExtra("url", uri.toString());
            i.putExtra("title", "本地视频");
            i.putExtra("kernel", "native");
            i.setData(uri);
            i.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /** 剪贴板里抽 http 链接，没有返回 null */
    private String clipUrl() {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm == null || cm.getPrimaryClip() == null || cm.getPrimaryClip().getItemAt(0) == null) return null;
            CharSequence t = cm.getPrimaryClip().getItemAt(0).getText();
            if (t == null) return null;
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("https?://\\S+").matcher(t.toString());
            return m.find() ? m.group() : null;
        } catch (Throwable e) { return null; }
    }

    static String dyAutoResolve(String text, String savedId) {
        if (text == null || text.length() == 0) {
            return savedId.length() > 0 ? "https://live.douyin.com/" + savedId : null;
        }
        String t = text.trim();
        if (t.matches("[0-9]{6,20}") || (t.matches("[0-9A-Za-z]{6,24}") && t.matches(".*[A-Za-z].*") && t.matches(".*[0-9].*")))
            return "https://live.douyin.com/" + t;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("live\\.douyin\\.com/([0-9A-Za-z]{6,24})").matcher(t);
        if (m.find()) return "https://live.douyin.com/" + m.group(1);
        m = java.util.regex.Pattern.compile("(?:search/|keyword=)([0-9]{6,20}|(?=.*[A-Za-z])(?=.*[0-9])[0-9A-Za-z]{6,24})").matcher(t);
        if (m.find()) return "https://live.douyin.com/" + m.group(1);
        return null;
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
        // 抖音直播开关(记忆)
        final TextView dyT = d.findViewById(R.id.tv_douyin_live);
        final android.content.SharedPreferences spf = getSharedPreferences("settings", MODE_PRIVATE);
        final android.text.TextWatcher repaint = new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence c, int a, int b, int q) {}
            public void onTextChanged(CharSequence c, int a, int b, int q) {}
            public void afterTextChanged(android.text.Editable e) {
                boolean on = spf.getBoolean("douyin_live", false);
                dyT.setText("抖音直播 " + (on ? "开" : "关"));
                dyT.setTextColor(on ? 0xFF25D0A5 : 0xAAFFFFFF);
            }
        };
        repaint.afterTextChanged(null);
        // 开关开着: 自动识别剪贴板里的抖音号或自动填上次直播间
        if (spf.getBoolean("douyin_live", false)) {
            String clipText = null;
            try {
                ClipboardManager cm0 = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm0 != null && cm0.getPrimaryClip() != null && cm0.getPrimaryClip().getItemAt(0) != null
                        && cm0.getPrimaryClip().getItemAt(0).getText() != null)
                    clipText = cm0.getPrimaryClip().getItemAt(0).getText().toString().trim();
            } catch (Throwable ignored) {}
            String auto = dyAutoResolve(clipText, spf.getString("dyid", ""));
            if (auto != null) {
                et.setText(auto);
                et.setHint("");
                // 自动开始嗅探
                final android.app.Dialog dAuto = d;
                dAuto.findViewById(R.id.btn_go).postDelayed(new Runnable() {
                    public void run() {
                        try { if (dAuto.isShowing()) dAuto.findViewById(R.id.btn_go).performClick(); } catch (Throwable ignored) {}
                    }
                }, 400);
            }
        }
        dyT.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                spf.edit().putBoolean("douyin_live", !spf.getBoolean("douyin_live", false)).apply();
                repaint.afterTextChanged(null);
                if (spf.getBoolean("douyin_live", false)) {
                    String saved = spf.getString("dyid", "");
                    if (saved.length() > 0) et.setHint("已记忆直播间 " + saved);
                }
            }
        });
        d.findViewById(R.id.btn_paste).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (cm != null && cm.getPrimaryClip() != null
                            && cm.getPrimaryClip().getItemAt(0) != null
                            && cm.getPrimaryClip().getItemAt(0).getText() != null) {
                        String pasted = cm.getPrimaryClip().getItemAt(0).getText().toString().trim();
                        boolean dyOn0 = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("douyin_live", false);
                        if (dyOn0) {
                            String auto = dyAutoResolve(pasted, getSharedPreferences("settings", MODE_PRIVATE).getString("dyid", ""));
                            if (auto != null) { pasted = auto; getSharedPreferences("settings", MODE_PRIVATE).edit().putString("dyid", auto.replace("https://live.douyin.com/","")).apply(); }
                        }
                        et.setText(pasted);
                        if (pasted.startsWith("https://live.douyin.com/")) {
                            final android.app.Dialog dP = d;
                            d.findViewById(R.id.btn_go).postDelayed(new Runnable() {
                                public void run() {
                                    try { if (dP.isShowing()) dP.findViewById(R.id.btn_go).performClick(); } catch (Throwable ignored) {}
                                }
                            }, 300);
                        }
                    }
                } catch (Exception e) { }
            }
        });
        d.findViewById(R.id.btn_cancel).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { d.dismiss(); }
        });
        d.findViewById(R.id.btn_go).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String input = et.getText().toString().trim();
                boolean dyOn = spf.getBoolean("douyin_live", false);
                if (dyOn) {
                    String auto = dyAutoResolve(input, spf.getString("dyid", ""));
                    if (auto != null) {
                        input = auto;
                        String id = input.replace("https://live.douyin.com/", "");
                        spf.edit().putString("dyid", id).apply();
                    }
                }
                Intent i = new Intent(HomeActivity.this, SniffActivity.class);
                i.putExtra("input", input);
                startActivity(i);
                d.dismiss();
            }
        });
        d.show();
    }

    /** 视频下载专用弹窗: [进入]仅进页, [下载]带链接直开 */
    private void showVdlDialog() {
        android.app.Dialog d = new android.app.Dialog(this);
        d.setContentView(R.layout.dialog_vdl);
        android.view.Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        final EditText et = d.findViewById(R.id.et_vdl_url);
        d.findViewById(R.id.btn_vdl_paste).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (cm != null && cm.getPrimaryClip() != null
                            && cm.getPrimaryClip().getItemAt(0) != null
                            && cm.getPrimaryClip().getItemAt(0).getText() != null) {
                        String pasted = cm.getPrimaryClip().getItemAt(0).getText().toString().trim();
                        boolean dyOn0 = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("douyin_live", false);
                        if (dyOn0) {
                            String auto = dyAutoResolve(pasted, getSharedPreferences("settings", MODE_PRIVATE).getString("dyid", ""));
                            if (auto != null) { pasted = auto; getSharedPreferences("settings", MODE_PRIVATE).edit().putString("dyid", auto.replace("https://live.douyin.com/","")).apply(); }
                        }
                        et.setText(pasted);
                        if (pasted.startsWith("https://live.douyin.com/")) {
                            final android.app.Dialog dP = d;
                            d.findViewById(R.id.btn_go).postDelayed(new Runnable() {
                                public void run() {
                                    try { if (dP.isShowing()) dP.findViewById(R.id.btn_go).performClick(); } catch (Throwable ignored) {}
                                }
                            }, 300);
                        }
                    }
                } catch (Exception e) { }
            }
        });
        d.findViewById(R.id.btn_vdl_cancel).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(HomeActivity.this, VideoDlActivity.class));
                d.dismiss();
            }
        });
        d.findViewById(R.id.btn_vdl_go).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(HomeActivity.this, VideoDlActivity.class);
                i.putExtra("url", et.getText().toString().trim());
                startActivity(i);
                d.dismiss();
            }
        });
        d.show();
    }


}
