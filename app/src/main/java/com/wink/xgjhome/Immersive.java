package com.wink.xgjhome;

import android.app.Activity;
import android.view.View;

/** 全局隐藏底部导航栏(沉浸式STICKY, 上滑临时呼出自动回落) */
public class Immersive {
    public static void hide(Activity a) {
        try {
            View d = a.getWindow().getDecorView();
            d.setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        } catch (Throwable ignored) {}
    }
}
