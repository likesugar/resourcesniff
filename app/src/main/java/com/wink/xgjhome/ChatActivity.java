package com.wink.xgjhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 聊天室: 局域网UDP广播互联(手机↔手机), 电脑经局域网共享网页收发 */
public class ChatActivity extends Activity {

    private LinearLayout msgList;
    private ScrollView scroll;
    private EditText input;
    private String nick;
    private long lastTs = 0;
    private int msgCount = 0;
    private final SimpleDateFormat fmt = new SimpleDateFormat("HH:mm", Locale.US);

    private boolean dark;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dark = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("dark", false);
        nick = getSharedPreferences("chat", MODE_PRIVATE).getString("nick", "");
        ChatHub.init(getApplicationContext());
        ChatHub.start();
        buildUi();
        if (nick.isEmpty()) askNick();
    }

    private int bg()   { return dark ? 0xFF000000 : 0xFFFFFFFF; }
    private int main() { return dark ? 0xFFEEEEEE : 0xFF1F2329; }
    private int sub()  { return dark ? 0xFF888888 : 0xFF888888; }
    private int bubbleOther() { return dark ? 0xFF1C1F26 : 0xFFF2F3F5; }
    private int bubbleMine()  { return 0xFFD65DB1; }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg());

        // 顶栏
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dip(16), dip(40), dip(16), dip(10));
        TextView back = new TextView(this); back.setText("‹");
        back.setTextSize(24); back.setTextColor(main());
        back.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { finish(); } });
        TextView title = new TextView(this); title.setText("  聊天室");
        title.setTextSize(18); title.setTextColor(main()); title.setTypeface(null, android.graphics.Typeface.BOLD);
        TextView ip = new TextView(this); ip.setText("  " + LanShareServer.localIp());
        ip.setTextSize(11); ip.setTextColor(sub());
        top.addView(back); top.addView(title); top.addView(ip);
        root.addView(top, new LinearLayout.LayoutParams(-1, -2));

        msgList = new LinearLayout(this);
        msgList.setOrientation(LinearLayout.VERTICAL);
        msgList.setPadding(dip(12), dip(4), dip(12), dip(4));
        scroll = new ScrollView(this);
        scroll.addView(msgList);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        // 底部输入
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(dip(10), dip(8), dip(10), dip(8));
        input = new EditText(this);
        input.setHint("说点什么…");
        input.setTextSize(14);
        input.setTextColor(main());
        input.setHintTextColor(sub());
        input.setBackgroundResource(android.R.color.transparent);
        GradientDrawable ibg = new GradientDrawable();
        ibg.setColor(dark ? 0xFF1C1F26 : 0xFFF2F3F5);
        ibg.setCornerRadius(dip(20));
        input.setBackground(ibg);
        input.setPadding(dip(14), dip(8), dip(14), dip(8));
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, -2, 1f);
        bottom.addView(input, ilp);
        TextView send = new TextView(this);
        send.setText("发送");
        send.setTextColor(0xFFFFFFFF); send.setTextSize(14); send.setGravity(Gravity.CENTER);
        GradientDrawable sbg = new GradientDrawable();
        sbg.setColor(0xFF315CDE); sbg.setCornerRadius(dip(18));
        send.setBackground(sbg);
        send.setPadding(dip(16), dip(8), dip(16), dip(8));
        send.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { doSend(); } });
        bottom.addView(send, new LinearLayout.LayoutParams(-2, -2));
        root.addView(bottom, new LinearLayout.LayoutParams(-1, -2));
        input.setOnEditorActionListener(new android.widget.TextView.OnEditorActionListener() {
            public boolean onEditorAction(android.widget.TextView v, int a, android.view.KeyEvent e) {
                if (a == EditorInfo.IME_ACTION_SEND) { doSend(); return true; }
                return false;
            }
        });
        setContentView(root);
        renderAll();
    }

    private void doSend() {
        String m = input.getText().toString().trim();
        if (m.isEmpty()) return;
        input.setText("");
        if (nick.isEmpty()) { askNick(); return; }
        ChatHub.send(nick, m);
    }

    private void askNick() {
        final EditText et = new EditText(this);
        et.setHint("昵称");
        et.setText(nick);
        new AlertDialog.Builder(this)
            .setTitle("进聊天室先起个昵称")
            .setView(et)
            .setCancelable(false)
            .setPositiveButton("进入", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) {
                    String n = et.getText().toString().trim();
                    if (n.isEmpty()) n = "用户" + (int)(Math.random() * 900 + 100);
                    nick = n;
                    getSharedPreferences("chat", MODE_PRIVATE).edit().putString("nick", nick).apply();
                }
            }).show();
    }

    private void renderAll() {
        msgList.removeAllViews();
        msgCount = 0;
        org.json.JSONArray all = ChatHub.all();
        for (int i = 0; i < all.length(); i++) addBubble(all.optJSONObject(i), false);
        msgList.post(new Runnable() { public void run() { scroll.fullScroll(ScrollView.FOCUS_DOWN); } });
    }

    private void addBubble(JSONObject o, boolean scrollDown) {
        String nick2 = o.optString("nick", "?");
        String msg = o.optString("msg", "");
        long ts = o.optLong("ts", 0);
        boolean mine = nick2.equals(nick);
        if (ts > lastTs) lastTs = ts;
        msgCount++;

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(mine ? Gravity.END : Gravity.START);
        row.setPadding(0, dip(4), 0, dip(4));

        TextView meta = new TextView(this);
        meta.setText(nick2 + " · " + fmt.format(new Date(ts)));
        meta.setTextSize(10); meta.setTextColor(sub());
        row.addView(meta);

        TextView bubble = new TextView(this);
        bubble.setText(msg);
        bubble.setTextSize(15);
        bubble.setTextColor(mine ? 0xFFFFFFFF : main());
        bubble.setMaxWidth(dip(260));
        GradientDrawable bg2 = new GradientDrawable();
        bg2.setColor(mine ? bubbleMine() : bubbleOther());
        bg2.setCornerRadius(dip(14));
        bubble.setBackground(bg2);
        bubble.setPadding(dip(12), dip(8), dip(12), dip(8));
        row.addView(bubble, new LinearLayout.LayoutParams(-2, -2));

        msgList.addView(row, new LinearLayout.LayoutParams(-2, -2));
        if (scrollDown || msgCount % 50 == 0)
            msgList.post(new Runnable() { public void run() { scroll.fullScroll(ScrollView.FOCUS_DOWN); } });
    }

    private ChatHub.Listener listener = new ChatHub.Listener() {
        public void onMessage(final JSONObject o) {
            runOnUiThread(new Runnable() { public void run() {
                // 防重: 自己发的在send里已渲染
                if (o.optLong("ts", 0) <= lastTs && o.optString("nick").equals(nick)) return;
                addBubble(o, true);
            }});
        }
    };

    private int dip(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override
    protected void onResume() { super.onResume(); ChatHub.addListener(listener); renderAll(); }
    @Override
    protected void onPause() { super.onPause(); ChatHub.removeListener(listener); }
    @Override
    public void onBackPressed() { finish(); }
}
