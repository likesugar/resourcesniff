package com.wink.xgjhome;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;

/** 用药看门狗前台服务: app在后台/杀进程后仍每20秒补挂到点未确认的服药通知 */
public class MedWatchService extends Service {
    private static final String CH = "med_watch";
    private final Handler h = new Handler();
    private int idle = 0;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        try {
            NotificationChannel nc = new NotificationChannel(CH, "用药提醒守护", NotificationManager.IMPORTANCE_MIN);
            nc.setShowBadge(false);
            ((android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(nc);
        } catch (Throwable ignored) {}
        startForeground(20003, buildNote("用药提醒守护中"));
        h.postDelayed(tick, 3000);
    }

    private final Runnable tick = new Runnable() { public void run() {
        boolean hasPlans = false;
        try {
            hasPlans = MedPlanActivity.load(MedWatchService.this).length() > 0;
            if (hasPlans) MedPlanActivity.checkAndNotifyDue(MedWatchService.this);
        } catch (Throwable ignored) {}
        if (!hasPlans && ++idle > 30) { // 无计划约10分钟后自动收摊
            stopForeground(true);
            stopSelf();
            return;
        }
        h.postDelayed(this, 20000);
    }};

    private Notification buildNote(String s) {
        Notification.Builder b = android.os.Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CH) : new Notification.Builder(this);
        b.setContentTitle("小工具").setContentText(s)
         .setSmallIcon(getApplicationInfo().icon).setOngoing(true);
        return b.build();
    }

    @Override public int onStartCommand(Intent i, int f, int id) { return START_STICKY; }

    public static void ensure(Context c) {
        try {
            Intent i = new Intent(c, MedWatchService.class);
            if (android.os.Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
        } catch (Throwable ignored) {}
    }
}
