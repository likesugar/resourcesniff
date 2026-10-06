package com.wink.xgjhome;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 🍅 番茄钟: 90分钟专注+20分钟休息大循环, 专注中定时10秒小憩, 暂停记录 (萌系MD3) */
public class PomodoroActivity extends Activity {

    // 状态机
    private static final int IDLE = 0, FOCUS = 1, MICRO = 2, BIG_BREAK = 3;
    private int state = IDLE;

    private long remainMs;          // 当前阶段剩余
    private long lastTick;          // 上次tick时间
    private long focusElapsedMs;    // 本次专注已进行
    private long nextMicroAtMs;     // 下次小憩触发点(专注内已进行毫秒)
    private int pauseCount;         // 本次专注暂停次数
    private long pauseStartMs;
    private long totalFocusMin;     // 今日累计专注分钟

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences sp;
    private PomoRing ring;
    private TextView stateChip, subText, startBtn, statsText;
    private LinearLayout logHost;

    private final Runnable tick = new Runnable() { public void run() { onTick(); } };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("pomodoro", MODE_PRIVATE);
        setContentView(R.layout.activity_pomodoro);
        ring = findViewById(R.id.pomoRing);
        stateChip = findViewById(R.id.pomoState);
        subText = findViewById(R.id.pomoSub);
        startBtn = findViewById(R.id.pomoStart);
        statsText = findViewById(R.id.pomoStats);
        logHost = findViewById(R.id.pomoLog);

