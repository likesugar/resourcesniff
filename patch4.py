p='/home/z/my-project/xgjhome/app/src/main/java/com/wink/xgjhome/SniffActivity.java'
s=open(p).read()

# 1) 非 http(s) 一律页内转 https（不询问拉起外部）
old='''            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }'''
new='''            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                String sc = u.getScheme() == null ? "" : u.getScheme().toLowerCase();
                if ("http".equals(sc) || "https".equals(sc)) return false;
                // bilibili:// 等协议：不询问拉起外部，直接页内转 https
                String https = "https://" + u.toString().replaceAll("^[a-zA-Z][a-zA-Z0-9+.-]*://", "");
                view.loadUrl(https);
                return true;
            }'''
assert old in s, 'anchor1'
s=s.replace(old,new,1)

# 2+3) 记录行：去掉长按删除；每行加 ⁝ 按钮（黑色菜单：下载/播放/直播录制）
old2='''        // 点行复制，长按删除
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("url", url));
                Toast.makeText(SniffActivity.this, "已复制", Toast.LENGTH_SHORT).show();
            }
        });
        row.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                ViewGroup p = (ViewGroup) v.getParent();
                if (p != null) p.removeView(v);
                recordKeys.remove(url);
                foundUrls.remove(url);
                Toast.makeText(SniffActivity.this, "已删除", Toast.LENGTH_SHORT).show();
                return true;
            }
        });

        layoutRecords.addView(row, 0);'''
new2='''        // 点行复制
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("url", url));
                Toast.makeText(SniffActivity.this, "已复制", Toast.LENGTH_SHORT).show();
            }
        });

        // ⁝ 菜单：下载 / 播放 / 直播录制
        LinearLayout headRow = new LinearLayout(this);
        headRow.setOrientation(LinearLayout.HORIZONTAL);
        headRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView menuBtn = new TextView(this);
        menuBtn.setText("\\u2064\\u2064\\u2064");
        menuBtn.setTextColor(0xFFFFFFFF);
        menuBtn.setTextSize(16);
        menuBtn.setPadding(12, 4, 12, 4);
        menuBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showRecordMenu(v, url);
            }
        });
        headRow.addView(tv);
        headRow.addView(menuBtn, new LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(headRow);
        row.addView(tvUrl);

        layoutRecords.addView(row, 0);'''
assert old2 in s, 'anchor2'
s=s.replace(old2,new2,1)

# 3) 菜单实现 + 下载/播放/录制
old3='''    String readAsset(String name) {'''
new3='''    final java.util.Map<String, Thread> recThreads = new java.util.HashMap<>();

    void showRecordMenu(View anchor, final String url) {
        android.widget.PopupMenu pm = new android.widget.PopupMenu(this, anchor);
        pm.getMenu().add("下载");
        pm.getMenu().add("播放");
        final String label = recThreads.containsKey(url) ? "停止录制" : "直播录制";
        pm.getMenu().add(label);
        pm.setOnMenuItemClickListener(new android.widget.PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(android.view.MenuItem item) {
                String t = item.getTitle().toString();
                if (t.equals("下载")) downloadUrl(url);
                else if (t.equals("播放")) playUrl(url);
                else if (t.equals("直播录制")) startRecord(url);
                else if (t.equals("停止录制")) stopRecord(url);
                return true;
            }
        });
        pm.show();
    }

    String refererFor(String url) {
        return url.contains("bilibili.com") || url.contains("bilivideo") ? "https://www.bilibili.com/" : null;
    }

    void downloadUrl(String url) {
        try {
            android.app.DownloadManager.Request req = new android.app.DownloadManager.Request(android.net.Uri.parse(url));
            String rf = refererFor(url);
            if (rf != null) req.addRequestHeader("Referer", rf);
            req.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_MOVIES,
                    "资源嗅探_" + System.currentTimeMillis() + ".ts");
            ((android.app.DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(req);
            Toast.makeText(this, "已加入下载（Movies）", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "下载失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    void playUrl(String url) {
        try {
            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_VIEW);
            i.setDataAndType(android.net.Uri.parse(url), "video/*");
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "没有可用播放器", Toast.LENGTH_SHORT).show();
        }
    }

    void startRecord(final String url) {
        if (recThreads.containsKey(url)) return;
        Toast.makeText(this, "开始录制", Toast.LENGTH_SHORT).show();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                java.io.File out = new java.io.File(
                        android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES),
                        "资源嗅探_直播_" + System.currentTimeMillis() + ".ts");
                java.io.InputStream in = null;
                java.io.FileOutputStream fos = null;
                try {
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                    c.setConnectTimeout(8000); c.setReadTimeout(8000);
                    c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
                    String rf = refererFor(url);
                    if (rf != null) c.setRequestProperty("Referer", rf);
                    in = c.getInputStream();
                    fos = new java.io.FileOutputStream(out);
                    byte[] buf = new byte[65536]; int n; long total = 0;
                    while ((n = in.read(buf)) > 0) {
                        fos.write(buf, 0, n);
                        total += n;
                    }
                    final String msg = "录制完成: " + (total / 1048576) + "MB " + out.getName();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() { Toast.makeText(SniffActivity.this, msg, Toast.LENGTH_LONG).show(); }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() { Toast.makeText(SniffActivity.this, "录制中断: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
                    });
                } finally {
                    try { if (in != null) in.close(); } catch (Exception e) { }
                    try { if (fos != null) fos.close(); } catch (Exception e) { }
                    recThreads.remove(url);
                }
            }
        });
        recThreads.put(url, t);
        t.start();
    }

    void stopRecord(String url) {
        Thread t = recThreads.remove(url);
        if (t != null) t.interrupt();
        Toast.makeText(this, "已停止", Toast.LENGTH_SHORT).show();
    }

    String readAsset(String name) {'''
assert old3 in s, 'anchor3'
s=s.replace(old3,new3,1)
open(p,'w').write(s)
print('ok')
