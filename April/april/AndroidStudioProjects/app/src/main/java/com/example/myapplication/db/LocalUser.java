package com.example.myapplication.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "local_users")
public class LocalUser {
    @PrimaryKey(autoGenerate = true)
    public int id;
    
    public String username;
    public String email;
    public String password;
    public String role;
    public boolean isSynced = false; // 🔄 New in v7.1: Track Mirroring state

    public LocalUser(String username, String email, String password, String role) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.role = role == null ? "Patient" : role;
        this.isSynced = false;
    }
}
