package com.wink.xgjhome;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;

/** 视频下载（DBdown 图1 复刻：粘贴链接 → yt-dlp 解析/下载 → 任务卡片） */
public class VideoDlActivity extends Activity {

    static class Task {
        String url, title = "解析中…", size = "", res = "";
        int percent = -1;            // -1解析中/等待 0-100下载中 100完成 -2失败
        String err; Uri saved; File out;
    }

    private static final ArrayList<Task> TASKS = new ArrayList<Task>();

    private LinearLayout list;
    private int chip = 0;           // 0全部 1进行中 2已完成
    private LinearLayout[] chips;
    private TextView[] chipTx;
    private EditText input;
    private boolean inited = false;
    private final boolean dark = true;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(dark ? 0xFF10141C : 0xFFF2F6FF);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(18);
        col.setPadding(pad, pad + dp(24), pad, pad);
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        // 头部：视频下载 + 🗑 + ⚙
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("视频下载");
        title.setTextSize(26); title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(dark ? Color.WHITE : 0xFF1F2329);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        head.addView(iconBtn("🗑", new OnClickListener() { public void onClick(View v) {
            for (int i = TASKS.size() - 1; i >= 0; i--) if (TASKS.get(i).percent >= 100 || TASKS.get(i).percent == -2) TASKS.remove(i);
            render();
        }}));
        head.addView(iconBtn("⚙", new OnClickListener() { public void onClick(View v) {
            startActivity(new Intent(VideoDlActivity.this, SettingsActivity.class));
        }}));
        col.addView(head);

