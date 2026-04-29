package com.example.myapplication.db;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {LocalVitalSign.class, LocalSession.class, LocalUser.class}, version = 4, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    public abstract VitalSignDao vitalSignDao();
    public abstract UserDao userDao();

    private static volatile AppDatabase instance;
    private static final int NUMBER_OF_THREADS = 4;
    public static final java.util.concurrent.ExecutorService databaseWriteExecutor =
            java.util.concurrent.Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    public static AppDatabase getInstance(final android.content.Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = androidx.room.Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "vitals_db")
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return instance;
    }
}
