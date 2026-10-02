package com.wink.xgjhome;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/** 局域网共享服务：stopWithTask —— 应用任务被划掉时自动销毁并撤通知、停服务器 */
public class LanShareService extends Service {

    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LanShareServer.start();
        showNotification();
        return START_NOT_STICKY;
    }

    private void showNotification() {
        try {
            String url = "http://" + LanShareServer.localIp() + ":" + LanShareServer.getPort();
            android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(new android.app.NotificationChannel("lan", "局域网共享", android.app.NotificationManager.IMPORTANCE_LOW));
            }
            android.app.Notification.Builder nb = android.os.Build.VERSION.SDK_INT >= 26
                ? new android.app.Notification.Builder(this, "lan")
                : new android.app.Notification.Builder(this);
            nb.setSmallIcon(android.R.drawable.ic_menu_share)
              .setContentTitle("局域网共享已开启")
              .setContentText("电脑打开 " + url)
              .setStyle(new android.app.Notification.BigTextStyle().bigText("电脑浏览器打开 " + url + " 可查看并打开记录中的链接"))
              .setOngoing(true);
            nm.notify(1001, nb.build());
        } catch (Throwable ignored) {}
    }

    @Override
    public void onDestroy() {
        LanShareServer.stop();
        try {
            android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.cancel(1001);
        } catch (Throwable ignored) {}
        super.onDestroy();
    }
}