        // 粘贴视频链接 pill
        LinearLayout pillRow = new LinearLayout(this);
        pillRow.setOrientation(LinearLayout.HORIZONTAL);
        pillRow.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable pb = new GradientDrawable();
        pb.setCornerRadius(dp(24)); pb.setColor(dark ? 0xFF181E2A : 0xFFFFFFFF);
        if (!dark) pb.setStroke(dp(1), 0xFFE4EAF5);
        pillRow.setBackground(pb);
        pillRow.setPadding(dp(18), dp(14), dp(10), dp(14));
        LinearLayout.LayoutParams prlp = new LinearLayout.LayoutParams(-1, -2);
        prlp.topMargin = dp(16);
        TextView link = new TextView(this);
        link.setText("🔗"); link.setTextSize(16);
        pillRow.addView(link);
        input = new EditText(this);
        input.setHint("粘贴视频链接");
        input.setBackground(null);
        input.setSingleLine(true);
        input.setTextSize(15);
        input.setTextColor(dark ? 0xFFE8ECF4 : 0xFF1F2329);
        input.setHintTextColor(dark ? 0xFF6B7684 : 0xFF9AA3AE);
        input.setPadding(dp(10), 0, 0, 0);
        pillRow.addView(input, new LinearLayout.LayoutParams(0, -2, 1f));
        try {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null && cm.getPrimaryClip() != null && cm.getPrimaryClip().getItemAt(0) != null && cm.getPrimaryClip().getItemAt(0).getText() != null) {
                String cu = cm.getPrimaryClip().getItemAt(0).getText().toString().trim();
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("https?://\\S+").matcher(cu);
                if (m.find()) input.setText(m.group());
            }
        } catch (Throwable ignored) {}
        TextView go = new TextView(this);
        go.setText("→"); go.setTextSize(20);
        go.setTextColor(dark ? 0xFFB4C5FF : 0xFF315CDE);
        go.setPadding(dp(12), 0, dp(6), 0);
        go.setOnClickListener(new OnClickListener() { public void onClick(View v) { submit(); }});
        pillRow.addView(go);
        col.addView(pillRow, prlp);

        // chips
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chipsRow = new LinearLayout(this);
        chipsRow.setOrientation(LinearLayout.HORIZONTAL);
        chipsRow.setPadding(0, dp(18), 0, dp(10));
        hs.addView(chipsRow);
        col.addView(hs);
        String[] names = {"全部", "进行中", "已完成"};
        chips = new LinearLayout[3]; chipTx = new TextView[3];
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            LinearLayout c = new LinearLayout(this);
            c.setGravity(Gravity.CENTER);
            c.setPadding(dp(22), dp(8), dp(22), dp(8));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
            clp.rightMargin = dp(10);
            TextView t = new TextView(this);
            t.setText(names[i]); t.setTextSize(14);
            c.addView(t);
            chipsRow.addView(c, clp);
            chips[i] = c; chipTx[i] = t;
            c.setOnClickListener(new OnClickListener() { public void onClick(View v) { chip = idx; styleChips(); render(); }});
        }
        styleChips();

        // 任务列表
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        col.addView(list, new LinearLayout.LayoutParams(-1, -2));

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        setContentView(root);
        render();
    }

    private TextView iconBtn(String s, OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(20);
        t.setPadding(dp(12), dp(4), dp(4), dp(4));
        t.setOnClickListener(l);
        return t;
    }

    private void styleChips() {
        for (int i = 0; i < 3; i++) {
            boolean sel = i == chip;
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(22));
            g.setColor(sel ? 0xFF4A4458 : Color.TRANSPARENT);
            if (!sel) g.setStroke(dp(1), 0xFF3A4252);
            chips[i].setBackground(g);
            chipTx[i].setTextColor(sel ? Color.WHITE : 0xFFAEB6C2);
        }
    }

    private void submit() {
        String u = input.getText().toString().trim();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("https?://\\S+").matcher(u);
        if (m.find()) u = m.group(); else { Toast.makeText(this, "请输入有效链接", Toast.LENGTH_SHORT).show(); return; }
        for (Task t : TASKS) if (t.url.equals(u) && t.percent < 100 && t.percent != -2) { Toast.makeText(this, "该链接已在队列", Toast.LENGTH_SHORT).show(); return; }
        input.setText("");
        final Task tk = new Task(); tk.url = u;
        TASKS.add(0, tk);
        render();
        runTask(tk);
    }

    private void runTask(final Task tk) {
        new Thread(new Runnable() { public void run() {
            try {
                ensureEngine();
                // 1) 元数据
                try {
                    com.yausername.youtubedl_android.YoutubeDLRequest meta =
                            new com.yausername.youtubedl_android.YoutubeDLRequest(tk.url);
                    meta.addOption("--dump-json");
                    meta.addOption("--no-playlist");
                    meta.addOption("--cookies", cookies().getAbsolutePath());
                    com.yausername.youtubedl_android.YoutubeDLResponse mr =
                            com.yausername.youtubedl_android.YoutubeDL.getInstance().execute(meta, null);
                    JSONObject j = new JSONObject(mr.getOut().trim().split("\n")[0]);
                    tk.title = j.optString("title", tk.title);
                    if (j.has("filesize") && j.optLong("filesize") > 0) tk.size = fmtMB(j.optLong("filesize"));
                    else if (j.has("filesize_approx")) tk.size = "~" + fmtMB(j.optLong("filesize_approx"));
                    int w = j.optInt("width"), h = j.optInt("height"), fps = j.optInt("fps", 0);
                    if (h > 0) tk.res = w + " × " + h + (fps > 0 ? " · " + fps + " fps" : "");
                    tk.percent = 0;
                    runUi(new Runnable() { public void run() { render(); }});
                } catch (Throwable e) {
                    tk.percent = 0;  // 元数据失败继续下
                }
                // 2) 下载
                File cache = getExternalCacheDir() != null ? getExternalCacheDir() : getCacheDir();
                File out = new File(cache, "vdl_%(id)s.%(ext)s");
                com.yausername.youtubedl_android.YoutubeDLRequest req =
                        new com.yausername.youtubedl_android.YoutubeDLRequest(tk.url);
                req.addOption("-f", "b[ext=mp4]/bv*[ext=mp4]+ba[ext=m4a]/b");  // 精简引擎无ffmpeg:优先自带音轨合并流
                req.addOption("--cookies", cookies().getAbsolutePath());
                req.addOption("-o", out.getAbsolutePath());
                req.addOption("--no-playlist"); req.addOption("--no-mtime");
                // 进度监视：轮询 .part 文件大小
                new Thread(new Runnable() { public void run() {
                    while (tk.percent >= 0 && tk.percent < 100) {
                        try {
                            File cache2 = getExternalCacheDir() != null ? getExternalCacheDir() : getCacheDir();
                            long mx = 0;
                            for (File f2 : cache2.listFiles()) {
                                if (f2.getName().startsWith("vdl_") && f2.getName().endsWith(".part") && f2.length() > mx) mx = f2.length();
                            }
                            if (mx > 0) {
                                tk.size = fmtMB(mx);
                                if (tk.percent < 1) tk.percent = 1;
                                runUi(new Runnable() { public void run() { render(); }});
                            }
                        } catch (Throwable ignored) {}
                        try { Thread.sleep(1500); } catch (Throwable e) { return; }
                    }
                }}).start();
                com.yausername.youtubedl_android.YoutubeDLResponse resp =
                        com.yausername.youtubedl_android.YoutubeDL.getInstance().execute(req);
                // 3) 找产物
                File done = null;
                for (File f : cache.listFiles()) {
                    if (f.getName().startsWith("vdl_")) {
                        if (done == null || f.lastModified() > done.lastModified()) done = f;
                    }
                }
                if (done == null || done.length() < 1024) throw new Exception("未生成视频文件");
                tk.out = done;
                tk.saved = store(done, safeName(tk.title));
                tk.percent = 100;
                HistoryStore.add(VideoDlActivity.this, "视频", safeName(tk.title), tk.saved.toString());
                runUi(new Runnable() { public void run() { render(); }});
            } catch (Throwable e) {
                tk.percent = -2;
                tk.err = e.getMessage() == null ? e.toString() : e.getMessage();
                try {
                    java.io.StringWriter sw = new java.io.StringWriter();
                    e.printStackTrace(new java.io.PrintWriter(sw));
                    String full = sw.toString();
                    for (String fn : new String[]{"网页诊断.txt", "fc2_debug.txt"}) {
                        java.io.File dir = getExternalFilesDir(null) != null ? getExternalFilesDir(null).getParentFile() : getFilesDir();
                        java.io.FileWriter fw = new java.io.FileWriter(new java.io.File(dir, fn), true);
                        fw.write("\n==== VDL " + new java.util.Date() + " url=" + tk.url + " ====\n" + full + "\n");
                        fw.close();
                    }
                } catch (Throwable ignored) {}
                runUi(new Runnable() { public void run() { render(); }});
            }
        }}).start();
    }

    private void ensureEngine() throws Exception {
        synchronized (VideoDlActivity.class) {
            if (!inited) {
                System.setProperty("java.net.preferIPv4Stack", "true");
                com.yausername.youtubedl_android.YoutubeDL.getInstance().init(getApplicationContext());
                inited = true;
            }
        }
    }

    private File cookies() throws Exception {
        android.webkit.CookieManager cm = android.webkit.CookieManager.getInstance();
        String raw = cm.getCookie("https://www.youtube.com");
        File f = new File(getFilesDir(), "yt_cookies.txt");
        java.io.PrintWriter pw = new java.io.PrintWriter(f, "UTF-8");
        pw.println("# Netscape HTTP Cookie File");
        if (raw != null) for (String p : raw.split(";")) {
            String[] kv = p.trim().split("=", 2);
            if (kv.length == 2) pw.println(".youtube.com\tTRUE\t/\tTRUE\t0\t" + kv[0] + "\t" + kv[1]);
        }
        pw.close();
        return f;
    }

    private void runUi(final Runnable r) { runOnUiThread(r); }

    // ---------- 渲染 ----------
    private void render() {
        if (list == null) return;
        list.removeAllViews();
        int dp2 = dp(12);
        boolean any = false;
        for (final Task tk : TASKS) {
            boolean busy = tk.percent >= 0 && tk.percent < 100;
            boolean done = tk.percent == 100;
            if (chip == 1 && !busy) continue;
            if (chip == 2 && !done) continue;
            any = true;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(24));
            g.setColor(0xFF171C27);
            card.setBackground(g);
            card.setPadding(dp(18), dp(16), dp(18), dp(16));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
            clp.topMargin = dp(12);
            // 行1: 徽标 + 大小 + ⋮
            LinearLayout r1 = new LinearLayout(this);
            r1.setGravity(Gravity.CENTER_VERTICAL);
            TextView badge = new TextView(this);
            badge.setText(platformEmoji(tk.url)); badge.setTextSize(20);
            r1.addView(badge);
            r1.addView(new TextView(this), new LinearLayout.LayoutParams(0, 0, 1f));
            TextView size = new TextView(this);
            size.setText(tk.percent >= 0 && tk.percent < 100 ? tk.percent + "%" : tk.size);
            size.setTextSize(14); size.setTextColor(0xFFAEB6C2);
            r1.addView(size);
            TextView more = new TextView(this);
            more.setText("⋮"); more.setTextSize(18);
            more.setTextColor(0xFF8A94A6);
            more.setPadding(dp(12), 0, 0, 0);
            more.setOnClickListener(new OnClickListener() { public void onClick(View v) { taskMenu(tk); }});
            r1.addView(more);
            card.addView(r1, new LinearLayout.LayoutParams(-1, -2));
            // 行2: 标题
            TextView tTitle = new TextView(this);
            String txt = tk.percent == -2 ? "失败: " + tk.err : tk.title;
            tTitle.setText(txt);
            tTitle.setTextSize(16); tTitle.setTypeface(Typeface.DEFAULT_BOLD);
            tTitle.setTextColor(tk.percent == -2 ? 0xFFFF7B8A : (dark ? Color.WHITE : Color.WHITE));
            tTitle.setMaxLines(2);
            tTitle.setPadding(0, dp2(10), 0, 0);
            card.addView(tTitle, new LinearLayout.LayoutParams(-1, -2));
            // 行3: 分辨率 + 播放 + 分享
            LinearLayout r3 = new LinearLayout(this);
            r3.setGravity(Gravity.CENTER_VERTICAL);
            r3.setPadding(0, dp2(12), 0, 0);
            TextView res = new TextView(this);
            res.setText(tk.percent == 100 ? (tk.res.length() > 0 ? tk.res : "已完成") : (tk.percent == -1 ? "解析中…" : (tk.percent == -2 ? "长按可删除" : "下载中…")));
            res.setTextSize(15); res.setTextColor(0xFFAEB6C2);
            r3.addView(res, new LinearLayout.LayoutParams(0, -2, 1f));
            if (done) {
                TextView play = new TextView(this);
                play.setText("▷"); play.setTextSize(22); play.setTextColor(0xFFE8ECF4);
                play.setPadding(dp2(14), 0, dp2(14), 0);
                play.setOnClickListener(new OnClickListener() { public void onClick(View v) {
                    try {
                        Intent i = new Intent(VideoDlActivity.this, NativePlayerActivity.class);
                        i.putExtra("url", tk.saved.toString());
                        i.putExtra("title", tk.title);
                        i.putExtra("kernel", "native");
                        startActivity(i);
                    } catch (Throwable e) { Toast.makeText(VideoDlActivity.this, "打不开", Toast.LENGTH_SHORT).show(); }
                }});
                r3.addView(play);
                TextView share = new TextView(this);
                share.setText("⤴"); share.setTextSize(20); share.setTextColor(0xFF8A94A6);
                share.setOnClickListener(new OnClickListener() { public void onClick(View v) {
                    try {
                        Intent sh = new Intent(Intent.ACTION_SEND);
                        sh.setType("video/mp4");
                        sh.putExtra(Intent.EXTRA_STREAM, tk.saved);
                        sh.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(sh, "分享视频"));
                    } catch (Throwable e) { }
                }});
                r3.addView(share);
            }
            card.addView(r3, new LinearLayout.LayoutParams(-1, -2));
            list.addView(card, clp);
        }
        if (!any) {
            TextView e = new TextView(this);
            e.setText("暂无任务，粘贴链接开始");
            e.setTextColor(0xFF6B7684); e.setTextSize(14);
            e.setGravity(Gravity.CENTER);
            e.setPadding(0, dp(40), 0, 0);
            list.addView(e, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private void taskMenu(final Task tk) {
        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this);
        java.util.ArrayList<String> its = new java.util.ArrayList<String>();
        if (tk.percent == 100) its.add("播放");
        if (tk.percent == -2 || tk.percent == 100) its.add("删除");
        if (tk.percent == -2) its.add("重试");
        its.add("复制链接");
        b.setItems(its.toArray(new String[0]), new android.content.DialogInterface.OnClickListener() {
            public void onClick(android.content.DialogInterface d, int w) {
                String t = its.get(w);
                if (t.equals("删除")) { TASKS.remove(tk); render(); }
                else if (t.equals("重试")) { tk.percent = -1; tk.err = null; render(); runTask(tk); }
                else if (t.equals("播放")) {
                    Intent i = new Intent(VideoDlActivity.this, NativePlayerActivity.class);
                    i.putExtra("url", tk.saved.toString()); i.putExtra("title", tk.title); i.putExtra("kernel", "native");
                    startActivity(i);
                } else if (t.equals("复制链接")) {
                    try { ((android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText("u", tk.url)); } catch (Throwable ignored) {}
                }
            }
        }).show();
    }

    private String platformEmoji(String u) {
        if (u == null) return "🌐";
        String l = u.toLowerCase();
        if (l.contains("douyin") || l.contains("tiktok") || l.contains("amemv")) return "🎵";
        if (l.contains("bilibili") || l.contains("b23.tv")) return "📺";
        if (l.contains("youtube") || l.contains("youtu.be")) return "▶";
        return "🌐";
    }

    private String fmtMB(long b) {
        return String.format(java.util.Locale.US, "%.1f MB", b / 1048576.0);
    }

    private String safeName(String t) {
        String n = t == null || t.trim().isEmpty() ? "视频" : t.trim();
        return n.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private Uri store(File f, String name) throws Exception {
        try {
            ContentValues cv = new ContentValues();
            cv.put(android.provider.MediaStore.Video.Media.DISPLAY_NAME, name + ".mp4");
            cv.put(android.provider.MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            if (Build.VERSION.SDK_INT >= 29)
                cv.put(android.provider.MediaStore.Video.Media.RELATIVE_PATH, "Movies/资源嗅探");
            Uri uri = getContentResolver().insert(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv);
            if (uri != null) {
                java.io.OutputStream os = getContentResolver().openOutputStream(uri);
                java.io.FileInputStream fis = new java.io.FileInputStream(f);
                byte[] b = new byte[65536]; int r;
                while ((r = fis.read(b)) > 0) os.write(b, 0, r);
                fis.close(); os.close();
                f.delete();
                return uri;
            }
        } catch (Throwable ignored) {}
        return Uri.fromFile(f);
    }

    private int dp2(int v) { return dp(v); }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
