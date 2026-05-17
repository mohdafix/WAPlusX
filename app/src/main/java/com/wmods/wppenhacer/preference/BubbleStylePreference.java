package com.wmods.wppenhacer.preference;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.AttributeSet;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.preference.ListPreference;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.wmods.wppenhacer.R;

import java.io.InputStream;

public class BubbleStylePreference extends ListPreference {

    private static final LruCache<String, Bitmap> bitmapCache = new LruCache<>(120);

    public BubbleStylePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public BubbleStylePreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onClick() {
        CharSequence[] entries = getEntries();
        CharSequence[] entryValues = getEntryValues();
        if (entries == null || entryValues == null) {
            super.onClick();
            return;
        }

        Context context = getContext();
        String currentValue = getValue();
        if (currentValue == null) currentValue = "stock";
        float density = context.getResources().getDisplayMetrics().density;

        View dialogView = LayoutInflater.from(context).inflate(R.layout.preference_bubble_style, null);
        LinearLayout container = dialogView.findViewById(R.id.bubble_style_list_container);

        var dialogBuilder = new MaterialAlertDialogBuilder(context);
        dialogBuilder.setTitle(getTitle());
        dialogBuilder.setView(dialogView);
        dialogBuilder.setNegativeButton(android.R.string.cancel, null);

        final var dialog = dialogBuilder.create();

        for (int i = 0; i < entries.length && i < entryValues.length; i++) {
            String label = entries[i].toString();
            String value = entryValues[i].toString();

            View itemView = LayoutInflater.from(context).inflate(R.layout.item_bubble_style, container, false);
            MaterialCardView card = (MaterialCardView) itemView;
            TextView title = itemView.findViewById(R.id.style_name);
            ImageView selectedIcon = itemView.findViewById(R.id.selected_icon);
            View previewContainer = itemView.findViewById(R.id.preview_container);
            ImageView previewIncoming = itemView.findViewById(R.id.preview_incoming);
            ImageView previewOutgoing = itemView.findViewById(R.id.preview_outgoing);

            title.setText(label);

            boolean isSelected = value.equals(currentValue);
            selectedIcon.setVisibility(isSelected ? View.VISIBLE : View.GONE);
            card.setStrokeWidth(isSelected ? (int) (2 * density) : 0);
            card.setStrokeColor(context.getResources().getColor(R.color.whatsapp_green));

            if ("stock".equals(value)) {
                previewContainer.setVisibility(View.GONE);
            } else {
                previewContainer.setVisibility(View.VISIBLE);
                loadBubbleBitmap(context, previewIncoming, value, true);
                loadBubbleBitmap(context, previewOutgoing, value, false);
            }

            final String itemValue = value;
            final String itemLabel = label;
            itemView.setOnClickListener(v -> {
                if (callChangeListener(itemValue)) {
                    setValue(itemValue);
                    setSummary(itemLabel);
                    notifyChanged();
                }
                dialog.dismiss();
            });
            container.addView(itemView);
        }

        dialog.show();
    }

    private static void loadBubbleBitmap(Context context, ImageView imageView, String value, boolean incoming) {
        String direction = incoming ? "incoming" : "outgoing";
        String cacheKey = "bubble_" + value + "_" + direction;
        Bitmap cached = bitmapCache.get(cacheKey);
        if (cached != null) {
            imageView.setImageBitmap(cached);
            return;
        }

        try {
            String path = "bubbles/" + value + "_balloon_" + direction + "_normal.9.png";
            InputStream is = context.getAssets().open(path);
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            is.close();
            if (bitmap != null) {
                bitmapCache.put(cacheKey, bitmap);
                imageView.setImageBitmap(bitmap);
            }
        } catch (Exception ignored) {
        }
    }
}
