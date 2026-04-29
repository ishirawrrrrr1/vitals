package com.example.myapplication;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SessionHistoryAdapter extends RecyclerView.Adapter<SessionHistoryAdapter.ViewHolder> {

    private List<SessionRecord> sessions = new ArrayList<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
    private OnShareClickListener shareListener;

    public interface OnShareClickListener {
        void onShare(SessionRecord session);
    }

    public SessionHistoryAdapter(OnShareClickListener listener) {
        this.shareListener = listener;
    }

    public static class SessionRecord {
        public int id;
        public String title;
        public long timestamp;
        public String mode;
        public String patientGender;  // Patient's gender classification (MALE/FEMALE)
        public String gatheredBy;     // Admin/Technician who ran the session
        public String details;
        public float hiiIndex;
        public boolean isExpanded = false;

        public SessionRecord(int id, String title, long timestamp, String mode, 
                             String patientGender, String gatheredBy, String details, float hiiIndex) {
            this.id = id;
            this.title = title;
            this.timestamp = timestamp;
            this.mode = mode;
            this.patientGender = patientGender;
            this.gatheredBy = gatheredBy;
            this.details = details;
            this.hiiIndex = hiiIndex;
        }
    }

    public void setSessions(List<SessionRecord> newSessions) {
        this.sessions = newSessions;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_session_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SessionRecord session = sessions.get(position);
        if (session == null || holder == null) return;

        if (holder.tvTitle != null) holder.tvTitle.setText(session.title != null ? session.title : "Unnamed Session");
        if (holder.tvTime != null) holder.tvTime.setText(timeFormat.format(new Date(session.timestamp)));
        if (holder.tvMode != null) holder.tvMode.setText("Mode: " + (session.mode != null ? session.mode : "Auto"));
        
        if (holder.tvRole != null) {
            String gathered = session.gatheredBy != null ? session.gatheredBy : "System";
            String gender = session.patientGender != null ? session.patientGender : "Not Set";
            holder.tvRole.setText("By: " + gathered + " | Patient: " + gender);
        }
        
        if (holder.tvDetails != null) holder.tvDetails.setText(session.details != null ? session.details : "");
        if (holder.tvHII != null) holder.tvHII.setText(String.format(Locale.getDefault(), "HII: %.1f", session.hiiIndex));

        if (holder.layoutDetails != null) holder.layoutDetails.setVisibility(session.isExpanded ? View.VISIBLE : View.GONE);
        if (holder.ivExpand != null) holder.ivExpand.setRotation(session.isExpanded ? 180 : 0);

        holder.itemView.setOnClickListener(v -> {
            session.isExpanded = !session.isExpanded;
            notifyItemChanged(position);
        });

        if (holder.btnShare != null) {
            holder.btnShare.setOnClickListener(v -> {
                if (shareListener != null) shareListener.onShare(session);
            });
        }
    }

    @Override
    public int getItemCount() {
        return sessions.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvTime, tvMode, tvRole, tvDetails, tvHII;
        LinearLayout layoutDetails;
        ImageView ivExpand;
        com.google.android.material.button.MaterialButton btnShare;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvSessionTitle);
            tvTime = itemView.findViewById(R.id.tvSessionTime);
            tvHII = itemView.findViewById(R.id.tvSessionHII);
            tvMode = itemView.findViewById(R.id.tvSessionMode);
            tvRole = itemView.findViewById(R.id.tvSessionRole);
            tvDetails = itemView.findViewById(R.id.tvSessionDetails);
            layoutDetails = itemView.findViewById(R.id.layoutDetails);
            ivExpand = itemView.findViewById(R.id.ivExpand);
            btnShare = itemView.findViewById(R.id.btnShareReport);
        }
    }
}
