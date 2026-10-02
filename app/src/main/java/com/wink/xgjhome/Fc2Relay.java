package com.wink.xgjhome;

import java.io.OutputStream;
import java.net.Socket;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 * FC2 WebSocket-FLV 中转：连接 wss 直播流，把 FLV 字节广播给本地 HTTP 客户端
 * （LiveProxy /fc2.flv 路由）。不碰哔哩哔哩/抖音的任何逻辑。
 */
public class Fc2Relay {

    private static volatile String wsUrl = null;
    private static volatile boolean running = false;
    private static volatile boolean connected = false;
    private static final List<OutputStream> taps = new ArrayList<OutputStream>();
    private static volatile byte[] flvHead = null;  // 开头 FLV 头，后进消费者先补
    private static volatile long lastData = 0;

    public static boolean isRunning() { return running; }
    public static boolean isConnected() { return connected; }

    public static synchronized void start(String url) {
        if (url == null || (!url.startsWith("ws://") && !url.startsWith("wss://"))) return;
        if (running && url.equals(wsUrl)) return;
        stop();
        wsUrl = url;
        running = true;
        new Thread(new Runnable() { public void run() { loop(); } }).start();
    }

    public static void stop() {
        running = false;
        connected = false;
        flvHead = null;
        synchronized (taps) { taps.clear(); }
    }

    /** 注册本地 HTTP 消费者，返回是否成功（先补 FLV 头） */
    public static boolean tap(OutputStream os) {
        if (!connected) return false;
        try {
            if (flvHead != null) os.write(flvHead);
            os.flush();
        } catch (Throwable e) { return false; }
        synchronized (taps) { taps.add(os); }
        return true;
    }

    public static void untap(OutputStream os) {
        synchronized (taps) { taps.remove(os); }
    }

    private static void broadcast(byte[] data) {
        synchronized (taps) {
            for (int i = taps.size() - 1; i >= 0; i--) {
                try { taps.get(i).write(data); } catch (Throwable e) { taps.remove(i); }
            }
        }
    }

