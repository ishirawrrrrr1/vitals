package com.example.myapplication.network;

public class VitalSign {
    public int id;
    public float heart_rate;
    public float temperature;
    public float spo2;
    public String blood_pressure;
    public String created_at;

    public VitalSign() {}

    public VitalSign(float heart_rate, float temperature, float spo2, String blood_pressure) {
        this.heart_rate = heart_rate;
        this.temperature = temperature;
        this.spo2 = spo2;
        this.blood_pressure = blood_pressure;
    }
}
