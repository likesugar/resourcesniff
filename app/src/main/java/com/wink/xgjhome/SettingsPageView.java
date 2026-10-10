package com.wink.xgjhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** 设置页：平台账号(动态渲染) / 剪贴板开关 / 下载通知 / OLED 主题 */
public class SettingsPageView extends android.widget.FrameLayout {

    private final Activity host;

    /** 内置平台: 名称 / 登录页 / Cookie特征 / 站点域 / 圆标emoji / 圆标色(暗) */
    private static final String[][] PLATFORMS = {
            {"哔哩哔哩", "https://passport.bilibili.com/h5-app/passport/login", "SESSDATA=", "https://www.bilibili.com", "📺", "0xFFFB7299"},
            {"抖音", "https://www.douyin.com/jingxuan", "sessionid=", "https://www.douyin.com", "🎵", "0xFF161823"},
            {"YouTube", "https://www.youtube.com/signin?next=%2F&hl=zh-CN", "SAPISID=", "https://www.youtube.com", "▶️", "0xFFFF0033"},
            {"小红书", "https://www.xiaohongshu.com", "web_session=", "https://www.xiaohongshu.com", "📕", "0xFFFF2442"},
    };

    private boolean dark;
    private LinearLayout platList;
    private final List<View> themed = new ArrayList<>();

