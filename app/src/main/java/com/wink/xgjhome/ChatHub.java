package com.wink.xgjhome;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;

/** 局域网聊天中枢: UDP广播(端口24688)互联, 无需任何服务器; 日志留最近500条 */
public class ChatHub {

    public static final int UDP_PORT = 24688;
    public interface Listener { void onMessage(JSONObject o); }

    private static volatile boolean running = false;
    private static MulticastSocket rx;
    private static final java.util.List<Listener> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final java.util.List<JSONObject> log = java.util.Collections.synchronizedList(new java.util.ArrayList<JSONObject>());
    private static Context ctx;

    public static void init(Context c) { ctx = c; loadLog(); }

    public static synchronized void start() {
        if (running) return;
        try {
            rx = new MulticastSocket(UDP_PORT);
            rx.setBroadcast(true);
            running = true;
            Thread t = new Thread(new Runnable() { public void run() {
                byte[] buf = new byte[4096];
                while (running) {
                    try {
                        DatagramPacket p = new DatagramPacket(buf, buf.length);
                        rx.receive(p);
                        String s = new String(p.getData(), 0, p.getLength(), "UTF-8");
                        JSONObject o = new JSONObject(s);
                        append(o);
                        notifyUi(o);
                    } catch (Throwable e) { if (!running) break; }
                }
            }});
            t.setDaemon(true);
            t.start();
        } catch (Throwable ignored) {}
    }

    public static synchronized void stop() {
        running = false;
        try { if (rx != null) rx.close(); } catch (Throwable ignored) {}
    }

    public static void send(String nick, String msg) {
        try {
            JSONObject o = new JSONObject();
            o.put("nick", nick == null ? "匿名" : nick);
            o.put("msg", msg);
            o.put("ts", System.currentTimeMillis());
            byte[] b = o.toString().getBytes("UTF-8");
            DatagramSocket out = new DatagramSocket();
            out.setBroadcast(true);
            // 全网广播 + 子网定向广播双保险
            try { out.send(new DatagramPacket(b, b.length, InetAddress.getByName("255.255.255.255"), UDP_PORT)); } catch (Throwable ignored) {}
            String ip = LanShareServer.localIp();
            if (ip != null && ip.contains(".")) {
                try {
                    String sub = ip.substring(0, ip.lastIndexOf('.') + 1) + "255";
                    out.send(new DatagramPacket(b, b.length, InetAddress.getByName(sub), UDP_PORT));
                } catch (Throwable ignored) {}
            }
            out.close();
            append(o);
            notifyUi(o);
        } catch (Throwable ignored) {}
    }

    public static void addListener(Listener l) { if (l != null) listeners.add(l); }
    public static void removeListener(Listener l) { listeners.remove(l); }
    private static void notifyUi(final JSONObject o) {
        for (Listener l : listeners) try { l.onMessage(o); } catch (Throwable ignored) {}
    }

    private static void append(JSONObject o) {
        synchronized (log) {
            log.add(o);
            while (log.size() > 500) log.remove(0);
        }
        saveLog();
    }

    /** 自上次之后的全部消息(PC轮询) */
    public static JSONArray since(long ts) {
        JSONArray a = new JSONArray();
        synchronized (log) {
            for (JSONObject o : log) if (o.optLong("ts", 0) > ts) a.put(o);
        }
        return a;
    }

    public static JSONArray all() { return since(-1); }

    private static void loadLog() {
        try {
            java.io.File f = new java.io.File(ctx.getFilesDir(), "chat_log.json");
            if (!f.exists()) return;
            byte[] b = new byte[(int) f.length()];
            java.io.FileInputStream fis = new java.io.FileInputStream(f);
            fis.read(b); fis.close();
            JSONArray a = new JSONArray(new String(b, "UTF-8"));
            synchronized (log) { for (int i = 0; i < a.length(); i++) log.add(a.getJSONObject(i)); }
        } catch (Throwable ignored) {}
    }

    private static void saveLog() {
        try {
            JSONArray a = new JSONArray();
            synchronized (log) { for (JSONObject o : log) a.put(o); }
            java.io.File f = new java.io.File(ctx.getFilesDir(), "chat_log.json");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(f);
            fos.write(a.toString().getBytes("UTF-8")); fos.close();
        } catch (Throwable ignored) {}
    }
}
