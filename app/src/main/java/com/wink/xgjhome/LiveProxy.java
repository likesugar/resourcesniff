package com.wink.xgjhome;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.HttpURLConnection;
import java.net.URLDecoder;
import java.util.LinkedHashSet;

/** 本地代理+独立刷新器：页面点播放后拿到媒体列表地址(c/d)，
 *  之后服务自己每 800ms 轮询该地址（页面可退），分片由刷新器抓取并写入录制文件，
 *  VLC 播 http://127.0.0.1:8123/playlist.m3u8（ts 经 /ts?u= 中转，绕过 ffmpeg-min 无 https） */
public class LiveProxy {

    public static final int PORT = 8123;
    public static volatile byte[] latestBody = null;
    public static volatile long latestAt = 0;
    public static volatile String mediaUrl = null;
    public static volatile String tsPipe = null;
    public static volatile long liveTsBytes = 0;
    public static volatile FileOutputStream kbOut = null;
    public static volatile String kbOutName = "";
    public static volatile int pollCount = 0;
    public static volatile int failCount = 0;
    private static final java.util.LinkedHashMap<String, byte[]> segCache = new java.util.LinkedHashMap<>();
    public static void putSeg(String url, byte[] body) {
        synchronized (segCache) {
            segCache.put(url, body);
            while (segCache.size() > 40) {
                String k = segCache.keySet().iterator().next();
                segCache.remove(k);
            }
        }
    }
    public static byte[] getSeg(String url) {
        synchronized (segCache) { return segCache.get(url); }
    }
    private static volatile boolean running = false;
    private static volatile boolean refreshing = false;
    private static final LinkedHashSet<String> fetched = new LinkedHashSet<>();

    private static volatile java.io.File scFile = null;
    private static volatile FileOutputStream scOut = null;
    private static android.content.Context sSCCtx = null;
    public static void setSCCtx(android.content.Context c) { sSCCtx = c.getApplicationContext(); }
    public static long scBytes() {
        try { return (scFile != null && scFile.exists()) ? scFile.length() : 0; } catch (Throwable e) { return 0; }
    }

    public static void start() {
        if (running) return;
        running = true;
        new Thread(new Runnable() {
            public void run() {
                try {
                    ServerSocket ss = new ServerSocket(PORT);
                    while (running) {
                        final Socket s = ss.accept();
                        new Thread(new Runnable() { public void run() {
                            try { handle(s); } catch (Throwable ignored) {}
                            finally { try { s.close(); } catch (Throwable ignored) {} }
                        } }).start();
                    }
                } catch (Throwable ignored) {}
            }
        }).start();
    }

    /** 独立刷新器：服务里启动；每 800ms 轮询媒体列表 + 抓新分片写录制文件 */
    public static void startRefresher() {
        if (refreshing) return;
        refreshing = true;
        new Thread(new Runnable() {
            public void run() {
                int fails = 0;
                while (refreshing) {
                    try {
                        String mu = mediaUrl;
                        if (mu == null) { Thread.sleep(800); continue; }
                        byte[] body = httpGet(mu);
                        if (body == null) {
                            Thread.sleep(1500);
                            continue;
                        }
                        fails = 0;
                        latestBody = body;
                        latestAt = System.currentTimeMillis();
                        // 解析分片，抓新的写录制
                        for (String ln : new String(body, "UTF-8").split("\n")) {
                            String t = ln.trim();
                            if (t.startsWith("http") && t.contains(".ts") && !fetched.contains(t)) {
                                byte[] seg = httpGet(t);
                                if (seg != null) {
                                    fetched.add(t);
                                    FileOutputStream fo = kbOut;
                                    if (fo != null) {
                                        fo.write(seg);
                                        fo.flush();
                                    }
                                } // 失败不标记，下一轮重试（减少时间戳断口）
                            }
                        }
                        Thread.sleep(800);
                    } catch (Throwable e) {
                        try { Thread.sleep(800); } catch (Exception ignored) {}
                    }
                }
                refreshing = false;
            }
        }).start();
    }

