package com.wink.xgjhome;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * stripchat(LL-HLS/fMP4) 旁路录制：shouldInterceptRequest 拦到页面的
 * init.mp4 / *_partN.mp4 时，一份字节还给页面，一份追加写本地。
 * 结束时 init+parts 顺序拼接即合法 fMP4，转 .mp4 入相册/历史。
 */
public class StripRec {

    private static volatile boolean recOn = false;
    private static File workDir = null;
    private static OutputStream out = null;
    private static File outFile = null;
    private static volatile byte[] initBody = null;   // 最近一次见到的 init
    private static volatile boolean initWritten = false;
    private static volatile String pageUrl = null;
    private static volatile long bytes = 0;

    public static boolean isRunning() { return recOn; }
    public static String getPageUrl() { return pageUrl; }

    public static synchronized void start(String page) {
        if (recOn) return;
        pageUrl = page;
        File base = new File(Context0.get().getExternalFilesDir(null), "sc_rec");
        base.mkdirs();
        int n = 1;
        while (new File(base, "rec" + n + ".mp4").exists()) n++;
        workDir = new File(base, "rec" + n);
        workDir.mkdirs();
        outFile = new File(base, "rec" + n + ".mp4");
        try { out = new FileOutputStream(outFile); } catch (Throwable e) { out = null; }
        bytes = 0;
        initWritten = false;
        // 之前缓存过 init 就先写进去
        if (out != null && initBody != null) {
            try { out.write(initBody); bytes += initBody.length; initWritten = true; } catch (Throwable ignored) {}
        }
        recOn = true;
    }

    /** 缓存/回填 init 分段 */
    public static synchronized void feed(String url, byte[] body) {
        if (body == null || body.length == 0) return;
        if (url.contains("init")) {
            initBody = body;
            if (recOn && out != null && !initWritten) {
                try { out.write(body); bytes += body.length; initWritten = true; } catch (Throwable ignored) {}
            }
            return;
        }
        if (recOn && out != null) {
            try { out.write(body); bytes += body.length; } catch (Throwable ignored) {}
        }
    }

    public static synchronized String stopAndMerge(Context0 c) {
        if (!recOn) return null;
        recOn = false;
        try { if (out != null) out.close(); } catch (Throwable ignored) {}
        out = null;
        if (outFile == null || !outFile.exists() || outFile.length() == 0) return null;
        // init 缺失时尝试从播放列表补
        if (!initWritten) {
            try {
                byte[] ib = fetchInitFromPlaylist();
                if (ib != null) prepend(ib);
            } catch (Throwable ignored) {}
        }
        try {
            android.content.ContentValues cv = new android.content.ContentValues();
            cv.put(android.provider.MediaStore.Video.Media.DISPLAY_NAME, "stripchat_" + System.currentTimeMillis() / 1000 + ".mp4");
            cv.put(android.provider.MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            if (android.os.Build.VERSION.SDK_INT >= 29)
                cv.put(android.provider.MediaStore.Video.Media.RELATIVE_PATH, "Movies/资源嗅探");
            android.net.Uri uri = c.get().getContentResolver().insert(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv);
            if (uri != null) {
                java.io.InputStream in = new java.io.FileInputStream(outFile);
                OutputStream os = c.get().getContentResolver().openOutputStream(uri);
                byte[] b = new byte[64 * 1024]; int n;
                while ((n = in.read(b)) > 0) os.write(b, 0, n);
                os.close(); in.close();
            }
            HistoryStore.add(c.get(), "视频", "stripchat_" + (outFile.getName()), "file://" + outFile.getAbsolutePath());
        } catch (Throwable ignored) {}
        try { for (File f : workDir.listFiles()) f.delete(); workDir.delete(); } catch (Throwable ignored) {}
        return outFile.getAbsolutePath();
    }

    private static void prepend(byte[] head) {
        try {
            File tmp = new File(outFile.getParentFile(), "tmp.mp4");
            FileOutputStream fo = new FileOutputStream(tmp);
            fo.write(head);
            java.io.FileInputStream fi = new java.io.FileInputStream(outFile);
            byte[] b = new byte[64 * 1024]; int n;
            while ((n = fi.read(b)) > 0) fo.write(b, 0, n);
            fi.close(); fo.close();
            java.io.FileInputStream a = new java.io.FileInputStream(tmp);
            FileOutputStream t2 = new FileOutputStream(outFile);
            while ((n = a.read(b)) > 0) t2.write(b, 0, n);
            t2.close(); a.close();
            tmp.delete();
        } catch (Throwable ignored) {}
    }

    /** 从最近的 m3u8（剥参版）解析 EXT-X-MAP 并拉 init */
    private static byte[] fetchInitFromPlaylist() {
        try {
            String pl = lastPlaylist;
            if (pl == null) return null;
            String proxied = "http://127.0.0.1:8123/relay?u=" + java.net.URLEncoder.encode(pl, "UTF-8");
            java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(proxied).openConnection();
            c.setConnectTimeout(6000); c.setReadTimeout(6000);
            InputStream in = c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[8192]; int n;
            while ((n = in.read(b)) > 0) bo.write(b, 0, n);
            in.close(); c.disconnect();
            String body = bo.toString("UTF-8");
            java.util.regex.Matcher mm = java.util.regex.Pattern.compile("URI=\"([^\"]+init\\.mp4[^\"]*)\"").matcher(body);
            if (!mm.find()) return null;
            String iu = mm.group(1);
            if (!iu.startsWith("http")) {
                java.net.URL pb = new java.net.URL(lastPlaylist);
                iu = pb.getProtocol() + "://" + pb.getHost() + (iu.startsWith("/") ? iu : pb.getPath().substring(0, pb.getPath().lastIndexOf('/') + 1) + iu);
            }
            byte[] raw = httpGetBytes(iu);
            return raw;
        } catch (Throwable e) { return null; }
    }

    static volatile String lastPlaylist = null;

    public static byte[] httpGetBytes(String url) throws Exception {
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        c.setConnectTimeout(8000); c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) Chrome/120 Mobile");
        if (url.contains("doppiocdn") || url.contains("stripchat")) {
            c.setRequestProperty("Referer", "https://zh.stripchat.cam/");
            c.setRequestProperty("Origin", "https://zh.stripchat.cam");
        }
        InputStream in = c.getInputStream();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] b = new byte[8192]; int n;
        while ((n = in.read(b)) > 0) bo.write(b, 0, n);
        in.close(); c.disconnect();
        return bo.toByteArray();
    }

    /** 简易上下文持有，避免改动现有初始化 */
    public static class Context0 {
        private static android.content.Context c;
        public static void set(android.content.Context ctx) { c = ctx.getApplicationContext(); }
        public static android.content.Context get() { return c; }
    }
}
