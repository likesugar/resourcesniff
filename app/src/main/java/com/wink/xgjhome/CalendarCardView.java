package com.wink.xgjhome;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/** 首页日历卡：月视图 + holiday-cn 节假日(休/班) + 每日签到 + 自定义提醒(到点通知)。扁平风格 */
public class CalendarCardView extends LinearLayout {

    private final Context ctx;
    private boolean dark;
    private int year, month; // month: 0-11
    private LinearLayout gridHost, remindList;
    private TextView monthLabel, signBtn, signInfo;
    private final Map<String, JSONObject> holidays = new HashMap<String, JSONObject>(); // date->obj
    private String holidayYearLoaded = "";

    private static final int ACCENT = 0xFF315CDE;
    private static final int RED = 0xFFFF6B6B;
    private static final int BLUE_W = 0xFF5B8DEF;

    public CalendarCardView(Context c) {
        super(c);
        ctx = c;
        Calendar cal = Calendar.getInstance();
        year = cal.get(Calendar.YEAR);
        month = cal.get(Calendar.MONTH);
        setOrientation(VERTICAL);
        build();
    }

    // ---------- 外观 ----------
    private int bgCard() { return dark ? 0xFF111418 : 0xFFFFFFFF; }
    private int fgMain() { return dark ? Color.WHITE : 0xFF1F2329; }
    private int fgSub() { return dark ? 0xFF9AA3AE : 0xFF8A94A6; }
    private int cellBg() { return dark ? 0xFF171B21 : 0xFFF2F6FF; }

    private GradientDrawable flatBg(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(radiusDp));
        g.setColor(color);
        return g;
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    public void setDark(boolean d) {
        dark = d;
        build();
    }

    private void build() {
        removeAllViews();
        int pad = dp(16);
        setPadding(pad, pad, pad, pad);
        setBackground(flatBg(bgCard(), 18));

        // 标题行: 📅 日历 + 月份切换
        LinearLayout head = new LinearLayout(ctx);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(ctx);
        title.setText("📅 日历");
        title.setTextSize(17); title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(fgMain());
        head.addView(title, new LayoutParams(0, -2, 1f));

        TextView prev = navBtn("‹");
        prev.setOnClickListener(new OnClickListener() { public void onClick(View v) {
            month--; if (month < 0) { month = 11; year--; }
            loadHolidaysAndBuildGrid();
        }});
        head.addView(prev);
        monthLabel = new TextView(ctx);
        monthLabel.setTextSize(15); monthLabel.setTypeface(Typeface.DEFAULT_BOLD);
        monthLabel.setTextColor(fgMain());
        monthLabel.setPadding(dp(10), 0, dp(10), 0);
        head.addView(monthLabel);
        TextView next = navBtn("›");
        next.setOnClickListener(new OnClickListener() { public void onClick(View v) {
            month++; if (month > 11) { month = 0; year++; }
            loadHolidaysAndBuildGrid();
        }});
        head.addView(next);
        addView(head);

        // 星期表头
        LinearLayout week = new LinearLayout(ctx);
        week.setPadding(0, dp(10), 0, dp(4));
        String[] wd = {"日", "一", "二", "三", "四", "五", "六"};
        for (int i = 0; i < 7; i++) {
            TextView t = new TextView(ctx);
            t.setText(wd[i]); t.setTextSize(12);
            t.setGravity(Gravity.CENTER);
            t.setTextColor(i == 0 || i == 6 ? RED : fgSub());
            week.addView(t, new LayoutParams(0, -2, 1f));
        }
        addView(week);

        gridHost = new LinearLayout(ctx);
        gridHost.setOrientation(VERTICAL);
        addView(gridHost);

        // 签到行
        LinearLayout signRow = new LinearLayout(ctx);
        signRow.setGravity(Gravity.CENTER_VERTICAL);
        signRow.setPadding(0, dp(14), 0, 0);
        signBtn = new TextView(ctx);
        signBtn.setTextSize(14); signBtn.setTypeface(Typeface.DEFAULT_BOLD);
        signBtn.setGravity(Gravity.CENTER);
        signBtn.setPadding(dp(22), dp(9), dp(22), dp(9));
        signBtn.setOnClickListener(new OnClickListener() { public void onClick(View v) { doSign(); }});
        signRow.addView(signBtn);
        signInfo = new TextView(ctx);
        signInfo.setTextSize(13); signInfo.setTextColor(fgSub());
        signInfo.setPadding(dp(14), 0, 0, 0);
        signRow.addView(signInfo, new LayoutParams(0, -2, 1f));
        addView(signRow);
        refreshSignUi();

        // 提醒行
        LinearLayout rHead = new LinearLayout(ctx);
        rHead.setGravity(Gravity.CENTER_VERTICAL);
        rHead.setPadding(0, dp(16), 0, dp(6));
        TextView rTitle = new TextView(ctx);
        rTitle.setText("⏰ 提醒");
        rTitle.setTextSize(15); rTitle.setTypeface(Typeface.DEFAULT_BOLD);
        rTitle.setTextColor(fgMain());
        rHead.addView(rTitle, new LayoutParams(0, -2, 1f));
        TextView addBtn = new TextView(ctx);
        addBtn.setText("＋ 新建");
        addBtn.setTextSize(13);
        addBtn.setTextColor(Color.WHITE);
        addBtn.setBackground(flatBg(ACCENT, 12));
        addBtn.setPadding(dp(14), dp(7), dp(14), dp(7));
        addBtn.setOnClickListener(new OnClickListener() { public void onClick(View v) { showAddReminder(); }});
        rHead.addView(addBtn);
        addView(rHead);

        remindList = new LinearLayout(ctx);
        remindList.setOrientation(VERTICAL);
        addView(remindList);

        loadHolidaysAndBuildGrid();
        refreshReminders();
        try { fireDue(ctx); scheduleNext(ctx); } catch (Throwable ignored) {}
    }

