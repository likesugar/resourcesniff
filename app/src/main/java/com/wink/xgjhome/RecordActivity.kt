package com.wink.xgjhome

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** 录制下载页：一列任务，右侧 ⋮ 弹菜单（开始/播放/结束录制/取消）—— Jetpack Compose 版 */
class RecordActivity : ComponentActivity() {

    private fun fmtDur(s: Long): String =
        String.format(java.util.Locale.US, "%02d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)

    private fun fmtSize(b: Long): String =
        if (b >= 1048576) String.format(java.util.Locale.US, "%.2fMB", b / 1048576.0)
        else String.format(java.util.Locale.US, "%.0fKB", b / 1024.0)

    private fun dlInfo(j: DlManager.DlJob): String {
        val sz = fmtSize(j.doneBytes) + (if (j.total > 0 && j.total > j.doneBytes) " / " + fmtSize(j.total) else "")
        return "下载大小: $sz\n状态: ${j.state}"
    }

    private fun qualify(j: RecManager.RecJob): String {
        val u = (j.url ?: "") + " " + (j.name ?: "")
        val q = when {
            u.contains("_or4") -> "原画"
            u.contains("_uhd") -> "蓝光"
            u.contains("_hd") -> "高清"
            else -> null
        }
        return if (q == null) (j.name ?: "") else "抖音·$q ${j.name}"
    }

    private fun buildInfo(j: RecManager.RecJob, live: Boolean): String {
        val secs = j.secs + (if (live && j.startTs > 0) (System.currentTimeMillis() - j.startTs) / 1000 else 0)
        return "录制时长: " + fmtDur(secs) +
            "\n录制大小: " + fmtSize(j.bytes) +
            "\n录制状态: " + (if (live) "录制中" else (j.state ?: "暂停录制"))
    }

    // ---------- 行数据快照 ----------
    private data class RowVM(
        val key: String, val kind: String, val state: String,
        val title: String, val info: String,
        val rec: RecManager.RecJob? = null, val live: Boolean = false,
        val dl: DlManager.DlJob? = null,
        val hist: HistoryStore.Item? = null, val histIdx: Int = -1)

    private val rowOrder = java.util.ArrayList<String>()

    private fun snapshot(tab: Int, ctx: Context): List<RowVM> {
        val hist = HistoryStore.load(ctx)
        val meta = java.util.LinkedHashMap<String, Array<Any?>>()
        for (j in RecManager.recJobs.values) meta["R" + j.id] = arrayOf(0, j, j.active as Any)
        for (j in RecManager.stoppedJobs.values) if (!meta.containsKey("R" + j.id)) meta["R" + j.id] = arrayOf(0, j, false)
        for (j in DlManager.jobs()) meta["D" + j.id] = arrayOf(1, j, false)
        for (i in hist.indices) meta["H$i"] = arrayOf(2, hist[i], false)

        val cat = HashMap<String, String>(); val st = HashMap<String, String>()
        for ((k, v) in meta) {
            val type = v[0] as Int; val live = v[2] as Boolean
            if (type == 2) {
                val it = v[1] as HistoryStore.Item
                cat[k] = it.type ?: "视频"; st[k] = "已完成"
            } else if (type == 1) {
                val dj = v[1] as DlManager.DlJob
                cat[k] = "视频"; st[k] = if (dj.active || dj.paused) "进行中" else "已完成"
            } else {
                cat[k] = "录制"; st[k] = if (live) "进行中" else "已完成"
            }
        }
        val want = arrayOf("全部", "视频", "录制", "进行中", "已完成")[tab]

        val order = java.util.ArrayList(rowOrder)
        order.retainAll(meta.keys)
        for (k in meta.keys) if (!order.contains(k)) order.add(k)
        rowOrder.clear(); rowOrder.addAll(order)

        val out = java.util.ArrayList<RowVM>()
        for (k in order) {
            val v = meta[k] ?: continue
            val state = st[k]
            val show = want == "全部" || (state != null && want == state) || (state == null && want == cat[k])
            if (!show) continue
            when (v[0] as Int) {
                0 -> {
                    val j = v[1] as RecManager.RecJob; val live = v[2] as Boolean
                    val cur = RecManager.recJobs[j.id]
                    out.add(RowVM(k, "录制", st[k]!!, qualify(j), buildInfo(j, live), rec = cur ?: j, live = cur != null && cur.active))
                }
                1 -> {
                    val j = v[1] as DlManager.DlJob
                    out.add(RowVM(k, "视频", st[k]!!, j.title, dlInfo(j), dl = j))
                }
                else -> {
                    val idx = k.substring(1).toInt()
                    val it = v[1] as HistoryStore.Item
                    out.add(RowVM(k, it.type ?: "视频", "已完成", it.title, "类型: ${it.type}\n状态: 已完成", hist = it, histIdx = idx))
                }
            }
        }
        return out
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DlManager.init(this)
        try { window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION } catch (_: Throwable) { }
        val initTab = Math.max(0, Math.min(4, getIntent().getIntExtra("tab", 0)))
        setContent { RecordScreen(initTab) }
    }

    @Composable
    private fun RecordScreen(initTab: Int) {
        val ctx = LocalContext.current
        var tab by remember { mutableStateOf(initTab) }
        var tick by remember { mutableStateOf(0) }
        LaunchedEffect(Unit) { while (true) { delay(1500); tick++ } }
        val rows = remember(tab, tick) { snapshot(tab, ctx) }

        Box(Modifier.fillMaxSize().background(Color(0xFFF7F8FA))) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp, 40.dp, 24.dp, 110.dp)) {
                Row(Modifier.fillMaxWidth().padding(bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("下载", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F2329),
                        modifier = Modifier.weight(1f))
                    Text("📋 读取剪贴板录制", color = Color.White, fontSize = 14.sp,
                        modifier = Modifier
                            .background(Color(0xB324485E), RoundedCornerShape(10.dp))
                            .clickable {
                                var u: String? = null
                                try {
                                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    u = cm.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
                                } catch (_: Throwable) { }
                                if (u == null || !u.startsWith("http")) {
                                    Toast.makeText(ctx, "剪贴板里没有有效链接", Toast.LENGTH_SHORT).show(); return@clickable
                                }
                                RecManager.startRecJob(u, u); tick++
                                Toast.makeText(ctx, "已开始录制", Toast.LENGTH_SHORT).show()
                            }
                            .padding(20.dp, 12.dp))
                }

                Row(Modifier.padding(bottom = 16.dp)) {
                    arrayOf("全部", "视频", "录制", "进行中", "已完成").forEachIndexed { i, t ->
                        val sel = i == tab
                        Text(t, fontSize = 14.sp,
                            color = if (sel) Color(0xFF315CDE) else Color(0xFF8A919E),
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier
                                .background(if (sel) Color(0x1A315CDE) else Color(0x0F1A2430), RoundedCornerShape(50))
                                .clickable { tab = i }
                                .padding(horizontal = 16.dp, vertical = 9.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                }

                if (rows.isEmpty()) {
                    Text("暂无录制任务", color = Color(0xFF8A919E), fontSize = 15.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 120.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }

            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp).padding(top = 150.dp, bottom = 110.dp)) {
                items(rows, key = { it.key }) { row ->
                    TaskRow(row, ctx) { tick++ }
                }
            }

            // 底部胶囊：首页 / 下载
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp)
                    .background(Color(0x66FFFFFF), RoundedCornerShape(50))
                    .padding(7.dp)
            ) {
                Box(Modifier.width(118.dp).height(50.dp)
                    .background(Color.Transparent, RoundedCornerShape(50))
                    .clickable { finish() }, contentAlignment = Alignment.Center) {
                    Text("⌂ 首页", color = Color(0xFF5F6B7A), fontSize = 14.sp)
                }
                Box(Modifier.width(118.dp).height(50.dp)
                    .background(Color(0xA64B6ADF), RoundedCornerShape(50))
                    .clickable { }, contentAlignment = Alignment.Center) {
                    Text("⬇ 下载", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    @Composable
    private fun TaskRow(row: RowVM, ctx: Context, onChanged: () -> Unit) {
        var menu by remember { mutableStateOf(false) }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(70.dp).height(45.dp).background(Color(0x661E242E), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center) {
                Text(if (row.dl != null) "⇣" else "▶", color = Color(0xFF8A919E))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(row.title, color = Color(0xFF1F2329), fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(row.info, color = Color(0xFF8A919E), fontSize = 13.sp, lineHeight = 18.sp)
            }
            Box {
                Text("⋮", color = Color(0xFF5F6B7A), fontSize = 22.sp, modifier = Modifier
                    .clickable { menu = true }.padding(12.dp))
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    @Composable fun act(label: String, fn: () -> Unit) {
                        DropdownMenuItem(text = { Text(label) }, onClick = { menu = false; fn(); onChanged() })
                    }
                    if (row.rec != null) {
                        val j = row.rec
                        if (row.live) act("暂停") { RecManager.recStop(j.id) }
                        else {
                            act("打开") { playRec(j, ctx) }
                            act("开始") { RecManager.recContinue(j.id) }
                        }
                        act("结束录制(转MP4)") { RecManager.recFinish(j.id) }
                        act("复制下载地址") {
                            try {
                                (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                    .setPrimaryClip(ClipData.newPlainText("url", j.url ?: ""))
                                Toast.makeText(ctx, "已复制", Toast.LENGTH_SHORT).show()
                            } catch (_: Throwable) { Toast.makeText(ctx, "复制失败", Toast.LENGTH_SHORT).show() }
                        }
                        act("打开所在目录") { openDir(ctx) }
                        act("打开录制合并目录") { openMergedDir(ctx) }
                        act("取消") { RecManager.recCancel(j.id) }
                    } else if (row.dl != null) {
                        val j = row.dl
                        if (j.hls) {
                            if (j.active) { act("暂停") { DlManager.pauseHls(j.id) }; act("结束(合并MP4)") { DlManager.finishHls(j.id) } }
                            else if (j.paused) { act("开始") { DlManager.resumeHls(j.id) }; act("结束(合并MP4)") { DlManager.finishHls(j.id) } }
                            else if (j.done) act("播放") { playDl(j, ctx) }
                        } else {
                            if (j.active) act("取消") { DlManager.cancel(j.id) }
                            if (j.done) act("播放") { playDl(j, ctx) }
                        }
                        act("删除") { DlManager.cancel(j.id) }
                    } else if (row.hist != null) {
                        val it = row.hist
                        act("播放") {
                            try { playInApp(ctx, it.path, it.title) } catch (_: Throwable) { Toast.makeText(ctx, "打不开", Toast.LENGTH_SHORT).show() }
                        }
                        act("删除") {
                            HistoryStore.removeAt(ctx, row.histIdx)
                        }
                    }
                }
            }
        }
    }

    private fun playInApp(ctx: Context, path: String, title: String?) {
        val i = Intent(ctx, NativePlayerActivity::class.java)
        i.putExtra("url", path)
        i.putExtra("title", title ?: "播放")
        i.putExtra("kernel", "native")
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    private fun playDl(j: DlManager.DlJob, ctx: Context) {
        var pth = "file://" + j.file.absolutePath
        if (j.hls && j.done) {
            val hh = HistoryStore.load(ctx)
            if (hh.isNotEmpty()) pth = hh[0].path
        }
        try { playInApp(ctx, pth, j.title) } catch (_: Throwable) { Toast.makeText(ctx, "打不开", Toast.LENGTH_SHORT).show() }
    }

    private fun playRec(j: RecManager.RecJob, ctx: Context) {
        try {
            if (j.storeUri == null) { Toast.makeText(ctx, "文件不存在", Toast.LENGTH_SHORT).show(); return }
            val it = Intent(ctx, HomeActivity::class.java)
            it.putExtra("autoUrl", j.storeUri.toString())
            ctx.startActivity(it)
        } catch (t: Throwable) { Toast.makeText(ctx, "打开失败: $t", Toast.LENGTH_SHORT).show() }
    }

    private fun openDir(ctx: Context) {
        try {
            val i = Intent(Intent.ACTION_VIEW)
            i.setClassName("bin.mt.plus", "bin.mt.plus.OpenFileActivity")
            i.setDataAndType(android.net.Uri.parse("file:///storage/emulated/0/Movies/录制"), "resource/folder")
            i.putExtra("path", "/storage/emulated/0/Movies/录制")
            i.putExtra("com.bin.mt.plus.path", "/storage/emulated/0/Movies/录制")
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            ctx.startActivity(i); return
        } catch (_: Throwable) { }
        try {
            val i = Intent(Intent.ACTION_VIEW)
            i.setClassName("bin.mt.plus", "bin.mt.plus.MainLightIcon")
            i.setDataAndType(android.net.Uri.parse("file:///storage/emulated/0/Movies/录制"), "resource/folder")
            i.putExtra("path", "/storage/emulated/0/Movies/录制")
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            ctx.startActivity(i); return
        } catch (_: Throwable) { }
        try {
            val dir = android.net.Uri.parse("content://com.android.externalstorage.documents/document/primary:Movies/录制")
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            i.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, dir)
            ctx.startActivity(i); return
        } catch (_: Throwable) { }
        try {
            val i = ctx.packageManager.getLaunchIntentForPackage("bin.mt.plus")
            if (i != null) { ctx.startActivity(i); Toast.makeText(ctx, "进 Movies/录制 目录", Toast.LENGTH_LONG).show(); return }
        } catch (_: Throwable) { }
        Toast.makeText(ctx, "请到 Movies/录制 目录查看", Toast.LENGTH_LONG).show()
    }

    private fun openMergedDir(ctx: Context) {
        try {
            val doc = android.provider.DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents", "primary:Android/data/com.wink.xgjhome/files/录制合并")
            val i = Intent(Intent.ACTION_VIEW)
            i.setDataAndType(doc, android.provider.DocumentsContract.Document.MIME_TYPE_DIR)
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i); return
        } catch (_: Throwable) { }
        try {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            i.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI,
                android.provider.DocumentsContract.buildDocumentUri("com.android.externalstorage.documents",
                    "primary:Android/data/com.wink.xgjhome/files"))
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        } catch (_: Throwable) { openDir(ctx) }
    }
}
