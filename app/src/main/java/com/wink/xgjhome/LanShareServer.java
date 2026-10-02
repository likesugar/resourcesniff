package com.wink.xgjhome;

import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.NetworkInterface;
import java.util.Collections;

/** 局域网共享：浏览器打开 http://手机IP:8180 可见并打开记录中的链接 */
public class LanShareServer {

    private static volatile int port = -1;
    private static volatile boolean running = false;
    private static ServerSocket ss = null;

    public static boolean isRunning() { return running; }
    public static int getPort() { return port; }

    private static int randomPort() {
        return 10000 + new java.util.Random().nextInt(55536);  // 五位数端口
    }

    public static synchronized void start() {
        if (running) return;
        running = true;
        new Thread(new Runnable() { public void run() {
            try {
                ServerSocket tmp = null;
                int p2 = -1;
                for (int i = 0; i < 20; i++) {   // 随机试绑，占用则换
                    try { p2 = randomPort(); tmp = new ServerSocket(p2); break; }
                    catch (Throwable e) { tmp = null; }
                }
                if (tmp == null) { running = false; return; }
                ss = tmp;
                port = p2;
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
        // 消费剩余请求头
        String l;
        while ((l = readLine(in)) != null && !l.isEmpty()) { }
        String path = "/";
        if (req != null && req.startsWith("GET ")) {
            String[] parts = req.split(" ");
            if (parts.length >= 2) path = parts[1];
        }
        if (path.startsWith("/open?u=")) {
            String u = java.net.URLDecoder.decode(path.substring(8), "UTF-8");
            // 302 到手机中转：由手机代拉流（带正确 Referer），电脑直接播
            String host = s.getInetAddress().getHostAddress();  // 访问者IP不用；用本机
            String me = localIp();
            byte[] empty = new byte[0];
            OutputStream os2 = s.getOutputStream();
            os2.write(("HTTP/1.1 302 Found\r\nLocation: http://" + me + ":8123/relay?u="
                + java.net.URLEncoder.encode(u, "UTF-8") + "\r\nConnection: close\r\nContent-Length: 0\r\n\r\n").getBytes());
            os2.flush();
            return;
        }
        String body = "";
        if (false) {
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
            body = sb.toString();
        }
        byte[] bb = body.getBytes("UTF-8");
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
