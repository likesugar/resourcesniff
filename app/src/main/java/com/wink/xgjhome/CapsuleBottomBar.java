package com.wink.xgjhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** SmoothBottomBar 风格底部迷你胶囊导航（2项：首页/播放） */
public class CapsuleBottomBar extends FrameLayout {

    public interface OnItem { void onItem(int index); }

    private final View pill;
    private final TextView[] items = new TextView[4];
    private final boolean dark;
    private final int itemW;
    private int active = 0;
    private OnItem cb;

    public CapsuleBottomBar(Context c, boolean darkMode, OnItem callback) {
        super(c);
        dark = darkMode;
        cb = callback;
        itemW = dp(64);

        int barBg = dark ? 0xF01A1E28 : 0xFFE1ECFF;
        int pillBg = dark ? 0xFF2A3242 : 0xFFFFFFFF;
        int activeTx = dark ? 0xFFB4C5FF : 0xFF315CDE;
        int inactiveTx = dark ? 0xFF8A94A6 : 0xFF7C8694;

        FrameLayout capsule = new FrameLayout(c);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(17));
        bg.setColor(barBg);
        capsule.setBackground(bg);
        capsule.setElevation(dp(8));
        LayoutParams clp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        clp.bottomMargin = dp(12);
        addView(capsule, clp);

        pill = new View(c);
        GradientDrawable pg = new GradientDrawable();
        pg.setCornerRadius(dp(14));
        pg.setColor(pillBg);
        pill.setBackground(pg);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(itemW, dp(26),
                Gravity.BOTTOM | Gravity.START);
        plp.leftMargin = dp(3); plp.bottomMargin = dp(3);
        capsule.addView(pill, plp);

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        capsule.addView(row, new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        String[] labels = {"🏠 首页", "🎬 媒体", "⚙️ 设置", "📡 播放器"};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            TextView t = new TextView(c);
            t.setText(labels[i]);
            t.setTextSize(11);
            t.setGravity(Gravity.CENTER);
            t.setTextColor(i == 0 ? activeTx : inactiveTx);
            row.addView(t, new LinearLayout.LayoutParams(itemW, dp(26)));
            items[i] = t;
            t.setOnClickListener(new OnClickListener() {
                public void onClick(View v) { select(idx, true); }
            });
        }
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        if (ev.getAction() == android.view.MotionEvent.ACTION_DOWN) { downX = ev.getX(); swiped = false; }
        else if (ev.getAction() == android.view.MotionEvent.ACTION_MOVE) {
            float dx = ev.getX() - downX;
            if (!swiped && Math.abs(dx) > dp(50)) {
                swiped = true;
                int target = dx < 0 ? Math.min(active + 1, 1) : Math.max(active - 1, 0);
                select(target, true);
            }
        }
        return super.dispatchTouchEvent(ev);
    }
    private float downX; private boolean swiped;

    /** 外部同步选中态(不触发回调) */
    public void setActive(int idx) { if (idx != active) select(idx, false); }

    public void select(int idx, boolean fire) {
        if (idx == active) { pill.setTranslationX(idx * itemW); if (fire && cb != null) cb.onItem(idx); return; }
        int old = active;
        active = idx;
        int activeTx = dark ? 0xFFB4C5FF : 0xFF315CDE;
        int inactiveTx = dark ? 0xFF8A94A6 : 0xFF7C8694;
        items[old].setTextColor(inactiveTx);
        items[active].setTextColor(activeTx);
        ValueAnimator va = ValueAnimator.ofFloat(old * itemW, active * itemW);
        va.setDuration(240);
        va.setInterpolator(new OvershootInterpolator(0.7f));
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) { pill.setTranslationX((Float) a.getAnimatedValue()); }
        });
        va.start();
        if (fire && cb != null) cb.onItem(idx);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
