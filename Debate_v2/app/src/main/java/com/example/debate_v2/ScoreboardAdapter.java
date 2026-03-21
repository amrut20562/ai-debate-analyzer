package com.example.debate_v2;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class ScoreboardAdapter
        extends RecyclerView.Adapter<ScoreboardAdapter.VH> {

    private final List<ScoreboardItem> items = new ArrayList<>();

    public void setItems(List<ScoreboardItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }
    public List<ScoreboardItem> getItems() {
        return items;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_scoreboard, parent, false);
        return new VH(v);
    }


    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        ScoreboardItem item = items.get(pos);
        String name =
                item.displayName != null && !item.displayName.isEmpty()
                        ? item.displayName
                        : item.firebaseUid;

        h.txtName.setText(name);
        h.txtScore.setText(String.valueOf(item.score));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView txtName, txtScore;

        VH(View v) {
            super(v);
            txtName = v.findViewById(R.id.txtName);
            txtScore = v.findViewById(R.id.txtScore);
        }
    }
}
