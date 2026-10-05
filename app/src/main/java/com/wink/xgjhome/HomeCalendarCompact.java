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
    private TextView dateText, holidayText, signText, remindText;
    private LinearLayout signBtn;
    private LinearLayout medHost;

    public HomeCalendarCompact(Activity a) {
        super(a);
        act = a;
        setOrientation(VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        setPadding(pad, pad, pad, pad);

        boolean dark = a.getSharedPreferences("settings", 0).getBoolean("dark", false);
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

    private LinearLayout medHost;

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
            boolean dark = act.getSharedPreferences("settings", 0).getBoolean("dark", false);
            int fgMain = dark ? Color.WHITE : 0xFF1F2329;
            String[] slots = {"早", "中", "晚"};
            String tday = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            for (int i = 0; i < 3; i++) {
                JSONArray a = mo.optJSONArray(slots[i]);
                if (a == null || a.length() == 0) continue;
                java.util.Set<String> taken = medTaken(tday, i);
                LinearLayout row = new LinearLayout(act);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, (int) (8 * getResources().getDisplayMetrics().density), 0, 0);
                TextView slotT = new TextView(act);
                slotT.setText(slots[i]);
                slotT.setTextSize(14); slotT.setTypeface(Typeface.DEFAULT_BOLD);
                slotT.setTextColor(fgMain);
                row.addView(slotT);
                for (int j = 0; j < a.length(); j++) {
                    final String dn = a.optString(j);
                    final int si = i;
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
            }
        } catch (Throwable ignored) {}
    }

    public void refresh() {
        try {
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Date now = new Date();
            String today = df.format(now);
            dateText.setText(new SimpleDateFormat("M月d日 EEEE", Locale.CHINA).format(now));
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
            // 签到
            android.content.SharedPreferences sp = act.getSharedPreferences("cal", 0);
            boolean signed = sp.getString("sign_last", "").equals(today);
            int days = sp.getInt("sign_days", 0);
            signText.setText(signed ? "✓ 已连签" + days + "天" : "签到");
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
            rebuildMeds();
        } catch (Throwable ignored) {}
    }

    // ---------- 首页药物确认区 ----------
    private static final String[] MED_SLOTS = {"早", "中", "晚"};

    private void rebuildMeds() {
        if (medHost == null) return;
        medHost.removeAllViews();
        try {
            boolean dark = act.getSharedPreferences("settings", 0).getBoolean("dark", false);
            JSONObject o = new JSONObject(act.getSharedPreferences("cal", 0).getString("meds", "{}"));
            String tday = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            JSONObject takenAll = new JSONObject(act.getSharedPreferences("cal", 0).getString("meds_taken", "{}"));
            JSONObject takenDay = takenAll.optJSONObject(tday); if (takenDay == null) takenDay = new JSONObject();
            int pad = (int) (8 * getResources().getDisplayMetrics().density);
            boolean any = false;
            for (int i = 0; i < 3; i++) {
                JSONArray a = o.optJSONArray(MED_SLOTS[i]);
                if (a == null || a.length() == 0) continue;
                any = true;
                TextView slot = new TextView(act);
                slot.setText(MED_SLOTS[i]);
                slot.setTextSize(14); slot.setTypeface(Typeface.DEFAULT_BOLD);
                slot.setTextColor(dark ? Color.WHITE : 0xFF1F2329);
                slot.setPadding(0, pad, 0, pad / 2);
                medHost.addView(slot);
                for (int j = 0; j < a.length(); j++) {
                    final String dn = a.optString(j);
                    JSONArray ta = takenDay.optJSONArray(MED_SLOTS[i]);
                    boolean ate = false;
                    if (ta != null) for (int k = 0; k < ta.length(); k++) if (dn.equals(ta.optString(k))) ate = true;
                    final int si = i;
                    TextView drug = new TextView(act);
                    drug.setText((ate ? "✓ " : "") + dn);
                    drug.setTextSize(15); drug.setTypeface(Typeface.DEFAULT_BOLD);
                    drug.setTextColor(ate ? 0xFF9CCC65 : (dark ? Color.WHITE : 0xFF1F2329));
                    drug.setPadding(pad, pad / 2, pad, pad / 2);
                    drug.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                        toggleTaken(si, dn);
                        refresh();
                    }});
                    medHost.addView(drug);
                }
            }
        } catch (Throwable ignored) {}
    }

    private void toggleTaken(int slot, String name) {
        try {
            String day = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            android.content.SharedPreferences sp = act.getSharedPreferences("cal", 0);
            JSONObject all = new JSONObject(sp.getString("meds_taken", "{}"));
            JSONObject d = all.optJSONObject(day); if (d == null) d = new JSONObject();
            JSONArray a = d.optJSONArray(MED_SLOTS[slot]); if (a == null) a = new JSONArray();
            java.util.List<String> l = new java.util.ArrayList<String>();
            for (int i = 0; i < a.length(); i++) l.add(a.optString(i));
            if (l.contains(name)) l.remove(name); else l.add(name);
            JSONArray na = new JSONArray(); for (String x : l) na.put(x);
            d.put(MED_SLOTS[slot], na); all.put(day, d);
            sp.edit().putString("meds_taken", all.toString()).apply();
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

    private void doSign() {
        try {
            android.content.SharedPreferences sp = act.getSharedPreferences("cal", 0);
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            if (sp.getString("sign_last", "").equals(today)) {
                Toast.makeText(act, "今天已签过", Toast.LENGTH_SHORT).show(); return;
            }
            Calendar y = Calendar.getInstance(); y.add(Calendar.DAY_OF_YEAR, -1);
            boolean consecutive = sp.getString("sign_last", "").equals(new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(y.getTime()));
            int days = consecutive ? sp.getInt("sign_days", 0) + 1 : 1;
            sp.edit().putString("sign_last", today).putInt("sign_days", days).apply();
            Toast.makeText(act, "签到成功，已连签 " + days + " 天", Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {}
    }
}
