package com.example.myapplication.network;

public class SessionCurrentResponse {
    public String status; // 'NONE', 'INITIAL', 'RUNNING', 'COMPLETED'
    public int session_id;
    public String intensity;
    public int duration_mins;
    public String start_time;
    public int total_seconds;
    public int elapsed_seconds;
    public Averages averages;
    public Float hii_index;
    public java.util.Map<String, String> baseline;
    public java.util.Map<String, String> outcome;

    public static class Averages {
        public Float avg_hr;
        public Float avg_temp;
        public Float avg_spo2;
    }
}
