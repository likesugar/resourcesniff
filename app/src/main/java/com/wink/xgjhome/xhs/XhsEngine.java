package com.wink.xgjhome.xhs;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** 下载调度中枢：解析→（选择性）下载→任务历史。单例，UI 订阅刷新 */
public final class XhsEngine {
    public interface Listener { void onChanged(); }

    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final List<Listener> LISTENERS = new ArrayList<Listener>();
    private static final Map<String, AtomicBoolean> CANCELS = new LinkedHashMap<String, AtomicBoolean>();
    private static final List<XhsStore.Task> TASKS = new ArrayList<XhsStore.Task>();
    private static XhsStore store;

    public static void init(Context c) {
        XhsApp.init(c);
        if (store == null) {
            store = new XhsStore(c.getApplicationContext());
            synchronized (TASKS) {
                TASKS.clear();
                TASKS.addAll(store.loadTasks());
                for (XhsStore.Task t : TASKS)
                    if ("running".equals(t.status) || "pending".equals(t.status)) t.status = "stopped";
            }
        }
    }

    public static XhsStore store() { return store; }

    public static List<XhsStore.Task> tasks() { synchronized (TASKS) { return new ArrayList<XhsStore.Task>(TASKS); } }

    public static void addListener(Listener l) { synchronized (LISTENERS) { if (!LISTENERS.contains(l)) LISTENERS.add(l); } }
    public static void removeListener(Listener l) { synchronized (LISTENERS) { LISTENERS.remove(l); } }

    private static void notifyUi() {
        MAIN.post(new Runnable() { public void run() {
            synchronized (LISTENERS) { for (Listener l : LISTENERS) l.onChanged(); }
        }});
    }

    private static void persist() {
        if (store != null) synchronized (TASKS) { store.saveTasks(TASKS); }
    }

    private static XhsStore.Task findTask(String id) {
        synchronized (TASKS) { for (XhsStore.Task t : TASKS) if (t.id.equals(id)) return t; }
        return null;
    }

    // ---------- 入口：解析并建任务 ----------
    /** mode: download=解析并下载 / info=仅解析保存信息 */
    public static XhsStore.Task enqueue(Context c, String input, boolean infoOnly) {
        List<String> links = XhsParser.extractLinks(input, new XhsParser.ShortUrlResolver() {
            public String resolve(String u) { return XhsNet.resolveShort(u); }
        });
        if (links.isEmpty()) return null;
        final String link = links.get(0);
        final String noteId = XhsParser.extractPostId(link);
        synchronized (TASKS) {
            for (XhsStore.Task t : TASKS)
                if (t.url.equals(link) && ("running".equals(t.status) || "pending".equals(t.status))) return t;
        }
        final XhsStore.Task task = new XhsStore.Task();
        task.id = noteId != null ? noteId : ("t" + System.currentTimeMillis());
        task.url = link;
        task.status = "pending";
        task.created = System.currentTimeMillis();
        synchronized (TASKS) { TASKS.add(0, task); }
        persist();
        notifyUi();
        final boolean info = infoOnly;
        POOL.execute(new Runnable() { public void run() { runTask(task, info); } });
        return task;
    }

    private static void runTask(final XhsStore.Task task, final boolean infoOnly) {
        try {
            task.status = "running";
            task.error = null;
            notifyUi(); persist();
            String html = XhsNet.fetchHtml(task.url);
            XhsParser.Note note = XhsParser.parse(html, XhsParser.extractPostId(task.url), task.url);
            task.noteJson = noteToJson(note);
            task.title = note.title == null || note.title.isEmpty()
                    ? (note.body == null || note.body.isEmpty() ? "笔记 " + task.id : note.body) : note.title;
            if (task.title.length() > 60) task.title = task.title.substring(0, 60);
            task.author = note.authorName == null ? "" : note.authorName;
            task.description = note.description;
            if (!note.items.isEmpty()) task.coverUrl = note.items.get(0).previewUrl;
            if (infoOnly) {
                task.status = "done";
                task.progress = 100;
                notifyUi(); persist();
                return;
            }
            if (store.selectiveDownload()) {
                task.status = "selecting";
                notifyUi(); persist();
                return;
            }
            Context c = XhsApp.context();
            File dir = noteDir(c, store, note, task);
            downloadItems(task, note, dir);
            task.status = "done";
            task.progress = 100;
        } catch (Throwable e) {
            if ("cancelled".equals(e.getMessage())) task.status = "stopped";
            else { task.status = "failed"; task.error = e.getMessage() == null ? e.toString() : e.getMessage(); }
        }
        notifyUi(); persist();
    }

