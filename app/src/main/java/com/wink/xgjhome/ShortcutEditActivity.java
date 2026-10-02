package com.wink.xgjhome;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.widget.ScrollView;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

/** MT管理器风格快捷方式编辑页（扁平化深色） */
public class ShortcutEditActivity extends Activity {

    private EditText etName, etPkg, etCls, etData, etExtra, etCustom;
    private RadioButton rbView, rbMain, rbCustom;
    private android.widget.CheckBox cbNewTask, cbRoot;
    private TextView iconPreview;
    private String editKey = null;   // 编辑模式下为原条目索引键

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0B0D10);

        // 顶栏：← 标题
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(12), dp(12), dp(12), dp(12));
        TextView back = new TextView(this);
        back.setText("←");
        back.setTextColor(0xFFE8ECF2);
        back.setTextSize(20);
        back.setPadding(dp(8), 0, dp(16), 0);
        back.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { finish(); } });
        top.addView(back);
        TextView title = new TextView(this);
        title.setText("创建快捷方式");
        title.setTextColor(0xFFE8ECF2);
        title.setTextSize(18);
        title.getPaint().setFakeBoldText(true);
        top.addView(title);
        root.addView(top);

        // 按钮行：收藏 / 取消 / 打开 / 创建
        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setPadding(dp(12), 0, dp(12), dp(6));
        String[] labels = {"收藏", "取消", "打开", "创建"};
        View.OnClickListener[] acts = {
            new View.OnClickListener() { public void onClick(View v) { doSave(false); } },
            new View.OnClickListener() { public void onClick(View v) { finish(); } },
            new View.OnClickListener() { public void onClick(View v) { doOpen(); } },
            new View.OnClickListener() { public void onClick(View v) { doCreate(); } }
        };
        for (int i = 0; i < labels.length; i++) {
            TextView bt = new TextView(this);
            bt.setText(labels[i]);
            bt.setTextColor(0xFFE8ECF2);
            bt.setTextSize(14);
            bt.setGravity(Gravity.CENTER);
            bt.setBackgroundResource(i == 3 ? 0xFF245C8D : 0xFF242B34);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(38), 1f);
            lp.rightMargin = dp(8);
            bt.setLayoutParams(lp);
            bt.setOnClickListener(acts[i]);
            btns.addView(bt);
        }
        root.addView(btns);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(8), dp(14), dp(14));

        iconPreview = new TextView(this);
        iconPreview.setText("快捷方式的图标(点击更换)");
        iconPreview.setTextColor(0xFF8A919E);
        iconPreview.setTextSize(13);
        iconPreview.setGravity(Gravity.CENTER);
        iconPreview.setPadding(0, dp(4), 0, dp(4));
        iconPreview.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pickApp(); } });
        body.addView(iconPreview);

        etName = field(body, "名称");
        etPkg = field(body, "包名");
        etPkg.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence c, int a, int s2, int d2) { }
            public void onTextChanged(CharSequence c, int a, int s2, int d2) { }
            public void afterTextChanged(android.text.Editable e) {
                try {
                    Drawable d2 = getPackageManager().getApplicationIcon(e.toString());
                    int w = d2.getIntrinsicWidth() > 0 ? d2.getIntrinsicWidth() : 96;
                    android.graphics.Bitmap bm = android.graphics.Bitmap.createBitmap(w,
                        d2.getIntrinsicHeight() > 0 ? d2.getIntrinsicHeight() : 96, android.graphics.Bitmap.Config.ARGB_8888);
                    android.graphics.Canvas cv = new android.graphics.Canvas(bm);
                    d2.setBounds(0, 0, bm.getWidth(), bm.getHeight());
                    d2.draw(cv);
                    iconPreview.setBackgroundResource(0);
                    iconPreview.setText("");
                    android.widget.ImageView iv = new android.widget.ImageView(ShortcutEditActivity.this);
                    iv.setImageBitmap(bm);
                    iv.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                    body.removeView(iconPreview);
                    body.addView(iv, body.indexOfChild(etName));
                    iconPreview = null;
                    iv.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pickApp(); } });
                } catch (Throwable ignored) {}
            }
        });
        etCls = field(body, "活动");
        etData = field(body, "附加Data(Uri)");
        etExtra = field(body, "附加Extra(每行一个)");
        etExtra.setSingleLine(false);
        etExtra.setMinLines(2);
        etExtra.setGravity(Gravity.TOP);

        TextView at = new TextView(this);
        at.setText("附加Action");
        at.setTextColor(0xFF8A919E);
        at.setTextSize(13);
        at.setPadding(0, dp(8), 0, dp(2));
        body.addView(at);
        RadioGroup rg = new RadioGroup(this);
        rg.setOrientation(RadioGroup.HORIZONTAL);
        rbView = radio(rg, "VIEW", true);
        rbMain = radio(rg, "MAIN", false);
        rbCustom = radio(rg, "自定义", false);
        body.addView(rg);
        etCustom = field(body, "自定义Action");
        etCustom.setVisibility(View.GONE);
        rbCustom.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { etCustom.setVisibility(View.VISIBLE); } });
        rbView.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { etCustom.setVisibility(View.GONE); } });
        rbMain.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { etCustom.setVisibility(View.GONE); } });

        cbNewTask = check(body, "FLAG_ACTIVITY_NEW_TASK", true);
        cbRoot = check(body, "使用Root打开(仅识别包名和活动并忽略其他参数)", false);

        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);

        // 编辑模式：带 key 进来
        String key = getIntent().getStringExtra("key");
        if (key != null) {
            editKey = key;
            String[] it = ShortcutActivity.findByKey(this, key);
            if (it != null) {
                etName.setText(it[0]);
                etPkg.setText(it[1]);
                etCls.setText(it[2]);
                if (it.length > 3 && it[3].length() > 0) etData.setText(it[3]);
                if (it.length > 4 && it[4].length() > 0) etExtra.setText(it[4]);
                int am2 = it.length > 5 ? parseInt(it[5]) : 0;
                if (am2 == 1) { rbMain.setChecked(true); etCustom.setVisibility(View.GONE); }
                else if (am2 == 2) { rbCustom.setChecked(true); etCustom.setVisibility(View.VISIBLE);
                    if (it.length > 6) etCustom.setText(it[6]); }
                if (it.length > 7) cbNewTask.setChecked(!it[7].equals("0"));
                if (it.length > 8) cbRoot.setChecked(it[8].equals("1"));
            }
        }
    }

    private int parseInt(String s2) { try { return Integer.parseInt(s2); } catch (Throwable e) { return 0; } }

    private EditText field(LinearLayout parent, String hint) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setSingleLine(true);
        et.setTextColor(0xFFE8ECF2);
        et.setHintTextColor(0xFF5C6470);
        et.setTextSize(15);
        et.setBackground(null);
        et.setPadding(0, dp(10), 0, dp(10));
        parent.addView(et);
        View line = new View(this);
        line.setBackgroundColor(0xFF242B34);
        parent.addView(line, new LinearLayout.LayoutParams(-1, 1));
        return et;
    }

    private RadioButton radio(RadioGroup rg, String text, boolean checked) {
        RadioButton rb = new RadioButton(this);
        rb.setText(text);
        rb.setTextColor(0xFFE8ECF2);
        rb.setPadding(dp(6), dp(4), dp(12), dp(4));
        rb.setChecked(checked);
        rg.addView(rb);
        return rb;
    }

    private android.widget.CheckBox check(LinearLayout parent, String text, boolean checked) {
        android.widget.CheckBox cb = new android.widget.CheckBox(this);
        cb.setText(text);
        cb.setTextColor(0xFFB9C0CA);
        cb.setTextSize(13);
        cb.setChecked(checked);
        parent.addView(cb);
        return cb;
    }

    private Intent buildIntent(boolean forOpen) {
        String pkg = etPkg.getText().toString().trim();
        String cls = etCls.getText().toString().trim();
        String data = etData.getText().toString().trim();
        String custom = etCustom.getText().toString().trim();
        if (pkg.length() == 0) { toast("包名不能为空"); return null; }
        Intent i = new Intent();
        if (rbCustom.isChecked() && custom.length() > 0) i.setAction(custom);
        else if (rbMain.isChecked()) { i.setAction(Intent.ACTION_MAIN); i.addCategory(Intent.CATEGORY_LAUNCHER); }
        else i.setAction(Intent.ACTION_VIEW);
        if (data.length() > 0) { try { i.setData(Uri.parse(data)); } catch (Throwable t) { toast("Data格式错误"); return null; } }
        if (cls.length() > 0) i.setComponent(new android.content.ComponentName(pkg, cls));
        String[] lines = etExtra.getText().toString().split("\n");
        for (String ln : lines) {
            ln = ln.trim();
            int eq = ln.indexOf('=');
            if (eq <= 0) continue;
            String k = ln.substring(0, eq), v = ln.substring(eq + 1);
            try { if (v.matches("-?\\d+")) i.putExtra(k, Long.parseLong(v));
            else if (v.equals("true") || v.equals("false")) i.putExtra(k, Boolean.parseBoolean(v));
            else i.putExtra(k, v);
            } catch (Throwable t) { i.putExtra(k, v); }
        }
        if (cbNewTask.isChecked()) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }

    private void doOpen() {
        Intent i = buildIntent(true);
        if (i == null) return;
        try { startActivity(i); } catch (Throwable t) {
            if (cbRoot.isChecked()) rootOpen();
            else toast("打开失败: " + t.getClass().getSimpleName());
        }
    }

    private void rootOpen() {
        try {
            String cls = etCls.getText().toString().trim();
            String cmd = "am start -n " + etPkg.getText().toString().trim()
                + (cls.length() > 0 ? "/" + cls : "");
            Process p2 = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            if (p2.waitFor() == 0) toast("Root启动成功");
            else toast("Root启动失败");
        } catch (Throwable t) { toast("无Root权限"); }
    }

    private void doSave(boolean alsoPin) {
        String name = etName.getText().toString().trim();
        String pkg = etPkg.getText().toString().trim();
        if (name.length() == 0 || pkg.length() == 0) { toast("名称和包名不能为空"); return; }
        String rec = joinFields();
        String key = editKey != null ? editKey : ("s" + System.currentTimeMillis());
        ShortcutActivity.saveByKey(this, key, rec);
        if (alsoPin) {
            Intent i = buildIntent(false);
            if (i != null) {
                try {
                    android.content.pm.ShortcutManager sm = (android.content.pm.ShortcutManager) getSystemService(SHORTCUT_SERVICE);
                    if (sm != null && sm.isRequestPinShortcutSupported()) {
                        android.content.pm.ShortcutInfo si = new android.content.pm.ShortcutInfo.Builder(this, key)
                            .setShortLabel(name).setLongLabel(name).setIntent(i).build();
                        sm.requestPinShortcut(si, null);
                    } else toast("桌面不支持固定快捷方式");
                } catch (Throwable t) { toast("固定失败: " + t.getClass().getSimpleName()); }
            }
        }
        setResult(RESULT_OK);
        finish();
    }

    private void doCreate() { doSave(true); }

    private String joinFields() {
        String am2 = rbMain.isChecked() ? "1" : (rbCustom.isChecked() ? "2" : "0");
        String cu = etCustom.getText().toString().trim();
        return etName.getText().toString().trim() + "\u0001"
            + etPkg.getText().toString().trim() + "\u0001"
            + etCls.getText().toString().trim() + "\u0001"
            + etData.getText().toString().trim() + "\u0001"
            + etExtra.getText().toString().trim() + "\u0001"
            + am2 + "\u0001" + cu + "\u0001"
            + (cbNewTask.isChecked() ? "1" : "0") + "\u0001"
            + (cbRoot.isChecked() ? "1" : "0");
    }

    private void toast(String s2) { Toast.makeText(this, s2, Toast.LENGTH_SHORT).show(); }

    private void pickApp() {
        try {
            android.content.pm.PackageManager pm = getPackageManager();
            Intent li = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            java.util.List<android.content.pm.ResolveInfo> apps = pm.queryIntentActivities(li, 0);
            java.util.Collections.sort(apps, new java.util.Comparator<android.content.pm.ResolveInfo>() {
                public int compare(android.content.pm.ResolveInfo a, android.content.pm.ResolveInfo b) {
                    return String.valueOf(a.loadLabel(pm)).compareToIgnoreCase(String.valueOf(b.loadLabel(pm)));
                }
            });
            final android.content.pm.ResolveInfo[] arr = apps.toArray(new android.content.pm.ResolveInfo[0]);
            String[] names = new String[arr.length];
            for (int i2 = 0; i2 < arr.length; i2++)
                names[i2] = arr[i2].loadLabel(pm) + " (" + arr[i2].activityInfo.packageName + ")";
            new android.app.AlertDialog.Builder(this)
                .setTitle("选择应用")
                .setItems(names, new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        etPkg.setText(arr[w].activityInfo.packageName);
                        etCls.setText(arr[w].activityInfo.name);
                        if (etName.getText().length() == 0) etName.setText(String.valueOf(arr[w].loadLabel(pm)));
                    }
                }).show();
        } catch (Throwable e) { toast("获取应用列表失败"); }
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}
