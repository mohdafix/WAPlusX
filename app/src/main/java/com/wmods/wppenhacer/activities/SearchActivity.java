package com.wmods.wppenhacer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.textfield.TextInputEditText;
import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.activities.base.BaseActivity;
import com.wmods.wppenhacer.adapter.SearchAdapter;
import com.wmods.wppenhacer.databinding.ActivitySearchBinding;
import com.wmods.wppenhacer.model.SearchableFeature;
import com.wmods.wppenhacer.utils.FeatureCatalog;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity for searching and navigating to app features.
 */
public class SearchActivity extends BaseActivity implements SearchAdapter.OnFeatureClickListener {

    private ActivitySearchBinding binding;
    private SearchAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivitySearchBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Setup toolbar
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.search_features_title);
        }

        // Setup RecyclerView
        adapter = new SearchAdapter(this);
        binding.searchResults.setLayoutManager(new LinearLayoutManager(this));
        binding.searchResults.setAdapter(adapter);

        // Setup search input
        setupSearchInput();

        // Show recent features by default
        loadRecentHistory();

        // Focus on search input
        binding.searchInput.requestFocus();
    }

    private void setupSearchInput() {
        binding.searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                performSearch(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void performSearch(String query) {
        if (query.trim().isEmpty()) {
            // Show recent history when search is empty
            loadRecentHistory();
            return;
        }

        // Search features
        List<SearchableFeature> results = FeatureCatalog.search(this, query);

        // Update adapter
        adapter.setFeatures(results);
        adapter.setSearchQuery(query);

        // Update empty state
        if (results.isEmpty()) {
            updateEmptyState(true, getString(R.string.search_no_results));
        } else {
            updateEmptyState(false, "");
        }
    }

    private void updateEmptyState(boolean show, String message) {
        if (show) {
            binding.emptyState.setVisibility(View.VISIBLE);
            binding.searchResults.setVisibility(View.GONE);
            binding.emptyStateText.setText(message);
        } else {
            binding.emptyState.setVisibility(View.GONE);
            binding.searchResults.setVisibility(View.VISIBLE);
        }
    }

    private static final String PREF_NAME = "SearchHistory";
    private static final String KEY_HISTORY = "recent_features";
    private static final int MAX_HISTORY = 3;

    @Override
    public void onFeatureClick(SearchableFeature feature) {
        saveToRecent(feature);
        if (feature.getFragmentType() == SearchableFeature.FragmentType.ACTIVITY) {
            if ("deleted_messages_activity".equals(feature.getKey())) {
                startActivity(new Intent(this, DeletedMessagesActivity.class));
            }
            return;
        }

        // Navigate back to MainActivity with feature information
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("navigate_to_fragment", feature.getFragmentType().getPosition());
        intent.putExtra("scroll_to_preference", feature.getKey());
        intent.putExtra("parent_preference", feature.getParentKey());
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void saveToRecent(SearchableFeature feature) {
        android.content.SharedPreferences prefs = getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE);
        String historyStr = prefs.getString(KEY_HISTORY, "");
        List<String> history = new ArrayList<>(java.util.Arrays.asList(historyStr.split(",")));
        history.remove(""); // Remove empty strings
        
        // Remove if it already exists to put it at the top
        history.remove(feature.getKey());
        
        // Add to the top
        history.add(0, feature.getKey());
        
        // Keep only up to MAX_HISTORY
        if (history.size() > MAX_HISTORY) {
            history = history.subList(0, MAX_HISTORY);
        }
        
        prefs.edit().putString(KEY_HISTORY, String.join(",", history)).apply();
    }

    private void loadRecentHistory() {
        android.content.SharedPreferences prefs = getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE);
        String historyStr = prefs.getString(KEY_HISTORY, "");
        
        if (historyStr.isEmpty()) {
            updateEmptyState(true, "No recent searches. Try searching above.");
            adapter.setFeatures(new ArrayList<>());
            return;
        }

        List<String> historyKeys = java.util.Arrays.asList(historyStr.split(","));
        List<SearchableFeature> allFeatures = FeatureCatalog.getAllFeatures(this);
        List<SearchableFeature> recentFeatures = new ArrayList<>();
        
        for (String key : historyKeys) {
            if (key.isEmpty()) continue;
            for (SearchableFeature feature : allFeatures) {
                if (feature.getKey().equals(key)) {
                    recentFeatures.add(feature);
                    break;
                }
            }
        }
        
        if (recentFeatures.isEmpty()) {
            updateEmptyState(true, "No recent searches. Try searching above.");
        } else {
            updateEmptyState(false, "");
        }
        
        adapter.setFeatures(recentFeatures);
        adapter.setSearchQuery("");
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