    private static File noteDir(Context c, XhsStore st, XhsParser.Note note, XhsStore.Task task) {
        XhsNaming.Note n = new XhsNaming.Note(task.title, note.authorName, note.authorId, note.noteId, note.publishTime);
        String base = st.useCustomNaming()
                ? XhsNaming.apply(st.namingTemplate(), n, 1, task.created)
                : ((note.authorName == null || note.authorName.isEmpty() ? "" : note.authorName + "-")
                    + XhsNaming.sanitize(task.title));
        File dir = new File(st.mediaDir(c), base);
        dir.mkdirs();
        return dir;
    }

    private static void downloadItems(final XhsStore.Task task, XhsParser.Note note, File dir) throws Exception {
        downloadItemsList(task, note.items, dir);
    }

    private static void downloadItemsList(final XhsStore.Task task, List<XhsParser.Media> items, File dir) throws Exception {
        XhsParser.Note note = noteFromJson(task.noteJson);
        if (items.isEmpty()) throw new Exception("未解析到媒体");
        XhsNaming.Note n = new XhsNaming.Note(task.title, note.authorName, note.authorId, note.noteId, note.publishTime);
        boolean checkExisting = store.checkExistingFiles();
        int index = 0;
        for (XhsParser.Media m : items) {
            index++;
            AtomicBoolean cancel = CANCELS.get(task.id);
            if (cancel != null && cancel.get()) throw new Exception("cancelled");
            boolean isVideo = "video".equals(m.kind);
            boolean isLive = "live".equals(m.kind);
            String base = store.useCustomNaming()
                    ? XhsNaming.apply(store.namingTemplate(), n, index, task.created)
                    : XhsNaming.sanitize((index > 1 ? task.title + "_" + index : task.title));
            if (isLive) base = base + "_live";
            task.status = "running";
            if (isVideo || isLive) {
                File dest = unique(dir, base + ".mp4", checkExisting);
                if (dest != null) addFile(task, dest);
                else downloadTo(task, m.url, dir, base + ".mp4", "视频");
                if (isLive && m.previewUrl != null && !m.previewUrl.isEmpty()) {
                    File img = unique(dir, base + ".jpg", checkExisting);
                    if (img != null) addFile(task, img);
                    else downloadTo(task, m.previewUrl, dir, base + ".jpg", "实况图");
                }
            } else {
                String ext = XhsNet.probeExt(m.url, m.url.toLowerCase().contains(".png") ? ".png"
                        : m.url.toLowerCase().contains(".webp") ? ".webp" : ".jpg");
                File img = unique(dir, base + ext, checkExisting);
                if (img != null) addFile(task, img);
                else downloadTo(task, m.url, dir, base + ext, "图片");
            }
        }
    }

    /** 已存在同名文件时跳过（checkExisting 开启时返回已存在的文件） */
    private static File unique(File dir, String name, boolean checkExisting) {
        File f = new File(dir, name);
        if (f.exists()) return checkExisting ? f : null;
        return null;
    }

    /** 选择性下载：UI 选好后回调，只下载选中的媒体 */
    public static void continueSelective(final XhsStore.Task t, final List<XhsParser.Media> chosen) {
        if (chosen == null || chosen.isEmpty()) { remove(t); return; }
        POOL.execute(new Runnable() { public void run() {
            try {
                XhsStore.Task fresh = findTask(t.id);
                if (fresh == null) return;
                fresh.status = "running"; fresh.progress = 0; notifyUi(); persist();
                Context c = XhsApp.context();
                XhsParser.Note note = noteFromJson(fresh.noteJson);
                note.items.clear();
                note.items.addAll(chosen);
                File dir = noteDir(c, store, note, fresh);
                downloadItems(fresh, note, dir);
                fresh.status = "done"; fresh.progress = 100;
            } catch (Throwable e) {
                XhsStore.Task fresh = findTask(t.id);
                if (fresh != null) { fresh.status = "failed"; fresh.error = e.getMessage(); }
            }
            notifyUi(); persist();
        }});
    }

