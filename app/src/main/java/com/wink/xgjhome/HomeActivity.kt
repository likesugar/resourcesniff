package com.wink.xgjhome

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 首页：工具箱（纯黑/冰蓝主题切换 · 全屏）—— Jetpack Compose 版 */
class HomeActivity : ComponentActivity() {

    private fun applyImmersive() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            val c = window.insetsController
            c?.hide(android.view.WindowInsets.Type.systemBars())
            c?.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersive()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RecManager.init(applicationContext)
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                var dir = getExternalFilesDir(null) ?: filesDir
                java.io.File(dir, "crash.txt").appendText(
                    "\n==== " + java.util.Date() + " thread=" + t.name + " ====\n" +
                        android.util.Log.getStackTraceString(e))
            } catch (_: Throwable) { }
            Thread.setDefaultUncaughtExceptionHandler(null)
            throw RuntimeException(e)
        }
        applyImmersive()
        setContent { HomeScreen() }
    }

    @Composable
    private fun HomeScreen() {
        val ctx = LocalContext.current
        val prefs = remember { getSharedPreferences("settings", MODE_PRIVATE) }
        var dark by remember { mutableStateOf(prefs.getBoolean("dark", false)) }
        var showSniff by remember { mutableStateOf(false) }
        var showLan by remember { mutableStateOf(false) }

        val bg = if (dark) Color.Black else Color(0xFFEEF4FF)
        val cardBg = if (dark) Color(0xFF15181E) else Color.White
        val textMain = if (dark) Color.White else Color(0xFF1F2329)

        Box(Modifier.fillMaxSize().background(bg)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 56.dp)
            ) {
                Text("小工具", color = textMain, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 18.dp))
                ToolCard("🔍", "资源嗅探", cardBg, textMain) { showSniff = true }
                ToolCard("🗄️", "局域网", cardBg, textMain) { showLan = true }
                ToolCard("🌗", "明暗", cardBg, textMain) {
                    dark = !dark
                    prefs.edit().putBoolean("dark", dark).apply()
                }
                ToolCard("⬇️", "视频下载", cardBg, textMain) {
                    try { startActivity(Intent(ctx, com.daxiaamu.dbdown.MainActivity::class.java)) }
                    catch (t: Throwable) { Toast.makeText(ctx, "打开失败: $t", Toast.LENGTH_LONG).show() }
                }
                ToolCard("⚡", "创建快捷键", cardBg, textMain) {
                    try { startActivity(Intent(ctx, ShortcutActivity::class.java)) }
                    catch (t: Throwable) { Toast.makeText(ctx, "打开失败: $t", Toast.LENGTH_LONG).show() }
                }
            }

            // 底部胶囊导航：首页 / 下载
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp)
                    .background(Color(0x66FFFFFF), RoundedCornerShape(50))
                    .padding(7.dp)
            ) {
                PillSeg("⌂ 首页", true)
                PillSeg("⬇ 下载", false) {
                    ctx.startActivity(Intent(ctx, RecordActivity::class.java).putExtra("tab", 0))
                }
            }
        }

        if (showSniff) SniffDialog { showSniff = false }
        if (showLan) LanDialog { showLan = false }
    }

    @Composable
    private fun ToolCard(icon: String, title: String, cardBg: Color, textMain: Color, onClick: () -> Unit) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 14.dp)
                .background(cardBg, RoundedCornerShape(22.dp))
                .clickable(onClick = onClick)
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, fontSize = 22.sp, modifier = Modifier.size(46.dp).background(Color(0x141677FF), RoundedCornerShape(14.dp)).wrapContentSize(Alignment.Center))
            Spacer(Modifier.width(12.dp))
            Text(title, color = textMain, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("›", color = Color(0xFFC3CAD6), fontSize = 20.sp)
        }
    }

    @Composable
    private fun PillSeg(label: String, selected: Boolean, onClick: () -> Unit = {}) {
        Box(
            Modifier.width(118.dp).height(50.dp)
                .background(if (selected) Color(0xFF4666DB) else Color.Transparent, RoundedCornerShape(50))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(label, color = if (selected) Color.White else Color(0xFF5F6B7A),
                fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        }
    }

    @Composable
    private fun SniffDialog(onClose: () -> Unit) {
        val ctx = LocalContext.current
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = onClose,
            title = { Text("输入视频地址") },
            text = {
                Column {
                    OutlinedTextField(
                        value = url, onValueChange = { url = it },
                        placeholder = { Text("粘贴视频链接") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextButton(onClick = {
                            try {
                                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val t = cm.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
                                if (t != null) url = t
                            } catch (_: Exception) { }
                        }) { Text("粘贴") }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onClose) { Text("取消") }
                        Button(onClick = {
                            ctx.startActivity(Intent(ctx, SniffActivity::class.java).putExtra("input", url.trim()))
                            onClose()
                        }) { Text("开始嗅探") }
                    }
                }
            },
            confirmButton = {})
    }

    @Composable
    private fun LanDialog(onClose: () -> Unit) {
        val ctx = LocalContext.current
        var on by remember { mutableStateOf(LanShareServer.isRunning()) }
        var note by remember {
            mutableStateOf(if (LanShareServer.isRunning())
                "已开启，电脑浏览器访问：\nhttp://" + LanShareServer.localIp() + ":" + LanShareServer.getPort() + "\n（可查看并打开记录中的链接）"
            else "已关闭。开启后，同一 WiFi 下\n电脑浏览器可访问并打开记录中的链接。")
        }
        val perm = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
        AlertDialog(
            onDismissRequest = onClose,
            title = { Text("🗄️ 局域网共享") },
            text = {
                Column {
                    Text(note, fontSize = 14.sp, color = Color(0xFF444444))
                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("局域网共享", fontSize = 16.sp, color = Color(0xFF222222), modifier = Modifier.weight(1f))
                        Switch(checked = on, onCheckedChange = { want ->
                            on = want
                            if (want) {
                                if (Build.VERSION.SDK_INT >= 33 &&
                                    checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED &&
                                    !getSharedPreferences("settings", MODE_PRIVATE).getBoolean("permAsked", false)) {
                                    getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("permAsked", true).apply()
                                    perm.launch("android.permission.POST_NOTIFICATIONS")
                                }
                                try { LiveProxy.start() } catch (_: Throwable) { }
                                LanShareServer.start()
                                ctx.startService(Intent(ctx, LanShareService::class.java))
                                note = "已开启，电脑浏览器访问：\nhttp://" + LanShareServer.localIp() + ":" + LanShareServer.getPort()
                            } else {
                                ctx.stopService(Intent(ctx, LanShareService::class.java))
                                note = "已关闭。"
                            }
                        })
                    }
                }
            },
            confirmButton = { TextButton(onClick = onClose) { Text("完成") } })
    }
}
