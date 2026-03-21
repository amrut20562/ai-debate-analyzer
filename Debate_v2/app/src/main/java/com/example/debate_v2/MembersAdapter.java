package com.example.debate_v2;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class MembersAdapter extends RecyclerView.Adapter<MembersAdapter.MemberViewHolder> {

    private List<IdeaActivity.Member> members;
    private OnChatClickListener listener;

    public interface OnChatClickListener {
        void onChatClick(IdeaActivity.Member member);
    }

    public MembersAdapter(List<IdeaActivity.Member> members, OnChatClickListener listener) {
        this.members = members;
        this.listener = listener;
    }

    @NonNull
    @Override
    public MemberViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_member, parent, false);
        return new MemberViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MemberViewHolder holder, int position) {
        IdeaActivity.Member member = members.get(position);
        holder.memberName.setText(member.getName());

        if (member.isOnline()) {
            holder.memberStatus.setText("Online");
            holder.memberStatus.setTextColor(holder.itemView.getContext()
                    .getResources().getColor(android.R.color.holo_green_dark));
            holder.onlineIndicator.setVisibility(View.VISIBLE);
        } else {
            holder.memberStatus.setText("Offline");
            holder.memberStatus.setTextColor(holder.itemView.getContext()
                    .getResources().getColor(android.R.color.darker_gray));
            holder.onlineIndicator.setVisibility(View.GONE);
        }

        holder.chatButton.setOnClickListener(v -> listener.onChatClick(member));
    }

    @Override
    public int getItemCount() {
        return members.size();
    }

    static class MemberViewHolder extends RecyclerView.ViewHolder {
        TextView memberName, memberStatus;
        View onlineIndicator;
        CardView chatButton;

        public MemberViewHolder(@NonNull View itemView) {
            super(itemView);
            memberName = itemView.findViewById(R.id.memberName);
            memberStatus = itemView.findViewById(R.id.memberStatus);
            onlineIndicator = itemView.findViewById(R.id.onlineIndicator);
            chatButton = itemView.findViewById(R.id.chatButton);
        }
    }
}
