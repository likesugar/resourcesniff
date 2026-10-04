package com.wink.xgjhome;

import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.widget.Toast;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.services.youtube.YoutubeService;
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

/** YouTube 解析下载（移植 DBdown 方案：NewPipeExtractor 解析 → 原样封装 MP4 → 进记录） */
public final class YtResolver {

    private static volatile boolean inited = false;
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private YtResolver() {}

    public static boolean isYt(String url) {
        return url != null && (url.contains("youtube.com") || url.contains("youtu.be")
                || url.contains("youtube-nocookie.com"));
    }

    private static synchronized void initOnce() {
        if (inited) return;
        NewPipe.init(new Downloader() {
            @Override
            public Response execute(Request request) throws java.io.IOException {
                HttpURLConnection c = (HttpURLConnection) new URL(request.url()).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(20000);
                c.setRequestProperty("User-Agent", UA);
                c.setInstanceFollowRedirects(true);
                String method = request.httpMethod();
                if (!"GET".equals(method)) c.setRequestMethod(method);
                if (request.dataToSend() != null && request.dataToSend().length > 0) {
                    c.setDoOutput(true);
                    c.setRequestProperty("Content-Type", "application/json");
                }
                Map<String, List<String>> reqHeaders = request.headers();
                if (reqHeaders != null) for (Map.Entry<String, List<String>> e : reqHeaders.entrySet()) {
                    if (e.getValue() != null && !e.getValue().isEmpty())
                        c.setRequestProperty(e.getKey(), e.getValue().get(0));
                }
                if (request.dataToSend() != null && request.dataToSend().length > 0) {
                    OutputStream os = c.getOutputStream();
                    os.write(request.dataToSend());
                    os.close();
                }
                int code = c.getResponseCode();
                String ctype = c.getContentType();
                InputStream in = code < 400 ? c.getInputStream() : c.getErrorStream();
                StringBuilder sb = new StringBuilder();
                if (in != null) {
                    byte[] b = new byte[8192]; int r;
                    while ((r = in.read(b)) > 0) sb.append(new String(b, 0, r, "UTF-8"));
                    in.close();
                }
                return new Response(code, ctype, c.getHeaderFields(), sb.toString(), request.url());
            }
        });
        inited = true;
    }

    /** 在嗅探页接手 YouTube 链接：后台解析→下载→封装→进记录 */
    public static void handle(final android.app.Activity act, final String url) {
        Toast.makeText(act, "YouTube 解析中…", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() { public void run() {
            try {
                initOnce();
                StreamInfo info = StreamInfo.getInfo(new YoutubeService(0), url);
                List<VideoStream> vs = info.getVideoStreams();          // 含音视频的渐进式
                List<VideoStream> vo = info.getVideoOnlyStreams();      // 纯视频
                List<AudioStream> as = info.getAudioStreams();

                // 选流：优先渐进式(单文件)；否则最高分辨率视频+最高码率音轨，ffmpeg 封装
                VideoStream muxed = pickMuxed(vs);
                VideoStream vid = pickVideo(vo);
                AudioStream aud = pickAudio(as);
                final String title = sanitize(info.getName());
                final String u = muxed != null ? muxed.getContent() : (vid != null ? vid.getContent() : null);
                final String au = (muxed == null && vid != null && aud != null) ? aud.getContent() : null;
                if (u == null) throw new Exception("无可下载格式");

                File d = new File(act.getCacheDir(), "yt");
                d.mkdirs();
                File vf = new File(d, "v_" + System.currentTimeMillis() + (au != null ? ".mp4" : ".mp4"));
                download(u, vf);
                if (vf.length() < 1000) throw new Exception("视频下载失败");
                File af = null;
                if (au != null) {
                    af = new File(d, "a_" + System.currentTimeMillis() + ".m4a");
                    download(au, af);
                    if (af.length() < 500) { af = null; }
                }

                File out;
                if (af != null) {
                    out = new File(d, "o_" + System.currentTimeMillis() + ".mp4");
                    com.arthenica.ffmpegkit.FFmpegSession st = com.arthenica.ffmpegkit.FFmpegKit.executeWithArguments(
                            new String[]{"-y", "-i", vf.getAbsolutePath(), "-i", af.getAbsolutePath(),
                                    "-c", "copy", "-movflags", "+faststart", out.getAbsolutePath()});
                    vf.delete(); af.delete();
                    if (!st.getState().equals(com.arthenica.ffmpegkit.SessionState.COMPLETED) || out.length() < 1000) {
                        throw new Exception("音视频合并失败");
                    }
                } else {
                    out = vf;
                }
                final String path = saveToGallery(act, out, title);
                final String name = title;
                act.runOnUiThread(new Runnable() { public void run() {
                    Toast.makeText(act, "YouTube 已保存到记录", Toast.LENGTH_SHORT).show();
                }});
                HistoryStore.add(act, "视频", name, path);
                try { SniffActivity.registerLanLink(path, name); } catch (Throwable ignored) {}
            } catch (final Throwable t) {
                act.runOnUiThread(new Runnable() { public void run() {
                    Toast.makeText(act, "YouTube 解析失败: " + t.getMessage(), Toast.LENGTH_LONG).show();
                }});
            }
        }}).start();
    }

    private static VideoStream pickMuxed(List<VideoStream> vs) {
        VideoStream best = null; int bestH = -1;
        for (VideoStream v : vs) {
            if (v.isVideoOnly()) continue;
            if (v.getHeight() > bestH) { bestH = v.getHeight(); best = v; }
        }
        return best;
    }

    private static VideoStream pickVideo(List<VideoStream> vo) {
        VideoStream best = null; long bestPx = -1;
        for (VideoStream v : vo) {
            long px = (long) v.getHeight() * Math.max(1, v.getFps());
            if (px > bestPx) { bestPx = px; best = v; }
        }
        return best;
    }

    private static AudioStream pickAudio(List<AudioStream> as) {
        AudioStream best = null; int bestBr = -1;
        for (AudioStream a : as) {
            if (a.getAverageBitrate() > bestBr) { bestBr = a.getAverageBitrate(); best = a; }
        }
        return best;
    }

    private static String sanitize(String s) {
        if (s == null || s.length() == 0) s = "YouTube";
        return s.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static void download(String u, File out) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", UA);
        InputStream in = c.getInputStream();
        FileOutputStream fo = new FileOutputStream(out);
        byte[] b = new byte[65536]; int r;
        while ((r = in.read(b)) > 0) fo.write(b, 0, r);
        fo.close(); in.close();
    }

    private static String saveToGallery(android.app.Activity act, File f, String name) {
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
