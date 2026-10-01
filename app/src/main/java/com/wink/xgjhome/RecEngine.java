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

/** 录制/下载记录存储 + 录制引擎（所有记录进"录制视频管理"页） */
public class RecEngine {

    public static final String TYPE_REC = "录制";
    public static final String TYPE_DL = "下载";
    public static final String ST_REC = "录制中";
    public static final String ST_DONE = "已结束";
    public static final String ST_DL = "下载中";
    public static final String ST_OK = "已完成";
    public static final String ST_CANCEL = "已取消";

    static final String PREF = "records";

    public static SharedPreferences sp(Context c) { return c.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE); }

    public static synchronized List<JSONObject> load(Context c) {
        List<JSONObject> out = new ArrayList<JSONObject>();
        try {
            JSONArray arr = new JSONArray(sp(c).getString("list", "[]"));
            for (int i = 0; i < arr.length(); i++) out.add(arr.getJSONObject(i));
        } catch (Throwable e) { }
        return out;
    }

    public static synchronized void save(Context c, List<JSONObject> list) {
        try {
            JSONArray arr = new JSONArray();
            for (JSONObject o : list) arr.put(o);
            sp(c).edit().putString("list", arr.toString()).apply();
        } catch (Throwable e) { }
    }

    public static synchronized JSONObject find(Context c, String url) {
        for (JSONObject o : load(c)) {
            if (url.equals(o.optString("url"))) return o;
        }
        return null;
    }

    public static synchronized void add(Context c, String type, String title, String url) {
        List<JSONObject> list = load(c);
        for (JSONObject o : list) if (url.equals(o.optString("url")) && TYPE_REC.equals(type)) return;
        try {
            JSONObject o = new JSONObject();
            o.put("type", type);
            o.put("title", title);
            o.put("url", url);
            o.put("status", TYPE_REC.equals(type) ? ST_REC : ST_DL);
            o.put("size", 0);
            o.put("duration", 0);
            o.put("start", System.currentTimeMillis());
            list.add(0, o);
            save(c, list);
        } catch (Throwable e) { }
    }

    public static synchronized void update(Context c, String url, String statusKey, Object v) {
        List<JSONObject> list = load(c);
        try {
            for (JSONObject o : list) {
                if (url.equals(o.optString("url"))) {
                    o.put(statusKey, v);
                    break;
                }
            }
            save(c, list);
        } catch (Throwable e) { }
    }

    public static synchronized void remove(Context c, String url) {
        List<JSONObject> list = load(c);
        Iterator<JSONObject> it = list.iterator();
        while (it.hasNext()) {
            if (url.equals(it.next().optString("url"))) it.remove();
        }
        save(c, list);
    }

    // ---------- 录制引擎 ----------
    static final Map<String, Thread> REC = new HashMap<String, Thread>();

    public static void startRecording(final Context ctx, final String url, final String title) {
        if (REC.containsKey(url)) return;
        add(ctx, TYPE_REC, title, url);
        Thread t = new Thread(new Runnable() {
            public void run() {
                long start = System.currentTimeMillis();
                long bytes = 0;
                InputStream in = null;
                FileOutputStream fos = null;
                try {
                    File out = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                            "资源嗅探_" + System.currentTimeMillis() + ".ts");
                    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                    c.setConnectTimeout(8000); c.setReadTimeout(8000);
                    if (url.contains("bilibili") || url.contains("bilivideo"))
                        c.setRequestProperty("Referer", "https://www.bilibili.com/");
                    c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
                    in = c.getInputStream();
                    fos = new FileOutputStream(out);
                    byte[] buf = new byte[65536]; int n;
                    while ((n = in.read(buf)) > 0 && REC.containsKey(url)) {
                        fos.write(buf, 0, n);
                        bytes += n;
                        long dur = (System.currentTimeMillis() - start) / 1000;
                        update(ctx, url, "size", bytes);
                        update(ctx, url, "duration", dur);
                        update(ctx, url, "path", out.getAbsolutePath());
                    }
                    update(ctx, url, "status", ST_DONE);
                } catch (Throwable e) {
                    update(ctx, url, "status", ST_DONE);
                } finally {
                    try { if (in != null) in.close(); } catch (Throwable e) { }
                    try { if (fos != null) fos.close(); } catch (Throwable e) { }
                    REC.remove(url);
                }
            }
        });
        REC.put(url, t);
        t.start();
    }


    /** 下载：入记录 + DownloadManager 排队 + 轮询完成回写 */
    public static void startDownload(final Context ctx, final String url, String title) {
        add(ctx, TYPE_DL, title, url);
        new Thread(new Runnable() { public void run() {
            try {
                android.app.DownloadManager.Request req = new android.app.DownloadManager.Request(android.net.Uri.parse(url));
                if (url.contains("bilibili.com") || url.contains("bilivideo")) req.addRequestHeader("Referer", "https://www.bilibili.com/");
                req.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                req.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_MOVIES, "资源嗅探_" + System.currentTimeMillis() + ".mp4");
                android.app.DownloadManager dm = (android.app.DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE);
                final long id = dm.enqueue(req);
                update(ctx, url, "dlId", id);
                while (true) {
                    try { Thread.sleep(3000); } catch (InterruptedException e) { return; }
                    android.database.Cursor cur = dm.query(new android.app.DownloadManager.Query().setFilterById(id));
                    boolean done = false, failed = false;
                    if (cur != null && cur.moveToFirst()) {
                        int st = cur.getInt(cur.getColumnIndex(android.app.DownloadManager.COLUMN_STATUS));
                        long bytes = cur.getLong(cur.getColumnIndex(android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                        done = st == android.app.DownloadManager.STATUS_SUCCESSFUL;
                        failed = st == android.app.DownloadManager.STATUS_FAILED;
                        if (bytes > 0) update(ctx, url, "size", bytes);
                    }
                    if (cur != null) cur.close();
                    if (done || failed) {
                        update(ctx, url, "status", done ? ST_OK : ST_CANCEL);
                        return;
                    }
                }
            } catch (Throwable e) {
                update(ctx, url, "status", ST_CANCEL);
            }
        }}).start();
    }

    public static void stopRecording(Context ctx, String url) {
        Thread t = REC.remove(url);
        if (t != null) t.interrupt();
        update(ctx, url, "status", ST_DONE);
    }
}
