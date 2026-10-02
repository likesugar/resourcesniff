package com.wink.xgjhome;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** 应用选择页（图1式）：图标+名称+包名+活动列表按钮，可开关系统应用 */
public class AppPickerActivity extends Activity {

    private LinearLayout list;
    private TextView tvCount, tvToggle;
    private java.util.List<android.content.pm.PackageInfo> all = new java.util.ArrayList<android.content.pm.PackageInfo>();
    private java.util.List<android.content.pm.PackageInfo> shown = new java.util.ArrayList<android.content.pm.PackageInfo>();
    private boolean showSystem = false;
    private android.content.pm.PackageManager pm;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        pm = getPackageManager();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0B0D10);

        // 顶栏：标题+数量 | 系统应用开关
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout tcol = new LinearLayout(this);
        tcol.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText("应用选择");
        title.setTextColor(0xFFE8ECF2);
        title.setTextSize(18);
        title.getPaint().setFakeBoldText(true);
        tcol.addView(title);
        tvCount = new TextView(this);
        tvCount.setTextColor(0xFF8A919E);
        tvCount.setTextSize(13);
        tcol.addView(tvCount);
        top.addView(tcol, new LinearLayout.LayoutParams(0, -2, 1f));
        tvToggle = new TextView(this);
        tvToggle.setTextSize(13);
        tvToggle.setPadding(dp(12), dp(8), dp(12), dp(8));
        tvToggle.setGravity(Gravity.CENTER);
        tvToggle.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            showSystem = !showSystem;
            applyFilter();
        }});
        top.addView(tvToggle);
        root.addView(top);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(10), dp(4), dp(10), dp(16));
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);

        loadApps();
        applyFilter();
    }

    private void loadApps() {
        try { all = pm.getInstalledPackages(0); } catch (Throwable e) { all = new java.util.ArrayList<android.content.pm.PackageInfo>(); }
        java.util.Collections.sort(all, new java.util.Comparator<android.content.pm.PackageInfo>() {
            public int compare(android.content.pm.PackageInfo a, android.content.pm.PackageInfo b) {
                return labelOf(a).compareToIgnoreCase(labelOf(b));
            }
        });
    }

    private void applyFilter() {
        shown.clear();
        for (android.content.pm.PackageInfo pi : all) {
            if (!showSystem && (pi.applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            shown.add(pi);
        }
        tvCount.setText("共" + shown.size() + "个（系统应用" + (showSystem ? "已显示" : "已隐藏") + "）");
        tvToggle.setText(showSystem ? "隐藏系统应用" : "显示系统应用");
        tvToggle.setTextColor(showSystem ? 0xFF3D7BFF : 0xFF8A919E);
        tvToggle.setBackgroundColor(0xFF1B222B);
        renderList();
    }

    private void renderList() {
        list.removeAllViews();
        for (final android.content.pm.PackageInfo pi : shown) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundColor(0xFF161A20);
            int pad = dp(10);
            row.setPadding(pad, pad, pad, pad);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
            rlp.topMargin = dp(8);
            row.setLayoutParams(rlp);

            ImageView ic = new ImageView(this);
            ic.setScaleType(ImageView.ScaleType.FIT_CENTER);
            ic.setImageBitmap(iconToBmp(pi));
            row.addView(ic, new LinearLayout.LayoutParams(dp(46), dp(46)));

            LinearLayout mid = new LinearLayout(this);
            mid.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0, -2, 1f);
            mlp.leftMargin = dp(12);
            mid.setLayoutParams(mlp);
            TextView nm = new TextView(this);
            nm.setText(labelOf(pi));
            nm.setTextColor(0xFFE8ECF2);
            nm.setTextSize(15);
            nm.getPaint().setFakeBoldText(true);
            mid.addView(nm);
            TextView pkg = new TextView(this);
            pkg.setText(pi.packageName);
            pkg.setTextColor(0xFF8A919E);
            pkg.setTextSize(12);
            mid.addView(pkg);
            row.addView(mid);

            TextView actsBtn = new TextView(this);
            actsBtn.setText("活动列表");
            actsBtn.setTextColor(0xFFCFE3F5);
            actsBtn.setTextSize(13);
            actsBtn.setGravity(Gravity.CENTER);
            actsBtn.setBackgroundColor(0xFF33627E);
            actsBtn.setPadding(dp(12), dp(8), dp(12), dp(8));
            actsBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showActivities(pi); } });
            row.addView(actsBtn, new LinearLayout.LayoutParams(-2, -2));

            // 点行 = 直达入口活动
            row.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                Intent li = pm.getLaunchIntentForPackage(pi.packageName);
                if (li != null && li.getComponent() != null) {
                    finishWith(pi.packageName, li.getComponent().getClassName());
                } else {
                    showActivities(pi);
                }
            }});
            list.addView(row);
        }
    }

    private void showActivities(final android.content.pm.PackageInfo pi) {
        try {
            android.content.pm.ActivityInfo[] acts = pm.getPackageInfo(pi.packageName, android.content.pm.PackageManager.GET_ACTIVITIES).activities;
            if (acts == null || acts.length == 0) { toast("该包没有可列出的活动"); return; }
            String[] names = new String[acts.length];
            for (int i = 0; i < acts.length; i++) {
                String nm = acts[i].name;
                int dot = nm.lastIndexOf('.');
                String short2 = dot >= 0 && dot < nm.length() - 1 ? nm.substring(dot + 1) : nm;
                names[i] = short2 + (acts[i].exported ? "  [可直启]" : "  [需Root]");
            }
            new android.app.AlertDialog.Builder(this)
                .setTitle(pi.packageName + " 的 " + acts.length + " 个活动")
                .setItems(names, new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        finishWith(pi.packageName, acts[w].name);
                    }
                }).show();
        } catch (Throwable e) { toast("读取活动失败"); }
    }

    private void finishWith(String pkg, String cls) {
        Intent out = new Intent();
        out.putExtra("pkg", pkg);
        out.putExtra("cls", cls);
        out.putExtra("name", labelOf(pkg.equals("") ? null : pkgInfoByPkg(pkg)));
        setResult(RESULT_OK, out);
        finish();
    }

    private android.content.pm.PackageInfo pkgInfoByPkg(String pkg) {
        for (android.content.pm.PackageInfo pi : all) if (pi.packageName.equals(pkg)) return pi;
        return null;
    }

    private String labelOf(android.content.pm.PackageInfo pi) {
        try { return String.valueOf(pi.applicationInfo.loadLabel(pm)); }
        catch (Throwable e) { return pi.packageName; }
    }

    private Bitmap iconToBmp(android.content.pm.PackageInfo pi) {
        try {
            android.graphics.drawable.Drawable d = pi.applicationInfo.loadIcon(pm);
            int w = d.getIntrinsicWidth() > 0 ? d.getIntrinsicWidth() : 96;
            int h = d.getIntrinsicHeight() > 0 ? d.getIntrinsicHeight() : 96;
            Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(bm);
            d.setBounds(0, 0, w, h);
            d.draw(cv);
            return bm;
        } catch (Throwable e) {
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        }
    }

    private void toast(String s2) { Toast.makeText(this, s2, Toast.LENGTH_SHORT).show(); }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}