    private TextView navBtn(String s) {
        TextView t = new TextView(ctx);
        t.setText(s); t.setTextSize(16);
        t.setTextColor(fgMain());
        t.setGravity(Gravity.CENTER);
        t.setBackground(flatBg(cellBg(), 10));
        t.setPadding(dp(12), dp(6), dp(12), dp(6));
        return t;
    }

    // 当天提醒弹窗
    private void showDayReminders(final String date) {
        JSONArray arr = reminders();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(VERTICAL);
        int p = dp(18);
        box.setPadding(p, dp(10), p, 0);
        int cnt = 0;
        for (int i = 0; i < arr.length(); i++) {
            try {
                final JSONObject o = arr.getJSONObject(i);
                boolean isDaily = o.optBoolean("daily");
                String oday = df.format(new Date(o.optLong("ts")));
                if (!isDaily && !date.equals(oday)) continue;
                if (isDaily && date.compareTo(oday) < 0) continue;  // 每日: 从开始日起
                cnt++;
                LinearLayout row = new LinearLayout(ctx);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setBackground(flatBg(cellBg(), 10));
                row.setPadding(dp(12), dp(8), dp(12), dp(8));
                LayoutParams rlp = new LayoutParams(-1, -2);
                rlp.setMargins(0, dp(6), 0, 0);
                TextView t = new TextView(ctx);
                String when = isDaily ? "每天 " + new SimpleDateFormat("HH:mm", Locale.US).format(new Date(o.optLong("ts"))) : new SimpleDateFormat("HH:mm", Locale.US).format(new Date(o.optLong("ts")));
                t.setText(when + "  " + o.optString("t"));
                t.setTextSize(13); t.setTextColor(fgMain());
                row.addView(t, new LayoutParams(0, -2, 1f));
                TextView ok = new TextView(ctx);
                ok.setText("确认"); ok.setTextSize(13);
                ok.setTextColor(Color.WHITE);
                ok.setBackground(flatBg(ACCENT, 8));
                ok.setPadding(dp(12), dp(6), dp(12), dp(6));
                final int idx = i;
                ok.setOnClickListener(new OnClickListener() { public void onClick(View v) {
                    JSONArray arr2 = reminders();
                    JSONArray out = new JSONArray();
                    try { for (int j = 0; j < arr2.length(); j++) if (j != idx) out.put(arr2.getJSONObject(j)); } catch (Throwable ignored) {}
                    saveReminders(out);
                    Toast.makeText(ctx, "已完成", Toast.LENGTH_SHORT).show();
                    buildGrid();
                }});
                row.addView(ok);
                box.addView(row, rlp);
            } catch (Throwable ignored) {}
        }
        if (cnt == 0) {
            TextView e = new TextView(ctx);
            e.setText("这一天没有提醒");
            e.setTextSize(13); e.setTextColor(fgSub());
            box.addView(e);
        }
        new AlertDialog.Builder(ctx).setTitle(date).setView(box)
            .setPositiveButton("关闭", null).show();
    }

