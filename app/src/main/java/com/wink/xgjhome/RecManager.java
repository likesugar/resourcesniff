package com.wink.xgjhome;

import android.content.Context;
import android.content.Intent;
import com.wink.xgjhome.HomeActivity;

public class RecManager {

    // ---------- 直播录制（纯 Java 拉流写文件，支持多路并行） ----------
    static class RecJob {
        int id;
        volatile boolean active = true;
        volatile boolean paused = false;
        volatile java.net.HttpURLConnection conn;
        Thread thread;
        String name;
        String url;
        java.io.File file;
        android.net.Uri storeUri;
        volatile long bytes = 0;
        volatile boolean finishNow = false;
        volatile String state = null;   // null=暂停录制 / 转换MP4中 / 转换失败
        int notifId;
        volatile long secs = 0;
        volatile long startTs = 0;
    }
    public static final java.util.concurrent.ConcurrentHashMap<Integer, RecJob> recJobs =
        new java.util.concurrent.ConcurrentHashMap<>();
    public static final java.util.concurrent.ConcurrentHashMap<Integer, RecJob> stoppedJobs =
        new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile int recSeq = 0;
    private static volatile com.arthenica.ffmpegkit.FFmpegSession ffkSession = null;
    private static android.os.PowerManager.WakeLock recWake = null;
    private static Context sCtx;
    public static void init(Context appCtx) { if (sCtx == null) sCtx = appCtx; }
    public static volatile String lastStreamUrl = null;

