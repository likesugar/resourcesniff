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
 * 底部胶囊导航（全新重写）
 * 四项：首页 / 媒体 / 设置 / 播放器
 * - 点按任意项切换
 * - 胶囊上横向滑动切换（始终向手指方向走，循环）
 * - 滑块平滑跟随
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
        itemW = dp(64);

        int barBg = dark ? 0xF01A1E28 : 0xFFE1ECFF;
        int pillBg = dark ? 0xFF2A3242 : 0xFFFFFFFF;
        int activeTx = dark ? 0xFFB4C5FF : 0xFF315CDE;
        int inactiveTx = dark ? 0xFF8A94A6 : 0xFF7C8694;

        // 外层胶囊体（FrameLayout 叠层: pill 在下, 标签行在上）
        FrameLayout capsule = new FrameLayout(c);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(17));
        bg.setColor(barBg);
        capsule.setBackground(bg);
        capsule.setElevation(dp(8));
        addView(capsule, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));

        // 滑块（选中底衬, 底部左起, 随选中平移）
        pill = new View(c);
        GradientDrawable pg = new GradientDrawable();
        pg.setCornerRadius(dp(14));
        pg.setColor(pillBg);
        pill.setBackground(pg);
        LayoutParams plp = new LayoutParams(itemW, dp(26), Gravity.BOTTOM | Gravity.START);
        plp.leftMargin = dp(3);
        plp.bottomMargin = dp(3);
        capsule.addView(pill, plp);

        // 四个标签
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        capsule.addView(row, new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        for (int i = 0; i < ITEM_COUNT; i++) {
            final int idx = i;
            TextView t = new TextView(c);
            t.setText(LABELS[i]);
            t.setTextSize(11);
            t.setGravity(Gravity.CENTER);
            t.setTextColor(i == 0 ? activeTx : inactiveTx);
            t.setClickable(true);
            t.setOnClickListener(new OnClickListener() {
                public void onClick(View v) { select(idx, true); }
            });
            row.addView(t, new LinearLayout.LayoutParams(itemW, dp(26)));
            items[i] = t;
        }
    }

    /** 点击/滑动选中 */
    public void select(int idx, boolean fire) {
        if (idx < 0 || idx >= ITEM_COUNT) return;
        if (idx == active) {
            pill.setTranslationX(idx * itemW);
            if (fire && cb != null) cb.onItem(idx);
            return;
        }
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

    /** 外部同步选中态（不触发回调） */
    public void setActive(int idx) {
        if (idx != active) select(idx, false);
        else pill.setTranslationX(idx * itemW);
    }

    /** 胶囊上横向滑动：一次滑动走一步，方向循环 */
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
                    // 滑动循环: 首页↔媒体↔设置 (播放器仅点按)
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