    // ---------- holiday-cn ----------
    private void loadHolidaysAndBuildGrid() {
        monthLabel.setText(year + "年" + (month + 1) + "月");
        final String yKey = String.valueOf(year);
        if (holidayYearLoaded.equals(yKey)) { buildGrid(); return; }
        holidays.clear();
        buildGrid(); // 先画无节假日版
        new Thread(new Runnable() { public void run() {
            try {
                java.io.File f = new java.io.File(ctx.getFilesDir(), "holiday_" + yKey + ".json");
                String raw;
                if (f.exists() && f.length() > 50) {
                    raw = readFile(f);
                } else {
                    raw = fetch("https://raw.githubusercontent.com/NateScarlet/holiday-cn/master/" + yKey + ".json");
                    if (raw == null || raw.length() < 50)
                        raw = fetch("https://gh-proxy.com/https://raw.githubusercontent.com/NateScarlet/holiday-cn/master/" + yKey + ".json");
                    if (raw != null && raw.length() > 50) writeFile(f, raw);
                }
                if (raw != null && raw.length() > 50) {
                    JSONObject o = new JSONObject(raw);
                    JSONArray days = o.optJSONArray("days");
                    if (days != null) {
                        synchronized (holidays) {
                            for (int i = 0; i < days.length(); i++) {
                                JSONObject d = days.getJSONObject(i);
                                holidays.put(d.optString("date"), d);
                            }
                        }
                        holidayYearLoaded = yKey;
                        post(new Runnable() { public void run() { buildGrid(); }});
                    }
                }
            } catch (Throwable ignored) {}
        }}).start();
    }

    private String fetch(String url) {
        try {
            java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            c.setConnectTimeout(8000); c.setReadTimeout(8000);
            java.io.InputStream in = c.getInputStream();
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[4096]; int r;
            while ((r = in.read(b)) > 0) bo.write(b, 0, r);
            in.close();
            return bo.toString("UTF-8");
        } catch (Throwable e) { return null; }
    }

    private String readFile(java.io.File f) {
        try {
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[4096]; int r;
            while ((r = in.read(b)) > 0) bo.write(b, 0, r);
            in.close();
            return bo.toString("UTF-8");
        } catch (Throwable e) { return null; }
    }

    private void writeFile(java.io.File f, String s) {
        try {
            java.io.FileOutputStream o = new java.io.FileOutputStream(f);
            o.write(s.getBytes("UTF-8")); o.close();
        } catch (Throwable ignored) {}
    }

