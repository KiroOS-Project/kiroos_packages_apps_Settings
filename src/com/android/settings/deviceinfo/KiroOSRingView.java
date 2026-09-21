package com.android.settings.deviceinfo;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

/**
 * KiroOS updater: thin ring around the logo. Idle it is a quiet track, while checking a short
 * arc turns around it, while downloading it fills up with the real progress.
 */
public class KiroOSRingView extends View {

    private static final int TRACK = 0xFF1C1C1E;
    private static final int ARC = 0xFFFFFFFF;

    private final Paint mTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mArcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mOval = new RectF();

    private boolean mSpinning;
    private float mSpinAngle;
    private float mProgress = -1f; // < 0: no determinate progress shown
    private float mShownProgress;
    private ValueAnimator mSpinAnimator;
    private ValueAnimator mProgressAnimator;

    public KiroOSRingView(Context context) {
        this(context, null);
    }

    public KiroOSRingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        final float stroke = 3f * getResources().getDisplayMetrics().density;
        mTrackPaint.setStyle(Paint.Style.STROKE);
        mTrackPaint.setStrokeWidth(stroke);
        mTrackPaint.setColor(TRACK);
        mArcPaint.setStyle(Paint.Style.STROKE);
        mArcPaint.setStrokeWidth(stroke);
        mArcPaint.setStrokeCap(Paint.Cap.ROUND);
        mArcPaint.setColor(ARC);
    }

    /** Indeterminate: a short arc turning around the ring. */
    public void setSpinning(boolean spinning) {
        if (mSpinning == spinning) return;
        mSpinning = spinning;
        if (spinning) {
            mSpinAnimator = ValueAnimator.ofFloat(0f, 360f);
            mSpinAnimator.setDuration(1400);
            mSpinAnimator.setInterpolator(new LinearInterpolator());
            mSpinAnimator.setRepeatCount(ValueAnimator.INFINITE);
            mSpinAnimator.addUpdateListener(a -> {
                mSpinAngle = (float) a.getAnimatedValue();
                invalidate();
            });
            mSpinAnimator.start();
        } else if (mSpinAnimator != null) {
            mSpinAnimator.cancel();
            mSpinAnimator = null;
        }
        invalidate();
    }

    /** Determinate progress 0..1, or a negative value to hide it. Changes glide smoothly. */
    public void setProgress(float progress) {
        if (progress < 0f) {
            mProgress = -1f;
            mShownProgress = 0f;
            if (mProgressAnimator != null) mProgressAnimator.cancel();
            invalidate();
            return;
        }
        final float target = Math.min(1f, progress);
        if (mProgress < 0f) mShownProgress = 0f;
        mProgress = target;
        if (mProgressAnimator != null) mProgressAnimator.cancel();
        mProgressAnimator = ValueAnimator.ofFloat(mShownProgress, target);
        mProgressAnimator.setDuration(450);
        mProgressAnimator.setInterpolator(new DecelerateInterpolator());
        mProgressAnimator.addUpdateListener(a -> {
            mShownProgress = (float) a.getAnimatedValue();
            invalidate();
        });
        mProgressAnimator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        setSpinning(false);
        if (mProgressAnimator != null) mProgressAnimator.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final float inset = mTrackPaint.getStrokeWidth();
        mOval.set(inset, inset, getWidth() - inset, getHeight() - inset);
        canvas.drawOval(mOval, mTrackPaint);
        if (mProgress >= 0f) {
            canvas.drawArc(mOval, -90f, 360f * mShownProgress, false, mArcPaint);
        } else if (mSpinning) {
            canvas.drawArc(mOval, mSpinAngle - 90f, 70f, false, mArcPaint);
        }
    }
}
