package com.wink.xgjhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** SmoothBottomBar 风格底部胶囊导航（胶囊指示器平滑滑动，2项：首页/播放器） */
public class CapsuleBottomBar extends FrameLayout {

    public interface OnItem { void onItem(int index); }

    private final LinearLayout row;
    private final View pill;
    private final TextView[] items = new TextView[2];
    private int active = 0;
    private OnItem callback;
    private final boolean dark;

    public CapsuleBottomBar(Context c, boolean darkMode, final OnItem cb) {
        super(c);
        dark = darkMode;
        callback = cb;

        int barBg = dark ? 0xE61A2130 : 0xE8FFFFFF;
        int pillBg = dark ? 0xFF2A3242 : 0xFFE7EDFB;
        int activeTx = dark ? 0xFFB4C5FF : 0xFF315CDE;
        int inactiveTx = dark ? 0xFF8A94A6 : 0xFF7C8694;

        // 外层胶囊容器
        LinearLayout capsule = new LinearLayout(c);
        capsule.setOrientation(LinearLayout.HORIZONTAL);
        capsule.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(26));
        bg.setColor(barBg);
        if (!dark) bg.setStroke(dp(1), 0xFFE4EAF5);
        capsule.setBackground(bg);
        capsule.setElevation(dp(8));
        LayoutParams clp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        clp.bottomMargin = dp(18);
        addView(capsule, clp);

        // 滑动胶囊指示器
        pill = new View(c);
        GradientDrawable pg = new GradientDrawable();
        pg.setCornerRadius(dp(22));
        pg.setColor(pillBg);
        pill.setBackground(pg);
        row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        LayoutParams rlp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        row.setClipChildren(false);
        capsule.addView(row, rlp);
        // pill 铺在 row 底下
        row.addView(pill, 0, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        String[] labels = {"🏠 首页", "📡 播放器"};
        for (int i = 0; i < 2; i++) {
            final int idx = i;
            TextView t = new TextView(c);
            t.setText(labels[i]);
            t.setTextSize(14);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(26), dp(10), dp(26), dp(10));
            t.setTextColor(i == active ? activeTx : inactiveTx);
            row.addView(t, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT));
            items[i] = t;
            t.setOnClickListener(new OnClickListener() {
                public void onClick(View v) { select(idx, true); }
            });
        }
        row.post(new Runnable() { public void run() { layoutPill(false); } });
    }

    private void layoutPill(boolean animate) {
        TextView t = items[active];
        if (t == null) return;
        float targetX = t.getX();
        int targetW = t.getWidth();
        if (targetW <= 0) { row.postDelayed(new Runnable() { public void run() { layoutPill(false); } }, 60); return; }
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) pill.getLayoutParams();
        if (animate) {
            ValueAnimator va = ValueAnimator.ofFloat(pill.getX(), targetX);
            va.setDuration(260);
            va.setInterpolator(new OvershootInterpolator(0.8f));
            va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator a) {
                    pill.setTranslationX((Float) a.getAnimatedValue());
                }
            });
            va.start();
            ValueAnimator wa = ValueAnimator.ofInt(pill.getWidth(), targetW);
            wa.setDuration(260);
            wa.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator a) {
                    LinearLayout.LayoutParams lp2 = (LinearLayout.LayoutParams) pill.getLayoutParams();
                    lp2.weight = 0; lp2.width = (Integer) a.getAnimatedValue();
                    pill.setLayoutParams(lp2);
                }
            });
            wa.start();
        } else {
            lp.weight = 0; lp.width = targetW;
            pill.setLayoutParams(lp);
            pill.setTranslationX(targetX);
        }
    }

    public void select(int idx, boolean fire) {
        if (idx == active && fire) { if (callback != null) callback.onItem(idx); return; }
        int old = active;
        active = idx;
        int darkActiveTx = dark ? 0xFFB4C5FF : 0xFF315CDE;
        int inactiveTx = dark ? 0xFF8A94A6 : 0xFF7C8694;
        items[old].setTextColor(inactiveTx);
        items[active].setTextColor(darkActiveTx);
        layoutPill(true);
        if (fire && callback != null) callback.onItem(idx);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
