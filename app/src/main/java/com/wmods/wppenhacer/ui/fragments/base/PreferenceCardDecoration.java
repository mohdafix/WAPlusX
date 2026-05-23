package com.wmods.wppenhacer.ui.fragments.base;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroupAdapter;
import androidx.preference.PreferenceViewHolder;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.R;

public class PreferenceCardDecoration extends RecyclerView.ItemDecoration {

    private final Paint mCardPaint;
    private final int mRadius;
    private final RectF mRect = new RectF();

    public PreferenceCardDecoration(Context context) {
        mCardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(R.attr.colorSurfaceContainer, typedValue, true);
        mCardPaint.setColor(typedValue.data);
        
        mRadius = (int) (24 * context.getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        super.onDraw(c, parent, state);

        int childCount = parent.getChildCount();
        if (childCount == 0) return;
        
        RecyclerView.Adapter<?> adapter = parent.getAdapter();
        if (!(adapter instanceof PreferenceGroupAdapter)) return;
        
        PreferenceGroupAdapter prefAdapter = (PreferenceGroupAdapter) adapter;

        int firstTop = -1;
        boolean inGroup = false;
        
        for (int i = 0; i < childCount; i++) {
            View child = parent.getChildAt(i);
            RecyclerView.ViewHolder holder = parent.getChildViewHolder(child);
            
            if (holder instanceof PreferenceViewHolder) {
                int position = holder.getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    Preference pref = prefAdapter.getItem(position);
                    
                    if (pref instanceof PreferenceCategory) {
                        // Draw previous group if exists
                        if (inGroup && firstTop != -1) {
                            View prevChild = parent.getChildAt(i - 1);
                            drawCard(c, prevChild.getLeft(), firstTop, prevChild.getRight(), prevChild.getBottom() + Math.round(prevChild.getTranslationY()));
                        }
                        // Start new group right below the category title
                        firstTop = child.getBottom() + Math.round(child.getTranslationY());
                        inGroup = true;
                    } else if (!inGroup && firstTop == -1) {
                        // A preference without a category above it
                        firstTop = child.getTop() + Math.round(child.getTranslationY());
                        inGroup = true;
                    }
                }
            }
            
            // Draw last group
            if (i == childCount - 1 && inGroup && firstTop != -1) {
                drawCard(c, child.getLeft(), firstTop, child.getRight(), child.getBottom() + Math.round(child.getTranslationY()));
            }
        }
    }

    private void drawCard(Canvas c, int left, int top, int right, int bottom) {
        if (bottom <= top) return;
        mRect.set(left, top, right, bottom);
        c.drawRoundRect(mRect, mRadius, mRadius, mCardPaint);
    }
}
