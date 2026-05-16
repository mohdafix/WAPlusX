package com.wmods.wppenhacer.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.ui.models.PropOverride;

import java.util.List;

public class PropAdapter extends RecyclerView.Adapter<PropAdapter.ViewHolder> {

    private final List<PropOverride> propList;
    private final OnPropActionListener listener;

    public interface OnPropActionListener {
        void onEdit(PropOverride prop);
        void onDelete(PropOverride prop);
        void onExport(PropOverride prop);
    }

    public PropAdapter(List<PropOverride> propList, OnPropActionListener listener) {
        this.propList = propList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_prop, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PropOverride prop = propList.get(position);
        
        holder.textPropId.setText("ID: " + prop.getId());
        holder.textDescription.setText(prop.getDescription().isEmpty() ? "No description" : prop.getDescription());
        holder.textType.setText(prop.getType().substring(0, 1).toUpperCase() + prop.getType().substring(1));
        
        Object value = "boolean".equals(prop.getType()) ? prop.isBooleanValue() : prop.getIntegerValue();
        holder.textValue.setText("Value: " + value);

        holder.itemView.setOnClickListener(v -> listener.onEdit(prop));
        holder.btnDelete.setOnClickListener(v -> listener.onDelete(prop));
        holder.btnExport.setOnClickListener(v -> listener.onExport(prop));
    }

    @Override
    public int getItemCount() {
        return propList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textType, textPropId, textDescription, textValue;
        ImageButton btnDelete, btnExport;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            textType = itemView.findViewById(R.id.text_type);
            textPropId = itemView.findViewById(R.id.text_prop_id);
            textDescription = itemView.findViewById(R.id.text_description);
            textValue = itemView.findViewById(R.id.text_value);
            btnDelete = itemView.findViewById(R.id.button_delete);
            btnExport = itemView.findViewById(R.id.button_export);
        }
    }
}
