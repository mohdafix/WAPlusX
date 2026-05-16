package com.wmods.wppenhacer.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * BottomNavigationConfig - handles tab IDs, ordering, and visibility.
 * This is a port from WaEnhancer to unify SeparateGroup and HideTabs.
 */
public final class BottomNavigationConfig {
    public static final String PREF_TAB_ORDER = "bottom_nav_tab_order";
    public static final String PREF_HIDDEN_TABS = "bottom_nav_hidden_tabs";
    
    // Tab IDs used by WhatsApp and our custom ones
    public static final int TAB_CHATS = 200;
    public static final int TAB_UPDATES = 300;
    public static final int TAB_CALLS = 400;
    public static final int TAB_COMMUNITIES = 600;
    public static final int TAB_TOOLS = 700;
    public static final int TAB_GROUPS = 1001;
    public static final int TAB_FAVORITES = 1002;

    public static final List<Integer> BASE_TAB_IDS = List.of(TAB_CHATS, TAB_UPDATES, TAB_CALLS, TAB_COMMUNITIES, TAB_TOOLS);
    public static final List<Integer> ALL_KNOWN_TAB_IDS = List.of(200, 300, 400, 600, 700, 1001, 1002);

    private BottomNavigationConfig() {
    }

    /**
     * Reorders the tab list and filters hidden tabs based on preferences.
     */
    public static void applyTabsConfiguration(List<Integer> list, String savedOrderStr, Set<String> hiddenTabsSet, boolean forceStatusVisible) {
        ArrayList<Integer> currentList = new ArrayList<>();
        for (Integer id : list) {
            if (id != null) currentList.add(normalizeTabId(id));
        }

        ArrayList<Integer> reordered = orderAvailableTabs(currentList, parseOrder(savedOrderStr));
        
        Set<Integer> hiddenIds = parseTabIdSet(hiddenTabsSet);
        hiddenIds.remove(TAB_CHATS); // Never hide the main chats tab
        
        if (forceStatusVisible) {
            hiddenIds.remove(TAB_UPDATES);
        }

        reordered.removeIf(hiddenIds::contains);
        
        list.clear();
        list.addAll(reordered);
    }

    /**
     * Core reordering logic.
     */
    public static ArrayList<Integer> orderAvailableTabs(List<Integer> available, List<Integer> desired) {
        ArrayList<Integer> result = new ArrayList<>();
        if (desired == null) desired = Collections.emptyList();

        // 1. Add tabs in user's desired order if they are currently available
        for (Integer id : desired) {
            if (available.contains(id) && !result.contains(id)) {
                result.add(id);
            }
        }
        // 2. Append any remaining available tabs that weren't in the user's order
        for (Integer id : available) {
            if (!result.contains(id)) {
                result.add(id);
            }
        }
        return result;
    }

    /**
     * Default order generation.
     */
    public static ArrayList<Integer> defaultOrder(boolean showGroups, boolean showFavorites) {
        ArrayList<Integer> result = new ArrayList<>(BASE_TAB_IDS);
        int insertIdx = Math.min(1, result.size());
        if (showGroups) {
            result.add(insertIdx++, TAB_GROUPS);
        }
        if (showFavorites) {
            result.add(insertIdx, TAB_FAVORITES);
        }
        return result;
    }

    public static ArrayList<Integer> parseOrder(String str) {
        ArrayList<Integer> result = new ArrayList<>();
        if (str != null && !str.trim().isEmpty()) {
            for (String part : str.split(",")) {
                try {
                    int id = Integer.parseInt(part.trim());
                    id = normalizeTabId(id);
                    if (!result.contains(id)) result.add(id);
                } catch (NumberFormatException ignored) {}
            }
        }
        return result;
    }

    public static Set<Integer> parseTabIdSet(Set<String> set) {
        Set<Integer> result = new HashSet<>();
        if (set != null) {
            for (String val : set) {
                try {
                    result.add(normalizeTabId(Integer.parseInt(val)));
                } catch (NumberFormatException ignored) {}
            }
        }
        return result;
    }

    public static int normalizeTabId(int id) {
        // Handle alias mappings for consistency across versions
        if (id == 100) return TAB_COMMUNITIES;
        if (id == 500) return TAB_GROUPS;
        if (id == 1200 || id == 800) return TAB_FAVORITES;
        return id;
    }

    public static String toStoredOrder(List<Integer> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(list.get(i));
        }
        return sb.toString();
    }

    public static Set<String> toStringSet(Set<Integer> set) {
        Set<String> result = new HashSet<>();
        if (set != null) {
            for (Integer id : set) {
                result.add(String.valueOf(id));
            }
        }
        return result;
    }
}