    public static void stopRefresher() {
        refreshing = false;
        mediaUrl = null;
        fetched.clear();
    }

    private static byte[] httpGet(String url) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(6000);
            c.setReadTimeout(6000);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) Chrome/120 Mobile");
            c.setRequestProperty("Referer", "https://guangdongvideo.com/");
            if (c.getResponseCode() != 200) { c.disconnect(); return null; }
            InputStream in = c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[8192]; int n;
            while ((n = in.read(b)) > 0) bo.write(b, 0, n);
            in.close();
            c.disconnect();
            return bo.toByteArray();
        } catch (Throwable e) {
            return null;
        }
    }

    public static void fetchLatest(String url) {
        final String fUrl = url;
        new Thread(new Runnable() { public void run() {
            byte[] body = httpGet(fUrl);
            if (body != null) { latestBody = body; latestAt = System.currentTimeMillis(); }
        } }).start();
    }

    private static void handle(Socket s) {
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));
            String reqLine = br.readLine();
            if (reqLine == null) return;
            String path = reqLine.split(" ")[1];

            if (path.startsWith("/relay")) {
                // 通用中转：u=原始地址（m3u8 内容递归改写；分片流式转发）
                try {
                    String raw = URLDecoder.decode(queryParam(path, "u"), "UTF-8");
                    HttpURLConnection oc = (HttpURLConnection) new URL(raw).openConnection();
                    oc.setConnectTimeout(8000);
                    oc.setReadTimeout(8000);
                    oc.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) Chrome/120 Mobile");
                    oc.setRequestProperty("Referer", "https://guangdongvideo.com/");
                    int code = oc.getResponseCode();
                    if (code == 200) {
                        InputStream in = oc.getInputStream();
                        ByteArrayOutputStream bo = new ByteArrayOutputStream();
                        byte[] rb = new byte[8192];
                        int rn;
                        while ((rn = in.read(rb)) > 0) bo.write(rb, 0, rn);
                        in.close();
                        oc.disconnect();
                        byte[] body = bo.toByteArray();
                        String head = new String(body, 0, Math.min(body.length, 64), "UTF-8");
                        if (head.startsWith("#EXTM3U")) {
                            StringBuilder sb = new StringBuilder();
                            for (String ln : new String(body, "UTF-8").split("\n")) {
                                String t = ln.trim();
                                if (!t.isEmpty() && !t.startsWith("#")) {
                                    String abs = new URL(new URL(raw), t).toString();
                                    ln = "http://127.0.0.1:" + PORT + "/relay?u="
                                        + java.net.URLEncoder.encode(abs, "UTF-8");
                                }
                                sb.append(ln).append("\n");
                            }
                            writeResp(s, "200 OK", "application/vnd.apple.mpegurl", sb.toString().getBytes());
                        } else {
                            writeResp(s, "200 OK", "video/mp2t", body);
                        }
                        return;
                    }
                    oc.disconnect();
                    writeResp(s, "404 Not Found", "text/plain", "upstream err".getBytes());
                } catch (Throwable e) {
                    writeResp(s, "404 Not Found", "text/plain", "relay err".getBytes());
                }
                return;
            }

            if (path.startsWith("/striprec")) {
                // MPMux式页面录制：接收 MediaRecorder 分片 / 控制结束转封装
                try {
                    java.util.Map<String,String> q = new java.util.HashMap<String,String>();
                    int qi3 = path.indexOf('?');
                    if (qi3 >= 0) for (String kv : path.substring(qi3 + 1).split("&")) {
                        int eq = kv.indexOf('=');
                        if (eq > 0) q.put(kv.substring(0, eq), kv.substring(eq + 1));
                    }
                    String act = q.get("act");
                    // 中间 webm 放内部缓存，用户只看得到最终 MP4（相册/视频栏）
                    java.io.File base = new java.io.File(sSCCtx != null ? sSCCtx.getCacheDir() : null, "sc_webm");
                    base.mkdirs();
                    if ("start".equals(act)) {
                        scFile = new java.io.File(base, "rec" + System.currentTimeMillis() / 1000 + ".webm");
                        if (scOut != null) try { scOut.close(); } catch (Throwable ignored) {}
                        scOut = new java.io.FileOutputStream(scFile);
                        writeResp(s, "200 OK", "text/plain", "ok".getBytes());
                    } else if ("chunk".equals(act) && scOut != null) {
                        // 分片以 base64 走 query，避免与 BufferedReader 冲突
                        String d64 = q.get("d");
                        if (d64 != null && d64.length() > 0) {
                            byte[] bb = java.util.Base64.getDecoder().decode(d64);
                            scOut.write(bb);
                            scOut.flush();
                        }
                        writeResp(s, "200 OK", "text/plain", "ok".getBytes());
                    } else if ("stop".equals(act)) {
                        try { if (scOut != null) scOut.close(); } catch (Throwable ignored) {}
                        scOut = null;
                        final java.io.File wf = scFile;
                        scFile = null;
                        if (wf != null && wf.exists() && wf.length() > 0 && sSCCtx != null) {
                            new Thread(new Runnable() { public void run() {
                                try {
                                    java.io.File fin = new java.io.File(wf.getParentFile(), wf.getName().replace(".webm", ".mp4"));
                                    // 先试免转码拷流（h264源）；VP8 源 MP4 容器不吃，自动转码 H264
                                    com.arthenica.ffmpegkit.FFmpegSession cs = com.arthenica.ffmpegkit.FFmpegKit.executeWithArguments(
                                        new String[]{"-y", "-i", wf.getAbsolutePath(), "-c", "copy",
                                            "-movflags", "+faststart", fin.getAbsolutePath()});
                                    boolean ok = cs.getState().equals(com.arthenica.ffmpegkit.SessionState.COMPLETED) && fin.length() > 0;
                                    if (!ok) {
                                        try { fin.delete(); } catch (Throwable ignored) {}
                                        cs = com.arthenica.ffmpegkit.FFmpegKit.executeWithArguments(
                                            new String[]{"-y", "-i", wf.getAbsolutePath(),
                                                "-c:v", "libx264", "-preset", "ultrafast", "-crf", "23",
                                                "-c:a", "aac", "-b:a", "128k",
                                                "-movflags", "+faststart", fin.getAbsolutePath()});
                                        ok = cs.getState().equals(com.arthenica.ffmpegkit.SessionState.COMPLETED) && fin.length() > 0;
                                    }
                                    if (ok) {
                                        android.content.ContentValues cv = new android.content.ContentValues();
                                        cv.put(android.provider.MediaStore.Video.Media.DISPLAY_NAME, fin.getName());
                                        cv.put(android.provider.MediaStore.Video.Media.MIME_TYPE, "video/mp4");
                                        if (android.os.Build.VERSION.SDK_INT >= 29)
                                            cv.put(android.provider.MediaStore.Video.Media.RELATIVE_PATH, "Movies/资源嗅探");
                                        android.net.Uri uri = sSCCtx.getContentResolver().insert(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv);
                                        if (uri != null) {
                                            java.io.InputStream in2 = new java.io.FileInputStream(fin);
                                            OutputStream os2 = sSCCtx.getContentResolver().openOutputStream(uri);
                                            byte[] bb2 = new byte[64 * 1024]; int n2;
                                            while ((n2 = in2.read(bb2)) > 0) os2.write(bb2, 0, n2);
                                            os2.close(); in2.close();
                                        }
                                        HistoryStore.add(sSCCtx, "视频", fin.getName(), "file://" + fin.getAbsolutePath());
                                        wf.delete();
                                    }
                                } catch (Throwable ignored) {}
                            } }).start();
                        }
                        writeResp(s, "200 OK", "text/plain", "ok".getBytes());
                    } else {
                        writeResp(s, "400 Bad Request", "text/plain", "bad act".getBytes());
                    }
                } catch (Throwable e4) {
                    writeResp(s, "500 Error", "text/plain", ("err " + e4.getClass().getSimpleName()).getBytes());
                }
                return;
            }

            if (path.startsWith("/live.ts")) {
                // 桥接 TS 流：outPipe 是 FIFO，阻塞式直读即可（不能轮询 length）
                try {
                    String f = tsPipe;
                    if (f == null) { writeResp(s, "404 Not Found", "text/plain", "no bridge".getBytes()); return; }
                    OutputStream os = s.getOutputStream();
                    os.write("HTTP/1.1 200 OK\r\nContent-Type: video/mp2t\r\nConnection: close\r\n\r\n".getBytes());
                    os.flush();
                    java.io.FileInputStream in = new java.io.FileInputStream(f);
                    byte[] rb = new byte[65536];
                    int rn;
                    while ((rn = in.read(rb)) > 0) {
                        os.write(rb, 0, rn);
                        os.flush();
                    }
                    in.close();
                } catch (Throwable ignored) {}
                return;
            }

            if (path.startsWith("/bili")) {
                // B站中转：u=原始媒体地址，带 B站 Referer/UA；流式转发 + Range 支持
                try {
                    String raw = URLDecoder.decode(queryParam(path, "u"), "UTF-8");
                    // 读请求头里的 Range
                    String range = null;
                    String hl;
                    while ((hl = br.readLine()) != null && !hl.isEmpty()) {
                        if (hl.toLowerCase().startsWith("range:")) {
                            range = hl.substring(6).trim();
                        }
                    }
                    HttpURLConnection oc = (HttpURLConnection) new URL(raw).openConnection();
                    oc.setConnectTimeout(8000);
                    oc.setReadTimeout(30000);
                    oc.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) Chrome/120 Mobile");
                    oc.setRequestProperty("Referer", "https://www.bilibili.com/");
                    if (range != null) oc.setRequestProperty("Range", range);
                    int code = oc.getResponseCode();
                    if (code == 200 || code == 206) {
                        OutputStream os = s.getOutputStream();
                        StringBuilder hh = new StringBuilder("HTTP/1.1 " + code + (code == 206 ? " Partial Content" : " OK") + "\r\n");
                        String cr = oc.getHeaderField("Content-Range");
                        String cl = oc.getHeaderField("Content-Length");
                        String ct = oc.getHeaderField("Content-Type");
                        hh.append("Content-Type: ").append(ct != null ? ct : "video/mp4").append("\r\n");
                        if (cr != null) hh.append("Content-Range: ").append(cr).append("\r\n");
                        if (cl != null) hh.append("Content-Length: ").append(cl).append("\r\n");
                        hh.append("Accept-Ranges: bytes\r\nConnection: close\r\n\r\n");
                        os.write(hh.toString().getBytes());
                        InputStream in = oc.getInputStream();
                        byte[] rb = new byte[65536];
                        int rn;
                        while ((rn = in.read(rb)) > 0) os.write(rb, 0, rn);
                        os.flush();
                        in.close(); oc.disconnect();
                        return;
                    }
                    oc.disconnect();
                    writeResp(s, "404 Not Found", "text/plain", "bili upstream err".getBytes());
                } catch (Throwable e) {
                    writeResp(s, "404 Not Found", "text/plain", "bili relay err".getBytes());
                }
                return;
            }

            if (path.startsWith("/dy")) {
                // 抖音中转：u=原始媒体地址，带抖音 Referer/UA
                try {
                    String raw = URLDecoder.decode(queryParam(path, "u"), "UTF-8");
                    String range = null;
                    String hl;
                    while ((hl = br.readLine()) != null && !hl.isEmpty()) {
                        if (hl.toLowerCase().startsWith("range:")) range = hl.substring(6).trim();
                    }
                    HttpURLConnection oc = (HttpURLConnection) new URL(raw).openConnection();
                    oc.setConnectTimeout(8000);
                    oc.setReadTimeout(30000);
                    oc.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) Chrome/120 Mobile");
                    oc.setRequestProperty("Referer", "https://www.douyin.com/");
                    if (range != null) oc.setRequestProperty("Range", range);
                    int code = oc.getResponseCode();
                    if (code == 200 || code == 206) {
                        OutputStream os = s.getOutputStream();
                        StringBuilder hh = new StringBuilder("HTTP/1.1 " + code + (code == 206 ? " Partial Content" : " OK") + "\r\n");
                        String cr = oc.getHeaderField("Content-Range");
                        String cl = oc.getHeaderField("Content-Length");
                        String ct = oc.getHeaderField("Content-Type");
                        hh.append("Content-Type: ").append(ct != null ? ct : "video/mp4").append("\r\n");
                        if (cr != null) hh.append("Content-Range: ").append(cr).append("\r\n");
                        if (cl != null) hh.append("Content-Length: ").append(cl).append("\r\n");
                        hh.append("Accept-Ranges: bytes\r\nConnection: close\r\n\r\n");
                        os.write(hh.toString().getBytes());
                        InputStream in = oc.getInputStream();
                        byte[] rb = new byte[65536];
                        int rn;
                        while ((rn = in.read(rb)) > 0) os.write(rb, 0, rn);
                        os.flush();
                        in.close(); oc.disconnect();
                        return;
                    }
                    oc.disconnect();
                    writeResp(s, "404 Not Found", "text/plain", "dy upstream err".getBytes());
                } catch (Throwable e) {
                    writeResp(s, "404 Not Found", "text/plain", "dy relay err".getBytes());
                }
                return;
            }

            if (path.startsWith("/playlist.m3u8")) {
                byte[] body = latestBody;
                if (body == null) { writeResp(s, "404 Not Found", "text/plain", "no stream".getBytes()); return; }
                StringBuilder sb = new StringBuilder();
                for (String line : new String(body, "UTF-8").split("\n")) {
                    line = line.trim();
                    if (line.startsWith("#") || line.isEmpty()) { sb.append(line).append("\n"); continue; }
                    String enc = java.net.URLEncoder.encode(line, "UTF-8");
                    sb.append("http://127.0.0.1:").append(PORT).append("/ts?u=").append(enc).append("\n");
                }
                writeResp(s, "200 OK", "application/vnd.apple.mpegurl", sb.toString().getBytes());
                return;
            }

            if (path.startsWith("/ts?u=")) {
                String raw = URLDecoder.decode(queryParam(path, "u"), "UTF-8");
                byte[] body = httpGet(raw);
                if (body == null) { writeResp(s, "404 Not Found", "text/plain", "seg err".getBytes()); return; }
                writeResp(s, "200 OK", "video/mp2t", body);
                return;
            }

            writeResp(s, "404 Not Found", "text/plain", "no".getBytes());
        } catch (Throwable ignored) {}
    }

    private static String queryParam(String path, String key) {
        try {
            int qi = path.indexOf('?');
            if (qi < 0) return "";
            for (String kv : path.substring(qi + 1).split("&")) {
                int eq = kv.indexOf('=');
                if (eq > 0 && kv.substring(0, eq).equals(key)) return kv.substring(eq + 1);
            }
        } catch (Exception ignored) {}
        return "";
    }

    private static void writeResp(Socket s, String status, String type, byte[] body) {
        try {
            OutputStream os = s.getOutputStream();
            os.write(("HTTP/1.1 " + status + "\r\nContent-Type: " + type + "\r\nContent-Length: "
                + body.length + "\r\nConnection: close\r\n\r\n").getBytes());
            os.write(body);
            os.flush();
        } catch (Throwable ignored) {}
    }
}
