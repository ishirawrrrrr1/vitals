package com.example.myapplication.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "local_vitals")
public class LocalVitalSign {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String sensorType;
    public String metric;
    public float value;
    public String unit;
    public long timestamp;
    public int sessionId;
    public boolean isSynced;

    public LocalVitalSign(String sensorType, String metric, float value, String unit, long timestamp, int sessionId) {
        this.sensorType = sensorType;
        this.metric = metric;
        this.value = value;
        this.unit = unit;
        this.timestamp = timestamp;
        this.sessionId = sessionId;
        this.isSynced = false;
    }
}
