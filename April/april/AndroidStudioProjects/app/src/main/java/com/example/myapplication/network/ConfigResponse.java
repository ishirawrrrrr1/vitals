package com.example.myapplication.network;

import com.google.gson.annotations.SerializedName;

public class ConfigResponse {
    @SerializedName("temp_offset")
    public float tempOffset;
    
    @SerializedName("bpm_offset")
    public int bpmOffset;
    
    @SerializedName("spo2_offset")
    public int spo2Offset;
    
    @SerializedName("current_mode")
    public int currentMode;
    
    @SerializedName("current_force")
    public int currentForce;
}
