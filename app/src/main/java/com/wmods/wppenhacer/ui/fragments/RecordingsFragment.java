package com.wmods.wppenhacer.ui.fragments;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.adapter.RecordingsAdapter;
import com.wmods.wppenhacer.databinding.FragmentRecordingsBinding;
import com.wmods.wppenhacer.model.Recording;
import com.wmods.wppenhacer.ui.dialogs.AudioPlayerDialog;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RecordingsFragment extends Fragment implements RecordingsAdapter.OnRecordingActionListener {

    private FragmentRecordingsBinding binding;
    private RecordingsAdapter adapter;
    private List<Recording> allRecordings = new ArrayList<>();
    private List<File> baseDirs = new ArrayList<>();
    private boolean isGroupByContact = true;
    private String currentContactFilter = null;
    private int currentSortType = 1; // 1=date, 2=name, 3=duration, 4=contact
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentRecordingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new RecordingsAdapter(this);
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerView.setAdapter(adapter);

        // Set up selection change listener
        adapter.setSelectionChangeListener(count -> {
            if (count > 0) {
                binding.selectionBar.setVisibility(View.VISIBLE);
                binding.tvSelectionCount.setText(getString(R.string.selected_count, count));
            } else {
                binding.selectionBar.setVisibility(View.GONE);
            }
        });

        // Initialize base directories
        initializeBaseDirs();

        // View mode toggle
        binding.chipList.setOnClickListener(v -> {
            isGroupByContact = false;
            currentContactFilter = null;
            updateDisplayList();
        });
        
        binding.chipGroupByContact.setOnClickListener(v -> {
            isGroupByContact = true;
            currentContactFilter = null;
            updateDisplayList();
        });

        // Selection bar buttons
        binding.btnCloseSelection.setOnClickListener(v -> adapter.clearSelection());
        binding.btnSelectAll.setOnClickListener(v -> adapter.selectAll());
        binding.btnShareSelected.setOnClickListener(v -> shareSelectedRecordings());
        binding.btnDeleteSelected.setOnClickListener(v -> deleteSelectedRecordings());

        // Sort FAB
        binding.fabSort.setOnClickListener(v -> showSortMenu());

        binding.swipeRefresh.setOnRefreshListener(() -> {
            initializeBaseDirs();
            loadRecordings();
        });

        loadRecordings();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding == null) return;
        initializeBaseDirs();
        loadRecordings();
    }

    private void initializeBaseDirs() {
        var prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        String configuredPath = prefs.getString("call_recording_path", null);

        baseDirs.clear();
        Set<String> addedPaths = new LinkedHashSet<>();

        // 1. Current default location used by CallRecording
        addBaseDir(addedPaths, new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "WA Call Recordings"
        ));

        // 2. User configured location from shared preferences
        if (configuredPath != null && !configuredPath.isEmpty()) {
            addBaseDir(addedPaths, new File(configuredPath, "WA Call Recordings"));
        }

        // 3. Legacy root folder from older versions
        addBaseDir(addedPaths, new File(Environment.getExternalStorageDirectory(), "WA Call Recordings"));

        // 4. WhatsApp app external files
        addBaseDir(addedPaths, new File("/sdcard/Android/data/com.whatsapp/files/Recordings"));
        addBaseDir(addedPaths, new File("/sdcard/Android/data/com.whatsapp.w4b/files/Recordings"));

        // 5. Legacy fallback
        addBaseDir(addedPaths, new File(Environment.getExternalStorageDirectory(), "Music/WaEnhancer/Recordings"));
    }

    private void addBaseDir(@NonNull Set<String> addedPaths, @NonNull File dir) {
        String normalizedPath = normalizePath(dir);
        if (addedPaths.add(normalizedPath)) {
            baseDirs.add(dir);
        }
    }

    @NonNull
    private String normalizePath(@NonNull File dir) {
        try {
            return dir.getCanonicalPath();
        } catch (IOException ignored) {
            return dir.getAbsolutePath();
        }
    }

    private void loadRecordings() {
        if (binding == null) {
            return;
        }

        binding.swipeRefresh.setRefreshing(true);
        executorService.execute(() -> {
            List<Recording> recordings = new ArrayList<>();
            try {
                for (File baseDir : baseDirs) {
                    if (baseDir.exists() && baseDir.isDirectory()) {
                        traverseDirectory(baseDir, recordings);
                    }
                }

                // Apply sorting on the background thread
                applySort(recordings);

                if (getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        if (binding == null) return;
                        
                        allRecordings.clear();
                        allRecordings.addAll(recordings);
                        
                        if (allRecordings.isEmpty()) {
                            binding.emptyView.setVisibility(View.VISIBLE);
                            binding.recyclerView.setVisibility(View.GONE);
                        } else {
                            binding.emptyView.setVisibility(View.GONE);
                            binding.recyclerView.setVisibility(View.VISIBLE);
                            updateDisplayList();
                        }
                        binding.swipeRefresh.setRefreshing(false);
                    });
                }
            } catch (Exception ignored) {
                if (getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        if (binding != null) {
                            binding.swipeRefresh.setRefreshing(false);
                        }
                    });
                }
            }
        });
    }

    private void traverseDirectory(File dir, List<Recording> recordings) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    traverseDirectory(file, recordings);
                } else {
                    String name = file.getName().toLowerCase();
                    if (name.endsWith(".wav") || name.endsWith(".mp3") || name.endsWith(".aac") || name.endsWith(".m4a")) {
                        recordings.add(new Recording(file));
                    }
                }
            }
        }
    }

    private void applySort(List<Recording> recordings) {
        switch (currentSortType) {
            case 1 -> recordings.sort((r1, r2) -> Long.compare(r2.getDate(), r1.getDate())); // Date desc
            case 2 -> recordings.sort(Comparator.comparing(Recording::getContactName)); // Name
            case 3 -> recordings.sort((r1, r2) -> Long.compare(r2.getDuration(), r1.getDuration())); // Duration desc
            case 4 -> recordings.sort(Comparator.comparing(Recording::getContactName)
                    .thenComparing((r1, r2) -> Long.compare(r2.getDate(), r1.getDate()))); // Contact then date
        }
    }

    private void updateDisplayList() {
        if (isGroupByContact && currentContactFilter == null) {
            executorService.execute(() -> {
                Map<String, List<Recording>> groups = allRecordings.stream()
                        .collect(Collectors.groupingBy(Recording::getGroupKey));

                List<Recording> contactItems = new ArrayList<>();
                groups.forEach((name, recs) -> {
                    if (recs == null || recs.isEmpty()) return;

                    long latestDate = 0L;
                    for (Recording r : recs) {
                        if (r.getDate() > latestDate) latestDate = r.getDate();
                    }

                    final long latestDateFinal = latestDate;
                    final int countFinal = recs.size();

                    Recording groupItem = new Recording(recs.get(0).getFile()) {
                        @Override
                        public String getFormattedSize() { return countFinal + " recordings"; }
                        @Override
                        public String getFormattedDuration() { return ""; }
                        @Override
                        public long getDuration() { return 0; }
                        @Override
                        public long getDate() { return latestDateFinal; }
                    };
                    contactItems.add(groupItem);
                });

                contactItems.sort(getGroupedSortComparator());
                
                if (getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        if (isAdded() && binding != null) {
                            adapter.setGroupedMode(true);
                            adapter.setRecordings(contactItems);
                        }
                    });
                }
            });
        } else if (currentContactFilter != null) {
            List<Recording> filtered = allRecordings.stream()
                    .filter(r -> r.getGroupKey().equals(currentContactFilter))
                    .collect(Collectors.toList());
            adapter.setGroupedMode(false);
            adapter.setRecordings(filtered);
        } else {
            adapter.setGroupedMode(false);
            adapter.setRecordings(allRecordings);
        }
    }

    private Comparator<Recording> getGroupedSortComparator() {
        return switch (currentSortType) {
            case 1 -> Comparator.comparingLong(Recording::getDate).reversed()
                    .thenComparing(Recording::getContactName, String.CASE_INSENSITIVE_ORDER);
            case 2, 3 -> Comparator.comparing(Recording::getContactName, String.CASE_INSENSITIVE_ORDER);
            case 4 -> Comparator.comparing(Recording::getContactName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparingLong(Recording::getDate).reversed();
            default -> Comparator.comparing(Recording::getContactName, String.CASE_INSENSITIVE_ORDER);
        };
    }

    private void showSortMenu() {
        PopupMenu popup = new PopupMenu(requireContext(), binding.fabSort);
        popup.getMenu().add(0, 1, 0, R.string.sort_date);
        popup.getMenu().add(0, 2, 0, R.string.sort_name);
        popup.getMenu().add(0, 3, 0, R.string.sort_duration);
        popup.getMenu().add(0, 4, 0, R.string.sort_contact);
        
        popup.setOnMenuItemClickListener(item -> {
            currentSortType = item.getItemId();
            applySort(allRecordings);
            updateDisplayList();
            return true;
        });
        popup.show();
    }

    // RecordingsAdapter.OnRecordingActionListener implementation

    @Override
    public void onPlay(Recording recording) {
        if (isGroupByContact && currentContactFilter == null) {
            currentContactFilter = recording.getGroupKey();
            updateDisplayList();
        } else {
            AudioPlayerDialog dialog = new AudioPlayerDialog(requireContext(), recording.getFile());
            dialog.show();
        }
    }

    @Override
    public void onShare(Recording recording) {
        if (isGroupByContact && currentContactFilter == null) {
            Toast.makeText(requireContext(), "Sharing not available for grouped items", Toast.LENGTH_SHORT).show();
            return;
        }
        shareRecording(recording.getFile());
    }

    @Override
    public void onDelete(Recording recording) {
        if (isGroupByContact && currentContactFilter == null) {
            String groupKey = recording.getGroupKey();
            List<Recording> recordingsToDelete = allRecordings.stream()
                    .filter(r -> r.getGroupKey().equals(groupKey))
                    .collect(Collectors.toList());
            
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.delete_confirmation)
                    .setMessage("Delete " + recordingsToDelete.size() + " recordings for " + groupKey + "?")
                    .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                        int deleted = 0;
                        for (Recording rec : recordingsToDelete) {
                            if (rec.getFile().delete()) {
                                deleted++;
                            }
                        }
                        Toast.makeText(requireContext(), "Deleted " + deleted + " recordings", Toast.LENGTH_SHORT).show();
                        loadRecordings();
                    })
                    .setNegativeButton(android.R.string.no, null)
                    .show();
        } else {
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.delete_confirmation)
                    .setMessage(recording.getFile().getName())
                    .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                        if (recording.getFile().delete()) {
                            loadRecordings();
                        } else {
                            Toast.makeText(requireContext(), "Failed to delete", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton(android.R.string.no, null)
                    .show();
        }
    }

    @Override
    public void onLongPress(Recording recording, int position) {
        if (isGroupByContact && currentContactFilter == null) return;
        adapter.setSelectionMode(true);
        adapter.toggleSelection(position);
    }

    private void shareRecording(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(requireContext(), 
                    requireContext().getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("audio/*");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, getString(R.string.share_recording)));
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Error sharing: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void shareSelectedRecordings() {
        List<Recording> selected = adapter.getSelectedRecordings();
        if (selected.isEmpty()) return;

        if (selected.size() == 1) {
            shareRecording(selected.get(0).getFile());
            adapter.clearSelection();
            return;
        }

        ArrayList<Uri> uris = new ArrayList<>();
        for (Recording rec : selected) {
            try {
                Uri uri = FileProvider.getUriForFile(requireContext(),
                        requireContext().getPackageName() + ".fileprovider", rec.getFile());
                uris.add(uri);
            } catch (Exception ignored) {}
        }

        if (!uris.isEmpty()) {
            Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
            intent.setType("audio/*");
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, getString(R.string.share_recordings)));
        }
        adapter.clearSelection();
    }

    private void deleteSelectedRecordings() {
        List<Recording> selected = adapter.getSelectedRecordings();
        if (selected.isEmpty()) return;

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete_confirmation)
                .setMessage(getString(R.string.delete_multiple_confirmation, selected.size()))
                .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                    int deleted = 0;
                    for (Recording rec : selected) {
                        if (rec.getFile().delete()) {
                            deleted++;
                        }
                    }
                    Toast.makeText(requireContext(), "Deleted " + deleted + " recordings", Toast.LENGTH_SHORT).show();
                    adapter.clearSelection();
                    loadRecordings();
                })
                .setNegativeButton(android.R.string.no, null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }
}
