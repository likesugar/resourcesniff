package com.wink.xgjhome;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 提醒通知栏"确认"按钮: 确认后删除一次性提醒/滚动每日提醒 */
public class ConfirmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            long id = intent.getLongExtra("id", -1);
            boolean daily = intent.getBooleanExtra("daily", false);
            org.json.JSONArray arr = new org.json.JSONArray(
                context.getSharedPreferences("cal", 0).getString("reminders", "[]"));
            org.json.JSONArray out = new org.json.JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.getJSONObject(i);
                if (!daily && o.optLong("ts") == id) continue;          // 一次性: 确认即删
                if (daily && o.optLong("ts") == id) {                    // 每日: 滚到明天
                    java.util.Calendar c = java.util.Calendar.getInstance();
                    c.setTimeInMillis(id); c.add(java.util.Calendar.DATE, 1);
                    o.put("ts", c.getTimeInMillis());
                    o.put("fired", false);
                }
                out.put(o);
            }
            context.getSharedPreferences("cal", 0).edit().putString("reminders", out.toString()).apply();
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.cancel((int) (id & 0x7fffffff));
            try { CalendarCardView.scheduleNext(context); } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
    }
}
