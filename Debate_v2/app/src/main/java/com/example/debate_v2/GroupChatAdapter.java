package com.example.debate_v2;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class GroupChatAdapter extends RecyclerView.Adapter<GroupChatAdapter.ChatViewHolder> {

    private final List<MessageModel> messages = new ArrayList<>();
    private final Context context;
    private final String currentUid;

    public GroupChatAdapter(Context context, String currentUid) {
        this.context = context;
        this.currentUid = currentUid;
    }

    public void addMessage(String message, String username, String uid, long timestamp) {
        messages.add(new MessageModel(message, username, uid, timestamp));
        notifyDataSetChanged();
    }

    public void clearMessages() {
        messages.clear();
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_group_chat, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        MessageModel model = messages.get(position);

        holder.message.setText(model.message);
        holder.username.setText(model.username);
        holder.username.setVisibility(View.VISIBLE);  // FORCE VISIBLE


        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        holder.time.setText(sdf.format(new Date(model.timestamp)));

        if (model.uid.equals(currentUid)) {
            holder.container.setBackgroundResource(R.drawable.bg_chat_me);
            holder.parent.setGravity(android.view.Gravity.END);
        } else {
            holder.container.setBackgroundResource(R.drawable.bg_chat_other);
            holder.parent.setGravity(android.view.Gravity.START);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class ChatViewHolder extends RecyclerView.ViewHolder {
        TextView message, username, time;
        LinearLayout container, parent;

        public ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            message = itemView.findViewById(R.id.messageText);
            username = itemView.findViewById(R.id.usernameText);
            time = itemView.findViewById(R.id.timeText);
            container = itemView.findViewById(R.id.bubbleContainer);
            parent = itemView.findViewById(R.id.parentLayout);
        }
    }

    static class MessageModel {
        String message, username, uid;
        long timestamp;

        MessageModel(String message, String username, String uid, long timestamp) {
            this.message = message;
            this.username = username;
            this.uid = uid;
            this.timestamp = timestamp;
        }
    }
}
