package com.wink.xgjhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** 录制/下载记录存储 + 后台引擎（线程常驻，离开页面继续跑；支持暂停/继续） */
public class RecEngine {

    public static final String TYPE_REC = "录制";
    public static final String TYPE_DL = "下载";
    public static final String ST_REC = "录制中";
    public static final String ST_PAUSE = "已暂停";
    public static final String ST_DONE = "已结束";
    public static final String ST_DL = "下载中";
    public static final String ST_OK = "已完成";
    public static final String ST_CANCEL = "已取消";

    static final String PREF = "records";
    static final Map<String, Thread> TASK = new HashMap<>();
    static final Map<String, Boolean> PAUSED = new HashMap<>();
    static final Map<String, Long> START = new HashMap<>();

    public static SharedPreferences sp(Context c) { return c.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE); }

    public static List<JSONObject> load(Context c) {
        List<JSONObject> out = new ArrayList<JSONObject>();
        try {
            JSONArray arr = new JSONArray(sp(c).getString("list", "[]"));
            for (int i = 0; i < arr.length(); i++) out.add(arr.getJSONObject(i));
        } catch (Throwable e) { }
        return out;
    }

    public static void save(Context c, List<JSONObject> list) {
        JSONArray arr = new JSONArray();
        for (JSONObject o : list) arr.put(o);
        sp(c).edit().putString("list", arr.toString()).apply();
    }

    public static void add(Context c, String type, String title, String url) {
        List<JSONObject> l = load(c);
        for (JSONObject o : l) if (url.equals(o.optString("url"))) return;
        try {
            JSONObject o = new JSONObject();
            o.put("type", type); o.put("title", title); o.put("url", url);
            o.put("status", TYPE_REC.equals(type) ? ST_REC : ST_DL);
            o.put("size", 0); o.put("duration", 0); o.put("start", System.currentTimeMillis());
            o.put("path", "");
            l.add(0, o);
            save(c, l);
        } catch (Throwable e) { }
    }

    public static void update(Context c, String url, String key, Object v) {
        List<JSONObject> l = load(c);
        boolean ch = false;
        try {
            for (JSONObject o : l) {
                if (url.equals(o.optString("url"))) { o.put(key, v); ch = true; }
            }
            if (ch) save(c, l);
        } catch (Throwable e) { }
    }

    public static void remove(Context c, String url) {
        List<JSONObject> l = load(c);
        Iterator<JSONObject> it = l.iterator();
        while (it.hasNext()) if (url.equals(it.next().optString("url"))) it.remove();
        save(c, l);
        Thread t = TASK.remove(url);
        if (t != null) t.interrupt();
        PAUSED.remove(url); START.remove(url);
    }

    public static boolean running(String url) { return TASK.containsKey(url); }

    public static void pause(Context c, String url) {
        PAUSED.put(url, true);
        String st = statusOf(c, url);
        update(c, url, "status", TYPE_REC.equals(st) || url.contains(".flv") ? ST_PAUSE : ST_PAUSE);
        Thread t = TASK.get(url);
        if (t == null) update(c, url, "status", ST_PAUSE);
    }

    public static void resume(Context c, String url) {
        PAUSED.put(url, false);
        update(c, url, "status", TASK.containsKey(url) ? (isRec(c, url) ? ST_REC : ST_DL) : ST_PAUSE);
    }

    static boolean isRec(Context c, String url) {
        for (JSONObject o : load(c)) if (url.equals(o.optString("url"))) return TYPE_REC.equals(o.optString("type"));
        return false;
    }

    static String statusOf(Context c, String url) {
        for (JSONObject o : load(c)) if (url.equals(o.optString("url"))) return o.optString("status");
        return "";
    }

    static boolean paused(String url) { return Boolean.TRUE.equals(PAUSED.get(url)); }

    /** 录制：流式拉流写 ts，后台线程；暂停=停读不断流；结束=ffmpeg-kit 重建 PTS */
    public static void startRecording(final Context ctx, final String url, String title) {
        add(ctx, TYPE_REC, title, url);
        START.put(url, System.currentTimeMillis());
        Thread t = new Thread(new Runnable() { public void run() {
            HttpURLConnection conn = null; InputStream in = null; FileOutputStream fos = null;
            long bytes = 0;
            try {
                String path = get(ctx, url, "path");
                File out = (path != null && path.length() > 0) ? new File(path) :
                        new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                                "资源嗅探_" + System.currentTimeMillis() + ".ts");
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(10000); conn.setReadTimeout(10000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
                if (url.contains("bilibili")) conn.setRequestProperty("Referer", "https://www.bilibili.com/");
                in = conn.getInputStream();
                fos = new FileOutputStream(out, false);
                byte[] buf = new byte[65536];
                while (true) {
                    if (paused(url)) { try { Thread.sleep(500); } catch (InterruptedException e) { break; } continue; }
                    int n = in.read(buf);
                    if (n < 0) break;
                    fos.write(buf, 0, n);
                    bytes += n;
                    update(ctx, url, "size", bytes);
                    update(ctx, url, "duration", (System.currentTimeMillis() - START.get(url)) / 1000);
                    update(ctx, url, "path", out.getAbsolutePath());
                }
                finish(ctx, url, ST_DONE);
            } catch (Throwable e) {
                finish(ctx, url, ST_DONE);
            } finally {
                try { if (in != null) in.close(); } catch (Throwable e) { }
                try { if (fos != null) fos.close(); } catch (Throwable e) { }
                TASK.remove(url);
            }
        }});
        TASK.put(url, t);
        t.start();
    }

    /** 下载：自带线程 + Range 断点续传（DownloadManager 无法暂停），后台运行 */
    public static void startDownload(final Context ctx, final String url, String title) {
        add(ctx, TYPE_DL, title, url);
        START.put(url, System.currentTimeMillis());
        Thread t = new Thread(new Runnable() { public void run() {
            HttpURLConnection conn = null; InputStream in = null; FileOutputStream fos = null;
            try {
                String path = get(ctx, url, "path");
                File out = (path != null && path.length() > 0) ? new File(path) :
                        new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                                "资源嗅探_" + System.currentTimeMillis() + ".mp4");
                long offset = out.exists() ? out.length() : 0;
                long bytes = 0;
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(10000); conn.setReadTimeout(10000);
                if (offset > 0) conn.setRequestProperty("Range", "bytes=" + offset + "-");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
                if (url.contains("bilibili")) conn.setRequestProperty("Referer", "https://www.bilibili.com/");
                in = conn.getInputStream();
                fos = new FileOutputStream(out, offset > 0);
                byte[] buf = new byte[65536];
                while (true) {
                    if (paused(url)) { try { Thread.sleep(500); } catch (InterruptedException e) { break; } continue; }
                    int n = in.read(buf);
                    if (n < 0) break;
                    fos.write(buf, 0, n);
                    update(ctx, url, "size", offset + bytes);
                    update(ctx, url, "path", out.getAbsolutePath());
                    bytes += n;
                }
                finish(ctx, url, ST_OK);
            } catch (Throwable e) {
                finish(ctx, url, ST_PAUSE); // 失败可续传重试
            } finally {
                try { if (in != null) in.close(); } catch (Throwable e) { }
                try { if (fos != null) fos.close(); } catch (Throwable e) { }
                TASK.remove(url);
            }
        }});
        TASK.put(url, t);
        t.start();
    }

    public static void stopRecording(Context ctx, String url) {
        Thread t = TASK.remove(url);
        if (t != null) t.interrupt();
        PAUSED.remove(url);
        update(ctx, url, "status", ST_DONE);
        // ffmpeg-kit PTS 重建（小工具同款）
        final Context fc = ctx;
        new Thread(new Runnable() { public void run() {
            try {
                String path = get(fc, url, "path");
                if (path == null || path.isEmpty()) return;
                File src = new File(path);
                if (!src.exists() || src.length() == 0) return;
                File tmp = new File(src.getParentFile(), "fix_tmp.ts");
                String[] args = { "-y", "-fflags", "+genpts", "-i", src.getAbsolutePath(),
                    "-c", "copy", "-map", "0", "-f", "mpegts", tmp.getAbsolutePath() };
                com.arthenica.ffmpegkit.FFmpegKit.executeWithArgumentsAsync(args,
                    new com.arthenica.ffmpegkit.FFmpegSessionCompleteCallback() {
                        public void apply(com.arthenica.ffmpegkit.FFmpegSession st) {
                            if (tmp.exists() && tmp.length() > 0) { src.delete(); tmp.renameTo(src); }
                            else tmp.delete();
                        }
                    });
            } catch (Throwable ignored) {}
        }}).start();
    }

    static void finish(Context ctx, String url, String st) {
        update(ctx, url, "status", st);
        TASK.remove(url); PAUSED.remove(url);
    }

    static String get(Context ctx, String url, String key) {
        for (JSONObject o : load(ctx)) if (url.equals(o.optString("url"))) return o.optString(key, "");
        return null;
    }
}
