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
 * 底部胶囊导航（简洁重写版）
 * 四项：首页 / 媒体 / 设置 / 播放器 —— 纯点按，无手势检测
 * 外部通过 setActive(int) 同步选中态（不触发回调）
 */
public class CapsuleBottomBar extends FrameLayout {

    public interface OnItem { void onItem(int index); }

    private static final int COUNT = 4;
    private static final String[] LABELS = {"🏠 首页", "🎬 媒体", "⚙️ 设置", "📡 播放器"};

    private final TextView[] items = new TextView[COUNT];
    private final View pill;
    private final int itemW;
    private final boolean dark;
    private final OnItem cb;
    private int active = 0;

    public CapsuleBottomBar(Context c, boolean darkMode, OnItem callback) {
        super(c);
        dark = darkMode;
        cb = callback;
        itemW = dp(72);

        int barBg = dark ? 0xF51A1E28 : 0xFFE6EFFF;
        int pillBg = dark ? 0xFF3D6DFF : 0xFF1677FF;
        int onPill = 0xFFFFFFFF;
        int offText = dark ? 0xFF8A94A6 : 0xFF7C8694;

        // 胶囊体
        FrameLayout capsule = new FrameLayout(c);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(19));
        bg.setColor(barBg);
        capsule.setBackground(bg);
        capsule.setElevation(dp(10));
        addView(capsule, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));

        // 滑块（不设 elevation，保证在文字下层）
        pill = new View(c);
        GradientDrawable pg = new GradientDrawable();
        pg.setCornerRadius(dp(13));
        pg.setColor(pillBg);
        pill.setBackground(pg);
        LayoutParams plp = new LayoutParams(itemW - dp(8), dp(30), Gravity.CENTER_VERTICAL | Gravity.START);
        plp.leftMargin = dp(4);
        capsule.addView(pill, plp);

        // 标签行
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        capsule.addView(row, new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        for (int i = 0; i < COUNT; i++) {
            final int idx = i;
            TextView t = new TextView(c);
            t.setText(LABELS[i]);
            t.setTextSize(11.5f);
            t.setGravity(Gravity.CENTER);
            t.setTypeface(i == 0 ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);
            t.setTextColor(i == 0 ? onPill : offText);
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { select(idx, true); }
            });
            row.addView(t, new LinearLayout.LayoutParams(itemW, dp(36)));
            items[i] = t;
        }
    }

    /** 点按切换 */
    private void select(int idx, boolean fire) {
        if (idx == active) {
            if (fire && cb != null) cb.onItem(idx);
            return;
        }
        int old = active;
        active = idx;
        int onPill = 0xFFFFFFFF;
        int offText = dark ? 0xFF8A94A6 : 0xFF7C8694;
        items[old].setTextColor(offText);
        items[old].setTypeface(android.graphics.Typeface.DEFAULT);
        items[active].setTextColor(onPill);
        items[active].setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
        ValueAnimator va = ValueAnimator.ofFloat(old * itemW, active * itemW);
        va.setDuration(240);
        va.setInterpolator(new OvershootInterpolator(0.8f));
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) { pill.setTranslationX((Float) a.getAnimatedValue()); }
        });
        va.start();
        if (fire && cb != null) cb.onItem(idx);
    }

    /** 外部同步选中态（不触发回调、不弹动画错位） */
    public void setActive(int idx) {
        if (idx < 0 || idx >= COUNT || idx == active) return;
        select(idx, false);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
