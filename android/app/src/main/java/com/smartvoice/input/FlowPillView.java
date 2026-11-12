package com.smartvoice.input;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

public class FlowPillView extends View {
    public enum PillState {
        READY,
        RECORDING,
        PROCESSING,
        SUCCESS
    }

    private PillState currentState = PillState.READY;

    // Paints
    private Paint bgPaint;
    private Paint borderPaint;
    private Paint micPaint;
    private Paint wavePaint;
    private Paint textPaint;
    private Paint spinnerPaint;
    private Paint checkPaint;

    // Geometry
    private final RectF boundsRect = new RectF();
    private final RectF micHeadRect = new RectF();
    private final RectF micCradleRect = new RectF();
    private final RectF spinnerRect = new RectF();
    private final Path checkPath = new Path();

    // Animators
    private ValueAnimator waveAnimator;
    private ValueAnimator spinnerAnimator;
    private float wavePhase = 0f;
    private float spinnerAngle = 0f;

    private float density = 1.0f;

    public FlowPillView(Context context) {
        super(context);
        init(context);
    }

    public FlowPillView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        density = context.getResources().getDisplayMetrics().density;

        // Background Paint (Premium Obsidian Glass)
        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setStyle(Paint.Style.FILL);

        // Border Paint (Luminous Cyan Glow)
        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(1.5f * density);
        borderPaint.setColor(Color.parseColor("#4fe6d8"));

        // Mic Paint
        micPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        micPaint.setStyle(Paint.Style.STROKE);
        micPaint.setStrokeCap(Paint.Cap.ROUND);
        micPaint.setStrokeJoin(Paint.Join.ROUND);
        micPaint.setColor(Color.parseColor("#4fe6d8"));

        // Waveform Bars Paint
        wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        wavePaint.setStyle(Paint.Style.STROKE);
        wavePaint.setStrokeCap(Paint.Cap.ROUND);
        wavePaint.setStrokeWidth(2.5f * density);
        wavePaint.setColor(Color.parseColor("#4fe6d8"));