    public SettingsPageView(Activity hostActivity) {
        super(hostActivity);
        host = hostActivity;
        android.view.LayoutInflater.from(host).inflate(R.layout.activity_settings, this, true);
        findViewById(R.id.btnBack).setVisibility(GONE); // 内嵌页无返回键
        // 内层滚动区加底部留白给首页胶囊条
        android.view.ViewGroup root = (android.view.ViewGroup) getChildAt(0);
        for (int i = 0; i < root.getChildCount(); i++) {
            View c = root.getChildAt(i);
            if (c instanceof android.widget.ScrollView) {
                c.setPadding(c.getPaddingLeft(), c.getPaddingTop(), c.getPaddingRight(), (int) (170 * getResources().getDisplayMetrics().density));
                break;
            }
        }

        dark = host.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).getBoolean("dark", false);
        platList = (LinearLayout) findViewById(R.id.platList);
        android.widget.Switch swC = (android.widget.Switch) findViewById(R.id.swClipboard);
        swC.setChecked(host.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).getBoolean("clipboard", true));
        swC.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                host.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).edit().putBoolean("clipboard", on).apply();
                Toast.makeText(host, on ? "已开启自动检测剪贴板" : "已关闭自动检测剪贴板", Toast.LENGTH_SHORT).show();
            }
        });
        findViewById(R.id.cardOled).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                boolean oled = host.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).getBoolean("oled", false);
                host.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).edit().putBoolean("oled", !oled).apply();
                dark = host.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).getBoolean("dark", false);
                applyTheme();
            }
        });
        findViewById(R.id.cardNotify).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    android.content.Intent it = new android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                    it.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, host.getPackageName());
                    host.startActivity(it);
                } catch (Throwable t) {
                    Toast.makeText(host, "无法打开通知设置", Toast.LENGTH_SHORT).show();
                }
            }
        });
        findViewById(R.id.cardAbout2).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                new AlertDialog.Builder(host)
                        .setTitle("关于")
                        .setMessage("🏠 资源嗅探：打开网页嗅探视频/直播流，支持剪贴板链接直进（抖音/B站/YouTube），底部胶囊“播放”可播网络流和本地文件。\n\n⬇️ 下载/记录：管理下载与后台录制任务，结束后合并进记录，可播放/删除。\n\n📡 局域网共享：开启后电脑浏览器可访问手机上的记录与文件。\n\n🔗 快捷方式：为任意应用的活动创建桌面快捷方式，支持从已装应用选择。\n\n⚙️ 本页（设置）：平台账号登录（哔哩哔哩/抖音/YouTube）、自动检测剪贴板开关、下载通知、主题切换。\n\n🌍 主题：右上角 🌙/☀️ 切换纯黑/冰蓝两套主题。")
                        .setPositiveButton("知道了", null)
                        .show();
            }
        });
        findViewById(R.id.btnClearAll).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { clearAll(); }
        });
        applyTheme();
    }

    // ---------- 平台列表 ----------

    private List<String[]> allPlatforms() {
        List<String[]> out = new ArrayList<>();
        for (String[] p : PLATFORMS) out.add(p);
        String list = host.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).getString("custom_sites", "");
        for (String e : list.split("")) {
            if (e.length() == 0) continue;
            String[] p = e.split("", -1);
            if (p.length < 2) continue;
            out.add(new String[]{p[0], p[1], "", "", "🌐", "0xFF5B7CFA"});
        }
        return out;
    }

    private boolean isCustom(int i) { return i >= PLATFORMS.length; }

    private String platformDomain(int i) {
        if (i < PLATFORMS.length) return PLATFORMS[i][3];
        try { java.net.URL u = new java.net.URL(allPlatforms().get(i)[1]); return u.getProtocol() + "://" + u.getHost(); }
        catch (Throwable t) { return ""; }
    }

    private boolean loggedIn(int i) {
        List<String[]> all = allPlatforms();
        if (i >= all.size()) return false;
        String[] p = all.get(i);
        if (p[2].length() == 0) return false; // 自定义平台无特征串
        try {
            String c = android.webkit.CookieManager.getInstance().getCookie(platformDomain(i));
            return c != null && c.contains(p[2]);
        } catch (Throwable t) { return false; }
    }

    private void buildPlatList() {
        platList.removeAllViews();
        themed.clear();
        List<String[]> all = allPlatforms();
        int div = color(0xFF2A3142, 0xFFEDEFF3);
        for (int i = 0; i < all.size(); i++) {
            final int idx = i;
            String[] p = all.get(i);
            boolean in = loggedIn(i);
            String sub;
            if (isCustom(i)) sub = in ? "已登录(检测到Cookie)" : "已配置 · 点登录生效";
            else sub = in ? "已登录" : "未登录";
            View row = platRow(p[4], (int) ((long) Long.decode(p[5])), p[0], sub,
                    in ? "管理" : "登录", color(0xFFB4C5FF, 0xFF315CDE),
                    new View.OnClickListener() {
                        public void onClick(View v) { openWeb(idx); }
                    });
            if (isCustom(i)) {
                row.setOnLongClickListener(new View.OnLongClickListener() {
                    public boolean onLongClick(View v) { confirmDelete(idx); return true; }
                });
            }
            platList.addView(row);
            if (i < all.size() - 1) platList.addView(divLine(div));
        }
        // 添加平台
        View add = platRow("＋", color(0xFF2A3142, 0xFFEDEFF3), "添加平台",
                "支持任意网站的网页登录", "添加", color(0xFFB4C5FF, 0xFF315CDE),
                new View.OnClickListener() {
                    public void onClick(View v) { showAddDialog(); }
                });
        platList.addView(divLine(div));
        platList.addView(add);
    }

    private View platRow(String emoji, int avColor, String name, String sub, String action, int actionColor,
                         View.OnClickListener click) {
        int dp8 = dp(8);
        LinearLayout row = new LinearLayout(host);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp8, 0, dp8);

        TextView av = new TextView(host);
        av.setText(emoji);
        av.setTextSize(16);
        av.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(avColor);
        av.setBackground(bg);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(40), dp(40));
        alp.rightMargin = dp(12);
        av.setLayoutParams(alp);
        row.addView(av);

        LinearLayout mid = new LinearLayout(host);
        mid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        mid.setLayoutParams(mlp);
        TextView tvName = new TextView(host);
        tvName.setText(name); tvName.setTextSize(16); tvName.setTypeface(tvName.getTypeface(), Typeface.BOLD);
        tvName.setTextColor(color(0xFFE8ECF4, 0xFF1F2329));
        mid.addView(tvName);
        TextView tvSub = new TextView(host);
        tvSub.setText(sub); tvSub.setTextSize(12);
        tvSub.setTextColor(color(0xFF8A94A6, 0xFF8A94A6));
        tvSub.setPadding(0, dp8 / 2, 0, 0);
        mid.addView(tvSub);
        row.addView(mid);

        TextView go = new TextView(host);
        go.setText(action); go.setTextSize(15);
        go.setTextColor(actionColor);
        go.setPadding(dp8, dp8, 0, dp8);
        go.setOnClickListener(click);
        row.addView(go);

        themed.add(row);
        return row;
    }

    private View divLine(int c) {
        View v = new View(host);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        v.setBackgroundColor(c);
        return v;
    }

    private void openWeb(int idx) {
        try {
            String url = allPlatforms().get(idx)[1];
            CookieHosts.add(host, url);
            host.startActivity(new Intent(host, LoginWebActivity.class).putExtra("url", url));
        } catch (Throwable t) { Toast.makeText(host, "打开失败", Toast.LENGTH_SHORT).show(); }
    }

    private void confirmDelete(final int idx) {
        final String name = allPlatforms().get(idx)[0];
        new AlertDialog.Builder(host)
                .setTitle("删除平台")
                .setMessage("删除「" + name + "」？")
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        String list = host.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).getString("custom_sites", "");
                        StringBuilder sb = new StringBuilder();
                        for (String e : list.split("")) {
                            if (e.length() > 0 && !e.startsWith(name + "")) {
                                if (sb.length() > 0) sb.append('');
                                sb.append(e);
                            }
                        }
                        host.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).edit().putString("custom_sites", sb.toString()).apply();
                        buildPlatList();
                    }
                }).setNegativeButton("取消", null).show();
    }

    private void showAddDialog() {
        LinearLayout box = new LinearLayout(host);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        final EditText etName = new EditText(host);
        etName.setHint("平台名字"); etName.setSingleLine(true);
        box.addView(etName);
        final EditText etUrl = new EditText(host);
        etUrl.setHint("登录页 https://…"); etUrl.setSingleLine(true);
        box.addView(etUrl);
        RadioGroup rg = new RadioGroup(host);
        rg.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton rbM = new RadioButton(host); rbM.setText("手机版"); rbM.setId(101); rbM.setChecked(true);
        RadioButton rbP = new RadioButton(host); rbP.setText("PC版"); rbP.setId(102);
        rg.addView(rbM); rg.addView(rbP);
        box.addView(rg);
        new AlertDialog.Builder(host)
                .setTitle("添加平台")
                .setView(box)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        String n = etName.getText().toString().trim();
                        String u = etUrl.getText().toString().trim();
                        String mode = rg.getCheckedRadioButtonId() == 102 ? "pc" : "mobile";
                        if (n.length() == 0 || !u.startsWith("http")) {
                            Toast.makeText(host, "名字与登录页URL必填", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        String list = host.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).getString("custom_sites", "");
                        String add = n + "" + u + "" + mode;
                        host.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).edit()
                                .putString("custom_sites", list.length() > 0 ? list + "" + add : add).apply();
                        buildPlatList();
                        Toast.makeText(host, "已添加，点「登录」完成网页登录", Toast.LENGTH_SHORT).show();
                    }
                }).setNegativeButton("取消", null).show();
    }

    private void clearAll() {
        try {
            android.webkit.CookieManager cm = android.webkit.CookieManager.getInstance();
            cm.setAcceptCookie(true);
            cm.removeAllCookies(null);
            cm.flush();
            buildPlatList();
            Toast.makeText(host, "已清除全部网页登录", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Toast.makeText(host, "清除失败: " + t, Toast.LENGTH_SHORT).show();
        }
    }

    // ---------- 主题 ----------


    private int color(int darkC, int lightC) { return dark ? darkC : lightC; }

    /** 切到本页时调用：刷新主题与登录态 */
    public void refresh() { applyTheme(); }

    private void applyTheme() {
        boolean oled = host.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).getBoolean("oled", true);
        int bg = color(oled ? 0xFF000000 : 0xFF11151D, 0xFFEEF4FF);
        int card = color(oled ? 0xFF000000 : 0xFF191F2E, 0xFFFFFFFF);
        int title = color(0xFFE8ECF4, 0xFF1F2329);
        int sub = color(0xFF8A94A6, 0xFF8A94A6);
        int accent = color(0xFFB4C5FF, 0xFF315CDE);
        int div = color(0xFF2A3142, 0xFFEDEFF3);
        findViewById(R.id.settingsRoot).setBackgroundColor(bg);
        int[] titles = {R.id.tvTitle, R.id.t4, R.id.t5, R.id.t6, R.id.tOled, R.id.tvAboutTitle2};
        for (int id : titles) ((TextView) findViewById(id)).setTextColor(title);
        int[] subs = {R.id.label1, R.id.label2, R.id.descOled, R.id.desc1, R.id.desc2,
                R.id.desc3, R.id.desc4, R.id.desc5, R.id.desc6, R.id.tvAboutText2};
        for (int id : subs) ((TextView) findViewById(id)).setTextColor(sub);
        int[] accents = {R.id.btnClearAll, R.id.arrow};
        for (int id : accents) ((TextView) findViewById(id)).setTextColor(accent);
        int[] cards = {R.id.card1, R.id.card2, R.id.card3, R.id.cardOled, R.id.cardNotify, R.id.cardAbout2};
        for (int id : cards) findViewById(id).setBackgroundResource(dark ? (oled ? R.drawable.bg_card_oled : R.drawable.bg_card_md3_dark) : R.drawable.bg_card_md3);
        View dv = findViewById(R.id.div3);
        if (dv != null) dv.setBackgroundColor(div);
        TextView tvO = (TextView) findViewById(R.id.tvOled);
        if (tvO != null) tvO.setText(oled ? "开" : "关");
        buildPlatList();
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}
