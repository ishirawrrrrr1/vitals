package com.example.myapplication.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface VitalSignDao {
    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    void insert(LocalVitalSign vitalSign);

    @Query("SELECT * FROM local_vitals WHERE isSynced = 0")
    List<LocalVitalSign> getUnsyncedVitals();

    @Update
    void update(LocalVitalSign vitalSign);

    @Query("SELECT * FROM local_vitals ORDER BY timestamp DESC")
    List<LocalVitalSign> getAllVitals();

    @Query("SELECT * FROM local_vitals WHERE sessionId = :sessionId ORDER BY timestamp DESC")
    List<LocalVitalSign> getVitalsBySession(int sessionId);

    @Query("DELETE FROM local_vitals WHERE isSynced = 1 AND timestamp < :olderThan")
    void deleteOldSyncedVitals(long olderThan);

    @Query("DELETE FROM local_vitals")
    void deleteAll();

    // --- Session Support ---
    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    long insertSession(LocalSession session);

    @Update
    void updateSession(LocalSession session);

    @Query("SELECT * FROM local_sessions WHERE isSynced = 0")
    List<LocalSession> getUnsyncedSessions();

    @Query("SELECT * FROM local_sessions WHERE userId = :uId ORDER BY timestamp DESC")
    List<LocalSession> getSessionsByUserId(int uId);

    @Query("SELECT * FROM local_sessions ORDER BY timestamp DESC")
    List<LocalSession> getAllSessions();

    @Query("SELECT * FROM local_sessions WHERE patientName = :name ORDER BY timestamp DESC LIMIT 1")
    LocalSession getLastSessionByPatient(String name);

    @Query("SELECT * FROM local_sessions WHERE patientName = :name ORDER BY timestamp ASC LIMIT 1")
    LocalSession getFirstSessionByPatient(String name);

    @Query("DELETE FROM local_sessions WHERE id = :sId")
    void deleteSession(int sId);

    @Query("DELETE FROM local_vitals WHERE sessionId = :sId")
    void deleteVitalsBySession(int sId);
}
