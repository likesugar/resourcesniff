package com.wink.xgjhome;

import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.NetworkInterface;
import java.util.Collections;

/** 局域网共享服务器：电脑打开 http://手机IP:端口 可见并打开记录中的链接 */
public class LanShareServer {

    private static volatile int port = -1;
    private static volatile boolean running = false;
    private static ServerSocket ss = null;

    public static boolean isRunning() { return running; }
    public static int getPort() { return port; }

    private static int randomPort() {
        return 10000 + new java.util.Random().nextInt(55536);
    }

    public static synchronized void start() {
        if (running) return;
        // 同步绑定：调用返回时端口一定就绪（不再出现 -1）
        ServerSocket tmp = null;
        int p2 = -1;
        for (int i = 0; i < 20; i++) {
            try { p2 = randomPort(); tmp = new ServerSocket(p2); break; }
            catch (Throwable e) { tmp = null; }
        }
        if (tmp == null) return;
        ss = tmp;
        port = p2;
        running = true;
        new Thread(new Runnable() { public void run() {
            try {
                while (running) {
                    final Socket s = ss.accept();
                    new Thread(new Runnable() { public void run() {
                        try { handle(s); } catch (Throwable ignored) {}
                        finally { try { s.close(); } catch (Throwable ignored) {} }
                    }}).start();
                }
            } catch (Throwable ignored) {}
        }}).start();
    }

    public static synchronized void stop() {
        running = false;
        try { if (ss != null) ss.close(); } catch (Throwable ignored) {}
        ss = null;
    }

    private static void handle(Socket s) throws Exception {
        java.io.InputStream in = s.getInputStream();
        String req = readLine(in);
        String l;
        while ((l = readLine(in)) != null && !l.isEmpty()) { }
        String path = "/";
        if (req != null && req.startsWith("GET ")) {
            String[] parts = req.split(" ");
            if (parts.length >= 2) path = parts[1];
        }
        // 打开链接：302 到手机中转（代拉流，带正确 Referer）
        if (path.startsWith("/open?u=")) {
            String u = java.net.URLDecoder.decode(path.substring(8), "UTF-8");
            // 本地录制文件：直接由手机回放（带Range），不302到中转
            if (u.startsWith("file://")) {
                java.io.File f = new java.io.File(u.substring(7));
                OutputStream os = s.getOutputStream();
                if (!f.exists()) {
                    os.write(("HTTP/1.1 404 Not Found\r\nContent-Type: text/plain\r\nConnection: close\r\nContent-Length: 14\r\n\r\nfile not found").getBytes());
                    os.flush(); return;
                }
                String range = null; String l2;
                java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(s.getInputStream()));
                while ((l2 = br.readLine()) != null && !l2.isEmpty()) if (l2.toLowerCase().startsWith("range:")) range = l2;
                long len = f.length(), start = 0, end = len - 1; boolean partial = false;
                if (range != null && range.contains("bytes=")) {
                    try {
                        String spec = range.substring(range.indexOf('=') + 1).trim();
                        int dash = spec.indexOf('-');
                        start = Long.parseLong(spec.substring(0, dash).trim());
                        if (dash < spec.length() - 1) end = Long.parseLong(spec.substring(dash + 1).trim());
                        if (end >= len) end = len - 1;
                        partial = start <= end;
                    } catch (Throwable ignored) { }
                }
                String head = (partial ? "HTTP/1.1 206 Partial Content\r\n" : "HTTP/1.1 200 OK\r\n")
                    + "Content-Type: video/mp2t\r\nAccept-Ranges: bytes\r\n"
                    + (partial ? "Content-Range: bytes " + start + "-" + end + "/" + len + "\r\n" : "")
                    + "Content-Length: " + (end - start + 1) + "\r\nConnection: close\r\n\r\n";
                os.write(head.getBytes());
                java.io.InputStream fin = new java.io.FileInputStream(f);
                if (partial) fin.skip(start);
                byte[] buf = new byte[64 * 1024]; long remain = end - start + 1; int n;
                while (remain > 0 && (n = fin.read(buf, 0, (int)Math.min(buf.length, remain))) > 0) { os.write(buf, 0, n); remain -= n; }
                fin.close(); os.flush();
                return;
            }
            String me = localIp();
            OutputStream os = s.getOutputStream();
            os.write(("HTTP/1.1 302 Found\r\nLocation: http://" + me + ":8123/relay?u="
                + java.net.URLEncoder.encode(u, "UTF-8") + "\r\nConnection: close\r\nContent-Length: 0\r\n\r\n").getBytes());
            os.flush();
            return;
        }
        // 列表页
        StringBuilder sb = new StringBuilder();
        sb.append("<html><meta charset='utf-8'><meta name='viewport' content='width=device-width'>")
          .append("<body style='background:#111;color:#eee;font-family:monospace'>")
          .append("<h3>记录的链接</h3>");
        java.util.LinkedHashMap<String, String> links = SniffActivity.lanLinks();
        for (java.util.Map.Entry<String, String> e : links.entrySet()) {
            String enc = java.net.URLEncoder.encode(e.getKey(), "UTF-8");
            sb.append("<p><a style='color:#8ab4f8' href='/open?u=").append(enc).append("'>")
              .append(e.getValue()).append("</a><br><small style='color:#888'>").append(e.getKey()).append("</small></p>");
        }
        if (links.isEmpty()) sb.append("<p>（暂无记录）</p>");
        sb.append("</body></html>");
        byte[] bb = sb.toString().getBytes("UTF-8");
        OutputStream os = s.getOutputStream();
        os.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " + bb.length
            + "\r\nConnection: close\r\n\r\n").getBytes());
        os.write(bb);
        os.flush();
    }

    private static String readLine(java.io.InputStream in) throws Exception {
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
        int c = -1;
        while ((c = in.read()) >= 0) {
            if (c == '\n') break;
            if (c != '\r') b.write(c);
        }
        if (b.size() == 0 && c < 0) return null;
        return b.toString();
    }

    public static String localIp() {
        try {
            for (java.util.Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                for (java.net.InetAddress a : Collections.list(en.nextElement().getInetAddresses())) {
                    if (!a.isLoopbackAddress() && a.getHostAddress().indexOf(':') < 0) return a.getHostAddress();
                }
            }
        } catch (Throwable ignored) {}
        return "?";
    }
}
