package com.wmods.wppenhacer.activities;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.ItemTouchHelper;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.activities.base.BaseActivity;
import com.wmods.wppenhacer.utils.BottomNavigationConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class BottomNavigationSettingsActivity extends BaseActivity {
    private TabAdapter adapter;
    private String[] bottomBarModeValues;
    private ItemTouchHelper itemTouchHelper;
    private SharedPreferences prefs;
    private boolean skipLabelModeInitialCallback = true;
    private final ArrayList<TabItem> tabItems = new ArrayList<>();

    public static class TabAdapter extends RecyclerView.Adapter<TabAdapter.TabViewHolder> {
        private final ArrayList<TabItem> items;
        private final Listener listener;

        public interface Listener {
            void onDragRequested(RecyclerView.ViewHolder holder);
            void onVisibilityChanged(int position, boolean visible);
        }

        public static class TabViewHolder extends RecyclerView.ViewHolder {
            final ImageView dragHandle;
            final TextView summary;
            final TextView title;
            final SwitchMaterial visibilitySwitch;

            public TabViewHolder(View view) {
                super(view);
                this.title = view.findViewById(R.id.text_tab_title);
                this.summary = view.findViewById(R.id.text_tab_summary);
                this.dragHandle = view.findViewById(R.id.image_drag_handle);
                this.visibilitySwitch = view.findViewById(R.id.switch_tab_visibility);
            }
        }

        public TabAdapter(ArrayList<TabItem> items, Listener listener) {
            this.items = items;
            this.listener = listener;
        }

        @Override
        public int getItemCount() {
            return this.items.size();
        }

        @Override
        public void onBindViewHolder(TabViewHolder holder, int position) {
            TabItem tabItem = this.items.get(position);
            holder.title.setText(tabItem.title);
            
            int summaryRes;
            if (tabItem.canHide) {
                summaryRes = R.string.bottom_navigation_tab_visibility;
            } else {
                summaryRes = tabItem.lockedSummaryRes;
                if (summaryRes == 0) {
                    summaryRes = R.string.bottom_navigation_tab_chats_locked;
                }
            }
            holder.summary.setText(summaryRes);
            
            holder.visibilitySwitch.setOnCheckedChangeListener(null);
            holder.visibilitySwitch.setChecked(tabItem.visible);
            holder.visibilitySwitch.setEnabled(tabItem.canHide);
            holder.visibilitySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                int pos = holder.getBindingAdapterPosition();
                if (pos != -1) {
                    this.listener.onVisibilityChanged(pos, isChecked);
                }
            });

            holder.dragHandle.setOnTouchListener((view, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    this.listener.onDragRequested(holder);
                }
                return false;
            });
        }

        @Override
        public TabViewHolder onCreateViewHolder(ViewGroup viewGroup, int i4) {
            return new TabViewHolder(LayoutInflater.from(viewGroup.getContext())
                    .inflate(R.layout.item_bottom_navigation_tab, viewGroup, false));
        }
    }

    public static class TabItem {
        final boolean canHide;
        final int lockedSummaryRes;
        final int tabId;
        final String title;
        boolean visible;

        public TabItem(int id, String title, boolean canHide, boolean visible, int lockedSummaryRes) {
            this.tabId = id;
            this.title = title;
            this.canHide = canHide;
            this.visible = visible;
            this.lockedSummaryRes = lockedSummaryRes;
        }
    }

    private void handleGroupsToggle(boolean isChecked) {
        SharedPreferences.Editor editor = this.prefs.edit().putBoolean("separategroups", isChecked);
        if (isChecked) {
            editor.putBoolean("filtergroups", false);
            forceTabVisible(BottomNavigationConfig.TAB_GROUPS);
        }
        editor.apply();
        rebuildTabItems(true);
    }

    private void handleFavoritesToggle(boolean isChecked) {
        SharedPreferences.Editor editor = this.prefs.edit().putBoolean("separatefavorites", isChecked);
        if (isChecked) {
            editor.putBoolean("filtergroups", false);
            forceTabVisible(BottomNavigationConfig.TAB_FAVORITES);
        }
        editor.apply();
        rebuildTabItems(true);
    }

    private void forceTabVisible(int tabId) {
        Set<Integer> hidden = BottomNavigationConfig.parseTabIdSet(this.prefs.getStringSet(BottomNavigationConfig.PREF_HIDDEN_TABS, null));
        if (hidden.remove(BottomNavigationConfig.normalizeTabId(tabId))) {
            this.prefs.edit().putStringSet(BottomNavigationConfig.PREF_HIDDEN_TABS, BottomNavigationConfig.toStringSet(hidden)).apply();
        }
    }

    private void persistHiddenTabs() {
        HashSet<Integer> hiddenIds = new HashSet<>();
        HashSet<Integer> presentIds = new HashSet<>();
        for (TabItem item : this.tabItems) {
            presentIds.add(item.tabId);
            if (!item.visible && item.canHide) {
                hiddenIds.add(item.tabId);
            }
        }
        
        // Preserve hidden state for tabs not currently shown in the list (e.g. if a feature is disabled)
        Set<Integer> storedHidden = BottomNavigationConfig.parseTabIdSet(this.prefs.getStringSet(BottomNavigationConfig.PREF_HIDDEN_TABS, null));
        for (Integer id : storedHidden) {
            if (!presentIds.contains(id) && id != BottomNavigationConfig.TAB_CHATS) {
                hiddenIds.add(id);
            }
        }
        
        this.prefs.edit().putStringSet(BottomNavigationConfig.PREF_HIDDEN_TABS, BottomNavigationConfig.toStringSet(hiddenIds)).apply();
    }

    private void persistOrder() {
        ArrayList<Integer> order = new ArrayList<>();
        for (TabItem item : this.tabItems) {
            order.add(item.tabId);
        }
        
        // Ensure all known tabs have a place in the order
        List<Integer> storedOrder = BottomNavigationConfig.parseOrder(this.prefs.getString(BottomNavigationConfig.PREF_TAB_ORDER, null));
        for (Integer id : storedOrder) {
            if (!order.contains(id)) order.add(id);
        }
        for (Integer id : BottomNavigationConfig.ALL_KNOWN_TAB_IDS) {
            if (!order.contains(id)) order.add(id);
        }
        
        this.prefs.edit().putString(BottomNavigationConfig.PREF_TAB_ORDER, BottomNavigationConfig.toStoredOrder(order)).apply();
    }

    private void rebuildTabItems(boolean persist) {
        ArrayList<Integer> available = new ArrayList<>(BottomNavigationConfig.BASE_TAB_IDS);
        if (this.prefs.getBoolean("separategroups", false)) {
            available.add(BottomNavigationConfig.TAB_GROUPS);
        }
        if (this.prefs.getBoolean("separatefavorites", false)) {
            available.add(BottomNavigationConfig.TAB_FAVORITES);
        }

        ArrayList<Integer> order = BottomNavigationConfig.parseOrder(this.prefs.getString(BottomNavigationConfig.PREF_TAB_ORDER, null));
        if (order.isEmpty()) {
            order = BottomNavigationConfig.defaultOrder(
                    this.prefs.getBoolean("separategroups", false),
                    this.prefs.getBoolean("separatefavorites", false));
        }

        ArrayList<Integer> sorted = BottomNavigationConfig.orderAvailableTabs(available, order);
        Set<Integer> hidden = BottomNavigationConfig.parseTabIdSet(this.prefs.getStringSet(BottomNavigationConfig.PREF_HIDDEN_TABS, null));
        
        boolean forceStatus = this.prefs.getBoolean("igstatus", false) || this.prefs.getBoolean("native_igstatus", false);
        
        this.tabItems.clear();
        for (Integer id : sorted) {
            boolean canHide = (id != BottomNavigationConfig.TAB_CHATS);
            int lockedSummary = 0;
            
            if (id == BottomNavigationConfig.TAB_CHATS) {
                canHide = false;
                lockedSummary = R.string.bottom_navigation_tab_chats_locked;
            } else if (forceStatus && id == BottomNavigationConfig.TAB_UPDATES) {
                canHide = false;
                lockedSummary = R.string.bottom_navigation_tab_updates_locked;
            }
            
            boolean isVisible = canHide ? !hidden.contains(id) : true;
            this.tabItems.add(new TabItem(id, resolveTabTitle(id), canHide, isVisible, lockedSummary));
        }
        
        if (this.adapter != null) {
            this.adapter.notifyDataSetChanged();
        }
        
        if (persist) {
            persistOrder();
            persistHiddenTabs();
        }
    }

    private String resolveTabTitle(int id) {
        if (id == BottomNavigationConfig.TAB_CHATS) return getString(R.string.chat);
        if (id == BottomNavigationConfig.TAB_UPDATES) return getString(R.string.updates);
        if (id == BottomNavigationConfig.TAB_CALLS) return getString(R.string.calls);
        if (id == BottomNavigationConfig.TAB_COMMUNITIES) return getString(R.string.communities);
        if (id == BottomNavigationConfig.TAB_TOOLS) return getString(R.string.tools);
        if (id == BottomNavigationConfig.TAB_GROUPS) return getString(R.string.groups);
        if (id == BottomNavigationConfig.TAB_FAVORITES) return getString(R.string.favorites);
        return String.valueOf(id);
    }

    private void setupFeatureSwitches() {
        SwitchMaterial groupsSwitch = findViewById(R.id.switch_groups_tab);
        SwitchMaterial favoritesSwitch = findViewById(R.id.switch_favorites_tab);
        
        groupsSwitch.setChecked(this.prefs.getBoolean("separategroups", false));
        favoritesSwitch.setChecked(this.prefs.getBoolean("separatefavorites", false));
        
        groupsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> handleGroupsToggle(isChecked));
        favoritesSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> handleFavoritesToggle(isChecked));
    }

    private void setupLabelModeSelector() {
        Spinner spinner = findViewById(R.id.spinner_bottom_bar_mode);
        String[] entries = getResources().getStringArray(R.array.bottom_bar_mode_entries);
        this.bottomBarModeValues = getResources().getStringArray(R.array.bottom_bar_mode_values);
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, entries);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        
        String currentMode = this.prefs.getString("pref_bottom_nav_label_mode", "1");
        int selection = 0;
        for (int i = 0; i < bottomBarModeValues.length; i++) {
            if (bottomBarModeValues[i].equals(currentMode)) {
                selection = i;
                break;
            }
        }
        spinner.setSelection(selection, false);
        
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!skipLabelModeInitialCallback && position >= 0 && position < bottomBarModeValues.length) {
                    prefs.edit().putString("pref_bottom_nav_label_mode", bottomBarModeValues[position]).apply();
                }
                skipLabelModeInitialCallback = false;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        
        spinner.post(() -> skipLabelModeInitialCallback = false);
    }

    private void setupTabsRecyclerView() {
        RecyclerView recyclerView = findViewById(R.id.recycler_bottom_tabs);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        
        this.adapter = new TabAdapter(this.tabItems, new TabAdapter.Listener() {
            @Override
            public void onDragRequested(RecyclerView.ViewHolder holder) {
                if (itemTouchHelper != null) {
                    itemTouchHelper.startDrag(holder);
                }
            }

            @Override
            public void onVisibilityChanged(int position, boolean visible) {
                if (position >= 0 && position < tabItems.size()) {
                    TabItem item = tabItems.get(position);
                    if (item.canHide) {
                        item.visible = visible;
                        persistHiddenTabs();
                    }
                }
            }
        });
        
        recyclerView.setAdapter(this.adapter);
        
        this.itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }

            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                int fromPos = viewHolder.getBindingAdapterPosition();
                int toPos = target.getBindingAdapterPosition();
                if (fromPos == -1 || toPos == -1) return false;
                
                Collections.swap(tabItems, fromPos, toPos);
                adapter.notifyItemMoved(fromPos, toPos);
                persistOrder();
                return true;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {}
        });
        
        this.itemTouchHelper.attachToRecyclerView(recyclerView);
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bottom_navigation_settings);
        this.prefs = PreferenceManager.getDefaultSharedPreferences(this);
        
        setupToolbar();
        setupLabelModeSelector();
        setupFeatureSwitches();
        setupTabsRecyclerView();
        rebuildTabItems(false);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