    private static void acquireWake() {
        try {
            if (recWake == null && sCtx != null) {
                android.os.PowerManager pm = (android.os.PowerManager) sCtx.getSystemService(Context.POWER_SERVICE);
                recWake = pm.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "pillmate:rec");
                recWake.acquire(4 * 3600 * 1000L);
            }
        } catch (Throwable ignored) {}
    }

    private static void releaseWakeIfIdle() {
        try {
            if (recJobs.isEmpty() && recWake != null) { recWake.release(); recWake = null; }
        } catch (Throwable ignored) {}
    }

    static void startRecJob(final String url) {
        if (url.toLowerCase().contains(".m3u8")) { startHlsRec(url); return; }
        RecJob job = new RecJob();
        job.id = ++recSeq;
        job.notifId = 9000 + job.id;
        job.url = url;
        String name;
        try {
            String lu = url.toLowerCase();
            String ext = lu.contains(".flv") ? "flv" : "mp4";
            name = "录制_" + new java.text.SimpleDateFormat("MMdd_HHmmss", java.util.Locale.US).format(new java.util.Date()) + "_" + job.id + "." + ext;
            android.content.ContentValues cv = new android.content.ContentValues();
            cv.put(android.provider.MediaStore.Video.Media.DISPLAY_NAME, name);
            cv.put(android.provider.MediaStore.Video.Media.MIME_TYPE, "mp4".equals(ext) ? "video/mp4" : "video/x-flv");
            cv.put(android.provider.MediaStore.Video.Media.RELATIVE_PATH, "Movies/录制");
            job.storeUri = sCtx.getContentResolver().insert(
                android.provider.MediaStore.Video.Media.getContentUri("external_primary"), cv);
            if (job.storeUri == null) return;
        } catch (Throwable t) { return; }
        job.name = name;
        startPull(job, false);
    }

    private static void startPull(final RecJob job, final boolean append) {
        job.active = true;
        job.paused = false;
        job.startTs = System.currentTimeMillis();
        recJobs.put(job.id, job);
        stoppedJobs.remove(job.id);
        acquireWake();
        job.thread = new Thread(new Runnable() {
            public void run() {
                java.io.FileOutputStream fo = null;
                boolean ended = false;
                try {
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(job.url).openConnection();
                    job.conn = c;
                    c.setConnectTimeout(10000);
                    c.setReadTimeout(15000);
                    String lu = job.url.toLowerCase();
                    c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) Chrome/120 Mobile");
                    c.setRequestProperty("Referer", lu.contains("bilibili") ? "https://www.bilibili.com/" : "https://live.douyin.com/");
                    if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
                    java.io.InputStream in = c.getInputStream();
                    java.io.OutputStream os = sCtx.getContentResolver().openOutputStream(job.storeUri, append ? "wa" : "w");
                    fo = (os instanceof java.io.FileOutputStream) ? (java.io.FileOutputStream) os : null;
                    java.io.OutputStream out = fo != null ? fo : os;
                    byte[] b = new byte[32768]; int n;
                    while ((n = in.read(b)) > 0 && job.active && !job.paused) { out.write(b, 0, n); job.bytes += n; }
                    try { out.close(); } catch (Exception ignored) {} fo = null;
                    try { in.close(); } catch (Exception ignored) {}
                    try { c.disconnect(); } catch (Throwable ignored) {}
                    ended = !job.paused;
                } catch (Throwable t) {
                } finally {
                    try { if (fo != null) fo.close(); } catch (Exception ignored) {}
                    try { if (job.conn != null) job.conn.disconnect(); } catch (Throwable ignored) {}
                    job.conn = null;
                    if (job.startTs > 0) job.secs += (System.currentTimeMillis() - job.startTs) / 1000;
                    job.startTs = 0;
                    job.active = false;
                    recJobs.remove(job.id);
                    releaseWakeIfIdle();
                    try {   // 收尾：解除 pending，让系统文件管理器可见
                        android.content.ContentValues cv = new android.content.ContentValues();
                        cv.put(android.provider.MediaStore.Video.Media.IS_PENDING, 0);
                        sCtx.getContentResolver().update(job.storeUri, cv, null, null);
                    } catch (Throwable ignored) {}
                    if (job.finishNow) {
                        stoppedJobs.put(job.id, job);
                        convertToMp4(job);
                    } else {
                        job.state = null;
                        stoppedJobs.put(job.id, job);   // 暂停/断流：可继续
                    }
                }
            }
        });
        job.thread.start();
    }

    /** 停止（暂停）录制：断流、通知取消，文件保留可继续 */
    /** m3u8：ffmpeg-kit 直接拉 HLS 录成 MP4（-c copy 边下边封装） */

    /** m3u8：纯 Java 解析播放清单，循环抓 .ts 分片合并（小工具 KB 同款请求头） */
    static void startHlsRec(final String url) {
        try {
            final RecJob job = new RecJob();
            job.id = ++recSeq;
            job.notifId = 9000 + job.id;
            job.url = url;
            job.name = "直播_hls_" + System.currentTimeMillis() + ".ts";
            job.startTs = System.currentTimeMillis();
            android.content.ContentValues cv = new android.content.ContentValues();
            cv.put(android.provider.MediaStore.Video.Media.DISPLAY_NAME, job.name);
            cv.put(android.provider.MediaStore.Video.Media.MIME_TYPE, "video/mp2ts");
            cv.put(android.provider.MediaStore.Video.Media.RELATIVE_PATH, "Movies/录制");
            cv.put(android.provider.MediaStore.Video.Media.IS_PENDING, 1);
            job.storeUri = sCtx.getContentResolver()
                .insert(android.provider.MediaStore.Video.Media.getContentUri("external_primary"), cv);
            recJobs.put(job.id, job);
            acquireWake();
            job.thread = new Thread(new Runnable() {
                public void run() {
                    java.io.FileOutputStream fo = null;
                    try {
                        fo = (java.io.FileOutputStream) sCtx.getContentResolver().openOutputStream(job.storeUri, "w");
                        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
                        byte[] buf = new byte[65536];
                        String curUrl = job.url;
                        while (job.active) {
                            java.net.HttpURLConnection pc = (java.net.HttpURLConnection) new java.net.URL(curUrl).openConnection();
                            pc.setConnectTimeout(8000); pc.setReadTimeout(8000);
                            pc.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
                            pc.setRequestProperty("Referer", "https://live.douyin.com/");
                            java.util.List<String> segs = new java.util.ArrayList<String>();
                            String variant = null;
                            if (pc.getResponseCode() == 200) {
                                java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(pc.getInputStream()));
                                String ln, lastInf = null;
                                boolean inVariant = false;
                                while ((ln = br.readLine()) != null) {
                                    ln = ln.trim();
                                    if (ln.startsWith("#EXT-X-STREAM-INF")) { inVariant = true; continue; }
                                    if (inVariant && !ln.isEmpty() && !ln.startsWith("#")) {
                                        String v = ln;
                                        if (!v.startsWith("http")) v = new java.net.URL(new java.net.URL(curUrl), v).toString();
                                        variant = v;
                                        inVariant = false;
                                        continue;
                                    }
                                    if (ln.startsWith("#EXTINF")) lastInf = ln;
                                    else if (!ln.isEmpty() && !ln.startsWith("#") && lastInf != null) {
                                        String seg = ln;
                                        if (!seg.startsWith("http")) seg = new java.net.URL(new java.net.URL(curUrl), seg).toString();
                                        segs.add(seg);
                                        lastInf = null;
                                    }
                                }
                                br.close();
                            }
                            pc.disconnect();
                            if (variant != null && segs.isEmpty()) { curUrl = variant; continue; }
                            for (String seg : segs) {
                                if (!job.active) break;
                                if (!seen.add(seg)) continue;
                                java.net.HttpURLConnection sc = (java.net.HttpURLConnection) new java.net.URL(seg).openConnection();
                                sc.setConnectTimeout(8000); sc.setReadTimeout(15000);
                                sc.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
                                sc.setRequestProperty("Referer", "https://live.douyin.com/");
                                if (sc.getResponseCode() == 200 && fo != null) {
                                    java.io.InputStream in = sc.getInputStream();
                                    int n;
                                    while (job.active && (n = in.read(buf)) > 0) { fo.write(buf, 0, n); job.bytes += n; }
                                    fo.flush();
                                    in.close();
                                }
                                sc.disconnect();
                            }
                            if (job.finishNow) break;
                            Thread.sleep(2000);
                        }
                    } catch (Throwable t) {
                    } finally {
                        try { if (fo != null) fo.close(); } catch (Exception ignored) {}
                        if (job.startTs > 0) job.secs += (System.currentTimeMillis() - job.startTs) / 1000;
                        job.startTs = 0;
                        job.active = false;
                        recJobs.remove(job.id);
                        releaseWakeIfIdle();
                        try {
                            android.content.ContentValues cv2 = new android.content.ContentValues();
                            cv2.put(android.provider.MediaStore.Video.Media.IS_PENDING, 0);
                            sCtx.getContentResolver().update(job.storeUri, cv2, null, null);
                        } catch (Throwable ignored) {}
                        if (job.finishNow) convertToMp4(job);
                        else stoppedJobs.put(job.id, job);
                    }
                }
            });
            job.thread.start();
        } catch (Throwable e) { }
    }

    static void startFfmpegRec(final String url) {
        try {
            RecJob job = new RecJob();
            job.id = ++recSeq;
            job.notifId = 9000 + job.id;
            job.url = url;
            job.name = "直播·原画";
            job.startTs = System.currentTimeMillis();
            java.io.File out = new java.io.File(
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES),
                "资源嗅探_" + System.currentTimeMillis() + ".mp4");
            job.file = out;
            // MediaStore 登记（IS_PENDING 隐藏，完成转正）
            android.content.ContentValues cv = new android.content.ContentValues();
            cv.put(android.provider.MediaStore.Video.Media.DISPLAY_NAME, out.getName());
            cv.put(android.provider.MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            cv.put(android.provider.MediaStore.Video.Media.RELATIVE_PATH, "Movies/录制");
            cv.put(android.provider.MediaStore.Video.Media.IS_PENDING, 1);
            android.net.Uri storeUri = sCtx.getContentResolver()
                .insert(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv);
            job.storeUri = storeUri;
            recJobs.put(job.id, job);
            acquireWake();
            String args = "-y -user_agent \"" + "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36" + "\" -headers \"Referer: https://live.douyin.com/\\r\\n\" -i \"" + url + "\" -c copy -movflags +faststart -f mp4 \"" + out.getAbsolutePath() + "\"";
            ffkSession = com.arthenica.ffmpegkit.FFmpegKit.executeWithArgumentsAsync(
                com.arthenica.ffmpegkit.FFmpegKitConfig.parseArguments(args),
                new com.arthenica.ffmpegkit.FFmpegSessionCompleteCallback() {
                    public void apply(com.arthenica.ffmpegkit.FFmpegSession st) {
                        try {
                            android.content.ContentValues cv2 = new android.content.ContentValues();
                            cv2.put(android.provider.MediaStore.Video.Media.IS_PENDING, 0);
                            sCtx.getContentResolver().update(storeUri, cv2, null, null);
                        } catch (Throwable e) { }
                        recJobs.remove(job.id);
                        job.state = null;
                        stoppedJobs.put(job.id, job);
                        releaseWakeIfIdle();
                        ffkSession = null;
                    }
                });
            acquireWake();
        } catch (Throwable e) {
            ffkSession = null;
        }
    }

    public static void recStop(int jid) {
        if (ffkSession != null) {
            com.arthenica.ffmpegkit.FFmpegKit.cancel(ffkSession.getSessionId());
            return;
        }
        RecJob job = recJobs.get(jid);
        if (job == null) return;
        job.paused = true;
        job.active = false;
        try { if (job.conn != null) job.conn.disconnect(); } catch (Throwable ignored) {}
        try { if (job.thread != null) job.thread.interrupt(); } catch (Throwable ignored) {}
    }

    /** 继续：同一路重新拉流，追加写入同一文件 */
    public static void recContinue(int jid) {
        RecJob job = stoppedJobs.get(jid);
        if (job == null) return;
        startPull(job, true);
    }

    /** 结束录制：合并转封装成 mp4（后台执行），完成后从列表移除 */
    public static void recFinish(int jid) {
        RecJob live = recJobs.get(jid);
        if (live != null && ffkSession != null) {
            com.arthenica.ffmpegkit.FFmpegKit.cancel(ffkSession.getSessionId());
            return;
        }
        if (live != null) {
            live.finishNow = true;
            recStop(jid);
            return;
        }
        final RecJob job = stoppedJobs.get(jid);
        if (job == null) return;
        stoppedJobs.remove(job.id);
        new Thread(new Runnable() { public void run() { convertToMp4(job); } }).start();
    }

    static void convertToMp4(final RecJob job) {
        job.state = "转换MP4中…";
        try {
            String src = com.arthenica.ffmpegkit.FFmpegKitConfig.getSafParameterForRead(sCtx, job.storeUri);
            java.io.File tmp = new java.io.File(sCtx.getCacheDir(), "conv_" + System.currentTimeMillis() + ".mp4");
            com.arthenica.ffmpegkit.FFmpegSession st = com.arthenica.ffmpegkit.FFmpegKit.executeWithArguments(
                new String[]{"-y", "-i", src, "-c", "copy", "-movflags", "+faststart", tmp.getAbsolutePath()});
            if (st.getState().equals(com.arthenica.ffmpegkit.SessionState.COMPLETED) && tmp.length() > 0) {
                String name = job.name != null ? job.name : "rec.mp4";
                String mp4Name = (name.endsWith(".flv") ? name.substring(0, name.length() - 4) : name) + ".mp4";
                android.content.ContentValues cv = new android.content.ContentValues();
                cv.put(android.provider.MediaStore.Video.Media.DISPLAY_NAME, mp4Name);
                cv.put(android.provider.MediaStore.Video.Media.MIME_TYPE, "video/mp4");
                cv.put(android.provider.MediaStore.Video.Media.RELATIVE_PATH, "Movies/录制");
                android.net.Uri out = sCtx.getContentResolver().insert(
                    android.provider.MediaStore.Video.Media.getContentUri("external_primary"), cv);
                java.io.InputStream in = new java.io.FileInputStream(tmp);
                java.io.OutputStream os = sCtx.getContentResolver().openOutputStream(out);
                byte[] b = new byte[32768]; int n;
                while ((n = in.read(b)) > 0) os.write(b, 0, n);
                os.close(); in.close();
                try { sCtx.getContentResolver().delete(job.storeUri, null, null); } catch (Throwable ignored) {}
            }
            tmp.delete();
            stoppedJobs.remove(job.id);   // 成功：从列表移除
        } catch (Throwable t) {
            job.state = "转换失败(保留flv)";
        }
    }

    /** 彻底取消：删文件、从列表移除 */
    public static void recCancel(int jid) {
        RecJob j = recJobs.get(jid);
        if (j != null) recStop(jid);
        RecJob job = (j != null) ? j : stoppedJobs.get(jid);
        if (job == null) return;
        stoppedJobs.remove(job.id);
        try { if (job.storeUri != null) sCtx.getContentResolver().delete(job.storeUri, null, null); } catch (Throwable ignored) {}
    }

    public static void recStopAll() {
        for (Integer id : recJobs.keySet().toArray(new Integer[0])) recStop(id);
    }
}
