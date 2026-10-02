package com.wink.xgjhome;

import java.io.OutputStream;
import java.net.Socket;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 * 通用 WebSocket-FLV 中转：连接 wss/ws 直播流，把二进制帧广播给本地 HTTP 客户端
 * （LiveProxy /fc2.flv 路由）。FC2 系与 guangdongvideo 等镜像站通用。
 */
public class Fc2Relay {

    private static volatile String wsUrl = null;
    private static volatile boolean running = false;
    private static volatile boolean connected = false;
    private static final List<OutputStream> taps = new ArrayList<OutputStream>();
    private static volatile byte[] flvHead = null;
    private static volatile long lastData = 0;
    private static volatile int retry = 0;

    public static boolean isRunning() { return running; }
    public static boolean isConnected() { return connected; }
    public static long lastDataAt() { return lastData; }

    public static synchronized void start(String url) {
        if (url == null || (!url.startsWith("ws://") && !url.startsWith("wss://"))) return;
        if (running && url.equals(wsUrl)) return;
        stop();
        wsUrl = url;
        retry = 0;
        running = true;
        new Thread(new Runnable() { public void run() { loop(); } }).start();
    }

    public static synchronized void stop() {
        running = false;
        connected = false;
        try { synchronized (taps) { taps.clear(); } } catch (Throwable ignored) {}
    }

    private static void loop() {
        while (running && retry < 6) {
            try { wsRun(wsUrl); } catch (Throwable ignored) {}
            if (!running) break;
            connected = false;
            retry++;
            try { Thread.sleep(2000); } catch (Throwable e) { break; }
        }
        connected = false;
    }

