package com.wink.xgjhome;

import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;

/** YouTube 解析下载（移植 DBdown：NewPipeExtractor 主解析 + IOS 官方客户端探测兜底 → ffmpeg 原样封装 → 进记录） */
public final class YtResolver {

    private static volatile boolean inited = false;
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";
    private static final String UA_IOS = "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)";
    private static final String CLS_IOS = "IOS";
    private static final String VER_IOS = "20.10.4";

    static boolean isYt(String url) {
        return url != null && (url.contains("youtube.com") || url.contains("youtu.be"));
    }

    static String videoId(String url) {
        try {
            String u = url.contains("youtu.be/") ? "v=" + url.substring(url.indexOf("youtu.be/") + 9) : url;
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("[?&]v=([\\w-]{11})").matcher(u);
            if (m.find()) return m.group(1);
            m = java.util.regex.Pattern.compile("/(?:shorts|live|embed)/([\\w-]{11})").matcher(url);
            if (m.find()) return m.group(1);
        } catch (Throwable ignored) {}
        return null;
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

    /** 入口：嗅探页 YouTube 链接 */
    static void handle(final SniffActivity act, final String url) {
        toast(act, "YouTube 解析中…");
        new Thread(new Runnable() { public void run() {
            String out = null, err = "未知错误";
            try {
                String vid = videoId(url);
                dump("HANDLE url=" + url + " vid=" + vid);
                // 1) NewPipeExtractor 主路径
                try {
                    initPipe();
                    org.schabi.newpipe.extractor.stream.StreamExtractor ex = org.schabi.newpipe.extractor.ServiceList.YouTube.getStreamExtractor(url);
                    org.schabi.newpipe.extractor.stream.StreamInfo info = StreamInfo.getInfo(ex);
                    out = pickAndRun(act, info.getVideoStreams(), info.getAudioStreams(), info.getName());
                } catch (Throwable e) {
                    err = e.getMessage() == null ? e.toString() : e.getMessage();
                    dump("NP fail: " + err);
                }
                // 2) IOS 官方客户端探测兜底（匿名被风控时常用此路）
                if (out == null && vid != null) {
                    try {
                        out = iosClientRun(act, vid);
                    } catch (Throwable e) {
                        err = e.getMessage() == null ? e.toString() : e.getMessage();
                        dump("IOS fail: " + err);
                    }
                }
            } catch (Throwable t) {
                err = t.toString();
            }
            final String fOut = out, fErr = err;
            act.runOnUiThread(new Runnable() { public void run() {
                if (fOut != null) toast(act, "已保存到记录");
                else toast(act, "YouTube 解析失败: " + fErr);
            }});
        }}).start();
    }

    private static void toast(final SniffActivity act, final String s) {
        try { act.runOnUiThread(new Runnable() { public void run() { Toast.makeText(act, s, Toast.LENGTH_LONG).show(); }}); } catch (Throwable ignored) {}
    }

    private static void initPipe() throws Exception {
        if (inited) return;
        NewPipe.init(new Downloader() {
            public Response execute(Request request) throws java.io.IOException {
                HttpURLConnection c = (HttpURLConnection) new URL(request.url()).openConnection();
                c.setConnectTimeout(15000); c.setReadTimeout(20000);
                boolean hasUa = false;
                if (request.headers() != null) for (Map.Entry<String, List<String>> e : request.headers().entrySet()) {
                    for (String v : e.getValue()) { c.setRequestProperty(e.getKey(), v); if ("User-Agent".equalsIgnoreCase(e.getKey())) hasUa = true; }
                }
                if (!hasUa) c.setRequestProperty("User-Agent", UA);
                c.setRequestProperty("Cookie", "CONSENT=YES+cb; SOCS=CAI");
                if (request.dataToSend() != null && request.dataToSend().length > 0) {
                    c.setRequestMethod("POST"); c.setDoOutput(true);
                    c.setRequestProperty("Content-Type", "application/json");
                    OutputStream os = c.getOutputStream(); os.write(request.dataToSend()); os.close();
                }
                int code = c.getResponseCode();
                String ctype = c.getContentType();
                InputStream in = code < 400 ? c.getInputStream() : c.getErrorStream();
                StringBuilder sb = new StringBuilder();
                if (in != null) { byte[] b = new byte[8192]; int r; while ((r = in.read(b)) > 0) sb.append(new String(b, 0, r, "UTF-8")); in.close(); }
                return new Response(code, ctype, c.getHeaderFields(), sb.toString(), request.url());
            }
        });
        inited = true;
    }

    // ---------- 候选挑选 + 下载封装 ----------
    private static class Cand { String url; int px; int fps; int bitrate; String mime; boolean videoOnly; String label; }

    private static String pickAndRun(SniffActivity act, List<VideoStream> vs, List<AudioStream> as, String title) throws Exception {
        Cand bestV = null, bestA = null, bestMux = null;
        for (VideoStream v : vs) {
            if (v.getContent() == null || v.getContent().isEmpty()) continue;
            Cand c = new Cand();
            c.url = v.getContent(); c.px = v.getHeight(); c.fps = v.getFps(); c.videoOnly = v.isVideoOnly();
            c.bitrate = v.getBitrate(); c.mime = v.getFormat() != null ? v.getFormat().mimeType : "";
            if (c.videoOnly) { if (bestV == null || c.px > bestV.px || (c.px == bestV.px && c.fps > bestV.fps)) bestV = c; }
            else if (bestMux == null || c.px > bestMux.px) bestMux = c;
        }
        for (AudioStream a : as) {
            if (a.getContent() == null || a.getContent().isEmpty()) continue;
            Cand c = new Cand();
            c.url = a.getContent(); c.bitrate = a.getBitrate(); c.mime = a.getFormat() != null ? a.getFormat().mimeType : "";
            if (bestA == null || c.bitrate > bestA.bitrate) bestA = c;
        }
        dump("NP bestMux=" + (bestMux == null ? "null" : "" + bestMux.px) + " bestV=" + (bestV == null ? "null" : "" + bestV.px) + " bestA=" + (bestA == null ? "null" : "" + bestA.bitrate));
        if (bestV != null && bestA != null) return muxRun(act, bestV.url, bestA.url, title, bestV.px);
        if (bestMux != null) return downloadSingle(act, bestMux.url, title);
        throw new Exception("无可下载格式");
    }

    private static File dl(String url, File dst) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000); c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", UA);
        FileOutputStream fo = new FileOutputStream(dst);
        InputStream in = c.getInputStream();
        byte[] b = new byte[65536]; long total = 0; int r;
        while ((r = in.read(b)) > 0) { fo.write(b, 0, r); total += r; }
        in.close(); fo.close();
        dump("DL " + dst.getName() + " " + total + "B");
        if (total < 1024) throw new Exception("下载内容为空");
        return dst;
    }

    private static String downloadSingle(SniffActivity act, String url, String title) throws Exception {
        File cache = act.getExternalCacheDir() != null ? act.getExternalCacheDir() : act.getCacheDir();
        File v = new File(cache, "yt_v.mp4");
        dl(url, v);
        return store(act, v, safeName(title));
    }

    private static String muxRun(SniffActivity act, String vUrl, String aUrl, String title, int px) throws Exception {
        File cache = act.getExternalCacheDir() != null ? act.getExternalCacheDir() : act.getCacheDir();
        toast(act, "YouTube 下载中…");
        File v = dl(vUrl, new File(cache, "yt_v.mp4"));
        toast(act, "视频完成，下载音轨…");
        File a = dl(aUrl, new File(cache, "yt_a.m4a"));
        toast(act, "音轨完成，封装MP4…");
        File out = new File(cache, "yt_out.mp4");
        com.arthenica.ffmpegkit.FFmpegSession st = com.arthenica.ffmpegkit.FFmpegKit.executeWithArguments(
                new String[]{"-y", "-i", v.getAbsolutePath(), "-i", a.getAbsolutePath(), "-c", "copy", "-movflags", "+faststart", out.getAbsolutePath()});
        v.delete(); a.delete();
        if (!st.getState().equals(com.arthenica.ffmpegkit.SessionState.COMPLETED) || out.length() < 1024) {
            out.delete();
            throw new Exception("ffmpeg封装失败");
        }
        return store(act, out, safeName(title) + "_" + px + "p");
    }

    // ---------- IOS 官方客户端兜底（DBdown 同款思路：IOS 常不被风控） ----------
    private static JSONObject iosPlayer(String vid) throws Exception {
        JSONObject ctx = new JSONObject()
                .put("client", new JSONObject()
                        .put("clientName", CLS_IOS).put("clientVersion", VER_IOS)
                        .put("deviceModel", "iPhone16,2")
                        .put("osName", "iOS").put("osVersion", "18.1.0.22B83"));
        JSONObject body = new JSONObject()
                .put("context", ctx)
                .put("videoId", vid)
                .put("contentCheckOk", true).put("racyCheckOk", true);
        HttpURLConnection c = (HttpURLConnection) new URL("https://youtubei.googleapis.com/youtubei/v1/player?key=AIzaSyB-63vPrdThhKuerbB2N_l7Kwwcxj6yUAc").openConnection();
        c.setRequestMethod("POST"); c.setDoOutput(true); c.setConnectTimeout(15000); c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", UA_IOS);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("X-Goog-Api-Format-Version", "2");
        OutputStream os = c.getOutputStream(); os.write(body.toString().getBytes("UTF-8")); os.close();
        InputStream in = c.getResponseCode() < 400 ? c.getInputStream() : c.getErrorStream();
        StringBuilder sb = new StringBuilder();
        if (in != null) { byte[] b = new byte[8192]; int r; while ((r = in.read(b)) > 0) sb.append(new String(b, 0, r, "UTF-8")); in.close(); }
        dump("IOS status=" + c.getResponseCode() + " body=" + sb.substring(0, Math.min(400, sb.length())));
        return new JSONObject(sb.toString());
    }

    private static String iosClientRun(SniffActivity act, String vid) throws Exception {
        JSONObject resp = iosPlayer(vid);
        JSONObject ps = resp.optJSONObject("playabilityStatus");
        String status = ps != null ? ps.optString("status", "?") : "?";
        if (!"OK".equals(status)) throw new Exception("IOS客户端不可用(" + status + ")");
        JSONObject sd = resp.optJSONObject("streamingData");
        if (sd == null) throw new Exception("无streamingData");
        java.util.ArrayList<Cand> vids = new java.util.ArrayList<Cand>(), auds = new java.util.ArrayList<Cand>();
        JSONObject vd = resp.optJSONObject("videoDetails");
        String title = vd != null ? vd.optString("title", "YouTube") : "YouTube";
        JSONArray arr = sd.optJSONArray("adaptiveFormats");
        if (arr != null) for (int i = 0; i < arr.length(); i++) {
            JSONObject f = arr.optJSONObject(i);
            if (f == null || !f.has("url")) continue;   // IOS 客户端直链
            String mime = f.optString("mimeType", "");
            Cand cd = new Cand();
            cd.url = f.getString("url");
            cd.bitrate = f.optInt("bitrate", 0);
            cd.fps = f.optInt("fps", 0);
            cd.px = f.optInt("height", 0);
            cd.mime = mime;
            if (mime.startsWith("video")) { cd.videoOnly = true; vids.add(cd); }
            else if (mime.startsWith("audio")) auds.add(cd);
        }
        Cand bestMux = null;
        JSONArray fs2 = sd.optJSONArray("formats");
        if (fs2 != null) for (int i = 0; i < fs2.length(); i++) {
            JSONObject f = fs2.optJSONObject(i);
            if (f == null || !f.has("url")) continue;
            Cand cd = new Cand();
            cd.url = f.getString("url"); cd.videoOnly = false; cd.px = f.optInt("height", 0);
            if (bestMux == null || cd.px > bestMux.px) bestMux = cd;
        }
        Cand bestV = null, bestA = null;
        for (Cand c : vids) if (bestV == null || c.px > bestV.px || (c.px == bestV.px && c.fps > bestV.fps)) bestV = c;
        for (Cand c : auds) if (bestA == null || c.bitrate > bestA.bitrate) bestA = c;
        dump("IOS bestV=" + (bestV == null ? "null" : bestV.px + "/" + bestV.fps) + " bestA=" + (bestA == null ? "null" : "" + bestA.bitrate) + " bestMux=" + (bestMux == null ? "null" : "" + bestMux.px));
        if (bestV != null && bestA != null) return muxRun(act, bestV.url, bestA.url, title, bestV.px);
        if (bestMux != null) return downloadSingle(act, bestMux.url, title);
        throw new Exception("IOS客户端无可下载格式");
    }

    private static String safeName(String t) {
        String n = t == null || t.trim().isEmpty() ? "YouTube" : t.trim();
        return n.replaceAll("[\\\\/:*?\"<>|]", "_");
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
