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

    private static final java.util.concurrent.ConcurrentHashMap<Integer, com.arthenica.ffmpegkit.FFmpegSession> HLS_SESS =
        new java.util.concurrent.ConcurrentHashMap<Integer, com.arthenica.ffmpegkit.FFmpegSession>();

    /** HLS(m3u8) 下载：ffmpeg 合流拷贝到本地 ts，完成后入相册+历史 */
    public static void startHls(final String url) {
        final DlJob j = new DlJob();
        j.id = SEQ.incrementAndGet();
        j.url = url;
        j.title = "HLS_" + System.currentTimeMillis() / 1000;
        j.file = new java.io.File(sCtx.getExternalFilesDir(null), "下载/hls" + j.id + ".ts");
        try { j.file.getParentFile().mkdirs(); } catch (Throwable ignored) {}
        JOBS.put(j.id, j);
        j.state = "下载中";
        String[] args = { "-y", "-i", url, "-c", "copy", "-f", "mpegts", j.file.getAbsolutePath() };
        com.arthenica.ffmpegkit.FFmpegSession st = com.arthenica.ffmpegkit.FFmpegKit.executeWithArgumentsAsync(args,
            new com.arthenica.ffmpegkit.FFmpegSessionCompleteCallback() {
                public void apply(com.arthenica.ffmpegkit.FFmpegSession st2) {
                    boolean ok = st2.getState().equals(com.arthenica.ffmpegkit.SessionState.COMPLETED)
                        && j.file.exists() && j.file.length() > 0;
                    if (ok) {
                        j.doneBytes = j.file.length();
                        j.state = insertGallery(j) ? "已完成" : "完成(未入相册)";
                        HistoryStore.add(sCtx, "视频", j.title, "file://" + j.file.getAbsolutePath());
                        j.done = true; j.active = false;
                    } else {
                        j.failed = true; j.active = false;
                        j.state = "下载失败(" + st2.getState() + ")";
                        try { j.file.delete(); } catch (Throwable ignored) {}
                    }
                    HLS_SESS.remove(j.id);
                }
            });
        HLS_SESS.put(j.id, st);
    }

    public static void start(String url) {
        final DlJob j = new DlJob();
        j.id = SEQ.incrementAndGet();
        j.url = url;
        String t = url.substring(url.lastIndexOf('/') + 1);
        if (t.contains("?")) t = t.substring(0, t.indexOf('?'));
        if (t.length() == 0) t = "资源嗅探_" + System.currentTimeMillis() / 1000;
        j.title = t;
        j.file = new java.io.File(sCtx.getExternalFilesDir(null), "下载/dl" + j.id + ".ts");
        try { j.file.getParentFile().mkdirs(); } catch (Throwable ignored) {}
        JOBS.put(j.id, j);
        new Thread(new Runnable() { public void run() { download(j); } }).start();
    }

    private static void download(final DlJob j) {
        java.io.InputStream in = null;
        java.io.OutputStream out = null;
        try {
            java.net.URL u = new java.net.URL(j.url);
            java.net.HttpURLConnection c = (java.net.HttpURLConnection) u.openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            if (j.url.contains("bilibili") || j.url.contains("bilivideo"))
                c.setRequestProperty("Referer", "https://www.bilibili.com/");
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36");
            j.total = c.getContentLength();
            in = c.getInputStream();
            out = new java.io.FileOutputStream(j.file);
            byte[] buf = new byte[32 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                if (!j.active) return;  // 已取消
                out.write(buf, 0, n);
                j.doneBytes += n;
                j.state = "下载中";
            }
            out.close(); out = null;
            j.state = insertGallery(j) ? "已完成" : "完成(未入相册)";
            HistoryStore.add(sCtx, "视频", j.title, "file://" + j.file.getAbsolutePath());
            j.done = true;
            j.active = false;
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
            cv.put(android.provider.MediaStore.Video.Media.DISPLAY_NAME, j.title.endsWith(".ts") ? j.title : j.title + ".ts");
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
        com.arthenica.ffmpegkit.FFmpegSession hs = HLS_SESS.remove(id);
        if (hs != null) { try { com.arthenica.ffmpegkit.FFmpegKit.cancel(hs.getSessionId()); } catch (Throwable ignored) {} }
        try { if (j.file != null) j.file.delete(); } catch (Throwable ignored) {}
    }
}
