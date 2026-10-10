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

/**
 * 底部胶囊导航（美化版）
 * - 品牌蓝渐变滑块，选中项加粗白字，切换带弹性动画与触感反馈
 * - 点按四项直达；胶囊上横滑在 首页↔媒体↔设置 间循环（播放器仅点按）
 */
public class CapsuleBottomBar extends FrameLayout {

    public interface OnItem { void onItem(int index); }

    private static final int ITEM_COUNT = 4;
    private static final String[] LABELS = {"🏠 首页", "🎬 媒体", "⚙️ 设置", "📡 播放器"};

    private final TextView[] items = new TextView[ITEM_COUNT];
    private final View pill;
    private final int itemW;
    private final boolean dark;
    private final OnItem cb;
    private int active = 0;

    // 滑动检测
    private float downX, downY;
    private boolean swiping;

    public CapsuleBottomBar(Context c, boolean darkMode, OnItem callback) {
        super(c);
        dark = darkMode;
        cb = callback;
        itemW = dp(72);

        int barBg = dark ? 0xF51A1E28 : 0xFFE6EFFF;
        int activeTx = dark ? 0xFFFFFFFF : 0xFFFFFFFF;
        int inactiveTx = dark ? 0xFF8A94A6 : 0xFF7C8694;

        // 外层胶囊体（FrameLayout 叠层：滑块在下，标签在上）
        FrameLayout capsule = new FrameLayout(c);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(19));
        bg.setColor(barBg);
        bg.setShape(GradientDrawable.RECTANGLE);
        capsule.setBackground(bg);
        capsule.setElevation(dp(10));
        addView(capsule, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));

        // 滑块：品牌蓝渐变圆角条
        pill = new View(c);
        GradientDrawable pg = new GradientDrawable();
        pg.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
        pg.setColors(new int[]{dark ? 0xFF1677FF : 0xFF1677FF, dark ? 0xFF4C9AFF : 0xFF4C9AFF});
        pg.setCornerRadius(dp(15));
        pill.setBackground(pg);
        pill.setElevation(dp(4));
        LayoutParams plp = new LayoutParams(itemW - dp(6), dp(30), Gravity.CENTER_VERTICAL | Gravity.START);
        plp.leftMargin = dp(3);
        capsule.addView(pill, plp);

        // 四个标签
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        capsule.addView(row, new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        for (int i = 0; i < ITEM_COUNT; i++) {
            final int idx = i;
            TextView t = new TextView(c);
            t.setText(LABELS[i]);
            t.setTextSize(11.5f);
            t.setGravity(Gravity.CENTER);
            t.setTypeface(i == 0 ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);
            t.setTextColor(i == 0 ? activeTx : inactiveTx);
            row.addView(t, new LinearLayout.LayoutParams(itemW, dp(36)));
            items[i] = t;
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { select(idx, true); }
            });
        }
    }

    /** 外部同步选中态（不触发回调） */
    public void setActive(int idx) { if (idx != active) select(idx, false); }

    public void select(int idx, boolean fire) {
        if (idx == active) {
            pill.setTranslationX(idx * itemW);
            if (fire && cb != null) cb.onItem(idx);
            return;
        }
        int old = active;
        active = idx;
        int inactiveTx = dark ? 0xFF8A94A6 : 0xFF7C8694;
        items[old].setTextColor(inactiveTx);
        items[old].setTypeface(android.graphics.Typeface.DEFAULT);
        items[active].setTextColor(0xFFFFFFFF);
        items[active].setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
        ValueAnimator va = ValueAnimator.ofFloat(old * itemW, active * itemW);
        va.setDuration(260);
        va.setInterpolator(new OvershootInterpolator(0.8f));
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) { pill.setTranslationX((Float) a.getAnimatedValue()); }
        });
        va.start();
        if (fire && cb != null) cb.onItem(idx);
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case android.view.MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                swiping = false;
                break;
            case android.view.MotionEvent.ACTION_MOVE: {
                float dx = ev.getX() - downX;
                float dy = ev.getY() - downY;
                if (!swiping && Math.abs(dx) > dp(40) && Math.abs(dx) > Math.abs(dy)) {
                    swiping = true;
                    // 滑动循环：首页↔媒体↔设置（播放器仅点按）
                    int target = dx < 0 ? (active + 1) % 3 : (active - 1 + 3) % 3;
                    if (target != active) select(target, true);
                }
                break;
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
