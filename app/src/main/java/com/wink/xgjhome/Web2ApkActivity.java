package com.wink.xgjhome;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/** 网页转APK 配置编辑器: 页与指示器 / 浏览器与脚本 / 应用栏 / 侧滑栏 */
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
    private TextView tvName, tvSub;

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
        if (Build.VERSION.SDK_INT >= 26) {
            getWindow().getDecorView().setSystemUiVisibility(0);
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        // 顶栏: ≡ 名称/main  ▶ ▐▌ ⋮
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
        tvName = new TextView(this);
        tvName.setText(appName);
        tvName.setTextColor(TXT);
        tvName.setTextSize(18);
        tvName.setTypeface(Typeface.DEFAULT_BOLD);
        tvSub = new TextView(this);
        tvSub.setText(pkg);
        tvSub.setTextColor(SUB);
        tvSub.setTextSize(12);
        mid.addView(tvName);
        mid.addView(tvSub);
        top.addView(mid);
        top.addView(glyph("▶", TXT, 16));
        TextView ph = glyph("▮▮", TXT, 13);
        ph.setPadding(dp(16), 0, 0, 0);
        top.addView(ph);
        TextView dots = glyph("⋮", TXT, 18);
        dots.setPadding(dp(16), 0, 0, 0);
        top.addView(dots);
        root.addView(top, new LinearLayout.LayoutParams(-1, -2));

        // Tab 行
        tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(dp(12), dp(6), dp(12), dp(10));
        String[] tabNames = {"页与指示器", "浏览器与脚本", "应用栏", "侧滑栏"};
        for (int i = 0; i < tabNames.length; i++) {
            final int idx = i;
            TextView t = new TextView(this);
            t.setText(tabNames[idx]);
            t.setTextSize(15);
            t.setPadding(dp(16), dp(8), dp(16), dp(8));
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { showTab(idx); }
            });
            tabs.addView(t);
        }
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(tabs);
        root.addView(hs, new LinearLayout.LayoutParams(-1, -2));
        View topDiv = new View(this);
        topDiv.setBackgroundColor(DIV);
        root.addView(topDiv, new LinearLayout.LayoutParams(-1, dp(1)));

        // 内容区
        ScrollView sc = new ScrollView(this);
        sc.setBackgroundColor(BG);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        sc.addView(content, new ViewGroup.LayoutParams(-1, -2));
        root.addView(sc, new LinearLayout.LayoutParams(-1, -1));

        setContentView(root);
        showTab(0);
    }

    private void showTab(int idx) {
        for (int i = 0; i < tabs.getChildCount(); i++) {
            TextView t = (TextView) tabs.getChildAt(i);
            boolean sel = (i == idx);
            t.setTextColor(sel ? ACCENT : SUB);
            GradientDrawable p = new GradientDrawable();
            p.setCornerRadius(dp(18));
            p.setColor(sel ? PILL : 0x00000000);
            t.setBackground(p);
        }
        content.removeAllViews();
        if (idx == 0) {
            row("🔖", "指示器风格", "填充文本样式", false);
            row("📍", "指示器位置", "上方", false);
            row("🔄", "离屏预加载", "0页", false);
            rowSub("👆", "用户滑动", null, null, false);
            rowSub("🔁", "变换动画", "必须保证离屏预加载开启1～2页，不建议在性能较差的网站上开启。", "正常", false);
            section("浏览页");
        } else if (idx == 1) {
            head("浏览器配置");
            row("🌙", "夜间模式", "跟随主题", false);
            row("↗", "打开其它应用", "禁止打开", false);
            rowSub("◐", "色彩模式", "组件颜色自适应网页色彩", null, false);
            row("🧭", "浏览器标识", "默认", false);
            rowSub("💻", "电脑模式", "开启需搭配PC UA使用", null, false);
            row("JS", "启用JavaScript", null, true);
            section("网页控制");
        } else if (idx == 2) {
            row("▤", "启用应用栏", null, true);
            row("🔖", "应用栏样式", "默认风格", false);
            row("🙈", "自动隐藏", null, false);
            row("⇤", "Home按钮", null, false);
            row("🔍", "搜索功能", null, false);
            row("T", "标题", "AppBar", false);
            row("Tẗ", "子标题", null, false);
            section("菜单项");
        } else {
            row("☰", "启用侧滑栏", null, false);
            section("侧滑项");
        }
    }

    // 分组小标题(青色)
    private void head(String t) {
        TextView h = new TextView(this);
        h.setText(t);
        h.setTextColor(ACCENT);
        h.setTextSize(14);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setPadding(dp(16), dp(14), dp(16), dp(8));
        content.addView(h);
    }

    // 分组尾: 标题 + 右侧 🗑 ＋
    private void section(String t) {
        LinearLayout sec = new LinearLayout(this);
        sec.setOrientation(LinearLayout.HORIZONTAL);
        sec.setGravity(Gravity.CENTER_VERTICAL);
        sec.setPadding(dp(16), dp(20), dp(16), dp(8));
        TextView h = new TextView(this);
        h.setText(t);
        h.setTextColor(ACCENT);
        h.setTextSize(14);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(0, -2, 1f);
        h.setLayoutParams(hp);
        sec.addView(h);
        TextView del = glyph("🗑", TXT, 15);
        del.setPadding(0, 0, dp(18), 0);
        sec.addView(del);
        sec.addView(glyph("＋", TXT, 17));
        content.addView(sec, new LinearLayout.LayoutParams(-1, -2));
    }

    private View rowBase(String icon, String title, String sub) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(ROW);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout ic = new LinearLayout(this);
        ic.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setCornerRadius(dp(8));
        g.setColor(0xFF2A2E33);
        ic.setBackground(g);
        TextView iv = glyph(icon, TXT, sub == null ? 13 : 16);
        int isz = dp(34);
        ic.addView(iv);
        ic.setPadding(dp(6), dp(2), dp(6), dp(2));
        row.addView(ic, new LinearLayout.LayoutParams(isz, isz));
        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, -2, 1f);
        mp.setMargins(dp(14), 0, dp(10), 0);
        mid.setLayoutParams(mp);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(TXT);
        t.setTextSize(16);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        mid.addView(t);
        if (sub != null) {
            TextView ss = new TextView(this);
            ss.setText(sub);
            ss.setTextColor(SUB);
            ss.setTextSize(13);
            ss.setPadding(0, dp(3), 0, 0);
            mid.addView(ss);
        }
        row.addView(mid);
        return row;
    }

    private void row(String icon, String title, String value, boolean toggle) {
        View row = rowBase(icon, title, null);
        if (toggle) {
            Switch sw = new Switch(this);
            sw.setChecked("1".equals(getPref(title)));
            sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                public void onCheckedChanged(CompoundButton b, boolean on) {
                    savePref(title, on ? "1" : "0");
                }
            });
            ((LinearLayout) row).addView(sw);
        } else if (value != null) {
            TextView vv = new TextView(this);
            vv.setText(value);
            vv.setTextColor(TXT);
            vv.setTextSize(15);
            ((LinearLayout) row).addView(vv);
            TextView ar = glyph("›", SUB, 18);
            ar.setPadding(dp(8), 0, 0, 0);
            ((LinearLayout) row).addView(ar);
        }
        content.addView(row, new LinearLayout.LayoutParams(-1, -2));
        View div = new View(this);
        div.setBackgroundColor(DIV);
        content.addView(div, new LinearLayout.LayoutParams(-1, dp(1)));
    }

    private void rowSub(String icon, String title, String sub, String value, boolean toggle) {
        View row = rowBase(icon, title, sub);
        if (toggle) {
            Switch sw = new Switch(this);
            sw.setChecked("1".equals(getPref(title)));
            sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                public void onCheckedChanged(CompoundButton b, boolean on) {
                    savePref(title, on ? "1" : "0");
                }
            });
            ((LinearLayout) row).addView(sw);
        } else if (value != null) {
            TextView vv = new TextView(this);
            vv.setText(value);
            vv.setTextColor(TXT);
            vv.setTextSize(15);
            ((LinearLayout) row).addView(vv);
            TextView ar = glyph("›", SUB, 18);
            ar.setPadding(dp(8), 0, 0, 0);
            ((LinearLayout) row).addView(ar);
        }
        content.addView(row, new LinearLayout.LayoutParams(-1, -2));
        View div = new View(this);
        div.setBackgroundColor(DIV);
        content.addView(div, new LinearLayout.LayoutParams(-1, dp(1)));
    }

    private String getPref(String k) {
        if (pkg == null) return null;
        return getSharedPreferences("web2apk_" + pkg, MODE_PRIVATE).getString(k, null);
    }

    private void savePref(String k, String v) {
        if (pkg == null) return;
        getSharedPreferences("web2apk_" + pkg, MODE_PRIVATE).edit().putString(k, v).apply();
    }
}
