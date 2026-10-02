package com.wink.xgjhome;

import android.content.Context;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** 轻量下载管理：资源嗅探"下载"入口 → 卡片显示进度，完成后归到"视频"栏（入相册 Movies/资源嗅探） */
public class DlManager {

    public static class DlJob {
        public int id;
        public String url;
        public String title;
        public volatile boolean active = true;
        public volatile boolean done = false;
        public volatile boolean failed = false;
        public volatile long total = -1;   // -1 未知
        public volatile long doneBytes = 0;
        public volatile String state = "连接中…";
        public java.io.File file;
    }

    private static final ConcurrentHashMap<Integer, DlJob> JOBS = new ConcurrentHashMap<Integer, DlJob>();
    private static final AtomicInteger SEQ = new AtomicInteger(0);
    private static Context sCtx;

    public static void init(Context c) { sCtx = c.getApplicationContext(); }

    public static java.util.Collection<DlJob> jobs() { return JOBS.values(); }

    public static void start(String url) {
        final DlJob j = new DlJob();
        j.id = SEQ.incrementAndGet();
        j.url = url;
        j.title = "下载_" + System.currentTimeMillis() / 1000 + ".ts";
        java.io.File dir = new java.io.File(sCtx.getExternalFilesDir(null), "下载");
        dir.mkdirs();
        j.file = new java.io.File(dir, "dl" + j.id + ".ts");
        JOBS.put(j.id, j);
        new Thread(new Runnable() { public void run() { runDl(j); } }).start();
    }

    private static void runDl(final DlJob j) {
        java.io.InputStream in = null;
        java.io.OutputStream out = null;
        try {
            java.net.URL u = new java.net.URL(j.url);
            java.net.HttpURLConnection c = (java.net.HttpURLConnection) u.openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(15000);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36");
            if (j.url.contains("bilibili") || j.url.contains("bilivideo"))
                c.setRequestProperty("Referer", "https://www.bilibili.com/");
            else if (j.url.contains("douyin"))
                c.setRequestProperty("Referer", "https://live.douyin.com/");
            c.connect();
            j.total = c.getContentLength();
            in = c.getInputStream();
            out = new java.io.FileOutputStream(j.file);
            byte[] buf = new byte[64 * 1024];
            int n;
            while (j.active && (n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                j.doneBytes += n;
                j.state = "下载中";
            }
            out.close(); out = null;
            in.close(); in = null;
            if (!j.active) return;  // 已取消
            j.done = true;
            j.active = false;
            j.state = insertGallery(j) ? "已完成" : "完成(未入相册)";
        } catch (Throwable t) {
            if (j.active) { j.failed = true; j.active = false; j.state = "下载失败: " + t.getClass().getSimpleName(); }
        } finally {
            try { if (in != null) in.close(); } catch (Throwable ignored) {}
            try { if (out != null) out.close(); } catch (Throwable ignored) {}
        }
    }

    private static boolean insertGallery(DlJob j) {
        try {
            android.content.ContentValues cv = new android.content.ContentValues();
            cv.put(android.provider.MediaStore.Video.Media.DISPLAY_NAME, j.title);
            cv.put(android.provider.MediaStore.Video.Media.MIME_TYPE, "video/mp2t");
            if (android.os.Build.VERSION.SDK_INT >= 29)
                cv.put(android.provider.MediaStore.Video.Media.RELATIVE_PATH, "Movies/资源嗅探");
            android.net.Uri uri = sCtx.getContentResolver().insert(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) return false;
            java.io.InputStream in = new java.io.FileInputStream(j.file);
            java.io.OutputStream os = sCtx.getContentResolver().openOutputStream(uri);
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            os.close(); in.close();
            return true;
        } catch (Throwable t) { return false; }
    }

    public static void cancel(int id) {
        DlJob j = JOBS.get(id);
        if (j == null) return;
        j.active = false;
        JOBS.remove(id);
        try { if (j.file != null) j.file.delete(); } catch (Throwable ignored) {}
    }
}
