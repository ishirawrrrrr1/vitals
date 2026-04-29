package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Spinner;
import android.graphics.Color;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.view.LayoutInflater;
import android.widget.AutoCompleteTextView;
import android.widget.RadioGroup;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.activity.EdgeToEdge;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.myapplication.network.*;
import com.example.myapplication.db.*;
import com.google.android.material.navigation.NavigationView;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.*;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;

import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import android.view.LayoutInflater;
import android.widget.AutoCompleteTextView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ArrayAdapter;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.appcompat.app.AlertDialog;

import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AlertDialog;
import com.example.myapplication.network.RetrofitClient;

public class monitoring extends AppCompatActivity {

    DrawerLayout drawerLayout;
    NavigationView navigationView;
    Toolbar toolbar;
    ActionBarDrawerToggle toggle;
    TextView tvHeartRate, tvTemperature, tvSpo2, tvBloodPressure;
    TextView tvHeartRateStatus, tvTempStatus, tvSpo2Status, tvHubStatus;
    private View hubStatusDot, viewHrPulse;
    private android.widget.LinearLayout layoutVitalsVisuals;
    private androidx.appcompat.app.AlertDialog vitalsPopup;
    private TextView dlgHR, dlgSpO2, dlgTemp, dlgBP, dlgStatus;
    private boolean isGateOpen = false; // Smart-Valve flag
    private final android.os.Handler waveformHandler = new android.os.Handler();

    private Spinner spinnerIntensity, spinnerDuration, spinnerMode;
    TextView tabAutomatic, tabManual;
    android.widget.LinearLayout layoutAutomatic, layoutManual;
    private com.google.android.material.button.MaterialButton btnStart;
    private TextView btnScanHub, btnSelectPatient, tvActivePatientName;
    
    // Active Patient State (Multi-User v7.0)
    private String selectedPatientName = "New Patient";
    private String selectedGender = "Unknown";
    private String selectedAge = "N/A";
    private String selectedStroke = "N/A";

    // Manual Controls
    private com.google.android.material.button.MaterialButton btnHandRet, btnHandExt, btnHandStop;
    private com.google.android.material.button.MaterialButton btnLegRet, btnLegExt, btnLegStop;
    private androidx.appcompat.widget.SwitchCompat switchGlovePower;
    private com.google.android.material.button.MaterialButton btnCycleForce, btnCycleMode;

    // Intake View
    private TextView tvProgressAlert;
    private androidx.appcompat.widget.SwitchCompat switchVitalsOnly;

    // Session State
    private Timer sessionTimer;
    private long sessionStartTime = 0;
    private String sessionStatus = "IDLE"; 
    private boolean isFinishing = false; 
    private int baselineCount = 0;
    private float baselineSumHR = 0, baselineSumTemp = 0;
    private LocalSession currentLocalSession, initialSession;
    private int userId;
    private int currentSessionId = -1;
    private AppDatabase db;
    private VitalSignDao vitalSignDao;

    // Threading
    private java.util.concurrent.ExecutorService databaseExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();

    // 3-Point Sampling Statistics
    private float sampledHR1 = 0, sampledHR2 = 0, sampledHR3 = 0;
    private int complexityScore = 0; 
    private boolean sample2Captured = false;

    // Graph
    private LineChart vitalsChart;
    private ArrayList<Entry> hrEntries = new ArrayList<>();
    private LineDataSet hrDataSet;
    private int chartXValue = 0;
    private int lastKnownBPM = 0;
    
