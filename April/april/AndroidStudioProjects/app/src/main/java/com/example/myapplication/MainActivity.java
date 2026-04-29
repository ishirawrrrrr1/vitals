package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import java.util.Calendar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.navigation.NavigationView;

/**
 * Formerly Dashboard.java
 */
public class MainActivity extends AppCompatActivity {

    DrawerLayout drawerLayout;
    NavigationView nv_side;
    ActionBarDrawerToggle toggle;
    Toolbar toolbar;
    AppCompatButton btnLogoutSidebar;
    MaterialCardView cardMonitoring, cardProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_dashboard);

        android.content.SharedPreferences sharedPreferences = getSharedPreferences(LoginActivity.PREFS_NAME, MODE_PRIVATE);
        String userName = sharedPreferences.getString(LoginActivity.KEY_USER_NAME, "New Patient");

        drawerLayout = findViewById(R.id.dashboard);
        nv_side = findViewById(R.id.navigationView);
        toolbar = findViewById(R.id.toolbar);
        cardMonitoring = findViewById(R.id.cardMonitoring);
        cardProgress = findViewById(R.id.cardProgress);

        try {
            if (toolbar != null) {
                setSupportActionBar(toolbar);
            }

            if (drawerLayout != null && toolbar != null) {
                toggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
                drawerLayout.addDrawerListener(toggle);
                toggle.syncState();
            }
        } catch (Exception e) {
            android.util.Log.e("MAIN_CRASH", "Crash initializing dashboard UI", e);
        }

        // --- DASHBOARD SYNC: Push Offline Accounts to Server ---
        syncOfflineAccounts();

        // Quick Action Cards
        if (cardMonitoring != null) {
            cardMonitoring.setOnClickListener(v -> startActivity(new Intent(this, monitoring.class)));
        }
        if (cardProgress != null) {
            cardProgress.setOnClickListener(v -> startActivity(new Intent(this, progress.class)));
        }

        if (nv_side != null) {
            // Update Nav Header with real user data
            if (nv_side.getHeaderCount() > 0) {
                View header = nv_side.getHeaderView(0);
                TextView tvName = header.findViewById(R.id.tvNavHeaderName);
                TextView tvEmail = header.findViewById(R.id.tvNavHeaderEmail);
                
                String loggedEmail = sharedPreferences.getString(LoginActivity.KEY_EMAIL, "Patient Account");
                
                if (tvName != null) tvName.setText(userName);
                if (tvEmail != null) tvEmail.setText(loggedEmail);
            }

            nv_side.setNavigationItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.snv_Dashboard) {
                } else if (id == R.id.snv_Profile) {
                    startActivity(new Intent(this, profile.class));
                } else if (id == R.id.snv_monitoring) {
                    startActivity(new Intent(this, monitoring.class));
                } else if (id == R.id.snv_dashboard_web) {
                    startActivity(new Intent(this, DashboardWebViewActivity.class));
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

        View root = findViewById(R.id.dashboard);
        if (root != null) {
            ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom); 
                return insets;
            });
        }
        
        setupHealthTips(userName);
    }

    private void syncOfflineAccounts() {
        com.example.myapplication.db.AppDatabase db = com.example.myapplication.db.AppDatabase.getInstance(this);
        com.example.myapplication.network.VitalSignsApi api = com.example.myapplication.network.RetrofitClient.getClient(getApplicationContext()).create(com.example.myapplication.network.VitalSignsApi.class);

        com.example.myapplication.db.AppDatabase.databaseWriteExecutor.execute(() -> {
            java.util.List<com.example.myapplication.db.LocalUser> unsynced = db.userDao().getUnsyncedUsers();
            if (unsynced.isEmpty()) return;

            for (com.example.myapplication.db.LocalUser user : unsynced) {
                try {
                    // Try to register on the server
                    com.example.myapplication.second.SignupRequest request = new com.example.myapplication.second.SignupRequest(
                            user.username, user.email, user.password, user.role, "N/A", "Unknown", "N/A"
                    );
                    
                    retrofit2.Response<com.example.myapplication.second.SignupResponse> response = api.signup(request).execute();
                    if (response.isSuccessful() || (response.code() == 400)) { // 400 usually means Email Duplicate -> Already Synced
                        user.isSynced = true;
                        db.userDao().update(user);
                        android.util.Log.d("MIRROR_SYNC", "Account finalized on server: " + user.email);
                    }
                } catch (Exception e) {
                    android.util.Log.e("MIRROR_SYNC", "Push failed for " + user.email + " (Retrying next startup)");
                }
            }
        });
    }

    private void setupHealthTips(String userName) {
        TextView tvWelcome = findViewById(R.id.tvWelcomeTitle);
        TextView tvTipTitle = findViewById(R.id.tvHealthTipTitle);
        TextView tvTipDesc = findViewById(R.id.tvHealthTipDesc);

        if (tvWelcome != null) tvWelcome.setText("Welcome, " + userName + "!");

        int currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        
        if (currentHour > 10 && currentHour < 16) {
            if (tvTipTitle != null) tvTipTitle.setText("Hydration Alert ☀️");
            if (tvTipDesc != null) tvTipDesc.setText("It is peak sun hours. Please drink water to maintain circulation.");
        } else if (currentHour >= 18) {
            if (tvTipTitle != null) tvTipTitle.setText("Evening Recovery 🌙");
            if (tvTipDesc != null) tvTipDesc.setText("Keep your recovery area warm to prevent muscle stiffness tonight.");
        } else {
            if (tvTipTitle != null) tvTipTitle.setText("Morning Vitality 🍃");
            if (tvTipDesc != null) tvTipDesc.setText("Fresh air detected! A deep breathing session would be excellent for your recovery today.");
        }
    }

    private void logout() {
        android.content.SharedPreferences sharedPreferences = getSharedPreferences(LoginActivity.PREFS_NAME, MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(LoginActivity.KEY_USER_ID);
        editor.remove(LoginActivity.KEY_USER_NAME);
        editor.remove(LoginActivity.KEY_EMAIL);
        editor.apply();

        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
