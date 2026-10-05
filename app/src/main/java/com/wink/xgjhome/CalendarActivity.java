package com.wink.xgjhome;

import android.app.Activity;
import android.widget.Toast;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 完整日历页: 月视图 + 节假日 + 签到 + 提醒管理 */
public class CalendarActivity extends Activity {

    private CalendarCardView card;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try {
        boolean dark = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("dark", false);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(dark ? 0xFF000000 : 0xFFEEF4FF);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        col.setPadding(pad, pad + (int) (24 * getResources().getDisplayMetrics().density), pad, pad);
        android.widget.ScrollView page = new android.widget.ScrollView(this);
        page.setFillViewport(true);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));
        page.addView(col, new FrameLayout.LayoutParams(-1, -2));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = new TextView(this);
        back.setText("‹"); back.setTextSize(26);
        back.setTextColor(dark ? Color.WHITE : 0xFF1F2329);
        back.setPadding(0, 0, (int) (14 * getResources().getDisplayMetrics().density), 0);
        back.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { finish(); }});
        head.addView(back);
        TextView title = new TextView(this);
        title.setText("日历");
        title.setTextSize(22); title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(dark ? Color.WHITE : 0xFF1F2329);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        col.addView(head);

        // 星期表头
        LinearLayout week = new LinearLayout(this);
        week.setGravity(Gravity.CENTER_VERTICAL);
        int pw = (int) (10 * getResources().getDisplayMetrics().density);
        week.setPadding(pw, (int) (10 * getResources().getDisplayMetrics().density), pw, (int) (4 * getResources().getDisplayMetrics().density));
        String[] wd = {"日", "一", "二", "三", "四", "五", "六"};
        for (int i = 0; i < 7; i++) {
            TextView t = new TextView(this);
            t.setText(wd[i]); t.setTextSize(12);
            t.setGravity(Gravity.CENTER);
            t.setTextColor(i == 0 || i == 6 ? 0xFFFF6B6B : (dark ? 0xFF9AA3AE : 0xFF8A94A6));
            week.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        col.addView(week);

        // NCalendar 完整容器(周条+月视图+周翻页): 农历/节气/选中/月周切换
        com.necer.calendar.NCalendar nc = new com.necer.calendar.NCalendar(this, null);
        nc.setOnCalendarChangedListener(new com.necer.listener.OnCalendarChangedListener() {
            @Override public void onCalendarChange(int year, int month, java.time.LocalDate localDate, com.necer.enumeration.DateChangeBehavior b2) {}
        });
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-1, (int) (310 * getResources().getDisplayMetrics().density));
        col.addView(nc, nlp);

        LinearLayout host = new LinearLayout(this);
        host.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = (int) (12 * getResources().getDisplayMetrics().density);
        card = new CalendarCardView(this);
        host.addView(card);
        col.addView(host, lp);

        setContentView(root);
        } catch (Throwable t) {
            try {
                java.io.File dir = getExternalFilesDir(null) != null ? getExternalFilesDir(null).getParentFile() : getFilesDir();
                java.io.FileWriter fw = new java.io.FileWriter(new java.io.File(dir, "网页诊断.txt"), true);
                fw.write("\n==== CAL ACT ERR " + new java.util.Date() + " ====\n" + android.util.Log.getStackTraceString(t) + "\n");
                fw.close();
            } catch (Throwable ignored) {}
            Toast.makeText(this, "日历初始化失败: " + t.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

    }
}
