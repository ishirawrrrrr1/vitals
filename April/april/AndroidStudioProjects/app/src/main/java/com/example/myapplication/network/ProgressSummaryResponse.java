package com.example.myapplication.network;

import java.util.List;

public class ProgressSummaryResponse {
    public boolean success;
    public Summary summary;
    public List<SessionItem> sessions;

    public static class Summary {
        public int session_count;
        public float avg_hii;
        public int total_mins;
        public String last_session_at;
    }

    public static class SessionItem {
        public int id;
        public String patient_name;
        public String intensity;
        public int duration_mins;
        public String status;
        public float hii_index;
        public String created_at;
        public String ended_at;
        public String clinical_summary;
    }
}
