package com.wink.xgjhome;

import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.webkit.CookieManager;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;

/** YouTube 下载（Seal 同款引擎：youtubedl-android / yt-dlp，桥接 WebView 登录 Cookie） */
public final class YtResolver {

    private static volatile boolean inited = false;

    static boolean isYt(String url) {
        return url != null && (url.contains("youtube.com") || url.contains("youtu.be"));
    }

    static void dump(String st) {
        try {
            java.io.File dir = SniffActivity.dumpDir();
            if (dir == null) return;
            java.io.FileWriter fw = new java.io.FileWriter(new java.io.File(dir, "fc2_debug.txt"), true);
            fw.write(new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(new java.util.Date())
                    + " YT: " + st + "\n----------------\n");
            fw.close();
        } catch (Throwable ignored) {}
    }

    /** WebView 登录 Cookie → Netscape cookies.txt */
    private static File cookiesFile(SniffActivity act) throws Exception {
        CookieManager cm = CookieManager.getInstance();
        String raw = cm.getCookie("https://www.youtube.com");
        File f = new File(act.getFilesDir(), "yt_cookies.txt");
        PrintWriter pw = new PrintWriter(f, "UTF-8");
        pw.println("# Netscape HTTP Cookie File");
        if (raw != null) for (String p : raw.split(";")) {
            String[] kv = p.trim().split("=", 2);
            if (kv.length == 2 && kv[0].length() > 0)
                pw.println(".youtube.com\tTRUE\t/\tTRUE\t0\t" + kv[0] + "\t" + kv[1]);
        }
        pw.close();
        return f;
    }

    /** 入口：嗅探页 YouTube 链接 */
    static void handle(final SniffActivity act, final String url) {
        toast(act, "YouTube 解析中…");
        new Thread(new Runnable() { public void run() {
            String saved = null, err = "未知错误";
            try {
                synchronized (YtResolver.class) {
                    if (!inited) {
                        System.setProperty("java.net.preferIPv4Stack", "true");
                        com.yausername.youtubedl_android.YoutubeDL.getInstance().init(act.getApplicationContext());
                        try { com.yausername.ffmpeg.FFmpeg.getInstance().init(act.getApplicationContext()); } catch (Throwable ignored) {}
                        dump("ytdlp engine inited");
                        inited = true;
                    }
                }
                File cookies = cookiesFile(act);
                File cache = act.getExternalCacheDir() != null ? act.getExternalCacheDir() : act.getCacheDir();
                File outTpl = new File(cache, "yt_out_%(id)s.%(ext)s");
                com.yausername.youtubedl_android.YoutubeDLRequest req =
                        new com.yausername.youtubedl_android.YoutubeDLRequest(url);
                req.addOption("-f", "bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4]/bv*+ba/b");
                req.addOption("--merge-output-format", "mp4");
                req.addOption("--cookies", cookies.getAbsolutePath());
                req.addOption("-o", outTpl.getAbsolutePath());
                req.addOption("--no-mtime");
                req.addOption("--no-playlist");
                req.addOption("--no-part");
                com.yausername.youtubedl_android.YoutubeDLResponse resp =
                        com.yausername.youtubedl_android.YoutubeDL.getInstance().execute(req, null);
                dump("ytdlp done " + resp.getElapsedTime() + "s tail=" + tail(resp.getOut(), 300));
                File done = null;
                for (File f : cache.listFiles()) {
                    if (f.getName().startsWith("yt_out_") && (f.getName().endsWith(".mp4") || f.getName().endsWith(".mkv") || f.getName().endsWith(".webm"))) {
                        if (done == null || f.length() > done.length()) done = f;
                    }
                }
                if (done == null) throw new Exception("未生成视频文件");
                String title = "YouTube_" + done.getName().replaceAll("^yt_out_|\\.[a-z]+$", "");
                saved = store(act, done, title);
            } catch (Throwable t) {
                err = t.getMessage() == null ? t.toString() : t.getMessage();
                dump("ytdlp fail: " + err);
            }
            final String fOut = saved, fErr = err;
            act.runOnUiThread(new Runnable() { public void run() {
                if (fOut != null) toast(act, "已保存到记录");
                else toast(act, "YouTube 解析失败: " + fErr);
            }});
        }}).start();
    }

    private static String tail(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(s.length() - n);
    }

    private static void toast(final SniffActivity act, final String s) {
        try { act.runOnUiThread(new Runnable() { public void run() { Toast.makeText(act, s, Toast.LENGTH_LONG).show(); }}); } catch (Throwable ignored) {}
    }

    private static String store(SniffActivity act, File f, String name) throws Exception {
        try {
            ContentValues cv = new ContentValues();
            cv.put(android.provider.MediaStore.Video.Media.DISPLAY_NAME, name + ".mp4");
            cv.put(android.provider.MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            if (Build.VERSION.SDK_INT >= 29)
                cv.put(android.provider.MediaStore.Video.Media.RELATIVE_PATH, "Movies/资源嗅探");
            Uri uri = act.getContentResolver().insert(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv);
            if (uri != null) {
                OutputStream os = act.getContentResolver().openOutputStream(uri);
                FileInputStream fis = new FileInputStream(f);
                byte[] b = new byte[65536]; int r;
                while ((r = fis.read(b)) > 0) os.write(b, 0, r);
                fis.close(); os.close();
                f.delete();
                return uri.toString();
            }
        } catch (Throwable ignored) {}
        return "file://" + f.getAbsolutePath();
    }
}
