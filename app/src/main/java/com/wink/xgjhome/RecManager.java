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
        String u = url == null ? "" : url.trim();
        while (u.endsWith("\\") || u.endsWith("\"") || u.endsWith("'") || u.endsWith(",")) u = u.substring(0, u.length() - 1).trim();
        if (u.isEmpty() || !u.startsWith("http")) return;
        startBgRec(u); return;
        /*
*/
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

    /** m3u8：纯 Java 解析播放清单，循环抓 .ts 分片合并（小工具 KB 同款请求头，容错重试） */
    static void startHlsRec(final String url) {
        try {
            final RecJob job = new RecJob();
            job.id = ++recSeq;
            job.notifId = 9000 + job.id;
            job.url = url;
            job.name = "直播·原画_" + System.currentTimeMillis() / 1000 + ".ts";
            job.startTs = System.currentTimeMillis();
            java.io.File dir = new java.io.File(sCtx.getExternalFilesDir(null), "录制");
            dir.mkdirs();
            job.file = new java.io.File(dir, job.name);
            recJobs.put(job.id, job);
            acquireWake();
            job.thread = new Thread(new Runnable() {
                public void run() {
                    java.io.FileOutputStream fo = null;
                    try {
                        fo = new java.io.FileOutputStream(job.file, true);
                        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
                        byte[] buf = new byte[65536];
                        String curUrl = job.url;
                        int failStreak = 0;
                        while (job.active && failStreak < 30) {
                            // ---- 拉清单（独立容错）----
                            java.util.List<String> segs = new java.util.ArrayList<String>();
                            java.util.List<String> variants = new java.util.ArrayList<String>();
                            boolean ok = false;
                            java.net.HttpURLConnection pc = null;
                            try {
                                pc = (java.net.HttpURLConnection) new java.net.URL(curUrl).openConnection();
                                pc.setConnectTimeout(8000); pc.setReadTimeout(8000);
                                pc.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
                                pc.setRequestProperty("Referer", "https://live.douyin.com/");
                                int rc = pc.getResponseCode();
                                if (rc == 200) {
                                    ok = true;
                                    java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(pc.getInputStream()));
                                    String ln, lastInf = null;
                                    boolean inVariant = false;
                                    while ((ln = br.readLine()) != null) {
                                        ln = ln.trim();
                                        if (ln.startsWith("#EXT-X-STREAM-INF")) { inVariant = true; continue; }
                                        if (inVariant && !ln.isEmpty() && !ln.startsWith("#")) {
                                            String v = ln;
                                            if (!v.startsWith("http")) v = new java.net.URL(new java.net.URL(curUrl), v).toString();
                                            variants.add(v); inVariant = false; continue;
                                        }
                                        if (ln.startsWith("#EXTINF")) lastInf = ln;
                                        else if (!ln.isEmpty() && !ln.startsWith("#") && lastInf != null) {
                                            String seg = ln;
                                            if (!seg.startsWith("http")) seg = new java.net.URL(new java.net.URL(curUrl), seg).toString();
                                            segs.add(seg); lastInf = null;
                                        }
                                    }
                                    br.close();
                                }
                            } catch (Throwable pe) {
                            } finally {
                                if (pc != null) { try { pc.disconnect(); } catch (Throwable ignored) {} }
                            }
                            // master 清单：优先 _or4 原画，其次第一个变体
                            if (!variants.isEmpty() && segs.isEmpty()) {
                                String pick = variants.get(0);
                                for (String v : variants) if (v.contains("_or4")) { pick = v; break; }
                                curUrl = pick; failStreak = 0; continue;
                            }
                            if (!ok) { failStreak++; job.state = "清单失败x" + failStreak + " (HTTP" + pc.getResponseCode() + ")"; Thread.sleep(2000); continue; }
                            // ---- 逐分片下载（独立容错）----
                            for (String seg : segs) {
                                if (!job.active) break;
                                if (!seen.add(seg)) continue;
                                java.net.HttpURLConnection sc = null;
                                try {
                                    sc = (java.net.HttpURLConnection) new java.net.URL(seg).openConnection();
                                    sc.setConnectTimeout(8000); sc.setReadTimeout(15000);
                                    sc.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
                                    sc.setRequestProperty("Referer", "https://live.douyin.com/");
                                    if (sc.getResponseCode() == 200 && fo != null) {
                                        java.io.InputStream in = sc.getInputStream();
                                        int n;
                                        while (job.active && (n = in.read(buf)) > 0) { fo.write(buf, 0, n); job.bytes += n; }
                                        fo.flush();
                                        in.close();
                                        failStreak = 0;
                                    } else failStreak++;
                                } catch (Throwable se) {
                                    failStreak++;
                                    job.state = "分片失败x" + failStreak + " (" + se.getClass().getSimpleName() + ")";
                                } finally {
                                    if (sc != null) { try { sc.disconnect(); } catch (Throwable ignored) {} }
                                }
                            }
                            if (job.finishNow) break;
                            Thread.sleep(2000);
                        }
                    } catch (Throwable t) {
                        job.state = "中断: " + t.getClass().getSimpleName();
                    } finally {
                        try { if (fo != null) fo.close(); } catch (Exception ignored) {}
                        if (job.startTs > 0) job.secs += (System.currentTimeMillis() - job.startTs) / 1000;
                        job.startTs = 0;
                        job.active = false;
                        recJobs.remove(job.id);
                        releaseWakeIfIdle();
                        if (job.finishNow && job.bytes > 0) convertFileToMp4(job);
                        else stoppedJobs.put(job.id, job);
                    }
                }
            });
            job.thread.start();
        } catch (Throwable e) {
            try {
                RecJob jb = new RecJob();
                jb.id = ++recSeq;
                jb.name = "直播·原画（失败）";
                jb.state = "启动失败: " + e.getClass().getSimpleName();
                stoppedJobs.put(jb.id, jb);
            } catch (Throwable ignored) {}
        }
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
        RecJob bg = recJobs.get(jid);
        if (bg != null && bg.file != null && bg.file.isDirectory()) { pauseBg(bg, "暂停录制"); return; }
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
        if (job.file != null && job.file.isDirectory()) {
            stoppedJobs.remove(job.id);
            job.active = true;
            job.paused = false;
            job.state = null;
            job.startTs = System.currentTimeMillis();  // 续录时长继续走
            recJobs.put(job.id, job);
            acquireWake();
            startBgPlayer(job);
            startBgSession(job);
            startWatchdog(job);  // 续录重启看门狗，大小才会刷新
            return;
        }
        startPull(job, true);
    }

    /** 结束录制：合并转封装成 mp4（后台执行），完成后从列表移除 */
    public static void recFinish(int jid) {
        RecJob live = recJobs.get(jid);
        if (live != null && live.file != null && live.file.isDirectory()) {
            stopBgSession();
            stopBgPlayer();
            live.active = false;
            if (live.startTs > 0) {  // 结束时固化已录时长
                live.secs += (System.currentTimeMillis() - live.startTs) / 1000;
                live.startTs = 0;
            }
            recJobs.remove(live.id);
            releaseWakeIfIdle();
            mergeSegs(live);
            return;
        }
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
        if (job.file != null && job.file.isDirectory()) { mergeSegs(job); return; }
        new Thread(new Runnable() { public void run() { convertToMp4(job); } }).start();
    }

    /** ts 文件 → MP4 入相册 Movies/录制 */
    static void convertFileToMp4(final RecJob job) {
        job.state = "转换MP4中…";
        new Thread(new Runnable() { public void run() {
            try {
                String mp4Name = job.name.endsWith(".ts") ? job.name.substring(0, job.name.length() - 3) + ".mp4" : job.name + ".mp4";
                android.content.ContentValues cv = new android.content.ContentValues();
                cv.put(android.provider.MediaStore.Video.Media.DISPLAY_NAME, mp4Name);
                cv.put(android.provider.MediaStore.Video.Media.MIME_TYPE, "video/mp4");
                cv.put(android.provider.MediaStore.Video.Media.RELATIVE_PATH, "Movies/录制");
                android.net.Uri out = sCtx.getContentResolver().insert(
                    android.provider.MediaStore.Video.Media.getContentUri("external_primary"), cv);
                java.io.File tmp = new java.io.File(sCtx.getCacheDir(), "conv_" + System.currentTimeMillis() + ".mp4");
                com.arthenica.ffmpegkit.FFmpegSession st = com.arthenica.ffmpegkit.FFmpegKit.executeWithArguments(
                    new String[]{"-y", "-fflags", "+genpts", "-i", job.file.getAbsolutePath(), "-c", "copy", "-movflags", "+faststart", tmp.getAbsolutePath()});
                if (st.getState().equals(com.arthenica.ffmpegkit.SessionState.COMPLETED) && tmp.length() > 0) {
                    java.io.InputStream in = new java.io.FileInputStream(tmp);
                    java.io.OutputStream os = sCtx.getContentResolver().openOutputStream(out);
                    byte[] b = new byte[32768]; int n;
                    while ((n = in.read(b)) > 0) os.write(b, 0, n);
                    in.close(); os.close();
                    job.file.delete();
                    job.state = null;
                    HistoryStore.add(sCtx, "视频", job.name, out.toString());
                } else job.state = "转换失败";
                tmp.delete();
            } catch (Throwable e) {
                job.state = "转换失败: " + e.getClass().getSimpleName();
            }
            stoppedJobs.put(job.id, job);
        } }).start();
    }


    // ---------- 后台静音播放器拉流录制：分段落盘 + 无流量自动暂停 ----------
    private static tv.danmaku.ijk.media.player.IjkMediaPlayer bgPlayer;
    private static com.arthenica.ffmpegkit.FFmpegSession bgSession;
    private static volatile boolean bgCancel = false;
    private static final String BG_UA = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    static void startBgRec(final String url) {
        try {
            final RecJob job = new RecJob();
            job.id = ++recSeq;
            job.notifId = 9000 + job.id;
            job.url = url;
            job.name = "直播·原画_" + System.currentTimeMillis() / 1000 + ".ts";
            job.startTs = System.currentTimeMillis();
            String title = job.name.endsWith(".ts") ? job.name.substring(0, job.name.length() - 3) : job.name;
            final java.io.File dir = new java.io.File(sCtx.getExternalFilesDir(null), "录制合并/" + title);
            dir.mkdirs();
            job.file = dir;
            recJobs.put(job.id, job);
            acquireWake();
            startBgPlayer(job);
            startBgSession(job);
            startWatchdog(job);
        } catch (Throwable e) {
            try {
                RecJob jb = new RecJob();
                jb.id = ++recSeq;
                jb.name = "直播·原画（失败）";
                jb.state = "启动失败: " + e.getClass().getSimpleName();
                stoppedJobs.put(jb.id, jb);
            } catch (Throwable ignored) {}
        }
    }

    static void startWatchdog(final RecJob job) {
        new Thread(new Runnable() { public void run() {  // 流量看门狗：1s刷大小，5s查流量
            long last = -1;
            int idle = 0;
            int c = 0;
            while (job.active) {
                try { Thread.sleep(1000); } catch (Throwable e) { break; }
                if (!job.active) break;
                job.bytes = dirTotal(job);
                c++;
                if (c % 5 != 0) continue;
                long total = job.bytes;
                if (total == last) {
                    idle++;
                    if (idle <= 3) {   // 无流量 → 自动重试抓流
                        job.state = "无流量，重试抓流 " + idle + "/3…";
                        stopBgSession();
                        stopBgPlayer();
                        try { Thread.sleep(5000); } catch (Throwable e) { break; }
                        if (!job.active) break;
                        job.state = null;
                        startBgPlayer(job);
                        startBgSession(job);
                    } else {           // 连续3轮重试都拉不到 → 非手动暂停场景自动结束并合并MP4
                        job.state = "拉取不到，自动合并";
                        recFinish(job.id);
                        break;
                    }
                } else { idle = 0; }
                last = total;
            }
        } }).start();
    }

    static long dirTotal(RecJob job) {
        try {
            long t = 0;
            java.io.File[] fs = job.file.listFiles();
            if (fs == null) return -1;
            for (java.io.File f : fs) t += f.length();
            return t;
        } catch (Throwable e) { return -1; }
    }

    static volatile int bgGen = 0;  // 会话代号：旧会话回调不作数

    static void startBgSession(final RecJob job) {
        final int gen = ++bgGen;
        bgCancel = false;
        // 续录：分段号接着已有 seg 继续编，避免覆盖旧分段
        int segN = 0;
        try {
            java.io.File[] fs = job.file.listFiles();
            if (fs != null) for (java.io.File f : fs) if (f.getName().startsWith("seg")) segN++;
        } catch (Throwable ignored) {}
        String[] args = { "-y", "-user_agent", BG_UA,
            "-headers", "Referer: https://live.douyin.com/\r\n",
            "-i", job.url, "-c", "copy",
            "-f", "segment", "-segment_time", "30", "-reset_timestamps", "1",
            "-segment_start_number", String.valueOf(segN),
            new java.io.File(job.file, "seg%03d.ts").getAbsolutePath() };
        bgSession = com.arthenica.ffmpegkit.FFmpegKit.executeWithArgumentsAsync(args,
            new com.arthenica.ffmpegkit.FFmpegSessionCompleteCallback() {
                public void apply(com.arthenica.ffmpegkit.FFmpegSession st) {
                    if (gen != bgGen || bgCancel) return;  // 旧会话/已暂停：直接作废
                    if (job.active) {
                        try { Thread.sleep(1000); } catch (Throwable e) { }
                        if (job.active && gen == bgGen) startBgSession(job);  // 断流自动重连续录
                    }
                }
            });
    }

    static void startBgPlayer(final RecJob job) {
        stopBgPlayer();
        try {
            tv.danmaku.ijk.media.player.IjkMediaPlayer m = new tv.danmaku.ijk.media.player.IjkMediaPlayer();
            java.util.Map<String, String> h = new java.util.HashMap<String, String>();
            h.put("Referer", "https://live.douyin.com/");
            h.put("User-Agent", BG_UA);
            m.setDataSource(job.url, h);
            m.setVolume(0f, 0f);
            m.prepareAsync();
            m.start();
            bgPlayer = m;
        } catch (Throwable e) { stopBgPlayer(); }
    }

    static void stopBgPlayer() {
        try { if (bgPlayer != null) bgPlayer.stop(); } catch (Throwable ignored) {}
        try { if (bgPlayer != null) bgPlayer.release(); } catch (Throwable ignored) {}
        bgPlayer = null;
    }

    static void stopBgSession() {
        bgCancel = true;
        try { if (bgSession != null) com.arthenica.ffmpegkit.FFmpegKit.cancel(bgSession.getSessionId()); } catch (Throwable ignored) {}
        bgSession = null;
    }

    static void pauseBg(RecJob job, String why) {
        stopBgSession();
        stopBgPlayer();
        job.active = false;
        job.paused = true;
        if (job.startTs > 0) {
            job.secs += (System.currentTimeMillis() - job.startTs) / 1000;
            job.startTs = 0;
        }
        job.state = why;
        recJobs.remove(job.id);
        stoppedJobs.put(job.id, job);
        releaseWakeIfIdle();
    }

    static void mergeSegs(final RecJob job) {
        job.state = "转换MP4中…";
        new Thread(new Runnable() { public void run() {
            try {
                java.io.File[] segs = job.file.listFiles();
                java.util.Arrays.sort(segs);
                java.util.List<java.io.File> list = new java.util.ArrayList<java.io.File>();
                for (java.io.File f : segs) if (f.getName().startsWith("seg") && f.length() > 0) list.add(f);
                if (list.isEmpty()) { job.state = "无数据"; stoppedJobs.put(job.id, job); return; }
                java.io.File listFile = new java.io.File(job.file, "list.txt");
                java.io.FileWriter fw = new java.io.FileWriter(listFile);
                for (java.io.File f : list) fw.write("file '" + f.getAbsolutePath() + "'\n");
                fw.close();
                final java.io.File mp4 = new java.io.File(job.file, "out.mp4");
                final long totalSecs = job.secs > 0 ? job.secs : 1;
                com.arthenica.ffmpegkit.FFmpegSession st = com.arthenica.ffmpegkit.FFmpegKit.executeWithArgumentsAsync(
                    new String[]{"-y", "-f", "concat", "-safe", "0", "-i", listFile.getAbsolutePath(),
                        "-c", "copy", "-fflags", "+genpts", "-movflags", "+faststart", mp4.getAbsolutePath()},
                    new com.arthenica.ffmpegkit.FFmpegSessionCompleteCallback() {
                        public void apply(com.arthenica.ffmpegkit.FFmpegSession st2) { }
                    }, new com.arthenica.ffmpegkit.LogCallback() {
                        public void apply(com.arthenica.ffmpegkit.Log m) { }
                    }, new com.arthenica.ffmpegkit.StatisticsCallback() {
                        public void apply(com.arthenica.ffmpegkit.Statistics stat) {
                            try {
                                int p = (int) (stat.getTime() / totalSecs * 100);
                                if (p > 100) p = 100;
                                if (p < 0) p = 0;
                                job.state = "合并MP4中 " + p + "%";
                            } catch (Throwable ignored) {}
                        }
                    });
                while (!st.getState().equals(com.arthenica.ffmpegkit.SessionState.COMPLETED)
                    && !st.getState().equals(com.arthenica.ffmpegkit.SessionState.FAILED)
                    ) {
                    try { Thread.sleep(500); } catch (Throwable e) { }
                }
                if (st.getState().equals(com.arthenica.ffmpegkit.SessionState.COMPLETED) && mp4.length() > 0) {
                    String mp4Name = job.name.endsWith(".ts") ? job.name.substring(0, job.name.length() - 3) + ".mp4" : job.name + ".mp4";
                    android.content.ContentValues cv = new android.content.ContentValues();
                    cv.put(android.provider.MediaStore.Video.Media.DISPLAY_NAME, mp4Name);
                    cv.put(android.provider.MediaStore.Video.Media.MIME_TYPE, "video/mp4");
                    cv.put(android.provider.MediaStore.Video.Media.RELATIVE_PATH, "Movies/录制");
                    android.net.Uri out = sCtx.getContentResolver().insert(
                        android.provider.MediaStore.Video.Media.getContentUri("external_primary"), cv);
                    java.io.InputStream in = new java.io.FileInputStream(mp4);
                    java.io.OutputStream os = sCtx.getContentResolver().openOutputStream(out);
                    byte[] b = new byte[32768]; int n;
                    while ((n = in.read(b)) > 0) os.write(b, 0, n);
                    in.close(); os.close();
                    // 保留 MP4 在 /Android/data/com.wink.xgjhome/files/录制/<文件夹>/
                    for (java.io.File f : job.file.listFiles()) {
                        if (!f.equals(mp4) && !f.equals(listFile)) f.delete();
                    }
                    listFile.delete();
                    mp4.renameTo(new java.io.File(job.file, mp4Name));
                    job.state = "已保存: files/录制合并/" + mp4Name.replace(".mp4", "") + "/" + mp4Name;
                    HistoryStore.add(sCtx, "录制", mp4Name, "file://" + new java.io.File(job.file, mp4Name).getAbsolutePath());
                } else job.state = "合并失败";
                stoppedJobs.put(job.id, job);
            } catch (Throwable e) {
                job.state = "转换失败: " + e.getClass().getSimpleName();
                stoppedJobs.put(job.id, job);
            }
        } }).start();
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