        findViewById(R.id.pomoBack).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { finish(); overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right); }
        });
        findViewById(R.id.pomoGear).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showSettings(); }
        });
        startBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { onStartPause(); }
        });
        findViewById(R.id.pomoReset).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { toIdle(); render(); }
        });

        pauseCount = 0;
        remainMs = focusLenMs();
        render();
    }

    // ---------- 配置 ----------
    private long focusLenMs() { return sp.getInt("focus_min", 90) * 60000L; }
    private long bigBreakMs() { return sp.getInt("break_min", 20) * 60000L; }
    private long microEveryMs() { return sp.getInt("micro_every_min", 9) * 60000L; }
    private long microLenMs() { return sp.getInt("micro_sec", 10) * 1000L; }

    private void showSettings() {
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, pad);
        String[][] rows = {
            {"🍅 专注(分钟)", "focus_min", "90"},
            {"🍰 休息(分钟)", "break_min", "20"},
            {"🥤 小憩间隔(分钟)", "micro_every_min", "9"},
            {"😴 小憩时长(秒)", "micro_sec", "10"},
        };
        for (final String[] r : rows) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, (int) (8 * getResources().getDisplayMetrics().density), 0, 0);
            TextView label = new TextView(this);
            label.setText(r[0]); label.setTextSize(14); label.setTextColor(0xFF3D2B33);
            row.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));
            final TextView val = new TextView(this);
            val.setText(String.valueOf(sp.getInt(r[1], Integer.parseInt(r[2]))));
            val.setTextSize(15); val.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            val.setTextColor(0xFFC2497A);
            val.setBackgroundResource(R.drawable.bg_pomo_chip);
            val.setPadding((int) (16 * getResources().getDisplayMetrics().density), (int) (6 * getResources().getDisplayMetrics().density),
                (int) (16 * getResources().getDisplayMetrics().density), (int) (6 * getResources().getDisplayMetrics().density));
            val.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    android.widget.EditText et = new android.widget.EditText(PomodoroActivity.this);
                    et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
                    et.setText(String.valueOf(sp.getInt(r[1], Integer.parseInt(r[2]))));
                    new android.app.AlertDialog.Builder(PomodoroActivity.this).setTitle(r[0]).setView(et)
                        .setPositiveButton("好的", new android.content.DialogInterface.OnClickListener() {
                            public void onClick(android.content.DialogInterface d, int w) {
                                try { sp.edit().putInt(r[1], Integer.parseInt(et.getText().toString().trim())).apply(); } catch (Throwable ignored) {}
                                val.setText(String.valueOf(sp.getInt(r[1], Integer.parseInt(r[2]))));
                                if (state == IDLE) { remainMs = focusLenMs(); render(); }
                            }
                        }).setNegativeButton("取消", null).show();
                }
            });
            row.addView(val);
            box.addView(row);
        }
        new android.app.AlertDialog.Builder(this).setTitle("⏱️ 参数设置").setView(box)
            .setPositiveButton("完成", null).show();
    }

    // ---------- 状态流转 ----------
    private void onStartPause() {
        if (state == IDLE) {
            state = FOCUS; remainMs = focusLenMs(); focusElapsedMs = 0;
            nextMicroAtMs = microEveryMs(); pauseCount = 0;
            startBtn.setText("⏸ 暂停一下");
            resumeTick();
        } else if (state == FOCUS) {
            pauseCount++;
            handler.removeCallbacks(tick);
            startBtn.setText("▶ 继续专注");
            addLog("⏸ " + now() + " 暂停了一下 (" + pauseCount + ")");
            render();
        } else if (state == MICRO) {
            resumeTick();
        } else if (state == BIG_BREAK) {
            resumeTick();
        }
    }

    private boolean ticking = false;
    private void resumeTick() {
        ticking = true;
        if (state == FOCUS) startBtn.setText("⏸ 暂停一下");
        lastTick = System.currentTimeMillis();
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, 250);
    }

    private void onTick() {
        long now = System.currentTimeMillis();
        long delta = now - lastTick;
        lastTick = now;
        remainMs -= delta;
        if (state == FOCUS) {
            focusElapsedMs += delta;
            if (focusElapsedMs >= nextMicroAtMs) {
                state = MICRO;
                remainMs = microLenMs();
                subText.setText("睁眼看看远处～ 👀");
            }
        }
        if (remainMs <= 0) onPhaseEnd();
        render();
        handler.postDelayed(tick, 250);
    }

    private void onPhaseEnd() {
        handler.removeCallbacks(tick);
        if (state == FOCUS || state == MICRO) {
            notifyUser("🍅 专注完成！", "干得漂亮！休息 " + sp.getInt("break_min", 20) + " 分钟吧～");
            if (state == FOCUS) {
                totalFocusMin = sp.getLong("today_focus_min", 0) + sp.getInt("focus_min", 90);
                sp.edit().putLong("today_focus_min", totalFocusMin).apply();
            }
            addLog("✅ " + now() + " 专注完成！休息一下～");
            state = BIG_BREAK; remainMs = bigBreakMs();
            startBtn.setText("▶ 开始休息");
        } else if (state == BIG_BREAK) {
            notifyUser("🍰 休息结束！", "元气满满，开始下一轮专注吧！");
            addLog("🔄 " + now() + " 休息结束，准备下一轮");
            state = IDLE; remainMs = focusLenMs();
            startBtn.setText("▶ 开始专注");
        }
        render();
    }

    private void toIdle() {
        handler.removeCallbacks(tick);
        state = IDLE; remainMs = focusLenMs();
        startBtn.setText("▶ 开始专注");
        subText.setText("今天也要加油鸭 🌸");
        pauseCount = 0;
    }

    // ---------- 渲染 ----------
    private void render() {
        String emoji, chip, sub;
        long total;
        switch (state) {
            case FOCUS:  emoji = "🍅"; chip = "🍅 专注中"; total = focusLenMs();
                sub = "第 " + (int) Math.ceil((focusElapsedMs + 1) / (double) microEveryMs()) + " 轮 · 小憩剩 "
                    + Math.max(0, (nextMicroAtMs - focusElapsedMs) / 60000) + " 分钟"; break;
            case MICRO:  emoji = "😴"; chip = "😴 小憩一下"; total = microLenMs();
                sub = "望远处的天空 10 秒 ☁️"; break;
            case BIG_BREAK: emoji = "🍰"; chip = "🍰 休息中"; total = bigBreakMs();
                sub = "吃点东西喝口水 🧃"; break;
            default: emoji = "🍅"; chip = "😊 准备开始"; total = focusLenMs();
                sub = "今天也要加油鸭 🌸"; break;
        }
        if (state != FOCUS || subText.getText().length() == 0) subText.setText(sub);
        if (state == FOCUS) subText.setText(sub);
        stateChip.setText(chip);
        long rem = Math.max(0, remainMs);
        String t;
        if (total >= 3600000L) t = String.format(Locale.US, "%d:%02d:%02d", rem / 3600000, rem / 60000 % 60, rem / 1000 % 60);
        else t = String.format(Locale.US, "%02d:%02d", rem / 60000, rem / 1000 % 60);
        ring.set(total > 0 ? (float) rem / total : 0f, t, emoji);
        int done = sp.getInt("done_count", 0);
        statsText.setText("完成专注 " + done + " 次 · 本次暂停 " + pauseCount + " 次 · 累计专注 "
            + (sp.getLong("today_focus_min", 0)) + " 分钟");
    }

    private void addLog(String line) {
        TextView tv = new TextView(this);
        tv.setText(line); tv.setTextSize(13); tv.setTextColor(0xFF8A6A78);
        tv.setPadding(0, (int) (6 * getResources().getDisplayMetrics().density), 0, 0);
        logHost.addView(tv, 0);
    }

    private String now() { return new SimpleDateFormat("HH:mm", Locale.US).format(new Date()); }

    private void notifyUser(String title, String text) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (android.os.Build.VERSION.SDK_INT >= 26)
                nm.createNotificationChannel(new NotificationChannel("remind", "提醒", NotificationManager.IMPORTANCE_HIGH));
            Notification.Builder bd = android.os.Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, "remind") : new Notification.Builder(this);
            bd.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(text).setAutoCancel(true);
            nm.notify((int) System.currentTimeMillis(), bd.build());
        } catch (Throwable ignored) {}
    }

    @Override public void onBackPressed() {
        finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    @Override protected void onDestroy() { handler.removeCallbacks(tick); super.onDestroy(); }
}
