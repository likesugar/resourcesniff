package com.wink.xgjhome;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** 首页紧凑日历卡: 今天日期+休班 + 签到 + 提醒摘要; 点击进完整日历页 */
public class HomeCalendarCompact extends LinearLayout {

    private final Activity act;
    private boolean dark;
    private TextView dateText, holidayText, signText, remindText;
    private LinearLayout signBtn;
    private LinearLayout medHost;

    public HomeCalendarCompact(Activity a) {
        super(a);
        act = a;
        setOrientation(VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        setPadding(pad, pad, pad, pad);

        dark = a.getSharedPreferences("settings", 0).getBoolean("dark", false);
        int fgMain = dark ? Color.WHITE : 0xFF1F2329;
        int fgSub = dark ? 0xFF9AA3AE : 0xFF8A94A6;

        // 行1: 日期 + 休/班 + ›
        LinearLayout r1 = new LinearLayout(a);
        r1.setGravity(Gravity.CENTER_VERTICAL);
        dateText = new TextView(a);
        dateText.setTextSize(20); dateText.setTypeface(Typeface.DEFAULT_BOLD);
        dateText.setTextColor(fgMain);
        r1.addView(dateText);
        holidayText = new TextView(a);
        holidayText.setTextSize(13);
        holidayText.setPadding((int) (10 * getResources().getDisplayMetrics().density), 0, 0, 0);
        r1.addView(holidayText);
        TextView arrow = new TextView(a);
        arrow.setText("›"); arrow.setTextSize(20);
        arrow.setTextColor(dark ? 0xFF8A94A6 : 0xFFC3CAD6);
        r1.addView(arrow, new LinearLayout.LayoutParams(0, -2, 1f) );
        // arrow weight trick: set weight via LayoutParams above then re-add
        r1.removeAllViews();
        r1.addView(dateText);
        r1.addView(holidayText);
        TextView spacer = new TextView(a);
        r1.addView(spacer, new LinearLayout.LayoutParams(0, 0, 1f));
        r1.addView(arrow);
        addView(r1);

        // 行2: 签到按钮 + 连签 + 提醒摘要
        LinearLayout r2 = new LinearLayout(a);
        r2.setGravity(Gravity.CENTER_VERTICAL);
        r2.setPadding(0, (int) (12 * getResources().getDisplayMetrics().density), 0, 0);
        signBtn = new LinearLayout(a);
        signBtn.setGravity(Gravity.CENTER);
        signBtn.setOrientation(HORIZONTAL);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius((int) (14 * getResources().getDisplayMetrics().density));
        g.setColor(dark ? 0xFF232A38 : 0xFFE8EEFF);
        signBtn.setBackground(g);
        signText = new TextView(a);
        signText.setTextSize(14); signText.setTypeface(Typeface.DEFAULT_BOLD);
        signText.setTextColor(dark ? 0xFFB4C5FF : 0xFF315CDE);
        signText.setPadding((int) (18 * getResources().getDisplayMetrics().density), (int) (9 * getResources().getDisplayMetrics().density), (int) (18 * getResources().getDisplayMetrics().density), (int) (9 * getResources().getDisplayMetrics().density));
        signBtn.addView(signText);
        signBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { doSign(); refresh(); }});
        r2.addView(signBtn);
        remindText = new TextView(a);
        remindText.setTextSize(13); remindText.setTextColor(fgSub);
        remindText.setPadding((int) (14 * getResources().getDisplayMetrics().density), 0, 0, 0);
        r2.addView(remindText, new LinearLayout.LayoutParams(0, -2, 1f));
        addView(r2);
        medHost = new LinearLayout(a);
        medHost.setOrientation(VERTICAL);
        addView(medHost);

        // 药物快速确认区(签到后面)
        medHost = new LinearLayout(act);
        medHost.setOrientation(VERTICAL);
        addView(medHost);

        setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            act.startActivity(new Intent(act, CalendarActivity.class));
        }});
        refresh();
    }


    private java.util.Set<String> medTaken(String day, int slot) {
        java.util.Set<String> out = new java.util.HashSet<String>();
        try {
            JSONObject o = new JSONObject(act.getSharedPreferences("cal", 0).getString("meds_taken", "{}"));
            JSONObject d = o.optJSONObject(day);
            if (d != null) {
                JSONArray a = d.optJSONArray(new String[]{"早","中","晚"}[slot]);
                if (a != null) for (int i = 0; i < a.length(); i++) out.add(a.optString(i));
            }
        } catch (Throwable ignored) {}
        return out;
    }

    private void toggleTaken(int slot, String name) {
        try {
            String day = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            android.content.SharedPreferences sp = act.getSharedPreferences("cal", 0);
            JSONObject o = new JSONObject(sp.getString("meds_taken", "{}"));
            JSONObject d = o.optJSONObject(day); if (d == null) d = new JSONObject();
            JSONArray a = d.optJSONArray(new String[]{"早","中","晚"}[slot]); if (a == null) a = new JSONArray();
            java.util.List<String> l = new java.util.ArrayList<String>();
            for (int i = 0; i < a.length(); i++) l.add(a.optString(i));
            if (l.contains(name)) l.remove(name); else l.add(name);
            JSONArray na = new JSONArray(); for (String x : l) na.put(x);
            d.put(new String[]{"早","中","晚"}[slot], na); o.put(day, d);
            sp.edit().putString("meds_taken", o.toString()).apply();
        } catch (Throwable ignored) {}
    }

    private void buildMedQuick() {
        medHost.removeAllViews();
        try {
            JSONObject mo = new JSONObject(act.getSharedPreferences("cal", 0).getString("meds", "{}"));
            JSONObject tm = new JSONObject(act.getSharedPreferences("cal", 0).getString("meds_times", "{}"));
            int[] def = {8 * 60, 12 * 60, 18 * 60 + 30};
            String[] slots = {"早", "中", "晚"};
            boolean dark = act.getSharedPreferences("settings", 0).getBoolean("dark", false);
            int fgMain = dark ? Color.WHITE : 0xFF1F2329;
            Calendar now = Calendar.getInstance();
            int nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
            // 当前时段: 早时间前=早; 早后中前=中; 中后=晚
            int[] mins = new int[3];
            for (int i = 0; i < 3; i++) mins[i] = tm.optInt(slots[i], def[i]);
            int cur = nowMin < mins[0] ? 0 : (nowMin < mins[1] ? 1 : 2);
            JSONArray a = mo.optJSONArray(slots[cur]);
            if (a == null) a = new JSONArray();
            java.util.Set<String> taken = medTaken(new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now.getTime()), cur);
            LinearLayout row = new LinearLayout(act);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, (int) (8 * getResources().getDisplayMetrics().density), 0, 0);
            TextView slotT = new TextView(act);
            int h = mins[cur] / 60, mi = mins[cur] % 60;
            slotT.setText(slots[cur] + String.format(java.util.Locale.US, " %02d:%02d", h, mi));
            slotT.setTextSize(14); slotT.setTypeface(Typeface.DEFAULT_BOLD);
            slotT.setTextColor(fgMain);
            row.addView(slotT);
            for (int j = 0; j < a.length(); j++) {
                final String dn = a.optString(j);
                final int si = cur;
                if (dn.isEmpty()) continue;
                TextView drug = new TextView(act);
                boolean ate = taken.contains(dn);
                drug.setText((ate ? "✓ " : "") + dn);
                drug.setTextSize(15); drug.setTypeface(Typeface.DEFAULT_BOLD);
                drug.setTextColor(ate ? 0xFF9CCC65 : fgMain);
                drug.setPadding((int) (12 * getResources().getDisplayMetrics().density), 0, 0, 0);
                drug.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                    toggleTaken(si, dn);
                    refresh();
                }});
                row.addView(drug);
            }
            medHost.addView(row);
        } catch (Throwable ignored) {}
    }

    public void setDark(boolean d) {
        dark = d;
        int fgMain = d ? Color.WHITE : 0xFF1F2329;
        int fgSub = d ? 0xFF9AA3AE : 0xFF8A94A6;
        if (dateText != null) dateText.setTextColor(fgMain);
        if (holidayText != null) holidayText.setTextColor(fgSub);
        if (signText != null) signText.setTextColor(d ? 0xFFB4C5FF : 0xFF315CDE);
        if (remindText != null) remindText.setTextColor(fgSub);
        if (medHost != null) buildMedQuick();
        refresh();
    }

    public void refresh() {
        try {
            dark = act.getSharedPreferences("settings", 0).getBoolean("dark", false);
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Date now = new Date();
            String today = df.format(now);
            dateText.setText("日历");
            // 节假日标记(读缓存)
            String hol = "";
            try {
                java.io.File f = new java.io.File(act.getFilesDir(), "holiday_" + (new SimpleDateFormat("yyyy", Locale.US).format(now)) + ".json");
                if (f.exists()) {
                    JSONObject o = new JSONObject(readFile(f));
                    JSONArray days = o.optJSONArray("days");
                    if (days != null) for (int i = 0; i < days.length(); i++) {
                        JSONObject d = days.getJSONObject(i);
                        if (today.equals(d.optString("date"))) {
                            hol = d.optString("name", "") + (d.optBoolean("isOffDay") ? " 休" : " 班");
                            break;
                        }
                    }
                }
            } catch (Throwable ignored) {}
            holidayText.setText(hol);
            holidayText.setTextColor(hol.endsWith("休") ? 0xFFFF6B6B : 0xFF5B8DEF);
            // 签到(自定义签到: 全签完=已签)
            android.content.SharedPreferences sp = act.getSharedPreferences("cal", 0);
            signText.setText(allSigned(today) ? "✓ 已签" : "签到");
            // 日程摘要: 最近一条
            String next = "";
            try {
                JSONArray arr = new JSONArray(sp.getString("schedules", "[]"));
                long nowMs = System.currentTimeMillis();
                long best = Long.MAX_VALUE;
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject r = arr.getJSONObject(i);
                    long ts = r.optLong("ts");
                    if (ts >= nowMs && ts < best) { best = ts; next = r.optString("t", "日程"); }
                }
            } catch (Throwable ignored) {}
            remindText.setText(next.isEmpty() ? "暂无日程" : "日程: " + next);
            buildMedQuick();
        } catch (Throwable ignored) {}
    }

    private String readFile(java.io.File f) throws Exception {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        java.io.FileInputStream in = new java.io.FileInputStream(f);
        byte[] b = new byte[8192]; int r;
        while ((r = in.read(b)) > 0) bo.write(b, 0, r);
        in.close();
        return bo.toString("UTF-8");
    }

    private boolean allSigned(String today) {
        try {
            JSONArray a = new JSONArray(act.getSharedPreferences("cal", 0).getString("signs", "[{\"n\":\"每日签到\"}]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o != null && !today.equals(o.optString("last", ""))) return false;
            }
            return true;
        } catch (Throwable e) { return false; }
    }

    private void doSign() {
        try {
            android.content.SharedPreferences sp = act.getSharedPreferences("cal", 0);
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            if (allSigned(today)) { Toast.makeText(act, "今天已签过", Toast.LENGTH_SHORT).show(); return; }
            JSONArray a = new JSONArray(sp.getString("signs", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o != null && !today.equals(o.optString("last", ""))) o.put("last", today);
            }
            sp.edit().putString("signs", a.toString()).apply();
            Toast.makeText(act, "已签到", Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {}
    }
}
