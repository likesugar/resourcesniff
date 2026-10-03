package com.wink.xgjhome;

import android.app.Activity;
import android.content.Intent;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** 录制下载页：一列任务，右侧 ⋮ 弹菜单（开始/播放/结束录制/取消） */
public class RecordActivity extends Activity {

    private Handler handler;
    private LinearLayout list;
    private TextView tvEmpty;
    private TextView[] tabBtns;
    private View[] tabLines;
    private int curTab = 0;  // 0全部 1视频 2录制 3进行中 4已完成
    private java.util.ArrayList<HistoryStore.Item> hist;

    private void restyleTabs() {
        for (int i = 0; i < tabBtns.length; i++) {
            boolean sel = i == curTab;
            // 圆角筛选chip样式（对齐"视频下载"页 全部/进行中/已完成）
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setCornerRadius(50 * getResources().getDisplayMetrics().density);
            bg.setColor(sel ? 0xFFE7EDFD : 0xFFF2F4F8);
            tabBtns[i].setBackground(bg);
            tabBtns[i].setTextColor(sel ? 0xFF315CDE : 0xFF8A919E);
            tabBtns[i].setTypeface(sel ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
    }

    private static String dlInfo(DlManager.DlJob j) {
        String sz = fmtSize(j.doneBytes) + (j.total > 0 && j.total > j.doneBytes ? " / " + fmtSize(j.total) : "");
        return "下载大小: " + sz + "\n状态: " + j.state;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DlManager.init(this);
        try { getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION); } catch (Throwable ignored) {}
        hist = HistoryStore.load(this);
        curTab = Math.max(0, Math.min(4, getIntent().getIntExtra("tab", 0)));
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(24, 40, 24, 24);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, 0, 0, 20);
        TextView title = new TextView(this);
        title.setText("下载");
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(0, -2);
        hlp.weight = 1;
        title.setLayoutParams(hlp);
        head.addView(title);
        TextView recNow = new TextView(this);
        recNow.setText("📋 读取剪贴板录制");
        recNow.setTextColor(Color.WHITE);
        recNow.setTextSize(14);
        recNow.setPadding(20, 12, 20, 12);
        recNow.setBackgroundColor(0xFF24485E);
        recNow.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String u = null;
                try {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null && cm.getPrimaryClip() != null && cm.getPrimaryClip().getItemAt(0) != null
                            && cm.getPrimaryClip().getItemAt(0).getText() != null) {
                        u = cm.getPrimaryClip().getItemAt(0).getText().toString().trim();
                    }
                } catch (Throwable e) { }
                if (u == null || !u.startsWith("http")) {
                    Toast.makeText(RecordActivity.this, "剪贴板里没有有效链接", Toast.LENGTH_SHORT).show();
                    return;
                }
                RecManager.startRecJob(u, u);
                rebuild();
                Toast.makeText(RecordActivity.this, "已开始录制", Toast.LENGTH_SHORT).show();
            }
        });
        head.addView(recNow);
        col.addView(head);

        // 扁平化顶部导航栏：全部/视频/录制/下载
        LinearLayout tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setPadding(0, 0, 0, 16);
        final String[] tabs = {"全部", "视频", "录制", "下载"};
        tabBtns = new TextView[tabs.length];
        tabLines = new View[tabs.length];
        for (int i = 0; i < tabs.length; i++) {
            final int idx = i;
            TextView tb = new TextView(this);
            tb.setText(tabs[i]);
            tb.setTextSize(14);
            tb.setGravity(Gravity.CENTER);
            tb.setPadding(28, 16, 28, 16);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
            tlp.rightMargin = (int)(8 * getResources().getDisplayMetrics().density);
            tb.setLayoutParams(tlp);
            tb.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { curTab = idx; restyleTabs(); rebuild(); }
            });
            tabBtns[i] = tb;
            tabBar.addView(tb);
        }
        restyleTabs();
        col.addView(tabBar);

        tvEmpty = new TextView(this);
        tvEmpty.setText("暂无录制任务");
        tvEmpty.setTextColor(0xFF8A919E);
        tvEmpty.setGravity(Gravity.CENTER);
        tvEmpty.setPadding(0, 120, 0, 0);
        col.addView(tvEmpty);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        col.addView(list);

        sv.addView(col);
        root.addView(sv);

        // 底部胶囊导航（FloatingTabs 移植）：半透明胶囊 + 蓝色滑块 + 首页/下载
        try {
            float dm = getResources().getDisplayMetrics().density;
            final int TAB_W = (int)(118 * dm), TAB_H = (int)(50 * dm), INSET = (int)(7 * dm);
            android.widget.FrameLayout capsule = new android.widget.FrameLayout(this);
            android.graphics.drawable.GradientDrawable capBg = new android.graphics.drawable.GradientDrawable();
            capBg.setCornerRadius(50 * dm);
            capBg.setColor(0x8CFFFFFF);
            capsule.setBackground(capBg);
            capsule.setElevation(4 * dm);
            android.widget.FrameLayout.LayoutParams clp = new android.widget.FrameLayout.LayoutParams(
                    TAB_W * 2 + INSET * 2, TAB_H + INSET * 2,
                    android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);
            clp.bottomMargin = (int)(18 * dm);
            capsule.setLayoutParams(clp);

            final android.widget.FrameLayout thumb = new android.widget.FrameLayout(this);
            android.graphics.drawable.GradientDrawable thBg = new android.graphics.drawable.GradientDrawable();
            thBg.setCornerRadius(50 * dm);
            thBg.setColor(0xFF4666DB);
            thumb.setBackground(thBg);
            android.widget.FrameLayout.LayoutParams tlp = new android.widget.FrameLayout.LayoutParams(TAB_W, TAB_H);
            tlp.leftMargin = INSET;
            thumb.setLayoutParams(tlp);
            capsule.addView(thumb);

            LinearLayout labels = new LinearLayout(this);
            labels.setOrientation(LinearLayout.HORIZONTAL);
            capsule.addView(labels, new android.widget.FrameLayout.LayoutParams(-1, -1));
            final TextView[] segs = new TextView[2];
            final String[] labs = {"⌂ 首页", "⬇ 下载"};
            for (int i = 0; i < 2; i++) {
                final int idx = i;
                TextView tv = new TextView(this);
                tv.setText(labs[i]);
                tv.setTextSize(14);
                tv.setGravity(Gravity.CENTER);
                tv.setLayoutParams(new LinearLayout.LayoutParams(0, -1, 1f));
                tv.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        restylePill(thumb, segs, idx, INSET, TAB_W);
                        if (idx == 0) finish();  // 首页 → 回小工具首页
                    }
                });
                segs[i] = tv;
                labels.addView(tv);
            }
            restylePill(thumb, segs, 1, INSET, TAB_W);
            root.addView(capsule);
        } catch (Throwable t) { }
                setContentView(root);

        // 视频下载式刷新：数据变化驱动（DlManager/RecManager 回调），不再用定时轮询
        DlManager.onProgress = new Runnable() { public void run() { runOnUiThread(new Runnable() { public void run() { rebuild(); } }); } };
        RecManager.onProgress = DlManager.onProgress;
    }

    private float dmv() { return getResources().getDisplayMetrics().density; }
    private void restylePill(android.widget.FrameLayout thumb, TextView[] segs, int sel, int inset, int tabW) {
        android.widget.FrameLayout.LayoutParams lp = (android.widget.FrameLayout.LayoutParams) thumb.getLayoutParams();
        lp.leftMargin = inset + sel * tabW;
        thumb.setLayoutParams(lp);
        for (int i = 0; i < 2; i++) {
            segs[i].setTextColor(i == sel ? 0xFFFFFFFF : 0xFF5F6B7A);
            segs[i].setTypeface(i == sel ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
    }

    @Override
    protected void onResume() { super.onResume(); rebuild(); }

    private static String fmtDur(long s) {
        return String.format(java.util.Locale.US, "%02d:%02d:%02d", s / 3600, s / 60 % 60, s % 60);
    }

    private static String fmtSize(long b) {
        if (b >= 1048576) return String.format(java.util.Locale.US, "%.2fMB", b / 1048576.0);
        return String.format(java.util.Locale.US, "%.0fKB", b / 1024.0);
    }

    private final java.util.ArrayList<String> rowOrder = new java.util.ArrayList<String>();  // 按任务id固定行位置
    private final java.util.HashMap<String, TextView> rowInfo = new java.util.HashMap<String, TextView>();

    private void rebuild() {
        // key -> [type(0录制/1下载), job, live]
        final java.util.LinkedHashMap<String, Object[]> meta = new java.util.LinkedHashMap<String, Object[]>();
        for (RecManager.RecJob j : RecManager.recJobs.values()) meta.put("R" + j.id, new Object[]{0, j, (Boolean) j.active});
        for (RecManager.RecJob j : RecManager.stoppedJobs.values()) if (!meta.containsKey("R" + j.id)) meta.put("R" + j.id, new Object[]{0, j, Boolean.FALSE});
        for (DlManager.DlJob j : DlManager.jobs()) meta.put("D" + j.id, new Object[]{1, j, Boolean.FALSE});
        if (hist != null) for (int i = 0; i < hist.size(); i++) meta.put("H" + i, new Object[]{2, hist.get(i), Boolean.FALSE});

        // 分类：kind(视频/录制) + state(进行中/已完成)，chips=全部/视频/录制/进行中/已完成
        java.util.HashMap<String, String> cat = new java.util.HashMap<String, String>();
        java.util.HashMap<String, String> st = new java.util.HashMap<String, String>();
        for (java.util.Map.Entry<String, Object[]> e : meta.entrySet()) {
            int type = (Integer) e.getValue()[0];
            boolean live = (Boolean) e.getValue()[2];
            if (type == 2) {
                HistoryStore.Item it = (HistoryStore.Item) e.getValue()[1];
                cat.put(e.getKey(), it.type == null ? "视频" : it.type);
                st.put(e.getKey(), "已完成");
            } else if (type == 1) {
                DlManager.DlJob dj = (DlManager.DlJob) e.getValue()[1];
                cat.put(e.getKey(), "视频");
                st.put(e.getKey(), (dj.active || dj.paused) ? "进行中" : "已完成");
            } else {
                cat.put(e.getKey(), "录制");
                st.put(e.getKey(), live ? "进行中" : "已完成");
            }
        }
        String want = new String[]{"全部", "视频", "录制", "进行中", "已完成"}[curTab];
        String wantSt = curTab == 3 ? "进行中" : (curTab == 4 ? "已完成" : null);

        // 稳定顺序
        java.util.ArrayList<String> order = new java.util.ArrayList<String>(rowOrder);
        order.retainAll(meta.keySet());
        for (String k : meta.keySet()) if (!order.contains(k)) order.add(k);
        rowOrder.clear(); rowOrder.addAll(order);

        // 当前tab要显示的
        java.util.ArrayList<String> shown = new java.util.ArrayList<String>();
        for (String k : order) {
            String w = new String[]{"全部", "视频", "录制", "进行中", "已完成"}[curTab];
            String kind = cat.get(k), state = st.get(k);
            boolean show = w.equals("全部") || (state != null && w.equals(state)) || (state == null && w.equals(kind));
            if (show) shown.add(k);
        }
        tvEmpty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);

        // 已显示行 == 当前应显示行 → 只刷文字
        java.util.ArrayList<String> curRows = new java.util.ArrayList<String>();
        for (int i = 0; i < list.getChildCount(); i++) {
            Object t = list.getChildAt(i).getTag();
            if (t instanceof String) curRows.add((String) t);
        }
        if (curRows.equals(shown)) {
            for (String key : shown) {
                Runnable u = rowUpdaters.get(key);
                if (u != null) u.run();
            }
            return;
        }
        list.removeAllViews();
        rowUpdaters.clear();
        for (String key : shown) {
            Object[] m = meta.get(key);
            java.util.concurrent.atomic.AtomicReference<TextView> infoRef = new java.util.concurrent.atomic.AtomicReference<TextView>();
            LinearLayout row;
            if ((Integer) m[0] == 0) {
                RecManager.RecJob j = (RecManager.RecJob) m[1];
                final boolean live = (Boolean) m[2];
                addRow(list, j, live, infoRef);
                row = (LinearLayout) list.getChildAt(list.getChildCount() - 1);
                final RecManager.RecJob fj = j;
                rowUpdaters.put(key, new Runnable() { public void run() { if (infoRef.get() != null) infoRef.get().setText(buildInfo(fj, live)); } });
            } else if ((Integer) m[0] == 1) {
                final DlManager.DlJob j = (DlManager.DlJob) m[1];
                addDlRow(list, j, infoRef);
                row = (LinearLayout) list.getChildAt(list.getChildCount() - 1);
            } else {
                final HistoryStore.Item it = (HistoryStore.Item) m[1];
                final int hidx = Integer.parseInt(key.substring(1));
                addHistRow(list, it, hidx, infoRef);
                row = (LinearLayout) list.getChildAt(list.getChildCount() - 1);
            }
            row.setTag(key);
        }
    }

    private final java.util.HashMap<String, Runnable> rowUpdaters = new java.util.HashMap<String, Runnable>();

    private void addDlRow(LinearLayout parent, final DlManager.DlJob j, final java.util.concurrent.atomic.AtomicReference<TextView> infoRef) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 24, 0, 24);
        FrameLayout thumb = new FrameLayout(this);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(140, 90);
        tp.rightMargin = 24;
        thumb.setLayoutParams(tp);
        thumb.setBackgroundColor(0xFF1E242E);
        TextView play = new TextView(this);
        play.setText("⇣");
        play.setTextColor(0xFF8A919E);
        play.setGravity(Gravity.CENTER);
        thumb.addView(play, new FrameLayout.LayoutParams(-1, -1));
        row.addView(thumb);
        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, -2);
        mp.weight = 1;
        mid.setLayoutParams(mp);
        TextView tvName = new TextView(this);
        tvName.setText(j.title);
        tvName.setTextColor(Color.WHITE);
        tvName.setTextSize(16);
        tvName.setTypeface(Typeface.DEFAULT_BOLD);
        tvName.setSingleLine(true);
        mid.addView(tvName);
        TextView tvInfo = new TextView(this);
        tvInfo.setText(dlInfo(j));
        tvInfo.setTextColor(0xFF8A919E);
        tvInfo.setTextSize(13);
        tvInfo.setLineSpacing(4, 1);
        mid.addView(tvInfo);
        row.addView(mid);
        TextView more = new TextView(this);
        more.setText("⋮");
        more.setTextColor(Color.WHITE);
        more.setTextSize(22);
        more.setGravity(Gravity.CENTER);
        more.setPadding(24, 24, 24, 24);
        more.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showDlMenu(v, j); }
        });
        row.addView(more);
        parent.addView(row);
        infoRef.set(tvInfo);
    }

    private void addHistRow(LinearLayout parent, final HistoryStore.Item it, final int idx, final java.util.concurrent.atomic.AtomicReference<TextView> infoRef) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 24, 0, 24);
        FrameLayout thumb = new FrameLayout(this);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(140, 90);
        tp.rightMargin = 24;
        thumb.setLayoutParams(tp);
        thumb.setBackgroundColor(0xFF1E242E);
        TextView play = new TextView(this);
        play.setText("▶");
        play.setTextColor(0xFF8A919E);
        play.setGravity(Gravity.CENTER);
        thumb.addView(play, new FrameLayout.LayoutParams(-1, -1));
        row.addView(thumb);
        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, -2);
        mp.weight = 1;
        mid.setLayoutParams(mp);
        TextView tvName = new TextView(this);
        tvName.setText(it.title);
        tvName.setTextColor(Color.WHITE);
        tvName.setTextSize(16);
        tvName.setTypeface(Typeface.DEFAULT_BOLD);
        tvName.setSingleLine(true);
        mid.addView(tvName);
        TextView tvInfo = new TextView(this);
        tvInfo.setText("类型: " + it.type + "\n状态: 已完成");
        tvInfo.setTextColor(0xFF8A919E);
        tvInfo.setTextSize(13);
        tvInfo.setLineSpacing(4, 1);
        mid.addView(tvInfo);
        row.addView(mid);
        TextView more = new TextView(this);
        more.setText("⋮");
        more.setTextColor(Color.WHITE);
        more.setTextSize(22);
        more.setGravity(Gravity.CENTER);
        more.setPadding(24, 24, 24, 24);
        more.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                PopupMenu pm = new PopupMenu(RecordActivity.this, v);
                pm.getMenu().add("播放");
                pm.getMenu().add("删除");
                pm.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    public boolean onMenuItemClick(android.view.MenuItem m2) {
                        String t = m2.getTitle().toString();
                        if (t.equals("播放")) {
                            try { playInApp(it.path, it.title); } catch (Throwable e) { Toast.makeText(RecordActivity.this, "打不开", Toast.LENGTH_SHORT).show(); }
                        } else if (t.equals("删除")) {
                            HistoryStore.removeAt(RecordActivity.this, idx);
                            hist = HistoryStore.load(RecordActivity.this);
                            rowOrder.clear();
                            rebuild();
                        }
                        return true;
                    }
                });
                pm.show();
            }
        });
        row.addView(more);
        parent.addView(row);
        infoRef.set(tvInfo);
    }

    private void playInApp(String path, String title) {
        Intent i = new Intent(this, NativePlayerActivity.class);
        i.putExtra("url", path);
        i.putExtra("title", title == null ? "播放" : title);
        i.putExtra("kernel", "native");
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    private void showDlMenu(View anchor, final DlManager.DlJob j) {
        PopupMenu pm = new PopupMenu(this, anchor);
        if (j.hls) {
            if (j.active) {
                pm.getMenu().add("暂停");
                pm.getMenu().add("结束(合并MP4)");
            } else if (j.paused) {
                pm.getMenu().add("开始");
                pm.getMenu().add("结束(合并MP4)");
            } else if (j.done) {
                pm.getMenu().add("播放");
            }
        } else {
            if (j.active) pm.getMenu().add("取消");
            if (j.done) pm.getMenu().add("播放");
        }
        pm.getMenu().add("删除");
        pm.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            public boolean onMenuItemClick(android.view.MenuItem it) {
                String t = it.getTitle().toString();
                if (t.equals("暂停")) DlManager.pauseHls(j.id);
                else if (t.equals("开始")) DlManager.resumeHls(j.id);
                else if (t.equals("结束(合并MP4)")) DlManager.finishHls(j.id);
                else if (t.equals("取消")) DlManager.cancel(j.id);
                else if (t.equals("播放")) {
                    String pth = "file://" + j.file.getAbsolutePath();
                    if (j.hls && j.done) {
                        // done HLS 实体在相册，取历史最新一条播放
                        java.util.ArrayList<HistoryStore.Item> hh = HistoryStore.load(RecordActivity.this);
                        if (!hh.isEmpty()) pth = hh.get(0).path;
                    }
                    try { playInApp(pth, j.title); } catch (Throwable e) { Toast.makeText(RecordActivity.this, "打不开", Toast.LENGTH_SHORT).show(); }
                } else if (t.equals("删除")) { DlManager.cancel(j.id); }
                rebuild();
                return true;
            }
        });
        pm.show();
    }

    // 按抖音流地址后缀标注画质：优先原画 > 蓝光 > 高清，其余不标
    private static String qualify(RecManager.RecJob j) {
        String u = (j.url != null ? j.url : "") + " " + (j.name != null ? j.name : "");
        String q = null;
        if (u.contains("_or4")) q = "原画";
        else if (u.contains("_uhd")) q = "蓝光";
        else if (u.contains("_hd")) q = "高清";
        return q == null ? j.name : "抖音·" + q + " " + j.name;
    }

    private String buildInfo(RecManager.RecJob j, boolean live) {
        long secs = j.secs + (live && j.startTs > 0 ? (System.currentTimeMillis() - j.startTs) / 1000 : 0);
        return "录制时长: " + fmtDur(secs)
            + "\n录制大小: " + fmtSize(j.bytes)
            + "\n录制状态: " + (live ? "录制中" : (j.state != null ? j.state : "暂停录制"));
    }

    private void addRow(LinearLayout parent, final RecManager.RecJob j, final boolean live, final java.util.concurrent.atomic.AtomicReference<TextView> infoRef) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 24, 0, 24);

        // 左侧占位缩略块（▶）
        FrameLayout thumb = new FrameLayout(this);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(140, 90);
        tp.rightMargin = 24;
        thumb.setLayoutParams(tp);
        thumb.setBackgroundColor(0xFF1E242E);
        TextView play = new TextView(this);
        play.setText("▶");
        play.setTextColor(0xFF8A919E);
        play.setGravity(Gravity.CENTER);
        thumb.addView(play, new FrameLayout.LayoutParams(-1, -1));
        row.addView(thumb);

        // 中间信息
        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, -2);
        mp.weight = 1;
        mid.setLayoutParams(mp);

        TextView tvName = new TextView(this);
        tvName.setText(qualify(j));
        tvName.setTextColor(Color.WHITE);
        tvName.setTextSize(16);
        tvName.setTypeface(Typeface.DEFAULT_BOLD);
        tvName.setSingleLine(true);
        mid.addView(tvName);

        TextView tvInfo = new TextView(this);
        tvInfo.setText(buildInfo(j, live));
        if (infoRef != null) infoRef.set(tvInfo);
        tvInfo.setTextColor(0xFF8A919E);
        tvInfo.setTextSize(13);
        tvInfo.setLineSpacing(4, 1);
        mid.addView(tvInfo);
        row.addView(mid);

        // 右侧 ⋮ 菜单
        TextView more = new TextView(this);
        more.setText("⋮");
        more.setTextColor(Color.WHITE);
        more.setTextSize(22);
        more.setGravity(Gravity.CENTER);
        more.setPadding(24, 24, 24, 24);
        more.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                RecManager.RecJob cur = RecManager.recJobs.get(j.id);
                boolean nowLive = cur != null && cur.active;  // 点击时解析，避免行复用后菜单过期
                showMenu(v, cur != null ? cur : j, nowLive);
            }
        });
        row.addView(more);

        parent.addView(row);
    }

    private void showMenu(View anchor, RecManager.RecJob j, boolean live) {
        PopupMenu pm = new PopupMenu(this, anchor);
        if (live) {
            pm.getMenu().add("暂停").setOnMenuItemClickListener(new android.view.MenuItem.OnMenuItemClickListener() {
                public boolean onMenuItemClick(android.view.MenuItem it) { RecManager.recStop(j.id); rebuild(); return true; }
            });
        } else {
            pm.getMenu().add("打开").setOnMenuItemClickListener(new android.view.MenuItem.OnMenuItemClickListener() {
                public boolean onMenuItemClick(android.view.MenuItem it) { play(j); return true; }
            });
            pm.getMenu().add("开始").setOnMenuItemClickListener(new android.view.MenuItem.OnMenuItemClickListener() {
                public boolean onMenuItemClick(android.view.MenuItem it) { RecManager.recContinue(j.id); rebuild(); return true; }
            });
        }
        pm.getMenu().add("结束录制(转MP4)").setOnMenuItemClickListener(new android.view.MenuItem.OnMenuItemClickListener() {
            public boolean onMenuItemClick(android.view.MenuItem it) {
                RecManager.recFinish(j.id);
                rebuild();
                return true;
            }
        });
        pm.getMenu().add("复制下载地址").setOnMenuItemClickListener(new android.view.MenuItem.OnMenuItemClickListener() {
            public boolean onMenuItemClick(android.view.MenuItem it) {
                try {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("url", j.url != null ? j.url : ""));
                    Toast.makeText(RecordActivity.this, "已复制", Toast.LENGTH_SHORT).show();
                } catch (Throwable t) { Toast.makeText(RecordActivity.this, "复制失败", Toast.LENGTH_SHORT).show(); }
                return true;
            }
        });
        pm.getMenu().add("打开所在目录").setOnMenuItemClickListener(new android.view.MenuItem.OnMenuItemClickListener() {
            public boolean onMenuItemClick(android.view.MenuItem it) { openDir(); return true; }
        });
        pm.getMenu().add("打开录制合并目录").setOnMenuItemClickListener(new android.view.MenuItem.OnMenuItemClickListener() {
            public boolean onMenuItemClick(android.view.MenuItem it) { openMergedDir(); return true; }
        });
        pm.getMenu().add("取消").setOnMenuItemClickListener(new android.view.MenuItem.OnMenuItemClickListener() {
            public boolean onMenuItemClick(android.view.MenuItem it) { RecManager.recCancel(j.id); rebuild(); return true; }
        });
        pm.show();
    }

    private void openMergedDir() {
        // SAF 直达应用专属目录，绕开 /Android/data 权限（避免文件管理器"工作区创建失败"）
        try {
            android.net.Uri doc = android.provider.DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents", "primary:Android/data/com.wink.xgjhome/files/录制合并");
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(doc, android.provider.DocumentsContract.Document.MIME_TYPE_DIR);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            return;
        } catch (Throwable ignored) {}
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            i.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI,
                android.provider.DocumentsContract.buildDocumentUri("com.android.externalstorage.documents",
                    "primary:Android/data/com.wink.xgjhome/files"));
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable t) { openDir(); }
    }

    private void openDir() {
        // 1) MT 管理器 OpenFileActivity（data+type 都给）
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setClassName("bin.mt.plus", "bin.mt.plus.OpenFileActivity");
            i.setDataAndType(android.net.Uri.parse("file:///storage/emulated/0/Movies/录制"), "resource/folder");
            i.putExtra("path", "/storage/emulated/0/Movies/录制");
            i.putExtra("com.bin.mt.plus.path", "/storage/emulated/0/Movies/录制");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
            return;
        } catch (Throwable ignored) {}
        // 2) MT MainLightIcon
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setClassName("bin.mt.plus", "bin.mt.plus.MainLightIcon");
            i.setDataAndType(android.net.Uri.parse("file:///storage/emulated/0/Movies/录制"), "resource/folder");
            i.putExtra("path", "/storage/emulated/0/Movies/录制");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
            return;
        } catch (Throwable ignored) {}
        // 3) 系统目录选择器，直接定位到 Movies/录制
        try {
            android.net.Uri dir = android.net.Uri.parse(
                "content://com.android.externalstorage.documents/document/primary:Movies/录制");
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            i.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, dir);
            startActivity(i);
            return;
        } catch (Throwable ignored) {}
        // 兜底：拉起 MT 主界面
        try {
            Intent i = getPackageManager().getLaunchIntentForPackage("bin.mt.plus");
            if (i != null) { startActivity(i); Toast.makeText(this, "进 Movies/录制 目录", Toast.LENGTH_LONG).show(); return; }
        } catch (Throwable ignored) {}
        Toast.makeText(this, "请到 Movies/录制 目录查看", Toast.LENGTH_LONG).show();
    }

    private void play(RecManager.RecJob j) {
        try {
            if (j.storeUri == null) { Toast.makeText(this, "文件不存在", Toast.LENGTH_SHORT).show(); return; }
            Intent it = new Intent(this, HomeActivity.class);
            it.putExtra("autoUrl", j.storeUri.toString());
            startActivity(it);
        } catch (Throwable t) {
            Toast.makeText(this, "打开失败: " + t, Toast.LENGTH_SHORT).show();
        }
    }
}
