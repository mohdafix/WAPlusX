package com.wmods.wppenhacer.preference;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.preference.Preference;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.wmods.wppenhacer.R;
import java.lang.reflect.Field;
import java.util.*;

public class BubbleTickStylePreference extends Preference implements Preference.OnPreferenceClickListener {
    private static final String TICK_TAG = "_message_";
    // We require these variants for a style to be considered "complete" for preview
    private static final Set<String> REQUIRED_VARIANTS = new HashSet<>(Arrays.asList(
        "got_receipt_from_server_onmedia", "got_receipt_from_target_onmedia", "got_read_receipt_from_target_onmedia"
    ));

    public BubbleTickStylePreference(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        setOnPreferenceClickListener(this);
    }

    public static int getResId(String style, String variant) {
        Class<R.drawable> cls = R.drawable.class;
        try {
            return cls.getField(style + TICK_TAG + variant).getInt(null);
        } catch (Throwable unused) {
            return 0;
        }
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        Context context = getContext();
        Map<String, Set<String>> styleMap = new HashMap<>();

        // Dynamically find all available tick styles in the module's resources
        for (Field field : R.drawable.class.getFields()) {
            String name = field.getName();
            int idx = name.indexOf(TICK_TAG);
            if (idx > 0) {
                String prefix = name.substring(0, idx);
                String suffix = name.substring(idx + TICK_TAG.length());
                styleMap.computeIfAbsent(prefix, k -> new HashSet<>()).add(suffix);
            }
        }

        List<String> availableStyles = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : styleMap.entrySet()) {
            if (entry.getValue().containsAll(REQUIRED_VARIANTS)) {
                availableStyles.add(entry.getKey());
            }
        }
        Collections.sort(availableStyles);
        availableStyles.add(0, "default");

        SharedPreferences prefs = getSharedPreferences();
        String currentStyle = prefs != null ? prefs.getString(getKey(), "default") : "default";

        View dialogView = LayoutInflater.from(context).inflate(R.layout.preference_bubble_tick_style, null);
        LinearLayout container = dialogView.findViewById(R.id.bubble_tick_list_container);

        var dialogBuilder = new MaterialAlertDialogBuilder(context);
        dialogBuilder.setTitle("Select Bubble Tick Style");
        dialogBuilder.setView(dialogView);
        dialogBuilder.setNegativeButton(android.R.string.cancel, null);
        
        final var dialog = dialogBuilder.create();

        for (String style : availableStyles) {
            View itemView = LayoutInflater.from(context).inflate(R.layout.item_bubble_tick_style, container, false);
            MaterialCardView card = (MaterialCardView) itemView;
            TextView title = itemView.findViewById(R.id.style_name);
            ImageView selectedIcon = itemView.findViewById(R.id.selected_icon);
            View previewContainer = itemView.findViewById(R.id.preview_container);
            
            String displayName = style.replace('_', ' ');
            if (displayName.length() > 0) {
                displayName = Character.toUpperCase(displayName.charAt(0)) + displayName.substring(1);
            } else {
                displayName = "Default";
            }
            title.setText(displayName);

            boolean isSelected = style.equals(currentStyle);
            selectedIcon.setVisibility(isSelected ? View.VISIBLE : View.GONE);
            card.setStrokeWidth(isSelected ? (int)(2 * context.getResources().getDisplayMetrics().density) : 0);
            card.setStrokeColor(context.getResources().getColor(R.color.whatsapp_green));

            if ("default".equals(style)) {
                previewContainer.setVisibility(View.GONE);
            } else {
                previewContainer.setVisibility(View.VISIBLE);
                ((ImageView)itemView.findViewById(R.id.preview_sent)).setImageResource(getResId(style, "got_receipt_from_server_onmedia"));
                ((ImageView)itemView.findViewById(R.id.preview_delivered)).setImageResource(getResId(style, "got_receipt_from_target_onmedia"));
                ((ImageView)itemView.findViewById(R.id.preview_read)).setImageResource(getResId(style, "got_read_receipt_from_target_onmedia"));
                
                int unsentId = getResId(style, "unsent_onmedia");
                if (unsentId != 0) {
                    ((ImageView)itemView.findViewById(R.id.preview_unsent)).setImageResource(unsentId);
                    itemView.findViewById(R.id.preview_unsent).setVisibility(View.VISIBLE);
                } else {
                    itemView.findViewById(R.id.preview_unsent).setVisibility(View.GONE);
                }
            }

            final String itemStyle = style;
            final String finalDisplayName = displayName;
            itemView.setOnClickListener(v -> {
                if (prefs != null) {
                    prefs.edit().putString(getKey(), itemStyle).apply();
                    setSummary(finalDisplayName);
                    notifyChanged();
                }
                dialog.dismiss();
            });
            container.addView(itemView);
        }

        dialog.show();
        return true;
    }

    @Override
    public CharSequence getSummary() {
        SharedPreferences sharedPreferences = getSharedPreferences();
        String style = (sharedPreferences != null) ? sharedPreferences.getString(getKey(), "default") : "default";
        String displayName = style.replace('_', ' ');
        if (displayName.length() > 0) {
            displayName = Character.toUpperCase(displayName.charAt(0)) + displayName.substring(1);
        } else {
            displayName = "Default";
        }
        return displayName;
    }

    public BubbleTickStylePreference(Context context, AttributeSet attributeSet, int defStyleAttr) {
        super(context, attributeSet, defStyleAttr);
        setOnPreferenceClickListener(this);
    }
}
