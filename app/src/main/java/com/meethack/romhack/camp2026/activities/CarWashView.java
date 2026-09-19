package com.meethack.romhack.camp2026.activities;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import com.meethack.romhack.camp2026.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Purely decorative header animation used above the home title: neon droplets sliding down and
 * looping, like foam running off glass. No interaction, no state to save/restore.
 */
public class CarWashView extends View {
    private static final int DROPLET_COUNT = 14;
    private static final long CYCLE_DURATION_MS = 2600;

    private final List<Droplet> droplets = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ValueAnimator animator;

    public CarWashView(Context context) {
        super(context);
    }

    public CarWashView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        droplets.clear();
        Random random = new Random(0);
        int cyan = getContext().getColor(R.color.neon_cyan);
        int magenta = getContext().getColor(R.color.neon_magenta);
        for (int i = 0; i < DROPLET_COUNT; i++) {
            float x = random.nextFloat() * w;
            float phase = random.nextFloat();
            float radius = 3f + random.nextFloat() * 4f;
            int color = (i % 2 == 0) ? cyan : magenta;
            droplets.add(new Droplet(x, phase, radius, color));
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (isInEditMode()) {
            return;
        }
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(CYCLE_DURATION_MS);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> invalidate());
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int height = getHeight();
        float time = animator != null ? (float) animator.getAnimatedValue() : 0.3f;

        for (Droplet droplet : droplets) {
            float fraction = (time + droplet.phase) % 1f;
            float y = fraction * height;
            float fade = 1f - Math.abs(fraction - 0.5f) * 1.4f;
            paint.setColor(droplet.color);
            paint.setAlpha((int) (Math.max(0f, Math.min(1f, fade)) * 220));
            canvas.drawCircle(droplet.x, y, droplet.radius, paint);
        }
    }

    private static final class Droplet {
        final float x;
        final float phase;
        final float radius;
        final int color;

        Droplet(float x, float phase, float radius, int color) {
            this.x = x;
            this.phase = phase;
            this.radius = radius;
            this.color = color;
        }
    }
}