    // ---------- 月网格 ----------
    private void buildGrid() {
        gridHost.removeAllViews();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        Calendar cal = Calendar.getInstance();
        cal.set(year, month, 1);
        int firstDow = cal.get(Calendar.DAY_OF_WEEK) - 1; // 0=周日
        int maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        Calendar today = Calendar.getInstance();
        String todayStr = df.format(today.getTime());

        LinearLayout row = new LinearLayout(ctx);
        gridHost.addView(row, new LayoutParams(-1, -2));
        for (int b = 0; b < firstDow; b++) {   // 月首前置空白
            TextView pad = new TextView(ctx);
            pad.setTextSize(14);
            row.addView(pad, new LayoutParams(0, -2, 1f));
        }
        for (int day = 1; day <= maxDay; day++) {
            int pos = firstDow + day - 1;
            if (pos % 7 == 0) { row = new LinearLayout(ctx); gridHost.addView(row, new LayoutParams(-1, -2)); }
            cal.set(year, month, day);
            String ds = df.format(cal.getTime());
            JSONObject hol = holidays.get(ds);
            boolean isToday = ds.equals(todayStr);
            boolean weekend = pos % 7 == 0 || pos % 7 == 6;

            LinearLayout cell = new LinearLayout(ctx);
            cell.setOrientation(VERTICAL);
            cell.setGravity(Gravity.CENTER);
            LayoutParams clp = new LayoutParams(0, -2, 1f);
            clp.setMargins(dp(2), dp(2), dp(2), dp(2));
            cell.setBackground(flatBg(isToday ? ACCENT : cellBg(), 10));
            cell.setPadding(dp(2), dp(4), dp(2), dp(4));

            TextView num = new TextView(ctx);
            num.setText(String.valueOf(day));
            num.setTextSize(13); num.setTypeface(Typeface.DEFAULT_BOLD);
            num.setGravity(Gravity.CENTER);
            if (isToday) num.setTextColor(Color.WHITE);
            else if (hol != null && hol.optBoolean("isOffDay")) num.setTextColor(RED);
            else if (hol != null) num.setTextColor(BLUE_W);
            else if (weekend) num.setTextColor(dark ? 0xFFC77 : RED);
            else num.setTextColor(fgMain());
            cell.addView(num);
            final String fds = ds;
            cell.setOnClickListener(new OnClickListener() { public void onClick(View v) { showDayReminders(fds); }});

            String mark = null; int markColor = fgSub();
            if (hol != null) {
                mark = hol.optBoolean("isOffDay") ? "休" : "班";
                markColor = hol.optBoolean("isOffDay") ? RED : BLUE_W;
                if (isToday) markColor = Color.WHITE;
            } else if (isToday) {
                mark = "今"; markColor = Color.WHITE;
            }
            if (mark != null) {
                TextView mk = new TextView(ctx);
                mk.setText(mark); mk.setTextSize(9);
                mk.setTextColor(markColor);
                mk.setGravity(Gravity.CENTER);
                cell.addView(mk);
            } else {
                TextView mk = new TextView(ctx);
                mk.setText(" "); mk.setTextSize(9);
                cell.addView(mk);
            }
            row.addView(cell, clp);
        }
        // 补齐末行
        if (row != null) {
            int rest = (firstDow + maxDay) % 7;
            for (int i = rest; i < 7 && i > 0; i++) {
                TextView pad = new TextView(ctx);
                row.addView(pad, new LayoutParams(0, -2, 1f));
            }
        }
    }

