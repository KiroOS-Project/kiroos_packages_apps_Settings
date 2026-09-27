/*
 * Copyright (C) 2026 KiroOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.kiroos;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceCategory;
import android.support.v7.preference.PreferenceGroupAdapter;
import android.support.v7.widget.RecyclerView;
import android.view.View;

import com.android.settings.R;

/**
 * KiroOS 2.1 Settings pages: the preferences between two category titles are drawn on one
 * rounded card (like the main screen), the titles stay outside, on the page background.
 */
public class KiroGroupDecoration extends RecyclerView.ItemDecoration {

    private final Paint mCard = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mPath = new Path();
    private final RectF mRect = new RectF();
    private final float[] mRadii = new float[8];
    private final int mInset;
    private final int mGap;
    private final float mRadius;

    public KiroGroupDecoration(Context c) {
        final float d = c.getResources().getDisplayMetrics().density;
        mCard.setColor(c.getColor(R.color.kiro_group_card));
        mInset = Math.round(16 * d);
        mGap = Math.round(14 * d);
        mRadius = 18 * d;
    }

    private static Preference item(RecyclerView parent, int position) {
        final RecyclerView.Adapter a = parent.getAdapter();
        if (!(a instanceof PreferenceGroupAdapter) || position < 0
                || position >= a.getItemCount()) {
            return null;
        }
        return ((PreferenceGroupAdapter) a).getItem(position);
    }

    private static boolean isCard(RecyclerView parent, int position) {
        final Preference p = item(parent, position);
        return p != null && !(p instanceof PreferenceCategory);
    }

    @Override
    public void getItemOffsets(Rect out, View view, RecyclerView parent, RecyclerView.State state) {
        final int p = parent.getChildAdapterPosition(view);
        if (p == RecyclerView.NO_POSITION || !isCard(parent, p)) {
            return;
        }
        out.left = mInset;
        out.right = mInset;
        if (!isCard(parent, p + 1)) {
            out.bottom = mGap;          // air after a card
        }
        if (p == 0) {
            out.top = mGap / 2;
        }
    }

    @Override
    public void onDraw(Canvas c, RecyclerView parent, RecyclerView.State state) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            final View v = parent.getChildAt(i);
            final int p = parent.getChildAdapterPosition(v);
            if (p == RecyclerView.NO_POSITION || !isCard(parent, p)) {
                continue;
            }
            final boolean first = !isCard(parent, p - 1);
            final boolean last = !isCard(parent, p + 1);
            final float top = v.getTop() + v.getTranslationY();
            final float bottom = v.getBottom() + v.getTranslationY();
            mRect.set(v.getLeft(), top, v.getRight(), bottom);
            final float rt = first ? mRadius : 0, rb = last ? mRadius : 0;
            mRadii[0] = mRadii[1] = mRadii[2] = mRadii[3] = rt;
            mRadii[4] = mRadii[5] = mRadii[6] = mRadii[7] = rb;
            mPath.reset();
            mPath.addRoundRect(mRect, mRadii, Path.Direction.CW);
            mCard.setAlpha(Math.round(255 * v.getAlpha()));
            c.drawPath(mPath, mCard);
        }
    }
}
