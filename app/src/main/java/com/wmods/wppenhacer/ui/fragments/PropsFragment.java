package com.wmods.wppenhacer.ui.fragments;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.wmods.wppenhacer.App;
import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.adapter.PropAdapter;
import com.wmods.wppenhacer.databinding.FragmentPropsBinding;
import com.wmods.wppenhacer.ui.fragments.base.BaseFragment;
import com.wmods.wppenhacer.ui.models.PropOverride;
import com.wmods.wppenhacer.utils.FilePicker;
import com.wmods.wppenhacer.xposed.core.FeatureLoader;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import rikka.core.util.IOUtils;

public class PropsFragment extends BaseFragment implements PropAdapter.OnPropActionListener, MenuProvider {

    private FragmentPropsBinding propsBinding;
    private List<PropOverride> propList = new ArrayList<>();
    private PropAdapter adapter;
    private SharedPreferences prefs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        propsBinding = FragmentPropsBinding.inflate(inflater, container, false);
        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        
        setupToolbar(propsBinding.toolbar, null, R.string.prop_overrides, R.menu.prop_overrides_menu);
        
        initRecyclerView();
        loadProps();
        
        propsBinding.fabAddProp.setOnClickListener(v -> showAddEditDialog(null));
        propsBinding.fabImportProp.setOnClickListener(v -> importProps());
        propsBinding.btnImportEmpty.setOnClickListener(v -> importProps());
        
        return propsBinding.getRoot();
    }

    private void initRecyclerView() {
        adapter = new PropAdapter(propList, this);
        propsBinding.recyclerProps.setLayoutManager(new LinearLayoutManager(getContext()));
        propsBinding.recyclerProps.setAdapter(adapter);
    }

    private void loadProps() {
        propList.clear();
        String json = prefs.getString("prop_overrides", "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                propList.add(PropOverride.fromJsonObject(array.getJSONObject(i)));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        updateUI();
    }

    private void saveProps() {
        JSONArray array = new JSONArray();
        for (PropOverride prop : propList) {
            try {
                array.put(prop.toJsonObject());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        prefs.edit().putString("prop_overrides", array.toString()).apply();
        updateUI();
        
        // Notify Wpp that restart is needed
        App.getInstance().restartApp(FeatureLoader.PACKAGE_WPP);
    }

    private void updateUI() {
        adapter.notifyDataSetChanged();
        propsBinding.textPropCount.setText(propList.size() + " Prop Overrides");
        propsBinding.emptyState.setVisibility(propList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showAddEditDialog(@Nullable PropOverride existingProp) {
        View view = getLayoutInflater().inflate(R.layout.dialog_add_prop, null);
        EditText editId = view.findViewById(R.id.edit_prop_id);
        EditText editDesc = view.findViewById(R.id.edit_prop_desc);
        EditText editValue = view.findViewById(R.id.edit_prop_value);
        RadioGroup groupType = view.findViewById(R.id.group_prop_type);

        if (existingProp != null) {
            editId.setText(String.valueOf(existingProp.getId()));
            editDesc.setText(existingProp.getDescription());
            groupType.check("boolean".equals(existingProp.getType()) ? R.id.radio_boolean : R.id.radio_integer);
            Object val = "boolean".equals(existingProp.getType()) ? existingProp.isBooleanValue() : existingProp.getIntegerValue();
            editValue.setText(String.valueOf(val));
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(existingProp == null ? R.string.prop_add : R.string.prop_edit)
                .setView(view)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    try {
                        int id = Integer.parseInt(editId.getText().toString());
                        String desc = editDesc.getText().toString();
                        String type = groupType.getCheckedRadioButtonId() == R.id.radio_boolean ? "boolean" : "integer";
                        String valueStr = editValue.getText().toString();

                        PropOverride prop = existingProp != null ? existingProp : new PropOverride();
                        prop.setId(id);
                        prop.setDescription(desc);
                        prop.setType(type);
                        if ("boolean".equals(type)) {
                            prop.setBooleanValue(Boolean.parseBoolean(valueStr));
                        } else {
                            prop.setIntegerValue(Integer.parseInt(valueStr));
                        }

                        if (existingProp == null) propList.add(prop);
                        saveProps();
                    } catch (Exception e) {
                        showHint("Invalid input", true);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public void onEdit(PropOverride prop) {
        showAddEditDialog(prop);
    }

    @Override
    public void onDelete(PropOverride prop) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.prop_delete)
                .setMessage("Are you sure you want to delete this override?")
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    propList.remove(prop);
                    saveProps();
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }

    @Override
    public void onExport(PropOverride prop) {
        try {
            String json = prop.toJsonObject().toString(4);
            // Utils.copyToClipboard(requireContext(), json);
            showHint("Individual export not fully implemented, use Export All", true);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_prop_import) {
            importProps();
            return true;
        } else if (item.getItemId() == R.id.action_prop_export) {
            exportAllProps();
            return true;
        }
        return false;
    }

    @Override
    public void onCreateMenu(@NonNull android.view.Menu menu, @NonNull android.view.MenuInflater menuInflater) {
        // Menu is inflated in setupToolbar
    }

    private void exportAllProps() {
        if (propList.isEmpty()) {
            showHint("Nothing to export", true);
            return;
        }
        
        FilePicker.setOnUriPickedListener(uri -> {
            try (var output = requireContext().getContentResolver().openOutputStream(uri)) {
                JSONArray array = new JSONArray();
                for (PropOverride prop : propList) {
                    array.put(prop.toJsonObject());
                }
                if (output != null) {
                    output.write(array.toString(4).getBytes());
                    showHint("Exported successfully", true);
                }
            } catch (Exception e) {
                showHint("Error exporting: " + e.getMessage(), false);
            }
        });
        FilePicker.fileSalve.launch("prop_overrides_backup.json");
    }

    private void importProps() {
        FilePicker.setOnUriPickedListener(uri -> {
            try (var input = requireContext().getContentResolver().openInputStream(uri)) {
                if (input != null) {
                    String data = IOUtils.toString(input);
                    JSONArray array = new JSONArray(data);
                    int importedCount = 0;
                    for (int i = 0; i < array.length(); i++) {
                        PropOverride newProp = PropOverride.fromJsonObject(array.getJSONObject(i));
                        // Duplicate check (by ID)
                        propList.removeIf(p -> p.getId() == newProp.getId());
                        propList.add(newProp);
                        importedCount++;
                    }
                    saveProps();
                    showHint("Imported " + importedCount + " overrides", true);
                }
            } catch (Exception e) {
                showHint("Error importing: " + e.getMessage(), false);
            }
        });
        FilePicker.fileCapture.launch(new String[]{"application/json"});
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        propsBinding = null;
    }
}