    // ---------- 签到 ----------
    private void refreshSignUi() {
        android.content.SharedPreferences sp = ctx.getSharedPreferences("cal", 0);
        String last = sp.getString("sign_last", "");
        int days = sp.getInt("sign_days", 0);
        boolean signed = last.equals(new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()));
        if (signed) {
            signBtn.setText("已签到");
            signBtn.setTextColor(dark ? 0xFF9AA3AE : 0xFF8A94A6);
            signBtn.setBackground(flatBg(cellBg(), 12));
        } else {
            signBtn.setText("签到");
            signBtn.setTextColor(Color.WHITE);
            signBtn.setBackground(flatBg(ACCENT, 12));
        }
        signInfo.setText(days > 0 ? "已连签 " + days + " 天" : "今天还没签到");
    }

    private void doSign() {
        android.content.SharedPreferences sp = ctx.getSharedPreferences("cal", 0);
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        if (sp.getString("sign_last", "").equals(today)) { Toast.makeText(ctx, "今天已签过", Toast.LENGTH_SHORT).show(); return; }
        Calendar y = Calendar.getInstance(); y.add(Calendar.DATE, -1);
        boolean consecutive = sp.getString("sign_last", "").equals(new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(y.getTime()));
        int days = consecutive ? sp.getInt("sign_days", 0) + 1 : 1;
        sp.edit().putString("sign_last", today).putInt("sign_days", days).apply();
        refreshSignUi();
        Toast.makeText(ctx, "签到成功，已连签 " + days + " 天", Toast.LENGTH_SHORT).show();
    }

    // ---------- 提醒 ----------
    private JSONArray reminders() {
        try {
            String raw = ctx.getSharedPreferences("cal", 0).getString("reminders", "[]");
            return new JSONArray(raw);
        } catch (Throwable e) { return new JSONArray(); }
    }

    private void saveReminders(JSONArray arr) {
        ctx.getSharedPreferences("cal", 0).edit().putString("reminders", arr.toString()).apply();
        try { scheduleNext(ctx); } catch (Throwable ignored) {}
    }

    private void refreshReminders() {
        remindList.removeAllViews();
        JSONArray arr = reminders();
        if (arr.length() == 0) {
            TextView e = new TextView(ctx);
            e.setText("暂无提醒，点右上角＋新建");
            e.setTextSize(13); e.setTextColor(fgSub());
            e.setPadding(dp(12), dp(10), dp(12), dp(10));
            remindList.addView(e);
            return;
        }
        android.widget.ScrollView sv = new android.widget.ScrollView(ctx);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout inner = new LinearLayout(ctx);
        inner.setOrientation(VERTICAL);
        int rowH = dp(56);
        sv.addView(inner, new LayoutParams(-1, android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        for (int i = 0; i < arr.length(); i++) {
            try {
                final JSONObject o = arr.getJSONObject(i);
                final int idx = i;
                // 卡片框
                LinearLayout card = new LinearLayout(ctx);
                card.setOrientation(VERTICAL);
                card.setBackground(flatBg(cellBg(), 12));
                card.setPadding(dp(14), dp(10), dp(14), dp(10));
                LayoutParams clp = new LayoutParams(-1, -2);
                clp.setMargins(0, dp(6), 0, 0);
                // 行1: 内容
                LinearLayout row = new LinearLayout(ctx);
                row.setGravity(Gravity.CENTER_VERTICAL);
                TextView t = new TextView(ctx);
                String when = new SimpleDateFormat("MM-dd HH:mm", Locale.US).format(new Date(o.optLong("ts")));
                t.setText(o.optString("t") + (o.optBoolean("daily") ? " · 每天" : "") + "\n" + when);
                t.setTextSize(13); t.setTextColor(fgMain());
                row.addView(t, new LayoutParams(0, -2, 1f));
                // 确认按钮
                TextView ok = new TextView(ctx);
                ok.setText("✓ 确认"); ok.setTextSize(13);
                ok.setTextColor(Color.WHITE);
                ok.setGravity(Gravity.CENTER);
                ok.setBackground(flatBg(ACCENT, 10));
                ok.setPadding(dp(14), dp(7), dp(14), dp(7));
                ok.setOnClickListener(new OnClickListener() { public void onClick(View v) {
                    JSONArray arr2 = reminders();
                    JSONArray out = new JSONArray();
                    try { for (int j = 0; j < arr2.length(); j++) if (j != idx) out.put(arr2.getJSONObject(j)); } catch (Throwable ignored) {}
                    saveReminders(out);
                    Toast.makeText(ctx, "已完成", Toast.LENGTH_SHORT).show();
                    refreshReminders();
                }});
                row.addView(ok);
                card.addView(row);
                inner.addView(card, new LayoutParams(-1, android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
            } catch (Throwable ignored) {}
        }
        // 超过4条限高可滑动
        int maxH = rowH * 4 + dp(24);
        LayoutParams svlp = new LayoutParams(-1, Math.min(maxH, android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        remindList.addView(sv, svlp);
    }

    private void showAddReminder() {
        final LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(VERTICAL);
        int p = dp(20);
        box.setPadding(p, p, p, 0);
        final EditText et = new EditText(ctx);
        et.setHint("提醒内容");
        et.setSingleLine(true);
        box.addView(et);
        final long[] chosen = { System.currentTimeMillis() + 3600_000L };
        final TextView when = new TextView(ctx);
        refreshWhen(when, chosen[0]);
        when.setTextSize(14);
        when.setTextColor(dark ? 0xFFB4C5FF : 0xFF315CDE);
        when.setPadding(0, dp(14), 0, dp(4));
        box.addView(when);
        final boolean[] daily = { false };
        final TextView dailyT = new TextView(ctx);
        dailyT.setText("☐ 每天重复");
        dailyT.setTextSize(14); dailyT.setTextColor(fgMain());
        dailyT.setPadding(0, dp(8), 0, dp(8));
        dailyT.setOnClickListener(new OnClickListener() { public void onClick(View v) {
            daily[0] = !daily[0];
            dailyT.setText(daily[0] ? "☑ 每天重复" : "☐ 每天重复");
        }});
        box.addView(dailyT);
        when.setOnClickListener(new OnClickListener() { public void onClick(View v) {
            Calendar c0 = Calendar.getInstance(); c0.setTimeInMillis(chosen[0]);
            new DatePickerDialog(ctx, new DatePickerDialog.OnDateSetListener() {
                public void onDateSet(DatePicker dp2, int y2, int m2, int d2) {
                    Calendar c1 = Calendar.getInstance(); c1.setTimeInMillis(chosen[0]);
                    c1.set(y2, m2, d2);
                    chosen[0] = c1.getTimeInMillis();
                    new TimePickerDialog(ctx, new TimePickerDialog.OnTimeSetListener() {
                        public void onTimeSet(TimePicker tp, int h2, int mi2) {
                            Calendar c2 = Calendar.getInstance(); c2.setTimeInMillis(chosen[0]);
                            c2.set(Calendar.HOUR_OF_DAY, h2); c2.set(Calendar.MINUTE, mi2);
                            chosen[0] = c2.getTimeInMillis();
                            refreshWhen(when, chosen[0]);
                        }
                    }, c1.get(Calendar.HOUR_OF_DAY), c1.get(Calendar.MINUTE), true).show();
                }
            }, c0.get(Calendar.YEAR), c0.get(Calendar.MONTH), c0.get(Calendar.DAY_OF_MONTH)).show();
        }});

        new AlertDialog.Builder(ctx)
            .setTitle("新建日程")
            .setView(box)
            .setPositiveButton("确定", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface dlg, int w) {
                    String t = et.getText().toString().trim();
                    if (t.length() == 0) { Toast.makeText(ctx, "内容为空", Toast.LENGTH_SHORT).show(); return; }
                    try {
                        JSONArray arr = reminders();
                        JSONObject o = new JSONObject();
                        o.put("t", t); o.put("ts", chosen[0]); o.put("daily", daily[0]);
                        arr.put(o);
                        saveReminders(arr);
                        refreshReminders();
                    } catch (Throwable ignored) {}
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void refreshWhen(TextView tv, long ts) {
        tv.setText("时间: " + new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(ts)) + " (点击修改)");
    }

    // ---------- 到点触发(每30s由首页调用) ----------
    public void tick() {
        try { fireDue(ctx); } catch (Throwable ignored) {}
    }

    /** 到期触发: 通知+标记待确认(页面tick与后台闹钟共用) */
    public static void fireDue(Context c) {
        try {
            org.json.JSONArray arr = new org.json.JSONArray(c.getSharedPreferences("cal", 0).getString("reminders", "[]"));
            long now = System.currentTimeMillis();
            boolean changed = false;
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.getJSONObject(i);
                long ts = o.optLong("ts");
                boolean daily = o.optBoolean("daily");
                long due = ts;
                if (daily) { // 每日: 对齐到今天的同时刻
                    java.util.Calendar t = java.util.Calendar.getInstance();
                    java.util.Calendar d = java.util.Calendar.getInstance();
                    d.setTimeInMillis(ts);
                    t.set(java.util.Calendar.HOUR_OF_DAY, d.get(java.util.Calendar.HOUR_OF_DAY));
                    t.set(java.util.Calendar.MINUTE, d.get(java.util.Calendar.MINUTE));
                    due = t.getTimeInMillis();
                    if (o.optBoolean("fired") && !o.optString("fired_date", "").equals(
                            new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date()))) {
                        o.put("fired", false);
                        o.put("fired_date", new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date()));
                        changed = true;
                    }
                }
                if (due <= now && !o.optBoolean("fired")) {
                    notifyUserStatic(c, o.optString("t"), ts, daily);
                    o.put("fired", true);
                    changed = true;
                }
            }
            if (changed) {
                c.getSharedPreferences("cal", 0).edit().putString("reminders", arr.toString()).apply();
            }
        } catch (Throwable ignored) {}
    }

    /** 安排最近一次提醒的后台闹钟 */
    public static void scheduleNext(Context c) {
        try {
            org.json.JSONArray arr = new org.json.JSONArray(c.getSharedPreferences("cal", 0).getString("reminders", "[]"));
            long best = Long.MAX_VALUE;
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.getJSONObject(i);
                long ts = o.optLong("ts");
                if (o.optBoolean("daily")) {
                    java.util.Calendar t = java.util.Calendar.getInstance();
                    java.util.Calendar d = java.util.Calendar.getInstance();
                    d.setTimeInMillis(ts);
                    t.set(java.util.Calendar.HOUR_OF_DAY, d.get(java.util.Calendar.HOUR_OF_DAY));
                    t.set(java.util.Calendar.MINUTE, d.get(java.util.Calendar.MINUTE));
                    long due = t.getTimeInMillis();
                    if (due <= System.currentTimeMillis()) due += 86400000L;
                    if (due < best) best = due;
                } else if (ts > System.currentTimeMillis() && ts < best) best = ts;
            }
            android.app.AlarmManager am = (android.app.AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(c, 46001,
                new Intent(c, AlarmReceiver.class), android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
            am.cancel(pi);
            if (best != Long.MAX_VALUE) {
                if (android.os.Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms())
                    am.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, best, pi);
                else
                    am.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, best, pi);
            }
        } catch (Throwable ignored) {}
    }

    /** 当日提醒(每日提醒每天都算) */
    private int dayCount(String date, org.json.JSONArray arr, java.text.SimpleDateFormat df) { return 0; }

    public static void confirmById(Context c, long id, boolean daily) {
        try {
            JSONArray arr = new JSONArray(c.getSharedPreferences("cal", 0).getString("reminders", "[]"));
            JSONArray out = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (!daily && o.optLong("ts") == id) continue;
                if (daily && o.optLong("ts") == id) {
                    Calendar cc = Calendar.getInstance();
                    cc.setTimeInMillis(o.optLong("ts")); cc.add(Calendar.DATE, 1);
                    o.put("ts", cc.getTimeInMillis());
                    o.put("fired", false);
                }
                out.put(o);
            }
            c.getSharedPreferences("cal", 0).edit().putString("reminders", out.toString()).apply();
        } catch (Throwable ignored) {}
    }

    private static void notifyUserStatic(Context cx, String text, long id, boolean daily) {
        CalendarCardView v = null;
        // 静态环境直接构造通知
        try {
            NotificationManager nm = (NotificationManager) cx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel("remind", "提醒", NotificationManager.IMPORTANCE_HIGH);
                nm.createNotificationChannel(ch);
            }
            Intent ci = new Intent(cx, ConfirmReceiver.class);
            ci.putExtra("id", id); ci.putExtra("daily", daily);
            android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(cx, (int) (id & 0x7fffffff), ci,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(cx, "remind")
                : new Notification.Builder(cx);
            b.setSmallIcon(android.R.drawable.ic_dialog_info)
             .setContentTitle("提醒")
             .setContentText(text)
             .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_send, "确认", pi).build())
             .setAutoCancel(true);
            nm.notify((int) (id & 0x7fffffff), b.build());
        } catch (Throwable ignored) {}
    }

    private void notifyUser(String text, long id, boolean daily) {
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel("remind", "提醒", NotificationManager.IMPORTANCE_HIGH);
                nm.createNotificationChannel(ch);
            }
            Intent ci = new Intent(ctx, ConfirmReceiver.class);
            ci.putExtra("id", id); ci.putExtra("daily", daily);
            PendingIntent pi = PendingIntent.getBroadcast(ctx, (int) (id & 0x7fffffff), ci,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(ctx, "remind")
                : new Notification.Builder(ctx);
            b.setSmallIcon(android.R.drawable.ic_dialog_info)
             .setContentTitle("提醒")
             .setContentText(text)
             .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_send, "确认", pi).build())
             .setAutoCancel(true);
            nm.notify((int) (id & 0x7fffffff), b.build());
        } catch (Throwable ignored) {}
    }
}
