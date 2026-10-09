package com.wink.xgjhome.xhs;

import android.content.Context;
import android.webkit.CookieManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** 网络层：短链解析 / 页面抓取 / 媒体下载。对齐原版 XhsNetwork + ResumableTransfer */
public final class XhsNet {
    static final String UA = "Mozilla/5.0 (Linux; Android 13; RMX3823) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36";

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

    /** 短链跟随 30x（对齐原版最多 5 跳） */
    public static String resolveShort(String url) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", UA);
            int code = conn.getResponseCode();
            String loc = null;
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308)
                loc = conn.getHeaderField("Location");
            conn.disconnect();
            if (loc != null && !loc.isEmpty()) return loc;
        } catch (Throwable ignored) {}
        return null;
    }

    /** 抓笔记页面 HTML（带平台 Cookie） */
    public static String fetchHtml(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("User-Agent", UA);
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
            String c = cookie();
            if (c != null && !c.isEmpty()) conn.setRequestProperty("Cookie", c);
            int code = conn.getResponseCode();
            if (code != 200) throw new Exception("HTTP " + code);
            InputStream in = conn.getInputStream();
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            in.close();
            return bos.toString("UTF-8");
        } finally {
            conn.disconnect();
        }
    }

    public interface Progress { void onProgress(long done, long total); }

    /** 断点续传下载：.part 半成品 + Range 续传（对齐原版 ResumableTransfer） */
    public static void download(String url, File dest, String referer, Progress cb,
                                java.util.concurrent.atomic.AtomicBoolean cancel) throws Exception {
        File part = new File(dest.getParentFile(), dest.getName() + ".part");
        long existing = part.exists() ? part.length() : 0;
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("User-Agent", UA);
            if (referer != null && !referer.isEmpty()) conn.setRequestProperty("Referer", referer);
            if (existing > 0) conn.setRequestProperty("Range", "bytes=" + existing + "-");
            int code = conn.getResponseCode();
            if (code != 200 && code != 206) throw new Exception("HTTP " + code);
            long total = conn.getContentLength();
            if (total > 0 && code == 206) total += existing;
            InputStream in = conn.getInputStream();
            OutputStream out = new FileOutputStream(part, code == 206);
            long done = code == 206 ? existing : 0;
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) {
                if (cancel != null && cancel.get()) {
                    out.close(); in.close();
                    throw new Exception("cancelled");
                }
                out.write(buf, 0, n);
                done += n;
                if (cb != null && total > 0) cb.onProgress(done, total);
            }
            out.close();
            in.close();
            if (part.exists() && !part.renameTo(dest)) {
                copyFile(part, dest);
                part.delete();
            }
        } finally {
            conn.disconnect();
        }
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

    /** HEAD 探测真实扩展名 */
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