    private final BroadcastReceiver vitalsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, Intent intent) {
            String action = intent.getAction();
            if (VitalsMonitoringService.ACTION_VITALS_UPDATE.equals(action)) {
                String dataStr = intent.getStringExtra("data");
                if (dataStr != null) handleRealTimeUpdate(dataStr);
            } else if (VitalsMonitoringService.ACTION_HUB_STATUS.equals(action)) {
                boolean connected = intent.getBooleanExtra("connected", false);
                setHubStatusUI(connected);
            } else if (VitalsMonitoringService.ACTION_HARDWARE_BUTTON.equals(action)) {
                String cmd = intent.getStringExtra("command");
                handleHardwareButtonSync(cmd);
            }
        }
    };

    private long lastSessionStartTime = 0;

    private void handleHardwareButtonSync(String cmd) {
        runOnUiThread(() -> {
            Log.d("HardwareSync", "Processing hardware button: " + cmd);
            long now = System.currentTimeMillis();
            
            if ("TOGGLE".equals(cmd)) {
                if ("IDLE".equals(sessionStatus)) {
                    btnStart.performClick(); 
                    lastSessionStartTime = now;
                } else if (now - lastSessionStartTime > 3000) {
                    finishSession(); 
                }
            } else if ("FORCE".equals(cmd)) {
                int next = (spinnerIntensity.getSelectedItemPosition() + 1) % 3;
                spinnerIntensity.setSelection(next);
            } else if ("MODE".equals(cmd)) {
                int next = (spinnerMode.getSelectedItemPosition() + 1) % 3;
                spinnerMode.setSelection(next);
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.monitoring);

        db = AppDatabase.getInstance(this);
        vitalSignDao = db.vitalSignDao();
        
        android.content.SharedPreferences prefs = getSharedPreferences(LoginActivity.PREFS_NAME, MODE_PRIVATE);
        userId = prefs.getInt(LoginActivity.KEY_USER_ID, 1);
        selectedPatientName = prefs.getString(LoginActivity.KEY_USER_NAME, "New Patient");
        selectedGender = prefs.getString(LoginActivity.KEY_GENDER, "Unknown");

        try {
            initUI();
            setupVitalsChart();
            setupStartButton();
        } catch (Exception e) {
            android.util.Log.e("MONITORING_CRASH", "UI Init failed", e);
            Toast.makeText(this, "Display Error: Initializing basic view", Toast.LENGTH_LONG).show();
        }
        
        startService(new Intent(this, VitalsMonitoringService.class));
    }

    private void initUI() {
        toolbar = findViewById(R.id.toolbar);
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
        
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!"IDLE".equals(sessionStatus)) {
                    new androidx.appcompat.app.AlertDialog.Builder(monitoring.this)
                        .setTitle("Session in Progress")
                        .setMessage("Are you sure you want to exit? The current clinical record will be finalized.")
                        .setPositiveButton("Exit", (d, w) -> {
                            finishSession();
                            finish();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                } else {
                    finish();
                }
            }
        });

        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);
        
        tvHeartRate = findViewById(R.id.tvHeartRate);
        tvTemperature = findViewById(R.id.tvTemp);
        tvSpo2 = findViewById(R.id.tvSpo2);
        tvBloodPressure = findViewById(R.id.tvBP); // Matched to XML id 'tvBP'
        tvHeartRateStatus = findViewById(R.id.tvHeartRateStatus);
        tvTempStatus = findViewById(R.id.tvTempStatus);
        tvSpo2Status = findViewById(R.id.tvSpo2Status);
        tvHubStatus = findViewById(R.id.tvHubStatus);
        hubStatusDot = findViewById(R.id.hubStatusDot);
        btnScanHub = findViewById(R.id.btnScanHub);

        spinnerIntensity = findViewById(R.id.spinnerIntensity);
        spinnerMode = findViewById(R.id.spinnerMode);
        spinnerDuration = findViewById(R.id.spinnerDuration);
        btnStart = findViewById(R.id.btnStart);
        
        tvActivePatientName = findViewById(R.id.tvActivePatientName);
        if (tvActivePatientName != null) tvActivePatientName.setText("Account: " + selectedPatientName);
        btnSelectPatient = findViewById(R.id.btnSelectPatient);
        if (btnSelectPatient != null) btnSelectPatient.setVisibility(View.GONE);
        
        // Safety check: ensure graph container and pulse exist
        vitalsChart = findViewById(R.id.vitalsChart); 
        layoutVitalsVisuals = findViewById(R.id.layoutVitalsVisuals);
        viewHrPulse = findViewById(R.id.viewHrPulse);

        setupTabs();
        setupSpinners();
        setupManualActuators();
        setupIntensityListener();
        setupScanButton();
        setupNavigation();
        setupModeListener();
    }

    private void setupNavigation() {
        if (navigationView != null) {
            // Update Nav Header for Precilla
            // Dynamic Identity Fix
            if (navigationView.getHeaderCount() > 0) {
                View header = navigationView.getHeaderView(0);
                TextView tvName = header.findViewById(R.id.tvNavHeaderName);
                TextView tvEmail = header.findViewById(R.id.tvNavHeaderEmail);
                
                android.content.SharedPreferences prefs = getSharedPreferences(LoginActivity.PREFS_NAME, MODE_PRIVATE);
                String loggedName = prefs.getString(LoginActivity.KEY_USER_NAME, "Precilla");
                String loggedEmail = prefs.getString(LoginActivity.KEY_EMAIL, "Patient Account");
                
                if (tvName != null) tvName.setText(loggedName);
                if (tvEmail != null) tvEmail.setText(loggedEmail);
            }

            navigationView.setNavigationItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.snv_Dashboard) {
                    startActivity(new Intent(this, MainActivity.class));
                } else if (id == R.id.snv_Profile) {
                    startActivity(new Intent(this, profile.class));
                } else if (id == R.id.snv_monitoring) {
                    // Already here
                } else if (id == R.id.snv_Progress) {
                    startActivity(new Intent(this, progress.class));
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
    }

    private void logout() {
        android.content.SharedPreferences.Editor editor = getSharedPreferences(LoginActivity.PREFS_NAME, MODE_PRIVATE).edit();
        editor.putBoolean("isLoggedIn", false);
        editor.apply();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void setupTabs() {
        tabAutomatic = findViewById(R.id.tabAutomatic);
        tabManual = findViewById(R.id.tabManual);
        layoutAutomatic = findViewById(R.id.layoutAutomatic);
        layoutManual = findViewById(R.id.layoutManual);
        
        tabAutomatic.setOnClickListener(v -> {
            if (layoutAutomatic.getVisibility() == View.VISIBLE) return;
            
            // 🎬 SMOOTH TRANSITION (v7.1.1)
            layoutManual.animate().alpha(0f).setDuration(200).withEndAction(() -> {
                layoutManual.setVisibility(View.GONE);
                layoutAutomatic.setVisibility(View.VISIBLE);
                layoutAutomatic.setAlpha(0f);
                layoutAutomatic.animate().alpha(1f).setDuration(200).start();
            }).start();

            tabAutomatic.setBackgroundResource(R.drawable.tab_selected);
            tabAutomatic.setTextColor(Color.WHITE);
            tabManual.setBackgroundColor(Color.TRANSPARENT);
            tabManual.setTextColor(Color.parseColor("#64748B"));
        });

        tabManual.setOnClickListener(v -> {
            if (layoutManual.getVisibility() == View.VISIBLE) return;

            // 🎬 SMOOTH TRANSITION (v7.1.1)
            layoutAutomatic.animate().alpha(0f).setDuration(200).withEndAction(() -> {
                layoutAutomatic.setVisibility(View.GONE);
                layoutManual.setVisibility(View.VISIBLE);
                layoutManual.setAlpha(0f);
                layoutManual.animate().alpha(1f).setDuration(200).start();
            }).start();

            tabManual.setBackgroundResource(R.drawable.tab_selected);
            tabManual.setTextColor(Color.WHITE);
            tabAutomatic.setBackgroundColor(Color.TRANSPARENT);
            tabAutomatic.setTextColor(Color.parseColor("#64748B"));
        });
    }

    private void setupManualActuators() {
        btnHandRet = findViewById(R.id.btnHandRet);
        btnHandExt = findViewById(R.id.btnHandExt);
        btnHandStop = findViewById(R.id.btnHandStop);
        btnLegRet = findViewById(R.id.btnLegRet);
        btnLegExt = findViewById(R.id.btnLegExt);
        btnLegStop = findViewById(R.id.btnLegStop);
        switchGlovePower = findViewById(R.id.switchGlovePower);
        btnCycleForce = findViewById(R.id.btnCycleForce);
        btnCycleMode = findViewById(R.id.btnCycleMode);

        if (btnHandExt != null) btnHandExt.setOnClickListener(v -> sendHardwareCommand("CMD:HAND:EXT"));
        if (btnHandRet != null) btnHandRet.setOnClickListener(v -> sendHardwareCommand("CMD:HAND:RET"));
        if (btnHandStop != null) btnHandStop.setOnClickListener(v -> sendHardwareCommand("CMD:HAND:STP"));
        if (btnLegExt != null) btnLegExt.setOnClickListener(v -> sendHardwareCommand("CMD:LEG:EXT"));
        if (btnLegRet != null) btnLegRet.setOnClickListener(v -> sendHardwareCommand("CMD:LEG:RET"));
        if (btnLegStop != null) btnLegStop.setOnClickListener(v -> sendHardwareCommand("CMD:LEG:STP"));
        
        if (btnCycleForce != null) {
            btnCycleForce.setOnClickListener(v -> {
                int next = (spinnerIntensity.getSelectedItemPosition() + 1) % 3;
                spinnerIntensity.setSelection(next);
                // Note: setupIntensityListener will handle the actual command transmission
            });
        }
        
        if (btnCycleMode != null) {
            btnCycleMode.setOnClickListener(v -> {
                int next = (spinnerMode.getSelectedItemPosition() + 1) % 3;
                spinnerMode.setSelection(next);
                sendHardwareCommand("CMD:SET_MODE:" + next);
            });
        }

        if (switchGlovePower != null) {
            switchGlovePower.setOnCheckedChangeListener((bv, isChecked) -> sendHardwareCommand(isChecked ? "CMD:ALL:ON" : "CMD:STOP"));
        }
    }

    private void setupSpinners() {
        String[] intensities = {"Low Intensity", "Medium Intensity", "High Intensity"};
        spinnerIntensity.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, intensities));
        String[] modes = {"BOTH (Arm+Leg)", "ARM ONLY", "LEG ONLY"};
        spinnerMode.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, modes));
        String[] durations = {"10 mins", "20 mins", "30 mins"};
        spinnerDuration.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, durations));
    }

    private void setupIntensityListener() {
        spinnerIntensity.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                if (!"IDLE".equals(sessionStatus)) sendHardwareCommand("CMD:SET_FORCE:" + (pos + 1));
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
    }

    private void setupModeListener() {
        spinnerMode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                if (!"IDLE".equals(sessionStatus)) sendHardwareCommand("CMD:SET_MODE:" + pos);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
    }

    private void setupScanButton() {
        if (btnScanHub != null) {
            btnScanHub.setOnClickListener(v -> {
                VitalsMonitoringService service = VitalsMonitoringService.getInstance();
                if (service != null) service.triggerManualHubScan();
            });
        }
    }

    private void sendHardwareCommand(String cmd) {
        complexityScore++; 
        VitalsMonitoringService service = VitalsMonitoringService.getInstance();
        if (service != null) service.sendHubCommand(cmd);
    }

    private void setupStartButton() {
        btnStart.setOnClickListener(v -> {
            if (!"IDLE".equals(sessionStatus)) { finishSession(); return; }
            // Skip intake dialog - it is now account-based for Precilla
            startSessionFlow();
        });
    }

    private void startSessionFlow() {
        // CAPTURE UI STATE ON MAIN THREAD BEFORE BACKGROUND TASK
        final String intensity = spinnerIntensity.getSelectedItem().toString();
        final int forceLevel = spinnerIntensity.getSelectedItemPosition() + 1;
        final int modeLevel = spinnerMode.getSelectedItemPosition();
        final String durationStr = spinnerDuration.getSelectedItem().toString();
        final int mins = durationStr.contains("30") ? 30 : (durationStr.contains("20") ? 20 : 10);
        final String modeStr = spinnerMode.getSelectedItem().toString();
        final String combinedIntensity = intensity + " [" + modeStr + "]";
        
        VitalSignsApi api = RetrofitClient.getClient(getApplicationContext()).create(VitalSignsApi.class);
        api.startSession(new SessionStartRequest(userId, combinedIntensity, mins, selectedPatientName))
                .enqueue(new Callback<SessionStartResponse>() {
                    @Override
                    public void onResponse(Call<SessionStartResponse> call, Response<SessionStartResponse> response) {
                        if (!response.isSuccessful() || response.body() == null || !response.body().success) {
                            Toast.makeText(monitoring.this, "Cloud session start failed", Toast.LENGTH_LONG).show();
                            return;
                        }
                        currentSessionId = response.body().session_id;
                        beginSessionAfterCloudStart(combinedIntensity, mins, forceLevel, modeLevel);
                    }

                    @Override
                    public void onFailure(Call<SessionStartResponse> call, Throwable t) {
                        Toast.makeText(monitoring.this, "Backend unavailable. Session not started.", Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void beginSessionAfterCloudStart(String combinedIntensity, int mins, int forceLevel, int modeLevel) {
        databaseExecutor.execute(() -> {
            // CLINICAL POLICY: Overlap if session exists today
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
            String today = sdf.format(new java.util.Date());
            List<LocalSession> existing = vitalSignDao.getSessionsByUserId(userId);
            if (existing != null) {
                for (LocalSession s : existing) {
                    if (sdf.format(new java.util.Date(s.timestamp)).equals(today) && selectedPatientName.equals(s.patientName)) {
                        Log.d("OVERLAP", "Overlapping session from today for " + selectedPatientName + ": " + s.id);
                        vitalSignDao.deleteVitalsBySession(s.id);
                        vitalSignDao.deleteSession(s.id);
                    }
                }
            }

            currentLocalSession = new LocalSession(userId, selectedPatientName, combinedIntensity, mins, System.currentTimeMillis());
            currentLocalSession.gender = selectedGender;
            currentLocalSession.ageRange = selectedAge;
            currentLocalSession.strokeDuration = selectedStroke;
            currentLocalSession.id = currentSessionId;

            runOnUiThread(() -> {
                btnStart.setText("STOP SESSION");
                btnStart.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#E11D48")));
                sessionStatus = "RUNNING";
                
                // Sync Hardware with UI state
                sendHardwareCommand("CMD:SET_FORCE:" + forceLevel);
                sendHardwareCommand("CMD:SET_MODE:" + modeLevel);
                sendHardwareCommand("CMD:START");
                
                // Notify Service to start recording
                if (VitalsMonitoringService.getInstance() != null) {
                    VitalsMonitoringService.getInstance().sendHubCommand("CMD:START_SESSION:" + currentSessionId);
                }
                
                showWebDashboardModal();
                Toast.makeText(this, "Session Started & Web Bridge Active", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void showWebDashboardModal() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        WebView webView = new WebView(this);
        
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        
        String serverUrl = RetrofitClient.getBaseUrl() + "dashboard.html";
        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl(serverUrl);

        builder.setView(webView);
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void finalizeBaseline() {
        sessionStatus = "RUNNING";
        if (toolbar != null) toolbar.setTitle("Status: MONITORING");
    }

    private void finishSession() {
        if (isFinishing || "IDLE".equals(sessionStatus)) return;
        isFinishing = true;
        if (sessionTimer != null) sessionTimer.cancel();
        sessionStatus = "COMPLETED";
        float avgSessionHR = (sampledHR1 + sampledHR2 + sampledHR3) / 3.0f;
        float recoveryScore = Math.min(10.0f, (complexityScore * 0.5f) + (100.0f / (Math.abs(avgSessionHR - 75) + 1)));
        currentLocalSession.endTime = System.currentTimeMillis();
        currentLocalSession.hiiIndex = recoveryScore;
        sendHardwareCommand("CMD:STOP");
        VitalSignsApi api = RetrofitClient.getClient(getApplicationContext()).create(VitalSignsApi.class);
        api.completeSession(currentSessionId, new SessionCompleteRequest(recoveryScore, "Session completed from Android monitoring."))
                .enqueue(new Callback<SessionStartResponse>() {
                    @Override
                    public void onResponse(Call<SessionStartResponse> call, Response<SessionStartResponse> response) {
                    }

                    @Override
                    public void onFailure(Call<SessionStartResponse> call, Throwable t) {
                        Toast.makeText(monitoring.this, "Cloud completion sync failed", Toast.LENGTH_SHORT).show();
                    }
                });
        databaseExecutor.execute(() -> {
            vitalSignDao.updateSession(currentLocalSession);
            runOnUiThread(() -> showClinicalImprovement(recoveryScore));
        });
    }

    private void showClinicalImprovement(float currentScore) {
        String msg = "Evaluation Score: " + String.format("%.1f", currentScore) + "/10\n";
        msg += "Activity Complexity: " + (complexityScore > 5 ? "High" : "Moderate") + "\n\n";
        if (initialSession != null) {
            float initialScore = initialSession.hiiIndex;
            float improvement = ((currentScore - initialScore) / (initialScore > 0 ? initialScore : 1)) * 100;
            msg += (improvement > 0 ? "🚀 IMPROVEMENT: +" : "✅ STATUS: ") + String.format("%.1f", improvement) + "%\n";
        } else msg += "🏆 BASELINE ESTABLISHED\n";
        new AlertDialog.Builder(this).setTitle("Clinical Recovery Report").setMessage(msg).setPositiveButton("Finish", (d, w) -> resetStartButton()).setCancelable(false).show();
    }

    private void handleRealTimeUpdate(String data) {
        runOnUiThread(() -> {
            try {
                String cleaned = data == null ? "" : data.trim();
                if (cleaned.startsWith("DATA:")) {
                    cleaned = cleaned.substring(5).trim();
                }

                if (cleaned.startsWith("{")) {
                    JSONObject json = new JSONObject(cleaned);
                    int hr = json.optInt("heart_rate", json.optInt("BPM", 0));
                    int spo2 = json.optInt("spo2", json.optInt("SPO2", 0));
                    double temp = json.optDouble("body_temp", json.optDouble("temperature", 0));
                    boolean arm = json.optBoolean("arm_moving", false);
                    boolean leg = json.optBoolean("leg_moving", false);
                    boolean glove = json.optBoolean("glove_active", false);

                    lastKnownBPM = hr;
                    if (tvHeartRate != null) tvHeartRate.setText(hr > 0 ? String.valueOf(hr) : "--");
                    if (tvSpo2 != null) tvSpo2.setText(spo2 > 0 ? String.valueOf(spo2) : "--");
                    if (tvTemperature != null) tvTemperature.setText(temp > 0 ? String.format(java.util.Locale.getDefault(), "%.1f", temp) : "--");
                    if (tvBloodPressure != null && (arm || leg || glove)) tvBloodPressure.setText("ACTIVE");
                    if (tvHeartRateStatus != null) tvHeartRateStatus.setText(hr > 0 ? "LIVE" : "NO SIGNAL");
                    if (tvSpo2Status != null) tvSpo2Status.setText(spo2 > 0 ? "STABLE" : "WAITING");
                    if (tvTempStatus != null) tvTempStatus.setText(temp > 0 ? "STABLE" : "WAITING");
                    if (switchGlovePower != null && switchGlovePower.isChecked() != glove) {
                        switchGlovePower.setChecked(glove);
                    }
                    updateChart(hr > 0 ? hr : lastKnownBPM);
                }
            } catch (Exception e) {
                Log.e("MONITORING_PARSE", "Failed to parse live vitals", e);
            }
        });
    }

    private void showVitalsPopup(String title) {
        if (vitalsPopup != null && vitalsPopup.isShowing()) vitalsPopup.dismiss();
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_vitals_overlay, null);
        dlgHR = view.findViewById(R.id.dlgHeartRate);
        dlgSpO2 = view.findViewById(R.id.dlgSpo2);
        dlgTemp = view.findViewById(R.id.dlgTemp);
        dlgBP = view.findViewById(R.id.dlgBP);
        dlgStatus = view.findViewById(R.id.dlgStatus);
        dlgStatus.setText(title);
        vitalsPopup = new AlertDialog.Builder(this).setView(view).setCancelable(true).create();
        vitalsPopup.show();
        new android.os.Handler().postDelayed(() -> { if (vitalsPopup != null) vitalsPopup.dismiss(); }, 10000);
    }

    private float getCurrentHR() {
        try { String t = tvHeartRate.getText().toString().replaceAll("[^0-9]", ""); return Float.parseFloat(t); } catch (Exception e) { return 0; }
    }

    private void resetStartButton() {
        btnStart.setEnabled(true);
        btnStart.setText("INITIALIZE SESSION");
        btnStart.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#2563EB")));
        sessionStatus = "IDLE";
        complexityScore = 0; sample2Captured = false;
        if (layoutVitalsVisuals != null) layoutVitalsVisuals.setVisibility(View.VISIBLE);
        if (toolbar != null) toolbar.setTitle("VITALS MONITOR");
        isFinishing = false;
    }

    private final Runnable waveformRunnable = new Runnable() {
        @Override public void run() {
            if (lastKnownBPM > 0) updateChart(lastKnownBPM);
            waveformHandler.postDelayed(this, 1000);
        }
    };

    private void setupVitalsChart() {
        vitalsChart = findViewById(R.id.vitalsChart);
        if (vitalsChart == null) return;
        vitalsChart.setTouchEnabled(false);
        XAxis xAxis = vitalsChart.getXAxis();
        xAxis.setDrawGridLines(false); xAxis.setDrawLabels(false);
        YAxis leftAxis = vitalsChart.getAxisLeft();
        leftAxis.setTextColor(Color.parseColor("#94A3B8"));
        leftAxis.setAxisMinimum(40f); leftAxis.setAxisMaximum(130f);
        vitalsChart.getAxisRight().setEnabled(false);
        hrDataSet = new LineDataSet(hrEntries, "Live HR");
        hrDataSet.setColor(Color.parseColor("#38BDF8"));
        hrDataSet.setDrawCircles(false); hrDataSet.setDrawValues(false);
        hrDataSet.setLineWidth(2.5f); hrDataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        vitalsChart.setData(new LineData(hrDataSet));
        waveformHandler.post(waveformRunnable);
    }

    private void updateChart(int bpm) {
        if (vitalsChart == null || hrDataSet == null) return;
        hrEntries.add(new Entry(chartXValue++, bpm));
        if (hrEntries.size() > 40) hrEntries.remove(0);
        vitalsChart.getData().notifyDataChanged();
        vitalsChart.notifyDataSetChanged();
        vitalsChart.invalidate();
    }

    private void showPatientIntakeDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_patient_intake, null);
        AutoCompleteTextView actvName = view.findViewById(R.id.actvPatientName);
        RadioGroup rgGender = view.findViewById(R.id.rgGender);
        Spinner spAge = view.findViewById(R.id.spinnerAgeRange);
        Spinner spStroke = view.findViewById(R.id.spinnerStrokeDuration);
        AppCompatButton btnSave = view.findViewById(R.id.btnSavePatient);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this).setView(view).setCancelable(true).create();
        btnSave.setOnClickListener(v -> {
            selectedPatientName = actvName.getText().toString();
            selectedGender = rgGender.getCheckedRadioButtonId() == R.id.rbMale ? "Male" : "Female";
            selectedAge = spAge.getSelectedItem().toString();
            selectedStroke = spStroke.getSelectedItem().toString();
            if (tvActivePatientName != null) tvActivePatientName.setText(selectedPatientName);
            dialog.dismiss();
        });
        dialog.show();
    }

    private void setHubStatusUI(boolean connected) {
        runOnUiThread(() -> {
            boolean wasConnected = "HUB: ONLINE".equals(tvHubStatus.getText().toString());
            
            tvHubStatus.setText(connected ? "HUB: ONLINE" : "HUB: OFFLINE");
            tvHubStatus.setTextColor(Color.parseColor(connected ? "#22C55E" : "#EF4444"));
            hubStatusDot.setBackgroundResource(connected ? R.drawable.dot_green : R.drawable.dot_red);
            
            // 🔔 Add Visual Notifications
            if (connected && !wasConnected) {
                Toast.makeText(monitoring.this, "✅ VITALS HUB CONNECTED", Toast.LENGTH_SHORT).show();
            } else if (!connected && wasConnected) {
                Toast.makeText(monitoring.this, "❌ HUB DISCONNECTED", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction(VitalsMonitoringService.ACTION_VITALS_UPDATE);
        filter.addAction(VitalsMonitoringService.ACTION_HUB_STATUS);
        filter.addAction(VitalsMonitoringService.ACTION_HARDWARE_BUTTON);
        LocalBroadcastManager.getInstance(this).registerReceiver(vitalsReceiver, filter);
        waveformHandler.post(waveformRunnable);

        VitalsMonitoringService service = VitalsMonitoringService.getInstance();
        if (service != null) {
            setHubStatusUI(service.isHubConnected());
            String lastPayload = service.getLastVitalsPayload();
            if (lastPayload != null && !lastPayload.isEmpty()) {
                handleRealTimeUpdate(lastPayload);
            }
        }
    }
    
    @Override protected void onPause() {
        super.onPause();
        if (waveformHandler != null) waveformHandler.removeCallbacks(waveformRunnable);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(vitalsReceiver);
    }
}
