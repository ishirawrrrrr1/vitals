package com.example.myapplication.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertUser(LocalUser user);

    @Query("SELECT * FROM local_users WHERE (username = :u OR email = :u) AND password = :p LIMIT 1")
    LocalUser login(String u, String p);

    @Query("SELECT * FROM local_users WHERE email = :email LIMIT 1")
    LocalUser findByEmail(String email);
    
    @Query("SELECT * FROM local_users WHERE isSynced = 0")
    List<LocalUser> getUnsyncedUsers();

    @androidx.room.Update
    void update(LocalUser user);

    @Query("DELETE FROM local_users")
    void deleteAll();
}
