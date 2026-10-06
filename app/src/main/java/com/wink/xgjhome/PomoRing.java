package com.wink.xgjhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** 萌系圆环进度: 粉色渐变圆环 + 圆头 */
public class PomoRing extends View {
    private float progress = 1f; // 0..1 remaining fraction
    private String centerText = "90:00";
    private String emoji = "🍅";

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();

    public PomoRing(Context c) {
        super(c);
        track.setStyle(Paint.Style.STROKE);
        track.setColor(0xFFF6E3EA);
        track.setStrokeCap(Paint.Cap.ROUND);
        arc.setStyle(Paint.Style.STROKE);
        arc.setColor(0xFFFF8FAB);
        arc.setStrokeCap(Paint.Cap.ROUND);
        txt.setColor(0xFF3D2B33);
        txt.setTextAlign(Paint.Align.CENTER);
        txt.setFakeBoldText(true);
        emo.setTextAlign(Paint.Align.CENTER);
    }

    public void set(float p, String t, String e) {
        progress = p; centerText = t; emoji = e;
        invalidate();
    }

    @Override protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        float stroke = w * 0.075f;
        track.setStrokeWidth(stroke);
        arc.setStrokeWidth(stroke);
        float half = stroke / 2 + dp(4);
        box.set(half, half, w - half, h - half);
        cv.drawArc(box, 0, 360, false, track);
        cv.drawArc(box, -90, -360 * Math.max(0.001f, progress), false, arc);
        txt.setTextSize(w * 0.17f);
        cv.drawText(centerText, w / 2, h / 2 + w * 0.045f, txt);
        emo.setTextSize(w * 0.085f);
        cv.drawText(emoji, w / 2, h / 2 - w * 0.12f, emo);
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
}