    private static void loop() {
        while (running) {
            Socket sk = null;
            try {
                boolean ssl = wsUrl.startsWith("wss");
                String rest = wsUrl.substring(ssl ? 6 : 5);
                String path = "/";
                String host = rest;
                int pi = rest.indexOf('/');
                if (pi >= 0) { host = rest.substring(0, pi); path = rest.substring(pi); }
                String hostOnly = host;
                int port = ssl ? 443 : 80;
                int hi = host.indexOf(':');
                if (hi >= 0) { port = Integer.parseInt(host.substring(hi + 1)); hostOnly = host.substring(0, hi); }

                if (ssl) {
                    javax.net.ssl.SSLSocketFactory f = trustAll();
                    sk = f.createSocket(hostOnly, port);
                } else {
                    sk = new Socket(hostOnly, port);
                }
                OutputStream os = sk.getOutputStream();
                String key = java.util.Base64.getEncoder().encodeToString(
                    java.security.SecureRandom.getInstanceStrong().generateSeed(16));
                String req = "GET " + path + " HTTP/1.1\r\n"
                    + "Host: " + host + "\r\n"
                    + "Upgrade: websocket\r\nConnection: Upgrade\r\n"
                    + "Sec-WebSocket-Key: " + key + "\r\n"
                    + "Sec-WebSocket-Version: 13\r\n"
                    + "Origin: https://live.fc2.com\r\n"
                    + "User-Agent: Mozilla/5.0 (Linux; Android 13) Chrome/120 Mobile\r\n\r\n";
                os.write(req.getBytes());
                os.flush();

                java.io.InputStream in = sk.getInputStream();
                if (!waitHandshake(in)) throw new Exception("handshake fail");
                connected = true;
                flvHead = null;
                lastData = System.currentTimeMillis();

                byte[] acc = new byte[65536];
                int accLen = 0;
                byte[] hdr = new byte[16];
                while (running) {
                    readFull(in, hdr, 0, 2);
                    boolean fin = (hdr[0] & 0x80) != 0;
                    int op = hdr[0] & 0x0F;
                    boolean masked = (hdr[1] & 0x80) != 0;
                    long len = hdr[1] & 0x7F;
                    if (len == 126) { readFull(in, hdr, 2, 2); len = ((hdr[2] & 0xFF) << 8) | (hdr[3] & 0xFF); }
                    else if (len == 127) { readFull(in, hdr, 2, 8); len = 0; for (int i = 2; i < 10; i++) len = (len << 8) | (hdr[i] & 0xFF); }
                    byte[] mask = new byte[4];
                    if (masked) readFull(in, mask, 0, 4);

                    byte[] payload = new byte[(int) len];
                    readFull(in, payload, 0, (int) len);
                    if (masked) for (int i = 0; i < (int) len; i++) payload[i] ^= mask[i % 4];

                    lastData = System.currentTimeMillis();
                    if (op == 0x8) break;  // close
                    if (op == 0x9) {       // ping → pong
                        sendFrame(os, 0xA, payload);
                        continue;
                    }
                    if (op == 0xA) continue;  // pong
                    if (op == 0x2 || op == 0x0 || op == 0x1) {  // binary/cont/text
                        if (accLen + payload.length > acc.length) {
                            byte[] nb = new byte[(accLen + payload.length) * 2];
                            System.arraycopy(acc, 0, nb, 0, accLen);
                            acc = nb;
                        }
                        System.arraycopy(payload, 0, acc, accLen, payload.length);
                        accLen += payload.length;
                        if (fin) {
                            byte[] msg = new byte[accLen];
                            System.arraycopy(acc, 0, msg, 0, accLen);
                            accLen = 0;
                            if (msg.length > 4 && msg[0] == 'F' && msg[1] == 'L' && msg[2] == 'V') {
                                if (flvHead == null) {
                                    int hl = Math.min(msg.length, 4096);
                                    flvHead = new byte[hl];
                                    System.arraycopy(msg, 0, flvHead, 0, hl);
                                }
                                broadcast(msg);
                            } else if (flvHead != null) {
                                broadcast(msg);
                            }
                        }
                    }
                    if (System.currentTimeMillis() - lastData > 30000) break;  // 30s 无数据重连
                }
            } catch (Throwable ignored) {
            } finally {
                connected = false;
                try { if (sk != null) sk.close(); } catch (Throwable ignored) {}
            }
            if (running) { try { Thread.sleep(3000); } catch (Throwable e) { break; } }
        }
    }

    private static void sendFrame(OutputStream os, int op, byte[] payload) throws Exception {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        bo.write((byte) (0x80 | op));
        if (payload.length < 126) bo.write((byte) payload.length);
        else if (payload.length < 65536) { bo.write((byte) 126); bo.write((byte) (payload.length >> 8)); bo.write((byte) payload.length); }
        else { bo.write((byte) 127); for (int i = 7; i >= 0; i--) bo.write((byte) (payload.length >> (8 * i))); }
        byte[] mask = java.security.SecureRandom.getInstanceStrong().generateSeed(4);
        bo.write(mask);
        for (int i = 0; i < payload.length; i++) bo.write((byte) (payload[i] ^ mask[i % 4]));
        os.write(bo.toByteArray());
        os.flush();
    }

    private static boolean waitHandshake(java.io.InputStream in) throws Exception {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        int prev = 0;
        while (true) {
            int b = in.read();
            if (b < 0) return false;
            bo.write(b);
            if (prev == '\r' && b == '\n' && endsWithDD(bo)) return true;
            prev = b;
            if (bo.size() > 16384) return false;
        }
    }

    private static boolean endsWithDD(java.io.ByteArrayOutputStream bo) {
        byte[] a = bo.toByteArray();
        int n = a.length;
        return n >= 4 && a[n - 4] == '\r' && a[n - 3] == '\n' && a[n - 2] == '\r' && a[n - 1] == '\n';
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
