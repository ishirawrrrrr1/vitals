package com.example.myapplication.network;

public class SessionStartRequest {
    public int user_id;
    public String intensity;
    public int duration_mins;
    public String patient_name;

    public SessionStartRequest(int user_id, String intensity, int duration_mins, String patient_name) {
        this.user_id = user_id;
        this.intensity = intensity;
        this.duration_mins = duration_mins;
        this.patient_name = patient_name;
    }
}
