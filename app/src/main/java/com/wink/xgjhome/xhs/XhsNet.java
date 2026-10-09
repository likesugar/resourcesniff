package com.wink.xgjhome.xhs;

import android.webkit.CookieManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

/** 网络层：短链解析 / 页面抓取 / 媒体下载。对齐原版 XhsNetwork + ResumableTransfer */
public final class XhsNet {
    /** 对齐原版 ResumableTransfer.USER_AGENT */
    static final String UA = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/141.0 Mobile Safari/537.36 xiaohongshu";

    public interface Progress { void onProgress(long done, long total); }

    private XhsNet() {}

    /** 平台账号 Cookie（设置→平台账号→小红书 登录后走系统 CookieManager 共享） */
    public static String cookie() {
        try {
            CookieManager cm = CookieManager.getInstance();
            String c = cm.getCookie("https://www.xiaohongshu.com");
            if (c == null || c.isEmpty()) c = cm.getCookie("https://xiaohongshu.com");
            return c;
        } catch (Throwable t) { return null; }
    }

    public static boolean hasWebSession() {
        String c = cookie();
        return c != null && c.contains("web_session=");
    }

    /** 短链解析：跟随 302/301 */
    public static String resolveShort(String url) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            try {
                conn.setInstanceFollowRedirects(false);
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("User-Agent", UA);
                int code = conn.getResponseCode();
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    String loc = conn.getHeaderField("Location");
                    if (loc != null && !loc.isEmpty()) return loc;
                }
            } finally { conn.disconnect(); }
        } catch (Throwable ignored) { }
        return null;
    }

    /** 拉取笔记页 HTML（带平台账号 Cookie） */
    public static String fetchHtml(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("User-Agent", UA);
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
            String cookie = cookie();
            if (cookie != null && !cookie.isEmpty()) conn.setRequestProperty("Cookie", cookie);
            int code = conn.getResponseCode();
            if (code != 200) throw new Exception("HTTP " + code);
            InputStream in = conn.getInputStream();
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            in.close();
            return bos.toString("UTF-8");
        } finally { conn.disconnect(); }
    }

    /** 断点续传下载（.part 临时文件 + Range 续传） */
    public static void download(String url, File dest, String referer, Progress cb, AtomicBoolean cancel) throws Exception {
        File part = new File(dest.getParentFile(), dest.getName() + ".part");
        long existing = part.exists() ? part.length() : 0;
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("User-Agent", UA);
            if (referer != null && !referer.isEmpty()) conn.setRequestProperty("Referer", referer);
            String cookie = cookie();
            if (cookie != null && !cookie.isEmpty()) conn.setRequestProperty("Cookie", cookie);
            if (existing > 0) conn.setRequestProperty("Range", "bytes=" + existing + "-");
            int code = conn.getResponseCode();
            long total;
            InputStream in;
            OutputStream out;
            if (code == 206) {
                total = existing + conn.getContentLength();
                in = conn.getInputStream();
                out = new FileOutputStream(part, true);
            } else if (code == 200) {
                total = conn.getContentLength();
                existing = 0;
                in = conn.getInputStream();
                out = new FileOutputStream(part, false);
            } else {
                throw new Exception("HTTP " + code);
            }
            long done = existing;
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) {
                if (cancel != null && cancel.get()) {
                    out.close(); in.close();
                    throw new Exception("cancelled");
                }
                out.write(buf, 0, n);
                done += n;
                if (cb != null) cb.onProgress(done, total);
            }
            out.close();
            in.close();
            if (!part.renameTo(dest)) {
                copyFile(part, dest);
                part.delete();
            }
        } finally { conn.disconnect(); }
    }

    private static void copyFile(File src, File dst) throws Exception {
        FileInputStream fis = new FileInputStream(src);
        FileOutputStream fos = new FileOutputStream(dst);
        byte[] buf = new byte[16384];
        int n;
        while ((n = fis.read(buf)) > 0) fos.write(buf, 0, n);
        fis.close();
        fos.close();
    }

    /** HEAD 探测扩展名 */
    public static String probeExt(String url, String fallback) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", UA);
            String ct = conn.getContentType();
            if (ct != null) {
                ct = ct.split(";")[0].trim().toLowerCase();
                if (ct.contains("jpeg") || ct.contains("jpg")) return ".jpg";
                if (ct.contains("png")) return ".png";
                if (ct.contains("webp")) return ".webp";
                if (ct.contains("gif")) return ".gif";
                if (ct.contains("mp4")) return ".mp4";
                if (ct.contains("quicktime")) return ".mov";
                if (ct.contains("webm")) return ".webm";
            }
        } catch (Throwable ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
        return fallback;
    }
}