    private static void addFile(XhsStore.Task task, File f) {
        if (!task.files.contains(f.getAbsolutePath())) task.files.add(f.getAbsolutePath());
        notifyUi();
    }

    private static void downloadTo(final XhsStore.Task task, String url, File dir, String name, String label) throws Exception {
        File dest = new File(dir, name);
        AtomicBoolean cancel = CANCELS.get(task.id);
        XhsNet.download(url, dest, "https://www.xiaohongshu.com/", new XhsNet.Progress() {
            public void onProgress(long done, long total) {
                task.totalBytes = total;
                task.doneBytes = done;
                task.progress = (int) (done * 100 / total);
                notifyUi();
            }
        }, cancel);
        addFile(task, dest);
    }

    // ---------- 操作 ----------
    public static void cancel(String id) {
        AtomicBoolean c = CANCELS.get(id);
        if (c == null) { c = new AtomicBoolean(true); CANCELS.put(id, c); } else c.set(true);
        XhsStore.Task t = findTask(id);
        if (t != null && ("running".equals(t.status) || "pending".equals(t.status))) {
            t.status = "stopped";
            persist(); notifyUi();
        }
    }

    public static void retry(final XhsStore.Task t) {
        t.status = "pending";
        t.error = null;
        persist(); notifyUi();
        POOL.execute(new Runnable() { public void run() { runTask(t, false); } });
    }

    public static void remove(XhsStore.Task t) {
        synchronized (TASKS) { TASKS.remove(t); }
        persist(); notifyUi();
    }

    public static void clearDone() {
        synchronized (TASKS) {
            Iterator<XhsStore.Task> it = TASKS.iterator();
            while (it.hasNext()) if ("done".equals(it.next().status)) it.remove();
        }
        persist(); notifyUi();
    }

    // ---------- note <-> json ----------
    public static String noteToJson(XhsParser.Note n) {
        try {
            JSONObject o = new JSONObject();
            o.put("noteId", nz(n.noteId)); o.put("type", nz(n.type)); o.put("title", nz(n.title));
            o.put("desc", nz(n.body)); o.put("authorName", nz(n.authorName)); o.put("authorId", nz(n.authorId));
            o.put("publishTime", nz(n.publishTime)); o.put("canonicalUrl", nz(n.canonicalUrl));
            org.json.JSONArray arr = new org.json.JSONArray();
            for (XhsParser.Media m : n.items) {
                JSONObject mo = new JSONObject();
                mo.put("kind", nz(m.kind)); mo.put("url", nz(m.url)); mo.put("previewUrl", nz(m.previewUrl));
                mo.put("liveVideoUrl", nz(m.liveVideoUrl)); mo.put("width", m.width); mo.put("height", m.height);
                arr.put(mo);
            }
            o.put("items", arr);
            return o.toString();
        } catch (Throwable t) { return null; }
    }

    public static XhsParser.Note noteFromJson(String json) {
        XhsParser.Note n = new XhsParser.Note();
        try {
            JSONObject o = new JSONObject(json);
            n.noteId = o.optString("noteId"); n.type = o.optString("type");
            n.title = o.optString("title"); n.body = o.optString("desc");
            n.authorName = o.optString("authorName"); n.authorId = o.optString("authorId");
            n.publishTime = o.optString("publishTime"); n.canonicalUrl = o.optString("canonicalUrl");
            org.json.JSONArray arr = o.optJSONArray("items");
            if (arr != null) for (int i = 0; i < arr.length(); i++) {
                JSONObject mo = arr.optJSONObject(i);
                if (mo == null) continue;
                XhsParser.Media m = new XhsParser.Media();
                m.kind = mo.optString("kind", "image"); m.url = mo.optString("url");
                m.previewUrl = mo.optString("previewUrl"); m.liveVideoUrl = mo.optString("liveVideoUrl");
                m.width = mo.optInt("width"); m.height = mo.optInt("height");
                n.items.add(m);
            }
        } catch (Throwable ignored) { }
        return n;
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
