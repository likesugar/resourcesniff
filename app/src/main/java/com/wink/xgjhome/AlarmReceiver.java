package com.wink.xgjhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 日历提醒后台闹钟: 到点触发通知(带确认按钮)并安排下一次 */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        try { CalendarCardView.fireDue(context); } catch (Throwable ignored) {}
        try { CalendarCardView.scheduleNext(context); } catch (Throwable ignored) {}
    }
}
