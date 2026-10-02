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
    private TextView iconHint;
    private android.widget.ImageView iconView;
    private android.graphics.Bitmap curIcon = null;   // 用户自选 > 应用图标
    private String editKey = null;
    private String iconPath = "";

    private void pickSystemIcon() {
        try {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("image/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(i, "选择图标"), 7001);
        } catch (Throwable t) { toast("无法打开系统选择器"); }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 7001 && res == RESULT_OK && data != null && data.getData() != null) {
            try {
                android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                java.io.InputStream is = getContentResolver().openInputStream(data.getData());
                android.graphics.BitmapFactory.decodeStream(is, null, o);
                try { is.close(); } catch (Throwable ignored) {}
                int size = Math.max(o.outWidth, o.outHeight);
                int sample = 1;
                while (size / sample > 192) sample *= 2;
                o = new android.graphics.BitmapFactory.Options();
                o.inSampleSize = sample;
                is = getContentResolver().openInputStream(data.getData());
                android.graphics.Bitmap bm = android.graphics.BitmapFactory.decodeStream(is, null, o);
                try { is.close(); } catch (Throwable ignored) {}
                if (bm != null) {
                    curIcon = bm;
                    iconView.setImageBitmap(bm);
                    iconHint.setText("已使用自选图标");
                }
            } catch (Throwable t) { toast("读取图片失败"); }
        }
    }

    private android.graphics.Bitmap iconToBitmap(Drawable d) {
        int w = d.getIntrinsicWidth() > 0 ? d.getIntrinsicWidth() : 96;
        int h = d.getIntrinsicHeight() > 0 ? d.getIntrinsicHeight() : 96;
        android.graphics.Bitmap bm = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas cv = new android.graphics.Canvas(bm);
        d.setBounds(0, 0, w, h);
        d.draw(cv);
        return bm;
    }

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
            bt.setBackgroundColor(i == 3 ? 0xFF245C8D : 0xFF242B34);
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

        iconHint = new TextView(this);
        iconHint.setText("快捷方式的图标(点击从系统选择)");
        iconHint.setTextColor(0xFF8A919E);
        iconHint.setTextSize(13);
        iconHint.setGravity(Gravity.CENTER);
        iconHint.setPadding(0, dp(4), 0, dp(4));
        body.addView(iconHint);
        iconView = new android.widget.ImageView(this);
        iconView.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams ivp = new LinearLayout.LayoutParams(dp(64), dp(64));
        ivp.gravity = Gravity.CENTER_HORIZONTAL;
        iconView.setLayoutParams(ivp);
        iconView.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pickSystemIcon(); } });
        body.addView(iconView);
        TextView pickAppBtn = new TextView(this);
        pickAppBtn.setText("选择应用");
        pickAppBtn.setTextColor(0xFF3D7BFF);
        pickAppBtn.setTextSize(14);
        pickAppBtn.setGravity(Gravity.CENTER);
        pickAppBtn.setBackgroundColor(0xFF1B222B);
        pickAppBtn.setPadding(0, dp(8), 0, dp(8));
        LinearLayout.LayoutParams pap = new LinearLayout.LayoutParams(-1, -2);
        pap.topMargin = dp(8);
        pickAppBtn.setLayoutParams(pap);
        pickAppBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pickApp(); } });
        body.addView(pickAppBtn);
        if (curIcon != null) iconView.setImageBitmap(curIcon);

        etName = field(body, "名称");
        etPkg = field(body, "包名");
        etPkg.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence c, int a, int s2, int d2) { }
            public void onTextChanged(CharSequence c, int a, int s2, int d2) { }
            public void afterTextChanged(android.text.Editable e) {
                if (curIcon != null) return;  // 用户自选图标优先
                try {
                    curIcon = iconToBitmap(getPackageManager().getApplicationIcon(e.toString()));
                    iconView.setImageBitmap(curIcon);
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
        String[] it = null;
        if (getIntent().hasExtra("e_name")) {
            // 直接用卡片传来的现值（老条目无镜像也能回显）
            it = new String[]{
                getIntent().getStringExtra("e_name"), getIntent().getStringExtra("e_pkg"),
                getIntent().getStringExtra("e_cls"), getIntent().getStringExtra("e_data"),
                getIntent().getStringExtra("e_extra"), getIntent().getStringExtra("e_am"),
                getIntent().getStringExtra("e_custom"), getIntent().getStringExtra("e_newtask"),
                getIntent().getStringExtra("e_root"),
                getIntent().hasExtra("e_icon") ? getIntent().getStringExtra("e_icon") : "",
                getIntent().hasExtra("e_icon") ? getIntent().getStringExtra("e_icon") : ""};
        } else if (key != null) {
            it = ShortcutActivity.findByKey(this, key);
        }
        if (it != null) {
            editKey = key;
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
            if (it.length > 10 && it[10].length() > 0) {
                iconPath = it[10];
            } else {
                // 老条目兜底：取最近保存的自选图标文件
                try {
                    java.io.File idir = new java.io.File(getFilesDir(), "sc_icons");
                    java.io.File[] fs = idir.listFiles();
                    if (fs != null && fs.length > 0) {
                        java.io.File newest = fs[0];
                        for (java.io.File f : fs) if (f.lastModified() > newest.lastModified()) newest = f;
                        iconPath = newest.getAbsolutePath();
                    }
                } catch (Throwable ignored) {}
            }
            if (iconPath.length() > 0) {
                try {
                    android.graphics.Bitmap bm2 = android.graphics.BitmapFactory.decodeFile(iconPath);
                    if (bm2 != null) { curIcon = bm2; iconView.setImageBitmap(bm2); iconHint.setText("已使用自选图标"); }
                } catch (Throwable ignored) {}
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
        if (curIcon == null) {
            // 兜底：自动恢复最近自选图标
            try {
                java.io.File idir = new java.io.File(getFilesDir(), "sc_icons");
                java.io.File[] fs = idir.listFiles();
                if (fs != null && fs.length > 0) {
                    java.io.File newest = fs[0];
                    for (java.io.File f : fs) if (f.lastModified() > newest.lastModified()) newest = f;
                    android.graphics.Bitmap bm2 = android.graphics.BitmapFactory.decodeFile(newest.getAbsolutePath());
                    if (bm2 != null) { curIcon = bm2; iconPath = newest.getAbsolutePath(); }
                }
            } catch (Throwable ignored) {}
        }
        String rec = joinFields();
        String key = editKey != null ? editKey : ("s" + System.currentTimeMillis());
        if (curIcon != null) {
            try {
                java.io.File idir = new java.io.File(getFilesDir(), "sc_icons");
                idir.mkdirs();
                java.io.File ifile = new java.io.File(idir, key + ".png");
                java.io.FileOutputStream fo = new java.io.FileOutputStream(ifile);
                curIcon.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, fo);
                fo.close();
                iconPath = ifile.getAbsolutePath();
                rec = joinFields();  // 重新生成含路径的记录
            } catch (Throwable ignored) {}
        }
        ShortcutActivity.saveByKey(this, key, rec);
        try { ShortcutActivity.dumpFc2Debug("SAVE key=" + key + " iconPath=[" + iconPath + "] curIcon=" + (curIcon != null)); } catch (Throwable ignored) {}
        if (alsoPin) {
            Intent i = buildIntent(false);
            if (i != null) {
                try {
                    android.content.pm.ShortcutManager sm = (android.content.pm.ShortcutManager) getSystemService(SHORTCUT_SERVICE);
                    if (sm != null && sm.isRequestPinShortcutSupported()) {
                        android.content.pm.ShortcutInfo.Builder sb2 = new android.content.pm.ShortcutInfo.Builder(this, key)
                            .setShortLabel(name).setLongLabel(name).setIntent(i);
                        if (curIcon != null) sb2.setIcon(android.graphics.drawable.Icon.createWithBitmap(curIcon));
                        android.content.pm.ShortcutInfo si = sb2.build();
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
            + (cbRoot.isChecked() ? "1" : "0") + "\u0001"
            + iconPath;
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
