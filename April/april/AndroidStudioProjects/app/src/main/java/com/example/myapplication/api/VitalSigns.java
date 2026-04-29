package com.example.myapplication.api;

import com.google.gson.annotations.SerializedName;

public class VitalSigns {
    @SerializedName("heart_rate")
    private float heartRate;
    
    @SerializedName("temperature")
    private float temperature;
    
    @SerializedName("spo2")
    private float spo2;
    
    @SerializedName("blood_pressure")
    private String bloodPressure;

    // Getters
    public float getHeartRate() { return heartRate; }
    public float getTemperature() { return temperature; }
    public float getSpo2() { return spo2; }
    public String getBloodPressure() { return bloodPressure; }
}
