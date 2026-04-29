package com.example.myapplication.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "local_sessions")
public class LocalSession {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public int userId;
    public String patientName;
    public String intensity;
    public int durationMins;
    public long timestamp; // Start time
    public long endTime;
    
    // Intake Metadata
    public String gender;
    public String ageRange;
    public String strokeDuration;
    
    public String baselineJson; // Averaged HR/Temp at start
    public String outcomeJson;  // Averaged HR/Temp at end
    public float hiiIndex;      // Clinical Improvement Score
    public String clinicalSummary;
    
    public boolean isSynced;

    public LocalSession(int userId, String patientName, String intensity, int durationMins, long timestamp) {
        this.userId = userId;
        this.patientName = patientName;
        this.intensity = intensity;
        this.durationMins = durationMins;
        this.timestamp = timestamp;
        this.isSynced = false;
        this.hiiIndex = 0;
        this.clinicalSummary = "";
        this.gender = "Unknown";
        this.ageRange = "40-50";
        this.strokeDuration = "Recent";
    }
}