        // Text Paint
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.parseColor("#e2e8f0"));
        textPaint.setTextSize(11f * density);
        textPaint.setFakeBoldText(true);

        // Spinner Paint
        spinnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        spinnerPaint.setStyle(Paint.Style.STROKE);
        spinnerPaint.setStrokeCap(Paint.Cap.ROUND);
        spinnerPaint.setStrokeWidth(2.2f * density);
        spinnerPaint.setColor(Color.parseColor("#f59e0b")); // amber thinking glow

        // Checkmark Paint
        checkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        checkPaint.setStyle(Paint.Style.STROKE);
        checkPaint.setStrokeCap(Paint.Cap.ROUND);
        checkPaint.setStrokeJoin(Paint.Join.ROUND);
        checkPaint.setStrokeWidth(2.5f * density);
        checkPaint.setColor(Color.parseColor("#34d399")); // emerald green
    }

    public void setState(PillState state) {
        this.currentState = state;
        stopAnimations();

        if (state == PillState.RECORDING) {
            borderPaint.setColor(Color.parseColor("#5eead4"));
            startWaveAnimation();
        } else if (state == PillState.PROCESSING) {
            borderPaint.setColor(Color.parseColor("#f59e0b"));
            startSpinnerAnimation();
        } else if (state == PillState.SUCCESS) {
            borderPaint.setColor(Color.parseColor("#34d399"));
        } else {
            borderPaint.setColor(Color.parseColor("#4fe6d8"));
        }

        invalidate();
    }

    public PillState getState() {
        return currentState;
    }

    private void startWaveAnimation() {
        waveAnimator = ValueAnimator.ofFloat(0f, 1f);
        waveAnimator.setDuration(600);
        waveAnimator.setRepeatCount(ValueAnimator.INFINITE);
        waveAnimator.setInterpolator(new LinearInterpolator());
        waveAnimator.addUpdateListener(anim -> {
            wavePhase = (float) anim.getAnimatedValue();
            invalidate();
        });
        waveAnimator.start();
    }

    private void startSpinnerAnimation() {
        spinnerAnimator = ValueAnimator.ofFloat(0f, 360f);
        spinnerAnimator.setDuration(900);
        spinnerAnimator.setRepeatCount(ValueAnimator.INFINITE);
        spinnerAnimator.setInterpolator(new LinearInterpolator());
        spinnerAnimator.addUpdateListener(anim -> {
            spinnerAngle = (float) anim.getAnimatedValue();
            invalidate();
        });
        spinnerAnimator.start();
    }

    private void stopAnimations() {
        if (waveAnimator != null) {
            waveAnimator.cancel();
            waveAnimator = null;
        }
        if (spinnerAnimator != null) {
            spinnerAnimator.cancel();
            spinnerAnimator = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopAnimations();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (h > 0) {
            LinearGradient grad = new LinearGradient(
                0, 0, 0, h,
                new int[]{Color.parseColor("#f20f171c"), Color.parseColor("#f218252c")},
                null, Shader.TileMode.CLAMP
            );
            bgPaint.setShader(grad);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float cornerRadius = h / 2.0f;
        boundsRect.set(1.5f * density, 1.5f * density, w - 1.5f * density, h - 1.5f * density);

        // 1. Draw Glassmorphism Capsule
        canvas.drawRoundRect(boundsRect, cornerRadius, cornerRadius, bgPaint);
        canvas.drawRoundRect(boundsRect, cornerRadius, cornerRadius, borderPaint);

        // 2. Center or Left-aligned elements based on state
        float centerY = h / 2.0f;

        if (currentState == PillState.READY) {
            // Draw Centered Crisp Vector Mic + Ready Dot
            float micCenterX = w * 0.38f;
            drawVectorMic(canvas, micCenterX, centerY, 1.0f);

            // "Flow" or 3 mini status dots on right
            float dotX = w * 0.68f;
            wavePaint.setColor(Color.parseColor("#4fe6d8"));
            wavePaint.setStrokeWidth(3.0f * density);
            canvas.drawPoint(dotX - 7 * density, centerY, wavePaint);
            canvas.drawPoint(dotX, centerY, wavePaint);
            canvas.drawPoint(dotX + 7 * density, centerY, wavePaint);

        } else if (currentState == PillState.RECORDING) {
            // Left: Vector Mic pulsing
            float micCenterX = 22 * density;
            drawVectorMic(canvas, micCenterX, centerY, 1.15f);

            // Right: 4 Animated Audio Wave Bars
            float startWaveX = 46 * density;
            float barSpacing = 7.5f * density;
            wavePaint.setColor(Color.parseColor("#5eead4"));
            wavePaint.setStrokeWidth(2.6f * density);

            for (int i = 0; i < 4; i++) {
                float barX = startWaveX + (i * barSpacing);
                float sinVal = (float) Math.sin((wavePhase * Math.PI * 2) + (i * 1.3));
                float barHalfHeight = (4f + Math.abs(sinVal) * 8.5f) * density;
                canvas.drawLine(barX, centerY - barHalfHeight, barX, centerY + barHalfHeight, wavePaint);
            }

            // "REC" indicator text
            textPaint.setColor(Color.parseColor("#5eead4"));
            canvas.drawText("REC", startWaveX + 32 * density, centerY + 4 * density, textPaint);

        } else if (currentState == PillState.PROCESSING) {
            // Spinner Arc
            float spinnerSize = 14 * density;
            float spinX = 22 * density;
            spinnerRect.set(spinX - spinnerSize / 2, centerY - spinnerSize / 2, spinX + spinnerSize / 2, centerY + spinnerSize / 2);
            canvas.drawArc(spinnerRect, spinnerAngle, 260, false, spinnerPaint);

            // "AI..." text
            textPaint.setColor(Color.parseColor("#fbbf24"));
            canvas.drawText("Polishing...", spinX + 16 * density, centerY + 4 * density, textPaint);

        } else if (currentState == PillState.SUCCESS) {
            // Checkmark
            float checkX = 22 * density;
            checkPath.reset();
            checkPath.moveTo(checkX - 6 * density, centerY - 1 * density);
            checkPath.lineTo(checkX - 2 * density, centerY + 4 * density);
            checkPath.lineTo(checkX + 6 * density, centerY - 5 * density);
            canvas.drawPath(checkPath, checkPaint);

            // "Done" text
            textPaint.setColor(Color.parseColor("#34d399"));
            canvas.drawText("Done!", checkX + 16 * density, centerY + 4 * density, textPaint);
        }
    }

    private void drawVectorMic(Canvas canvas, float cx, float cy, float scale) {
        float s = density * scale;
        micPaint.setStrokeWidth(1.8f * s);

        // Mic capsule head
        float headW = 4.2f * s;
        float headH = 7.5f * s;
        micHeadRect.set(cx - headW, cy - headH - 1.5f * s, cx + headW, cy + headH - 4.5f * s);
        micPaint.setStyle(Paint.Style.STROKE);
        canvas.drawRoundRect(micHeadRect, headW, headW, micPaint);

        // Mic Cradle (U-arc)
        float cradleW = 6.8f * s;
        float cradleH = 6.5f * s;
        micCradleRect.set(cx - cradleW, cy - cradleH, cx + cradleW, cy + cradleH);
        canvas.drawArc(micCradleRect, 0, 180, false, micPaint);

        // Mic Stem & Base Line
        float stemTop = cy + cradleH;
        float stemBottom = stemTop + 3.5f * s;
        canvas.drawLine(cx, stemTop, cx, stemBottom, micPaint);
        canvas.drawLine(cx - 4.5f * s, stemBottom, cx + 4.5f * s, stemBottom, micPaint);
    }
}

