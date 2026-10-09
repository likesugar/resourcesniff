package com.wink.xgjhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.wink.xgjhome.xhs.XhsEngine;
import com.wink.xgjhome.xhs.XhsNet;
import com.wink.xgjhome.xhs.XhsParser;
import com.wink.xgjhome.xhs.XhsStore;

import java.io.File;
import java.util.List;

/** 小红书下载器 · 主页（复刻原版 MainScreen：搜索栏+过滤标签+任务列表+剪贴板气泡+手动输入弹窗） */
public class XhsActivity extends Activity implements XhsEngine.Listener {

    private boolean dark, oled;
    private int accent = 0xFF1677FF;
    private String filter = "all";
    private String query = "";
    private LinearLayout listBody;
    private TextView tvAll, tvPending, tvFailed;
    private EditText etSearch;
    private FrameLayout bubble;
    private final Handler ui = new Handler();
    private String lastClip = "";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dark = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("dark", false);
        oled = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("oled", false);
        if (dark) accent = 0xFF4D9AFF;
        XhsEngine.init(this);
        buildUi();
        applyTheme();
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }

    @Override
    protected void onResume() {
        super.onResume();
        XhsEngine.addListener(this);
        onChanged();
        if (XhsEngine.store().autoReadClipboard()) detectClipboard();
    }

    @Override
    protected void onPause() {
        super.onPause();
        XhsEngine.removeListener(this);
    }

    @Override public void onChanged() { runOnUiThread(new Runnable() { public void run() { rebuildList(); } }); }

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density); }
    private String nz(String s) { return s == null ? "" : s; }

    // ================= UI =================
    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(dark ? (oled ? Color.BLACK : 0xFF141414) : 0xFFEEF4FF);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        // 顶栏：标题 + 设置齿轮（对齐原版顶部）
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(16), dp(12), dp(16), dp(12));
        TextView title = new TextView(this);
        title.setText("小红书下载器");
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(dark ? Color.WHITE : 0xFF1F2329);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView gear = new TextView(this);
        gear.setText("⚙");
        gear.setTextSize(22);
        gear.setPadding(dp(8), 0, dp(4), 0);
        gear.setTextColor(dark ? Color.WHITE : 0xFF1F2329);
        gear.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            startActivity(new Intent(XhsActivity.this, XhsSettingsActivity.class));
        }});
        top.addView(gear);
        page.addView(top, new LinearLayout.LayoutParams(-1, -2));

        // 搜索栏（对齐原版 SearchBar）
        LinearLayout searchWrap = new LinearLayout(this);
        searchWrap.setOrientation(LinearLayout.HORIZONTAL);
        searchWrap.setPadding(dp(16), 0, dp(16), dp(8));
        etSearch = new EditText(this);
        etSearch.setHint("搜索历史记录");
        etSearch.setTextSize(14);
        etSearch.setSingleLine(true);
        etSearch.setBackgroundResource(dark ? R.drawable.bg_input_flat : R.drawable.bg_input);
        etSearch.setPadding(dp(14), dp(10), dp(14), dp(10));
        etSearch.setTextColor(dark ? Color.WHITE : 0xFF1F2329);
        etSearch.setHintTextColor(0xFF8A94A6);
        etSearch.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b2, int c) { }
            public void onTextChanged(CharSequence s, int a, int b2, int c) { }
            public void afterTextChanged(Editable s) { query = s.toString().trim(); rebuildList(); }
        });
        searchWrap.addView(etSearch, new LinearLayout.LayoutParams(-1, -2));
        page.addView(searchWrap);

        // 过滤标签（全部/待选择/失败）
        HorizontalScrollView tabsScroll = new HorizontalScrollView(this);
        tabsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(dp(16), 0, dp(16), dp(8));
        tvAll = tab(tabs, "全部");
        tvPending = tab(tabs, "待选择");
        tvFailed = tab(tabs, "失败");
        restyleTabs();
        tabsScroll.addView(tabs);
        page.addView(tabsScroll, new LinearLayout.LayoutParams(-1, -2));

        // 任务列表
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        listBody = new LinearLayout(this);
        listBody.setOrientation(LinearLayout.VERTICAL);
        listBody.setPadding(dp(16), 0, dp(16), dp(96));
        scroll.addView(listBody, new ScrollView.LayoutParams(-1, -2));
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        // 底部按钮：粘贴解析 + 手动输入（对齐原版底部入口）
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER);
        bottom.setPadding(dp(16), dp(8), dp(16), dp(16));
        bottom.addView(bigBtn("📋 粘贴解析", new View.OnClickListener() { public void onClick(View v) { doPaste(); } }));
        bottom.addView(bigBtn("✎ 手动输入", new View.OnClickListener() { public void onClick(View v) { showInputDialog(""); } }));
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        root.addView(bottom, bp);

        // 剪贴板气泡（叠加层）
        bubble = new FrameLayout(this);
        root.addView(bubble, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        bubble.setPadding(0, 0, 0, dp(76));

        setContentView(root);
    }

    private TextView tab(LinearLayout parent, final String label) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(14);
        tv.setPadding(dp(14), dp(6), dp(14), dp(6));
        tv.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            filter = label.equals("全部") ? "all" : label.equals("待选择") ? "pending" : "failed";
            restyleTabs();
            rebuildList();
        }});
        parent.addView(tv);
        return tv;
    }

    private void restyleTabs() {
        restyleTab(tvAll, "全部", "all");
        restyleTab(tvPending, "待选择", "pending");
        restyleTab(tvFailed, "失败", "failed");
    }

    private void restyleTab(TextView tv, String label, String key) {
        boolean sel = filter.equals(key);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(14));
        g.setColor(sel ? accent : (dark ? 0x22FFFFFF : 0xFFFFFFFF));
        tv.setBackground(g);
        tv.setTextColor(sel ? Color.WHITE : (dark ? 0xFFB0B8C4 : 0xFF8A94A6));
        int n = countFor(key);
        tv.setText(n > 0 ? label + " (" + n + ")" : label);
    }

    private int countFor(String key) {
        int n = 0;
        for (XhsStore.Task t : XhsEngine.tasks()) {
            if ("failed".equals(key)) { if ("failed".equals(t.status)) n++; }
            else if ("pending".equals(key)) {
                if ("selecting".equals(t.status) || "running".equals(t.status) || "pending".equals(t.status) || "stopped".equals(t.status)) n++;
            } else n++;
        }
        return n;
    }

    private TextView bigBtn(String text, View.OnClickListener l) {
        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setTextSize(15);
        btn.setTextColor(Color.WHITE);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(20), dp(12), dp(20), dp(12));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(24));
        g.setColor(accent);
        btn.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = dp(10);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(l);
        return btn;
    }

    // ================= 列表 =================
    private void rebuildList() {
        if (listBody == null) return;
        listBody.removeAllViews();
        restyleTabs();
        List<XhsStore.Task> tasks = XhsEngine.tasks();
        int shown = 0;
        for (final XhsStore.Task t : tasks) {
            if (!query.isEmpty()) {
                String hay = nz(t.title) + " " + nz(t.author);
                if (!hay.toLowerCase().contains(query.toLowerCase())) continue;
            }
            boolean hit;
            if ("failed".equals(filter)) hit = "failed".equals(t.status);
            else if ("pending".equals(filter)) hit = "selecting".equals(t.status) || "running".equals(t.status)
                    || "pending".equals(t.status) || "stopped".equals(t.status);
            else hit = true;
            if (!hit) continue;
            listBody.addView(taskCard(t));
            shown++;
        }
        if (shown == 0) {
            TextView empty = new TextView(this);
            empty.setText("暂无任务\n复制小红书分享链接后点「粘贴解析」");
            empty.setGravity(Gravity.CENTER);
            empty.setTextSize(13);
            empty.setTextColor(0xFF8A94A6);
            empty.setPadding(0, dp(80), 0, 0);
            listBody.addView(empty, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private View taskCard(final XhsStore.Task t) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackgroundResource(dark ? (oled ? R.drawable.bg_card_oled : R.drawable.bg_card_md3_dark) : R.drawable.bg_card_md3);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(10);
        card.setLayoutParams(lp);

        int statusColor;
        String statusText;
        if ("failed".equals(t.status)) { statusColor = 0xFFE5484D; statusText = "失败"; }
        else if ("done".equals(t.status)) { statusColor = accent; statusText = "完成"; }
        else if ("running".equals(t.status)) { statusColor = 0xFF8A94A6; statusText = "下载中"; }
        else if ("stopped".equals(t.status)) { statusColor = 0xFF8A94A6; statusText = "已停止"; }
        else if ("selecting".equals(t.status)) { statusColor = accent; statusText = "待选择"; }
        else { statusColor = 0xFF8A94A6; statusText = "排队中"; }

        TextView title = new TextView(this);
        String tt = nz(t.title);
        title.setText(tt.isEmpty() ? "解析中…" : tt);
        title.setTextSize(15);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(statusColor);
        card.addView(title, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout metaRow = new LinearLayout(this);
        metaRow.setOrientation(LinearLayout.HORIZONTAL);
        metaRow.setPadding(0, dp(4), 0, 0);
        TextView meta = new TextView(this);
        meta.setTextSize(12);
        meta.setTextColor(0xFF8A94A6);
        String m = "";
        if (!nz(t.author).isEmpty()) m += "@" + t.author + " · ";
        if ("failed".equals(t.status) && !nz(t.error).isEmpty()) m += t.error;
        else if (!"done".equals(t.status) && t.totalBytes > 0) m += fmtSize(t.doneBytes) + " / " + fmtSize(t.totalBytes);
        else if ("done".equals(t.status)) m += t.files.size() + " 个文件";
        meta.setText(m);
        metaRow.addView(meta, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView st = new TextView(this);
        st.setText(statusText);
        st.setTextSize(12);
        st.setTextColor(statusColor);
        metaRow.addView(st);
        card.addView(metaRow, new LinearLayout.LayoutParams(-1, -2));

        if ("running".equals(t.status)) {
            ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            pb.setMax(100);
            pb.setProgress(t.progress);
            LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, dp(6));
            pp.topMargin = dp(8);
            card.addView(pb, pp);
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams acp = new LinearLayout.LayoutParams(-1, -2);
        acp.topMargin = dp(8);
        if ("running".equals(t.status) || "pending".equals(t.status)) {
            actions.addView(miniBtn("取消", new View.OnClickListener() {
                public void onClick(View v) { XhsEngine.cancel(t.id); }
            }));
        } else {
            if ("selecting".equals(t.status))
                actions.addView(miniBtn("选择媒体", new View.OnClickListener() {
                    public void onClick(View v) { showSelectDialog(t); }
                }));
            if ("failed".equals(t.status) || "stopped".equals(t.status))
                actions.addView(miniBtn("重试", new View.OnClickListener() {
                    public void onClick(View v) { XhsEngine.retry(t); }
                }));
            if ("done".equals(t.status) && !t.files.isEmpty())
                actions.addView(miniBtn("打开目录", new View.OnClickListener() {
                    public void onClick(View v) { openFolder(t); }
                }));
            if (!"running".equals(t.status))
                actions.addView(miniBtn("删除", new View.OnClickListener() {
                    public void onClick(View v) { confirmDelete(t); }
                }));
        }
        card.addView(actions, acp);

        // 点卡片：复制文案（对齐原版 TaskCell 点击行为之一）
        card.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            if (!nz(t.description).isEmpty()) copyText(t.description);
        }});
        return card;
    }

    private TextView miniBtn(String text, View.OnClickListener l) {
        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setTextSize(12);
        btn.setTextColor(accent);
        btn.setPadding(dp(12), dp(6), dp(12), dp(6));
        GradientDrawable g = new GradientDrawable();
        g.setColor(dark ? 0x30FFFFFF : (accent & 0x18FFFFFF));
        g.setCornerRadius(dp(12));
        btn.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = dp(8);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(l);
        return btn;
    }

    private String fmtSize(long b) {
        if (b < 1024) return b + "B";
        if (b < 1024 * 1024) return String.format("%.1fKB", b / 1024f);
        return String.format("%.1fMB", b / 1024f / 1024f);
    }

    // ================= 动作 =================
    private void detectClipboard() {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip()) return;
            CharSequence cs = cm.getPrimaryClip().getItemAt(0).getText();
            if (cs == null) return;
            String s = cs.toString().trim();
            if (s.equals(lastClip) || s.isEmpty()) return;
            lastClip = s;
            if (!XhsNet.hasWebSession()) { /* 未登录也能解析图文，先不拦 */ }
            final List<String> links = XhsParser.extractLinks(s, new XhsParser.ShortUrlResolver() {
                public String resolve(String u) { return XhsNet.resolveShort(u); }
            });
            if (links.isEmpty()) return;
            if (XhsEngine.store().showClipboardBubble()) showBubble(links.get(0));
            else if (XhsEngine.store().autoReadClipboard()) XhsEngine.enqueue(this, s, false);
        } catch (Throwable ignored) { }
    }

    private void showBubble(final String link) {
        bubble.removeAllViews();
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(14), dp(10), dp(14), dp(10));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(24));
        g.setColor(dark ? 0xFF2A2A2A : Color.WHITE);
        bar.setBackground(g);
        TextView tv = new TextView(this);
        tv.setText("检测到小红书链接，立即解析？");
        tv.setTextSize(13);
        tv.setTextColor(dark ? Color.WHITE : 0xFF1F2329);
        bar.addView(tv, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView go = new TextView(this);
        go.setText("解析");
        go.setTextSize(13);
        go.setTypeface(Typeface.DEFAULT_BOLD);
        go.setTextColor(accent);
        go.setPadding(dp(10), 0, 0, 0);
        go.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            XhsEngine.enqueue(XhsActivity.this, link, false);
            bubble.removeAllViews();
        }});
        bar.addView(go);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
        bp.setMargins(dp(16), 0, dp(16), 0);
        bubble.addView(bar, bp);
        ui.postDelayed(new Runnable() { public void run() { bubble.removeAllViews(); } }, 6000);
    }

    private void doPaste() {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            CharSequence cs = cm == null || !cm.hasPrimaryClip() ? null : cm.getPrimaryClip().getItemAt(0).getText();
            String s = cs == null ? "" : cs.toString().trim();
            if (s.isEmpty()) { toast("剪贴板为空"); return; }
            XhsStore.Task t = XhsEngine.enqueue(this, s, false);
            if (t == null) toast("未找到小红书链接");
        } catch (Throwable e) { toast("读取剪贴板失败"); }
    }

    /** 手动输入弹窗（对齐原版：输入链接 → 下载 / 复制文案 / 仅保存信息） */
    private void showInputDialog(final String preset) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(20), dp(10), dp(20), 0);
        final EditText et = new EditText(this);
        et.setText(preset);
        et.setHint("粘贴笔记链接或分享文本，如\nhttp://xhslink.com/xxxx 或 https://www.xiaohongshu.com/explore/...");
        et.setTextSize(13);
        et.setMinLines(3);
        et.setGravity(Gravity.TOP);
        et.setBackgroundResource(dark ? R.drawable.bg_input_flat : R.drawable.bg_input);
        et.setPadding(dp(12), dp(10), dp(12), dp(10));
        et.setTextColor(dark ? Color.WHITE : 0xFF1F2329);
        wrap.addView(et, new LinearLayout.LayoutParams(-1, -2));

        new AlertDialog.Builder(this)
                .setTitle("手动输入")
                .setView(wrap)
                .setPositiveButton("下载", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        submit(et.getText().toString(), false);
                    }
                })
                .setNeutralButton("复制文案", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        copyText(et.getText().toString());
                    }
                })
                .setNegativeButton("仅保存信息", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        submit(et.getText().toString(), true);
                    }
                })
                .show();
    }

    private void submit(String s, boolean infoOnly) {
        if (nz(s).trim().isEmpty()) { toast("请输入链接"); return; }
        XhsStore.Task t = XhsEngine.enqueue(this, s, infoOnly);
        if (t == null) toast("链接无效，请重新输入");
    }

    private void openFolder(XhsStore.Task t) {
        try {
            if (!t.files.isEmpty()) {
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(Uri.fromFile(new File(t.files.get(0)).getParentFile()), "resource/folder");
                startActivity(i);
                return;
            }
        } catch (Throwable ignored) { }
        toast(nz(t.files.isEmpty() ? "" : t.files.get(0)));
    }

    private void copyText(String s) {
        if (nz(s).isEmpty()) { toast("没有文案"); return; }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(android.content.ClipData.newPlainText("xhs", s));
        toast("已复制");
    }

    private void confirmDelete(final XhsStore.Task t) {
        new AlertDialog.Builder(this)
                .setMessage("删除该任务记录？（已下载的文件保留）")
                .setPositiveButton("删除", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) { XhsEngine.remove(t); }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 选择性下载：勾选要保存的媒体（对齐原版选择性下载环节） */
    private void showSelectDialog(final XhsStore.Task t) {
        XhsParser.Note note = XhsEngine.noteFromJson(t.noteJson);
        final List<XhsParser.Media> items = note.items;
        if (items == null || items.isEmpty()) { toast("没有可选媒体"); return; }
        CharSequence[] names = new CharSequence[items.size()];
        final boolean[] checked = new boolean[items.size()];
        for (int i = 0; i < items.size(); i++) {
            XhsParser.Media m = items.get(i);
            String kind = "video".equals(m.kind) ? "视频" : "live".equals(m.kind) ? "实况" : "图片";
            names[i] = kind + " " + (i + 1) + (m.width > 0 ? " · " + m.width + "×" + m.height : "");
            checked[i] = true;
        }
        new AlertDialog.Builder(this)
                .setTitle("选择要下载的媒体")
                .setMultiChoiceItems(names, checked, new android.content.DialogInterface.OnMultiChoiceClickListener() {
                    public void onClick(android.content.DialogInterface d, int w, boolean isChecked) { }
                })
                .setPositiveButton("下载", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        List<XhsParser.Media> chosen = new java.util.ArrayList<XhsParser.Media>();
                        for (int i = 0; i < items.size(); i++) if (checked[i]) chosen.add(items.get(i));
                        XhsEngine.continueSelective(t, chosen);
                    }
                })
                .setNegativeButton("取消", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) { XhsEngine.remove(t); }
                })
                .show();
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private void applyTheme() {
        if (Build.VERSION.SDK_INT >= 23) {
            getWindow().getDecorView().setSystemUiVisibility(
                    dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }
}
