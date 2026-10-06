package com.wink.xgjhome;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** 💊 用药提醒(小工具18.0同款): 用药计划 + 多时间点强提醒 */
public class MedPlanActivity extends Activity {

    private static final String CH = "medplan";
    private LinearLayout listHost; private FrameLayout formHost;
    private ScrollView listScroll;
    private JSONArray plans;

    // 表单临时状态
    private JSONArray formTimes = new JSONArray();
    private String formRelation = "", formRepeat = "每天";
    private long formStart = 0, formEnd = 0;
    private int editIdx = -1;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        plans = load(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFFEAF2FF);
        root.setId(17901);

        listScroll = new ScrollView(this);
        listHost = new LinearLayout(this);
        listHost.setOrientation(LinearLayout.VERTICAL);
        listHost.setPadding(dp(16), dp(84), dp(16), dp(140));
        listScroll.addView(listHost);
        root.addView(listScroll, new FrameLayout.LayoutParams(-1, -1));

        TextView back = mkText("‹", 26, true, 0xFF1F2329); back.setPadding(dp(16), dp(30), dp(16), dp(10));
        back.setOnClickListener(v -> finish());
        root.addView(back, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START));

        TextView title = mkText("用药提醒", 20, true, 0xFF1F2329);
        root.addView(title, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        ((FrameLayout.LayoutParams) title.getLayoutParams()).topMargin = dp(32);

        TextView add = mkText("＋  新增用药计划", 16, true, Color.WHITE);
        add.setGravity(Gravity.CENTER);
        add.setBackgroundResource(R.drawable.bg_pill_blue);
        add.setPadding(dp(20), dp(16), dp(20), dp(16));
        add.setOnClickListener(v -> showForm(-1));
        FrameLayout.LayoutParams alp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        alp.leftMargin = dp(16); alp.rightMargin = dp(16); alp.bottomMargin = dp(24);
        root.addView(add, alp);

        formHost = new FrameLayout(this);
        formHost.setBackgroundColor(0xFFEAF2FF);
        formHost.setVisibility(View.GONE);
        root.addView(formHost, new FrameLayout.LayoutParams(-1, -1));

        setContentView(root);
        renderList();
        // 精确闹钟权限(Android 12+): 不批的话提醒会延迟
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= 31 && am != null && !am.canScheduleExactAlarms()) {
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    android.net.Uri.parse("package:" + getPackageName())));
            } catch (Throwable ignored) {}
        }
    }

    private void renderList() {
        listHost.removeAllViews();
        if (plans.length() == 0) {
            TextView e = mkText("还没有用药计划\n点下方「＋ 新增用药计划」创建 💊", 14, false, 0xFF8A94A6);
            e.setGravity(Gravity.CENTER);
            e.setPadding(0, dp(120), 0, 0);
            listHost.addView(e);
            return;
        }
        LinearLayout row = null;
        for (int i = 0; i < plans.length(); i++) {
            final int idx = i;
            JSONObject o = plans.optJSONObject(i);
            if (o == null) continue;
            if (i % 2 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
                if (i > 0) rlp.topMargin = dp(14);
                listHost.addView(row, rlp);
            }
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setBackgroundResource(R.drawable.bg_card_white);
            cell.setPadding(dp(16), dp(16), dp(16), dp(16));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, -2, 1f);
            if (i % 2 == 1) clp.leftMargin = dp(14);
            // 图标 + 药名
            TextView ic = mkText("💊", 20, false, 0xFF1F2329);
            cell.addView(ic);
            TextView name = mkText(o.optString("name", ""), 15, true, 0xFF1F2329);
            name.setPadding(0, dp(8), 0, 0);
            cell.addView(name);
            TextView dose = mkText(o.optString("dose", "1") + o.optString("unit", "片") + " · " + o.optString("relation", ""), 12, false, 0xFF8A94A6);
            dose.setPadding(0, dp(4), 0, 0);
            cell.addView(dose);
            // 首个时间胶囊
            JSONArray ts = o.optJSONArray("times");
            TextView chip = mkText((ts != null && ts.length() > 0 ? ts.optString(0) : "--:--") + (ts != null && ts.length() > 1 ? " +" + (ts.length() - 1) : ""), 13, true, 0xFF315CDE);
            chip.setBackgroundResource(R.drawable.bg_chip_blue);
            chip.setPadding(dp(12), dp(5), dp(12), dp(5));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.topMargin = dp(10);
            cell.addView(chip, lp);
            cell.setOnClickListener(v -> showForm(idx));
            row.addView(cell, clp);
        }
    }

    // ---------- 表单 ----------
    private void showForm(int idx) {
        editIdx = idx;
        formTimes = new JSONArray();
        formRelation = ""; formRepeat = "每天"; formStart = System.currentTimeMillis(); formEnd = 0;
        if (idx >= 0) {
            JSONObject o = plans.optJSONObject(idx);
            try { formTimes = new JSONArray(o.optString("times", "[]")); } catch (Throwable ignored) {}
            formRelation = o.optString("relation", "");
            formRepeat = o.optString("repeat", "每天");
            formStart = o.optLong("start", System.currentTimeMillis());
            formEnd = o.optLong("end", 0);
        }
        listScroll.setVisibility(View.GONE);
        formHost.removeAllViews();
        formHost.setVisibility(View.VISIBLE);
        ScrollView fs = new ScrollView(this);
        LinearLayout card = card();
        card.setPadding(dp(16), dp(84), dp(16), dp(40));
        fs.addView(card);
        formHost.addView(fs, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setBackgroundResource(R.drawable.bg_card_white);
        bottomBar.setPadding(dp(16), dp(12), dp(16), dp(12));
        formHost.addView(bottomBar, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        final EditText name = rowInput(card, "💊", "药品名称", "请输入药品名称", idx >= 0 ? plans.optJSONObject(idx).optString("name", "") : "");

        // 单次用量: 数字 + 片/粒
        LinearLayout doseRow = rowHead(card, "🥛", "单次用量");
        LinearLayout doseLine = new LinearLayout(this);
        doseLine.setOrientation(LinearLayout.HORIZONTAL);
        doseLine.setGravity(Gravity.CENTER_VERTICAL);
        final EditText dose = new EditText(this);
        dose.setText(idx >= 0 ? plans.optJSONObject(idx).optString("dose", "1") : "1");
        dose.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        dose.setGravity(Gravity.CENTER);
        dose.setTextSize(15); dose.setTextColor(0xFF1F2329);
        dose.setBackgroundResource(R.drawable.bg_input);
        dose.setPadding(dp(14), dp(8), dp(14), dp(8));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(0, -2, 1f);
        dlp.leftMargin = dp(10);
        doseLine.addView(dose, dlp);
        final String[] unit = {idx >= 0 ? plans.optJSONObject(idx).optString("unit", "片") : "片"};
        final LinearLayout unitBox = new LinearLayout(this);
        for (final String u : new String[]{"片", "粒"}) {
            TextView c = mkText(u, 14, u.equals(unit[0]), u.equals(unit[0]) ? 0xFF315CDE : 0xFF8A94A6);
            c.setBackgroundResource(u.equals(unit[0]) ? R.drawable.bg_chip_blue : R.drawable.bg_chip_gray);
            c.setPadding(dp(18), dp(8), dp(18), dp(8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.leftMargin = dp(10);
            c.setOnClickListener(v -> {
                unit[0] = u;
                renderUnitPick(unitBox, unit);
            });
            unitBox.addView(c, lp);
        }
        doseLine.addView(unitBox);
        LinearLayout.LayoutParams dlp2 = new LinearLayout.LayoutParams(-1, -2);
        dlp2.leftMargin = dp(10);
        doseRow.addView(doseLine, dlp2);

        // 服用时间
        LinearLayout timeHead = rowHead(card, "⏰", "服用时间");
        final TextView timeAdd = mkText("＋ 添加时间", 14, true, 0xFF315CDE);
        timeAdd.setBackgroundResource(R.drawable.bg_chip_dash);
        timeAdd.setPadding(dp(22), dp(12), dp(22), dp(12));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.topMargin = dp(12);
        timeHead.addView(timeAdd, tlp);
        final LinearLayout timeChips = new LinearLayout(this);
        timeChips.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams clp2 = new LinearLayout.LayoutParams(-1, -2);
        clp2.topMargin = dp(10);
        card.addView(timeChips);
        Runnable[] renderTimes = new Runnable[1];
        renderTimes[0] = () -> {
            timeChips.removeAllViews();
            for (int t = 0; t < formTimes.length(); t++) {
                final int ti = t;
                TextView chip = mkText(formTimes.optString(ti) + " ✕", 14, true, 0xFF315CDE);
                chip.setBackgroundResource(R.drawable.bg_chip_blue);
                chip.setPadding(dp(14), dp(8), dp(14), dp(8));
                chip.setOnClickListener(v -> {
                    JSONArray na = new JSONArray();
                    for (int m = 0; m < formTimes.length(); m++) if (m != ti) { try { na.put(formTimes.get(m)); } catch (Throwable ignored) {} }
                    formTimes = na;
                    renderTimes[0].run();
                });
                timeChips.addView(chip, chipLp(t > 0));
            }
        };
        timeAdd.setOnClickListener(v -> new TimePickerDialog(MedPlanActivity.this, (tp, hh, mm) -> {
            formTimes.put(String.format(Locale.US, "%02d:%02d", hh, mm));
            renderTimes[0].run();
        }, 8, 0, true).show());
        renderTimes[0].run();

        // 服药关系
        LinearLayout relHead = rowHead(card, "🍽️", "服药关系");
        final String[][] rels = {{"饭前", "饭后", "睡前"}};
        final LinearLayout relBox = new LinearLayout(this);
        relHead.addView(relBox);
        Runnable renderRel = () -> { renderPick(relBox, rels[0], new String[]{formRelation}, v -> formRelation = v); };
        renderRel.run();

        // 重复周期
        LinearLayout repHead = rowHead(card, "🔁", "重复周期");
        final LinearLayout repBox = new LinearLayout(this);
        repHead.addView(repBox);
        Runnable renderRep = () -> { renderPick(repBox, new String[]{"每天", "工作日", "自定义"}, new String[]{formRepeat}, v -> formRepeat = v); };
        renderRep.run();

        // 开始/结束日期(同一行紧凑排布)
        LinearLayout dateRow = rowHead(card, "📅", "开始 / 结束日期");
        LinearLayout dateBox = new LinearLayout(this);
        dateBox.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout startCell = new LinearLayout(this);
        startCell.setOrientation(LinearLayout.VERTICAL);
        startCell.setBackgroundResource(R.drawable.bg_input);
        startCell.setPadding(dp(14), dp(10), dp(14), dp(10));
        TextView sLabel = mkText("开始", 11, false, 0xFF8A94A6);
        final TextView startV = mkText(fmt(formStart), 14, true, 0xFF1F2329);
        startCell.addView(sLabel); startCell.addView(startV);
        startCell.setOnClickListener(v -> pickDate(formStart, t -> { formStart = t; startV.setText(fmt(t)); }));
        LinearLayout endCell = new LinearLayout(this);
        endCell.setOrientation(LinearLayout.VERTICAL);
        endCell.setBackgroundResource(R.drawable.bg_input);
        endCell.setPadding(dp(14), dp(10), dp(14), dp(10));
        TextView eLabel = mkText("结束", 11, false, 0xFF8A94A6);
        final TextView endV = mkText(formEnd > 0 ? fmt(formEnd) : "未设置", 14, true, formEnd > 0 ? 0xFF1F2329 : 0xFFB7BFCC);
        endCell.addView(eLabel); endCell.addView(endV);
        endCell.setOnClickListener(v -> pickDate(formEnd > 0 ? formEnd : System.currentTimeMillis(), t -> { formEnd = t; endV.setText(fmt(t)); endV.setTextColor(0xFF1F2329); }));
        LinearLayout.LayoutParams scp = new LinearLayout.LayoutParams(0, -2, 1f);
        LinearLayout.LayoutParams ecp = new LinearLayout.LayoutParams(0, -2, 1f);
        ecp.leftMargin = dp(10);
        dateBox.addView(startCell, scp); dateBox.addView(endCell, ecp);
        LinearLayout.LayoutParams dlp3 = new LinearLayout.LayoutParams(-1, -2);
        dlp3.leftMargin = dp(10); dlp3.topMargin = dp(12);
        dateRow.addView(dateBox, dlp3);

        // 保存/取消
        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
        blp.topMargin = dp(20);
        TextView cancel = mkText("取消", 16, true, 0xFF315CDE);
        cancel.setBackgroundResource(R.drawable.bg_btn_outline);
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(0, dp(14), 0, dp(14));
        TextView save = mkText("保存计划", 16, true, Color.WHITE);
        save.setBackgroundResource(R.drawable.bg_pill_blue);
        save.setGravity(Gravity.CENTER);
        save.setPadding(0, dp(14), 0, dp(14));
        LinearLayout.LayoutParams c1 = new LinearLayout.LayoutParams(0, -2, 1f);
        LinearLayout.LayoutParams c2 = new LinearLayout.LayoutParams(0, -2, 1f);
        c2.leftMargin = dp(14);
        btns.addView(cancel, c1); btns.addView(save, c2);
        bottomBar.addView(btns, new LinearLayout.LayoutParams(-1, -2));
        if (editIdx >= 0) {
            TextView del = mkText("🗑 删除此计划", 14, false, 0xFFFF7B8A);
            del.setGravity(Gravity.CENTER);
            del.setPadding(0, dp(14), 0, 0);
            card.addView(del, new LinearLayout.LayoutParams(-1, -2));
            del.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("删除计划")
                .setMessage("删除「" + name.getText().toString() + "」?")
                .setPositiveButton("删除", (d, w) -> { removePlan(editIdx); formHost.setVisibility(View.GONE); listScroll.setVisibility(View.VISIBLE); })
                .setNegativeButton("取消", null).show());
        }
        cancel.setOnClickListener(v -> { formHost.setVisibility(View.GONE); listScroll.setVisibility(View.VISIBLE); });
        save.setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            if (n.isEmpty()) { toast("请输入药品名称"); return; }
            if (formTimes.length() == 0) { toast("请至少添加一个服用时间"); return; }
            try {
                JSONObject o = new JSONObject();
                o.put("name", n);
                o.put("dose", dose.getText().toString().trim());
                o.put("unit", unit[0]);
                o.put("times", formTimes);
                o.put("relation", formRelation);
                o.put("repeat", formRepeat);
                o.put("start", formStart);
                o.put("end", formEnd);
                if (editIdx >= 0) plans.put(editIdx, o); else plans.put(o);
                save(this, plans);
                scheduleAll(this);
                formHost.setVisibility(View.GONE); listScroll.setVisibility(View.VISIBLE);
                renderList();
                toast("已保存，到点提醒 📳");
            } catch (Throwable ignored) {}
        });
    }

    private void renderUnitPick(LinearLayout box, String[] cur) {
        box.removeAllViews();
        renderPick(box, new String[]{"片", "粒"}, cur, v -> {});
    }

    private void renderPick(LinearLayout box, String[] opts, String[] cur, java.util.function.Consumer<String> set) {
        box.removeAllViews();
        for (final String o : opts) {
            boolean on = o.equals(cur[0]);
            TextView c = mkText(o, 14, on, on ? 0xFF315CDE : 0xFF8A94A6);
            c.setBackgroundResource(on ? R.drawable.bg_chip_blue : R.drawable.bg_chip_gray);
            c.setPadding(dp(18), dp(8), dp(18), dp(8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.leftMargin = dp(10);
            c.setOnClickListener(v -> { cur[0] = o; set.accept(o); renderPick(box, opts, cur, set); });
            box.addView(c, lp);
        }
    }

    private LinearLayout rowHead(LinearLayout parent, String icon, String label) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView ic = mkText(icon, 17, false, 0xFF1F2329);
        head.addView(ic);
        TextView tv = mkText(label, 16, true, 0xFF1F2329);
        tv.setPadding(dp(10), 0, 0, 0);
        head.addView(tv);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2);
        hlp.topMargin = dp(18);
        row.addView(head);
        parent.addView(row, hlp);
        return row;
    }

    private EditText rowInput(LinearLayout parent, String icon, String label, String hint, String val) {
        LinearLayout row = rowHead(parent, icon, label);
        EditText et = new EditText(this);
        et.setHint(hint); et.setText(val); et.setSingleLine(true);
        et.setTextSize(15); et.setTextColor(0xFF1F2329);
        et.setHintTextColor(0xFFB7BFCC);
        et.setBackgroundResource(R.drawable.bg_input);
        et.setPadding(dp(14), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.leftMargin = dp(10);
        row.addView(et, lp);
        return et;
    }

    private LinearLayout.LayoutParams chipLp(boolean margin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        if (margin) lp.leftMargin = dp(8);
        return lp;
    }

    private LinearLayout.LayoutParams endLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.gravity = Gravity.END;
        return lp;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackgroundResource(R.drawable.bg_card_white);
        c.setPadding(dp(20), dp(20), dp(20), dp(20));
        return c;
    }

    private TextView mkText(String t, int sp, boolean bold, int color) {
        TextView tv = new TextView(this);
        tv.setText(t); tv.setTextSize(sp); tv.setTextColor(color);
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    private void pickDate(long cur, java.util.function.Consumer<Long> cb) {
        Calendar c = Calendar.getInstance(); c.setTimeInMillis(cur);
        new android.app.DatePickerDialog(this, (dp2, y, m, d) -> {
            Calendar r = Calendar.getInstance();
            r.set(y, m, d, 23, 59, 59);
            cb.accept(r.getTimeInMillis());
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private int hour(String t) { try { return Integer.parseInt(t.split(":")[0]); } catch (Throwable e) { return 8; } }
    private int min(String t) { try { return Integer.parseInt(t.split(":")[1]); } catch (Throwable e) { return 0; } }
    private String fmt(long t) { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(t)); }
    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    // ---------- 存储 ----------
    static JSONArray load(Context c) {
        try { return new JSONArray(c.getSharedPreferences("medplan", 0).getString("plans", "[]")); }
        catch (Throwable e) { return new JSONArray(); }
    }
    static void save(Context c, JSONArray a) { c.getSharedPreferences("medplan", 0).edit().putString("plans", a.toString()).apply(); }
    private void removePlan(int idx) {
        JSONArray na = new JSONArray();
        for (int i = 0; i < plans.length(); i++) if (i != idx) { try { na.put(plans.get(i)); } catch (Throwable ignored) {} }
        plans = na; save(this, plans); scheduleAll(this); renderList();
    }

    // ---------- 调度: 每个时间点一颗精确闹钟, 杀后台也能响 ----------
    static void scheduleAll(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        JSONArray plans = load(c);
        // 先清掉旧闹钟(固定槽位)
        for (int slot = 0; slot < 400; slot++) {
            try { am.cancel(firePI(c, slot, -1, -1)); } catch (Throwable ignored) {}
        }
        Calendar now = Calendar.getInstance();
        for (int i = 0; i < plans.length(); i++) {
            JSONObject o = plans.optJSONObject(i);
            if (o == null) continue;
            JSONArray ts = o.optJSONArray("times");
            if (ts == null) continue;
            for (int t = 0; t < ts.length(); t++) {
                String tt = ts.optString(t);
                int hh, mm;
                try { hh = Integer.parseInt(tt.split(":")[0]); mm = Integer.parseInt(tt.split(":")[1]); }
                catch (Throwable e) { continue; }
                Calendar fire = Calendar.getInstance();
                fire.set(Calendar.HOUR_OF_DAY, hh);
                fire.set(Calendar.MINUTE, mm);
                fire.set(Calendar.SECOND, 0);
                fire.set(Calendar.MILLISECOND, 0);
                if (!fire.after(now)) fire.add(Calendar.DATE, 1); // 已过点->明天的这个点
                int slot = i * 20 + t;
                PendingIntent pi = firePI(c, slot, i, t);
                if (android.os.Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms())
                    am.setWindow(AlarmManager.RTC_WAKEUP, fire.getTimeInMillis(), 60_000, pi);
                else
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fire.getTimeInMillis(), pi);
            }
        }
    }

    private static PendingIntent firePI(Context c, int slot, int planIdx, int timeIdx) {
        Intent it = new Intent(c, MedPlanReceiver.class).setAction("MED_FIRE")
            .putExtra("i", planIdx).putExtra("t", timeIdx);
        return PendingIntent.getBroadcast(c, 990100 + slot, it, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static class MedPlanReceiver extends BroadcastReceiver {
        @Override public void onReceive(Context context, Intent intent) {
            try {
                ensureChannel(context);
                if (!"MED_FIRE".equals(intent.getAction())) return;
                int i = intent.getIntExtra("i", -1), t = intent.getIntExtra("t", -1);
                JSONArray plans = load(context);
                JSONObject o = plans.optJSONObject(i);
                if (o != null && t >= 0) {
                    String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
                    android.content.SharedPreferences pf = context.getSharedPreferences("medplan", 0);
                    if (!pf.getBoolean("ntf_" + today + "_" + i + "_" + t, false)) {
                        pf.edit().putBoolean("ntf_" + today + "_" + i + "_" + t, true).apply();
                        String txt = "💊 " + o.optString("name", "") + "  " + o.optString("dose", "1") + o.optString("unit", "片")
                            + (o.optString("relation", "").isEmpty() ? "" : "（" + o.optString("relation") + "）");
                        notifyMed(context, "该吃药啦 ⏰ " + o.optString("times", ""), txt);
                    }
                }
                scheduleAll(context); // 排下一个时间点
            } catch (Throwable ignored) {}
        }
    }

    /** 应用启动即创建渠道, 保证系统通知设置里能看到并配置 */
    public static void ensureChannel(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel(CH, "用药提醒", NotificationManager.IMPORTANCE_HIGH);
        ch.enableVibration(true);
        ch.enableLights(true);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);
        NotificationChannel r2 = new NotificationChannel("remind", "日程提醒", NotificationManager.IMPORTANCE_HIGH);
        r2.enableVibration(true);
        nm.createNotificationChannel(r2);
    }

    static void notifyMed(Context c, String title, String text) {
        ensureChannel(c);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        Intent open = new Intent(c, MedPlanActivity.class);
        PendingIntent pi = PendingIntent.getActivity(c, 990002, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(c, CH)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title).setContentText(text)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build();
        nm.notify((int) System.currentTimeMillis(), n);
    }
}