    private static void wsRun(String url) throws Exception {
        boolean ssl = url.startsWith("wss://");
        String rest = url.substring(ssl ? 6 : 5);
        int pi = rest.indexOf('/');
        String host = rest;
        String path = "/";
        if (pi >= 0) { host = rest.substring(0, pi); path = rest.substring(pi); }
        String hostOnly = host;
        int port = ssl ? 443 : 80;
        int hi = host.indexOf(':');
        if (hi >= 0) { port = Integer.parseInt(host.substring(hi + 1)); hostOnly = host.substring(0, hi); }

        Socket sk = ssl ? trustAll().createSocket(hostOnly, port) : new Socket(hostOnly, port);
        sk.setSoTimeout(0);
        java.io.InputStream in = sk.getInputStream();
        OutputStream out = sk.getOutputStream();

        String key = java.util.Base64.getEncoder().encodeToString(rand16());
        String req = "GET " + path + " HTTP/1.1\r\n"
            + "Host: " + host + "\r\n"
            + "Upgrade: websocket\r\n"
            + "Connection: Upgrade\r\n"
            + "Sec-WebSocket-Key: " + key + "\r\n"
            + "Sec-WebSocket-Version: 13\r\n"
            + "Origin: https://" + hostOnly + "\r\n"
            + "User-Agent: Mozilla/5.0 (Linux; Android 13) Chrome/120 Mobile\r\n"
            + "\r\n";
        out.write(req.getBytes());
        out.flush();

        // 读 HTTP 响应头
        String line;
        boolean ok = false;
        java.io.ByteArrayOutputStream hb = new java.io.ByteArrayOutputStream();
        while ((line = readLine(in)) != null) {
            if (line.startsWith("HTTP/1.1 101") || line.startsWith("HTTP/1.0 101")) ok = true;
            if (line.isEmpty()) break;
            hb.write((line + "\n").getBytes());
        }
        if (!ok) { sk.close(); throw new Exception("handshake fail"); }

        connected = true;
        retry = 0;
        lastData = System.currentTimeMillis();

        byte[] hdr = new byte[2];
        java.io.ByteArrayOutputStream frame = new java.io.ByteArrayOutputStream();
        while (running) {
            readFull(in, hdr, 0, 2);
            boolean fin = (hdr[0] & 0x80) != 0;
            int op = hdr[0] & 0x0F;
            boolean masked = (hdr[1] & 0x80) != 0;
            long len = hdr[1] & 0x7F;
            byte[] ext = null;
            if (len == 126) { ext = new byte[2]; readFull(in, ext, 0, 2); len = ((ext[0] & 0xFF) << 8) | (ext[1] & 0xFF); }
            else if (len == 127) { ext = new byte[8]; readFull(in, ext, 0, 8); len = 0;
                for (int i = 0; i < 8; i++) len = (len << 8) | (ext[i] & 0xFF); }
            byte[] mask = masked ? new byte[4] : null;
            if (masked) readFull(in, mask, 0, 4);
            byte[] payload = new byte[(int) len];
            if (len > 0) readFull(in, payload, 0, (int) len);
            if (masked) for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i % 4];

            if (op == 0x9) { // ping → pong
                out.write(pong(mask == null ? payload : payload));
                out.flush();
                continue;
            }
            if (op == 0x8) { sk.close(); throw new Exception("close frame"); }
            if (op == 0x2 || op == 0x0 || op == 0x1) {
                broadcast(payload);
                lastData = System.currentTimeMillis();
            }
            if (fin) frame.reset();
        }
        sk.close();
    }

    private static byte[] pong(byte[] payload) {
        int pl = Math.min(payload.length, 125);
        byte[] r = new byte[2 + pl];
        r[0] = (byte) 0x8A;
        r[1] = (byte) (0x80 | pl);  // 服务端要求客户端帧必须带掩码
        byte[] mask = rand16();
        System.arraycopy(mask, 0, r, 2, 4 > pl ? pl : 0); // 占位，下面重排
        // 重新构造
        byte[] out = new byte[2 + 4 + pl];
        out[0] = (byte) 0x8A;
        out[1] = (byte) (0x80 | pl);
        System.arraycopy(mask, 0, out, 2, 4);
        for (int i = 0; i < pl; i++) out[6 + i] = (byte) (payload[i] ^ mask[i % 4]);
        return out;
    }

    private static void broadcast(byte[] data) {
        if (flvHead == null && data.length >= 3 && data[0] == 'F' && data[1] == 'L' && data[2] == 'V') {
            flvHead = data;
        }
        synchronized (taps) {
            for (OutputStream t : new ArrayList<OutputStream>(taps)) {
                try {
                    t.write(data);
                    t.flush();
                } catch (Throwable e) {
                    try { taps.remove(t); } catch (Throwable ignored) {}
                }
            }
        }
    }

    /** 消费者接入：返回 false 表示当前未连接 */
    public static boolean tap(OutputStream os) {
        synchronized (taps) {
            if (!connected) return false;
            try {
                if (flvHead != null) { os.write(flvHead); os.flush(); }
            } catch (Throwable e) { return false; }
            taps.add(os);
            return true;
        }
    }

    public static void untap(OutputStream os) {
        synchronized (taps) { taps.remove(os); }
    }

    private static byte[] rand16() {
        byte[] b = new byte[16];
        new java.security.SecureRandom().nextBytes(b);
        return b;
    }

    private static String readLine(java.io.InputStream in) throws Exception {
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
        int c;
        while ((c = in.read()) >= 0) {
            if (c == '\n') break;
            if (c != '\r') b.write(c);
        }
        if (b.size() == 0 && c < 0) return null;
        return b.toString();
    }

    private static void readFull(java.io.InputStream in, byte[] b, int off, int len) throws Exception {
        int got = 0;
        while (got < len) {
            int n = in.read(b, off + got, len - got);
            if (n < 0) throw new Exception("eof");
            got += n;
        }
    }

    private static javax.net.ssl.SSLSocketFactory trustAll() throws Exception {
        javax.net.ssl.TrustManager[] tm = new javax.net.ssl.TrustManager[]{
            new javax.net.ssl.X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] c, String a) { }
                public void checkServerTrusted(X509Certificate[] c, String a) { }
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }};
        javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("TLS");
        sc.init(null, tm, new java.security.SecureRandom());
        return sc.getSocketFactory();
    }
}
