package com.wink.xgjhome;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.WindowManager;

/** 录制网页（screenity 式）：MediaProjection 屏幕捕获 → MP4 → 下载/网页录制 目录 */
public class ScreenRecService extends Service {

    private static final String CHANNEL = "webrec";
    public static final String ACTION_START = "com.wink.xgjhome.WEBREC_START";
    public static final String ACTION_STOP = "com.wink.xgjhome.WEBREC_STOP";
    public static final String EXTRA_CODE = "code";
    public static final String EXTRA_DATA = "data";

    private static MediaProjection projection;
    private static VirtualDisplay display;
    private static MediaRecorder recorder;
    private static String outFile;
    private static long startAt;
    private android.os.PowerManager.WakeLock wl;  // 亮屏锁：录制期间防灭屏（灭屏=画面源消失）

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "网页录制", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
        }
        Notification n;
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            n = new Notification.Builder(this, CHANNEL)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle("正在录制网页")
                    .setOngoing(true).build();
        } else {
            n = new Notification.Builder(this)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle("正在录制网页")
                    .setOngoing(true).build();
        }
        startForeground(2003, n);

        if (ACTION_STOP.equals(intent != null ? intent.getAction() : null)) { stopRecording(); stopSelf(); return START_NOT_STICKY; }
        startCapture(intent);
        return START_NOT_STICKY;
    }

    private void startCapture(Intent intent) {
        try {
            stopCaptureQuiet();
            int code = intent.getIntExtra(EXTRA_CODE, -1);
            Intent data = intent.getParcelableExtra(EXTRA_DATA);
            MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            projection = mpm.getMediaProjection(code, data);
            if (projection == null) { SniffActivity.webRecState = 0; stopSelf(); return; }
            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            DisplayMetrics dm = new DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(dm);
            int w = Math.min(dm.widthPixels, 1280);
            if (w % 2 != 0) w--;
            int h = w * dm.heightPixels / dm.widthPixels;
            if (h % 2 != 0) h--;
            outFile = new java.io.File(getExternalFilesDir(null), "录制网页/web_" + System.currentTimeMillis() / 1000 + ".mp4").getAbsolutePath();
            new java.io.File(outFile).getParentFile().mkdirs();
            recorder = new MediaRecorder();
            if (android.os.Build.VERSION.SDK_INT >= 31) recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            else recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            recorder.setVideoEncodingBitRate(6 * 1024 * 1024);
            recorder.setVideoFrameRate(30);
            recorder.setVideoSize(w, h);
            recorder.setOutputFile(outFile);
            recorder.prepare();
            display = projection.createVirtualDisplay("webrec", w, h, dm.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, recorder.getSurface(), null, null);
            recorder.start();
            startAt = System.currentTimeMillis();
            try {
                android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
                wl = pm.newWakeLock(android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                        | android.os.PowerManager.ON_AFTER_RELEASE, "xgjhome:webrec");
                wl.acquire();
            } catch (Throwable ignored) {}
            SniffActivity.webRecState = 1;
            SniffActivity.webRecFile = outFile;
            updateNotification("录制中 00:00");
            new Thread(new Runnable() { public void run() {
                while (SniffActivity.webRecState == 1) {
                    try { Thread.sleep(1000); } catch (Throwable e) { return; }
                    long s = (System.currentTimeMillis() - startAt) / 1000;
                    updateNotification(String.format("录制中 %02d:%02d", s / 60, s % 60));
                }
            }}).start();
        } catch (Throwable t) {
            SniffActivity.webRecState = 0;
            stopSelf();
        }
    }

    private void stopRecording() {
        stopCaptureQuiet();
        SniffActivity.webRecState = 0;
        try { if (wl != null && wl.isHeld()) wl.release(); } catch (Throwable ignored) {}
        try {
            if (outFile != null) {
                String name = "网页录制_" + startAt / 1000;
                String usePath = "file://" + outFile;
                try {
                    // 与下载同路径：MediaStore Movies/资源嗅探，下载页/相册都能看到
                    android.content.ContentValues cv = new android.content.ContentValues();
                    cv.put(android.provider.MediaStore.Video.Media.DISPLAY_NAME, name + ".mp4");
                    cv.put(android.provider.MediaStore.Video.Media.MIME_TYPE, "video/mp4");
                    if (android.os.Build.VERSION.SDK_INT >= 29)
                        cv.put(android.provider.MediaStore.Video.Media.RELATIVE_PATH, "Movies/资源嗅探");
                    android.net.Uri uri = getContentResolver().insert(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv);
                    if (uri != null) {
                        java.io.OutputStream os = getContentResolver().openOutputStream(uri);
                        java.io.FileInputStream fis = new java.io.FileInputStream(outFile);
                        byte[] buf = new byte[65536]; int r;
                        while ((r = fis.read(buf)) > 0) os.write(buf, 0, r);
                        fis.close(); os.close();
                        usePath = uri.toString();
                        new java.io.File(outFile).delete();
                    }
                } catch (Throwable ignored) {}
                HistoryStore.add(this, "视频", name, usePath);
                try {
                    SniffActivity.registerLanLink(usePath, name);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    private void stopCaptureQuiet() {
        try { if (recorder != null) { recorder.stop(); } } catch (Throwable ignored) {}
        try { if (recorder != null) recorder.release(); } catch (Throwable ignored) {}
        recorder = null;
        try { if (display != null) display.release(); } catch (Throwable ignored) {}
        display = null;
        try { if (projection != null) projection.stop(); } catch (Throwable ignored) {}
        projection = null;
    }

    private void updateNotification(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification b;
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            b = new Notification.Builder(this, CHANNEL)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle("正在录制网页").setContentText(text)
                    .setOngoing(true).build();
        } else {
            b = new Notification.Builder(this)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle("正在录制网页").setContentText(text)
                    .setOngoing(true).build();
        }
        try { nm.notify(2003, b); } catch (Throwable ignored) {}
    }

    public static boolean hasProjection() {
        return projection != null;
    }
}
