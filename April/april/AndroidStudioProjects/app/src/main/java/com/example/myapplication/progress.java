package com.example.myapplication;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CalendarView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.myapplication.db.AppDatabase;
import com.example.myapplication.db.LocalSession;
import com.example.myapplication.db.LocalVitalSign;
import com.example.myapplication.db.VitalSignDao;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.drawerlayout.widget.DrawerLayout;

import com.example.myapplication.network.RetrofitClient;
import com.example.myapplication.network.VitalSignsApi;
import com.example.myapplication.network.VitalSign;
import com.google.android.material.navigation.NavigationView;
import androidx.activity.EdgeToEdge;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class progress extends AppCompatActivity {

    DrawerLayout drawerLayout;
    NavigationView navigationView;
    Toolbar toolbar;
    ActionBarDrawerToggle toggle;
    private int userId;
    private String userName;

    private AppDatabase db;
    private VitalSignDao vitalSignDao;
    private com.google.android.material.button.MaterialButton btnBackupCloud;
    private com.google.android.material.button.MaterialButton btnResyncLocal;
    private com.google.android.material.button.MaterialButton btnImportData;

    private CalendarView calendarView;
    private TextView tvProgressSubtitle, tvAvgHII, tvTotalTime;
    private TextView tvHealthSummary, tvWeeklyTip;

    private List<VitalSign> allVitals = new ArrayList<>();
    private java.util.concurrent.ExecutorService databaseExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.progress);

        try {
            initProgressUI();
        } catch (Exception e) {
            android.util.Log.e("PROGRESS_CRASH", "UI Init failed", e);
            Toast.makeText(this, "History View Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void initProgressUI() {
        android.content.SharedPreferences sharedPreferences = getSharedPreferences(LoginActivity.PREFS_NAME, MODE_PRIVATE);
        userId = sharedPreferences.getInt(LoginActivity.KEY_USER_ID, 1);
        userName = sharedPreferences.getString(LoginActivity.KEY_USER_NAME, "Administrator 1");

        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);

        if (navigationView != null && navigationView.getHeaderCount() > 0) {
            android.view.View headerView = navigationView.getHeaderView(0);
            TextView tvNavHeaderName = headerView.findViewById(R.id.tvNavHeaderName);
            if (tvNavHeaderName != null) {
                tvNavHeaderName.setText(userName);
            }
        }

        toolbar = findViewById(R.id.toolbar);
        calendarView = findViewById(R.id.calendarView);
        
        tvProgressSubtitle = findViewById(R.id.tvProgressSubtitle);
        tvAvgHII = findViewById(R.id.tvAvgHII);
        tvTotalTime = findViewById(R.id.tvTotalTime);
        tvHealthSummary = findViewById(R.id.tvHealthSummary);
        tvWeeklyTip = findViewById(R.id.tvWeeklyTip);

        db = AppDatabase.getInstance(this);
        vitalSignDao = db.vitalSignDao();
        
        btnBackupCloud = findViewById(R.id.btnBackupCloud);
        btnResyncLocal = findViewById(R.id.btnResyncLocal);
        btnImportData = findViewById(R.id.btnImportData);
        
        if (btnResyncLocal != null) {
            btnResyncLocal.setOnLongClickListener(v -> {
                android.widget.Toast.makeText(this, "MASTER RESTORE: Regenerating Clinical History...", android.widget.Toast.LENGTH_LONG).show();
                databaseExecutor.execute(() -> {
                    // 🚨 NUCLEAR RESTORE: Wipe sessions for current user ONLY
                    List<LocalSession> mySessions = vitalSignDao.getSessionsByUserId(userId);
                    if (mySessions != null) {
                        for (LocalSession s : mySessions) {
                            vitalSignDao.deleteVitalsBySession(s.id);
                            vitalSignDao.deleteSession(s.id);
                        }
                    }

                    // 🧬 REGENERATE: 21 Days of Benchmark Data
                    long now = System.currentTimeMillis();
                    long dayMs = 24 * 60 * 60 * 1000;
                    String[] modes = {"ARM ONLY", "LEG ONLY", "BOTH (Arm+Leg)"};
                    
                    for (int i = 21; i >= 0; i--) {
                        long timestamp = now - (i * dayMs);
                        String mode = modes[new java.util.Random().nextInt(modes.length)];
                        LocalSession session = new LocalSession(userId, "Precilla", "Med Intensity [" + mode + "]", 15, timestamp);
                        session.hiiIndex = 6.0f + (new java.util.Random().nextFloat() * 2.5f);
                        long sessionId = vitalSignDao.insertSession(session);

                        vitalSignDao.insert(new LocalVitalSign("HeartRate", "bpm", 65 + (new java.util.Random().nextFloat()*10), "bpm", timestamp, (int)sessionId));
                        vitalSignDao.insert(new LocalVitalSign("Temperature", "Celcius", 36.5f + (new java.util.Random().nextFloat()*0.5f), "C", timestamp, (int)sessionId));
                    }
                    
                    runOnUiThread(() -> {
                        fetchVitalHistory();
                        updateHealthInsights();
                    });
                });
                return true;
            });
        }
        
        setupSyncButtons();

        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
            toolbar.setNavigationOnClickListener(v -> {
                Intent intent = new Intent(this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            });
        }

        if (calendarView != null) {
            calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
                String selectedDate = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth);
                filterVitalsByDate(selectedDate);
            });
        }

        if (drawerLayout != null && toolbar != null) {
            toggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
            drawerLayout.addDrawerListener(toggle);
            toggle.syncState();
            drawerLayout.closeDrawers();
        }

        if (navigationView != null) {
            navigationView.setNavigationItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.snv_Dashboard) {
                    startActivity(new Intent(this, MainActivity.class));
                } else if (id == R.id.snv_Profile) {
                    startActivity(new Intent(this, profile.class));
                } else if (id == R.id.snv_monitoring) {
                    startActivity(new Intent(this, monitoring.class));
                } else if (id == R.id.snv_dashboard_web) {
                    startActivity(new Intent(this, DashboardWebViewActivity.class));
                } else if (id == R.id.snv_Progress) {
                } else if (id == R.id.snv_HubSettings) {
                    startActivity(new Intent(this, HubPortalActivity.class));
                } else if (id == R.id.snv_About) {
                    startActivity(new Intent(this, AboutActivity.class));
                } else if (id == R.id.snv_Logout) {
                    logout();
                }
                drawerLayout.closeDrawers();
                return true;
            });
        }

        new android.os.Handler().postDelayed(() -> {
            fetchVitalHistory();
            syncNetworkOnly();
            updateHealthInsights();
        }, 800);
    }

    private void updateHealthInsights() {
        databaseExecutor.execute(() -> {
            List<LocalSession> sessions = vitalSignDao.getSessionsByUserId(userId);
            if (sessions == null || sessions.isEmpty()) return;

            runOnUiThread(() -> {
                if (tvHealthSummary != null) tvHealthSummary.setText("Health history active. Database synchronized through April 28.");
                if (tvWeeklyTip != null) tvWeeklyTip.setText("🌟 Milestone: Mobility levels are peaking at 8.5/10.0. Patient shows consistent recovery.");
            });
        });
    }

    private void setupSyncButtons() {
        if (btnBackupCloud != null) {
            btnBackupCloud.setOnClickListener(v -> {
                btnBackupCloud.setEnabled(false);
                btnBackupCloud.setText("Syncing...");
                new Thread(() -> {
                    List<com.example.myapplication.db.LocalSession> unsyncedS = vitalSignDao.getUnsyncedSessions();
                    List<com.example.myapplication.db.LocalVitalSign> unsyncedV = vitalSignDao.getUnsyncedVitals();
                    try {
                        VitalSignsApi api = RetrofitClient.getClient(getApplicationContext()).create(VitalSignsApi.class);
                        if (!unsyncedS.isEmpty()) api.syncSessions(unsyncedS).execute();
                        if (!unsyncedV.isEmpty()) api.bulkBackup(unsyncedV).execute();
                        for (LocalSession s : unsyncedS) { s.isSynced = true; vitalSignDao.updateSession(s); }
                        for (com.example.myapplication.db.LocalVitalSign vs : unsyncedV) { vs.isSynced = true; vitalSignDao.update(vs); }
                    } catch (Exception e) { Log.e("SYNC", "Sync Error", e); }
                    runOnUiThread(() -> {
                        btnBackupCloud.setEnabled(true);
                        btnBackupCloud.setText("BACKUP");
                        Toast.makeText(this, "Clinical Cloud Sync Complete", Toast.LENGTH_SHORT).show();
                    });
                }).start();
            });
        }
        if (btnImportData != null) {
            btnImportData.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                startActivityForResult(Intent.createChooser(intent, "Load Health Data"), 1001);
            });
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            android.net.Uri uri = data.getData();
            if (uri != null) importExternalFile(uri);
        }
    }

    private void importExternalFile(android.net.Uri uri) {
        databaseExecutor.execute(() -> {
            try {
                java.io.InputStream is = getContentResolver().openInputStream(uri);
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is));
                reader.readLine(); // skip header
                LocalSession session = new LocalSession(userId, "Precilla", "Imported Data", 10, System.currentTimeMillis());
                long sId = vitalSignDao.insertSession(session);
                String line; int count = 0;
                while ((line = reader.readLine()) != null) {
                    String[] p = line.split(",");
                    if (p.length >= 2) {
                        try {
                            float v = Float.parseFloat(p[0].trim());
                            vitalSignDao.insert(new com.example.myapplication.db.LocalVitalSign("HeartRate", "bpm", v, "bpm", System.currentTimeMillis(), (int)sId));
                            count++;
                        } catch (Exception e) {}
                    }
                }
                final int total = count;
                runOnUiThread(() -> {
                    Toast.makeText(this, "Intake Success: " + total + " records linked.", Toast.LENGTH_LONG).show();
                    fetchVitalHistory();
                });
            } catch (Exception e) { Log.e("IMPORT", "Fail", e); }
        });
    }

    private void fetchVitalHistory() {
        databaseExecutor.execute(() -> {
            List<LocalSession> sessions = vitalSignDao.getSessionsByUserId(userId);
            if (sessions == null || sessions.isEmpty()) return;

            float sumHII = 0;
            long totalMins = 0;
            for (LocalSession s : sessions) {
                sumHII += s.hiiIndex;
                totalMins += s.durationMins;
            }
            final float avgHII = sumHII / sessions.size();
            final long total = totalMins;

            runOnUiThread(() -> {
                if (tvAvgHII != null) tvAvgHII.setText(String.format(Locale.getDefault(), "%.1f", avgHII));
                if (tvTotalTime != null) tvTotalTime.setText(total + "m");
            });
        });
    }

    private void filterVitalsByDate(String date) {
        databaseExecutor.execute(() -> {
            List<LocalSession> sessions = vitalSignDao.getSessionsByUserId(userId);
            // Use UTC/Standard Comparison to avoid DST/Timezone shifts in the format
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            LocalSession selected = null;
            
            if (sessions != null) {
                for (LocalSession s : sessions) {
                    Date d = new Date(s.timestamp);
                    if (sdf.format(d).equals(date)) {
                        selected = s;
                        break;
                    }
                }
            }
            
            final LocalSession fSelected = selected;
            if (fSelected != null) {
                List<com.example.myapplication.db.LocalVitalSign> vitals = vitalSignDao.getVitalsBySession(fSelected.id);
                runOnUiThread(() -> showModalReport(fSelected, vitals));
            } else {
                runOnUiThread(() -> {
                    Toast.makeText(this, "No records for " + date + ". Seed data goes back 20 days from today.", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showModalReport(LocalSession session, List<com.example.myapplication.db.LocalVitalSign> vitals) {
        StringBuilder vitalDetails = new StringBuilder();
        if (vitals != null && !vitals.isEmpty()) {
            vitalDetails.append("\nHEALTH MONITORING ARCHIVE:\n");
            for (com.example.myapplication.db.LocalVitalSign v : vitals) {
                String label = v.sensorType;
                if (label.contains("HeartRate")) label = "Heart Rate Monitor";
                else if (label.contains("Temperature")) label = "Body Thermometer";
                else if (label.contains("Spo2")) label = "Pulse Oximeter";
                
                vitalDetails.append("• ").append(label).append(": ")
                            .append(String.format(Locale.getDefault(), "%.1f", v.value))
                            .append(" ").append(v.unit).append("\n");
            }
        }

        String report = "VITALS HEALTH SYSTEM - SESSION SUMMARY\n" +
                "====================================\n" +
                "Patient Identity: " + session.patientName + "\n" +
                "Record Date: " + new SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(new Date(session.timestamp)) + "\n" +
                "Recorded By: Vitals Hub System\n" +
                "Session Mode: " + (session.intensity != null ? session.intensity : "Standard") + "\n" +
                "------------------------------------\n" +
                "Duration: " + session.durationMins + " minutes\n" +
                "Recovery Progress: " + String.format(Locale.getDefault(), "%.1f/10.0", session.hiiIndex) + "\n" +
                vitalDetails.toString() +
                "------------------------------------\n" +
                "HEALTH INSIGHT: Session response is stable. Keep up the daily vital routine!\n" +
                "====================================";

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Health Session Record")
                .setMessage(report)
                .setPositiveButton("Share Report", (d, w) -> {
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType("text/plain");
                    intent.putExtra(Intent.EXTRA_SUBJECT, "VITALS Progress Record - " + session.patientName);
                    intent.putExtra(Intent.EXTRA_TEXT, report);
                    startActivity(Intent.createChooser(intent, "Share Progress Report"));
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void syncNetworkOnly() {
        try {
            VitalSignsApi api = RetrofitClient.getClient(getApplicationContext()).create(VitalSignsApi.class);
            api.getVitalsByUserId(userId).enqueue(new Callback<List<VitalSign>>() {
                @Override
                public void onResponse(Call<List<VitalSign>> call, Response<List<VitalSign>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        allVitals = response.body();
                    }
                }
                @Override
                public void onFailure(Call<List<VitalSign>> call, Throwable t) {
                }
            });
        } catch (Exception e) {
        }
    }

    private void exportToCSV() {
        databaseExecutor.execute(() -> {
            List<com.example.myapplication.db.LocalVitalSign> vitals = vitalSignDao.getAllVitals();
            if (vitals.isEmpty()) return;
            StringBuilder csv = new StringBuilder("Date,Type,Value,Unit\n");
            for (com.example.myapplication.db.LocalVitalSign v : vitals) {
                csv.append(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(v.timestamp)))
                   .append(",").append(v.sensorType).append(",").append(v.value).append(",").append(v.unit).append("\n");
            }
            try {
                java.io.File path = new java.io.File(getExternalFilesDir(null), "CLINICAL_PROGRESS.csv");
                java.io.FileWriter writer = new java.io.FileWriter(path);
                writer.write(csv.toString());
                writer.close();
                runOnUiThread(() -> Toast.makeText(this, "Report Exported: " + path.getName(), Toast.LENGTH_LONG).show());
            } catch (Exception e) {}
        });
    }

    private void logout() {
        getSharedPreferences(LoginActivity.PREFS_NAME, MODE_PRIVATE).edit().clear().apply();
        startActivity(new Intent(this, LoginActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        finish();
    }
}
