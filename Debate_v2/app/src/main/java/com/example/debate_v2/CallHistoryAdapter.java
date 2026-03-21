package com.example.debate_v2;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class CallHistoryAdapter
        extends RecyclerView.Adapter<CallHistoryAdapter.VH> {

    public interface OnCallClickListener {
        void onCallClicked(CallHistoryItem item);
    }

    private final List<CallHistoryItem> items;
    private final OnCallClickListener listener;

    public CallHistoryAdapter(List<CallHistoryItem> items,
                              OnCallClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_call_history, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        CallHistoryItem item = items.get(position);

        h.txtTitle.setText("Group Call");
        h.txtWinner.setText("Winner: " + item.winnerFirebaseUid);

        h.itemView.setOnClickListener(v ->
                listener.onCallClicked(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView txtTitle, txtWinner;

        VH(View v) {
            super(v);
            txtTitle = v.findViewById(R.id.txtCallTitle);
            txtWinner = v.findViewById(R.id.txtWinner);
        }
    }
}
