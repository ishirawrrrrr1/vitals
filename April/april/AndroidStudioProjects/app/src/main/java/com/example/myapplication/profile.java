package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;

public class profile extends AppCompatActivity {

    DrawerLayout drawerLayout;
    NavigationView navigationView;
    Toolbar toolbar;
    ActionBarDrawerToggle toggle;
    private TextView tvProfileName, tvProfileRole, tvProfileEmail, tvProfileAge, tvProfileGender;
    View layoutClinical;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.profile);

        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);
        toolbar = findViewById(R.id.toolbar);
        
        tvProfileName = findViewById(R.id.tvProfileName);
        tvProfileEmail = findViewById(R.id.tvProfileEmail);
        tvProfileRole = findViewById(R.id.tvProfileRole);
        tvProfileAge = findViewById(R.id.tvProfileAge);
        tvProfileGender = findViewById(R.id.tvProfileGender);
        layoutClinical = findViewById(R.id.layoutClinicalMetrics);
        
        android.widget.ImageView ivProfile = findViewById(R.id.ivProfileLarge);
        if (ivProfile != null) {
            ivProfile.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType("image/*");
                startActivityForResult(intent, 2002);
            });
        }

        android.content.SharedPreferences sharedPreferences = getSharedPreferences(LoginActivity.PREFS_NAME, MODE_PRIVATE);
        String name = sharedPreferences.getString(LoginActivity.KEY_USER_NAME, "Precilla");
        String email = sharedPreferences.getString(LoginActivity.KEY_EMAIL, "No Email");
        String role = sharedPreferences.getString("role", "Patient");
        
        // Mock data for demo
        String age = "62";
        String gender = "Female";

        if (tvProfileName != null) tvProfileName.setText(name);
        if (tvProfileEmail != null) tvProfileEmail.setText(email);
        if (tvProfileRole != null) tvProfileRole.setText(role);
        
        if (layoutClinical != null) {
            layoutClinical.setVisibility(View.VISIBLE);
            if (tvProfileAge != null) tvProfileAge.setText(age);
            if (tvProfileGender != null) tvProfileGender.setText(gender);
        }

        if (toolbar != null) {
            setSupportActionBar(toolbar);
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
                } else if (id == R.id.snv_monitoring) {
                    startActivity(new Intent(this, monitoring.class));
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

        // Activate Change Password Button
        View btnChangePassword = findViewById(R.id.btnChangePassword);
        if (btnChangePassword != null) {
            btnChangePassword.setOnClickListener(v -> {
                Toast.makeText(this, "Secure Reset Tool: Check registered email for link.", Toast.LENGTH_LONG).show();
            });
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 2002 && resultCode == RESULT_OK && data != null) {
            android.net.Uri imageUri = data.getData();
            ImageView ivProfile = findViewById(R.id.ivProfileLarge);
            if (ivProfile != null && imageUri != null) {
                ivProfile.setImageURI(imageUri);
                Toast.makeText(this, "Photo updated!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void logout() {
        android.content.SharedPreferences sharedPreferences = getSharedPreferences(LoginActivity.PREFS_NAME, MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.apply();

        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
