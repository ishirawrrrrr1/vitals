package com.example.myapplication;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.myapplication.network.RetrofitClient;
import com.example.myapplication.network.VitalSignsApi;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class second extends AppCompatActivity {

    ImageView arrow;
    Button createAccountButton;
    EditText nameEdit, emailEdit, passwordEdit, confirmPasswordEdit;
    android.widget.Spinner spinnerGender;
    EditText editAge, editStroke;
    View layoutPatientInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_second);

        View root = findViewById(R.id.main);
        if (root != null) {
            ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });
        }

        arrow = findViewById(R.id.arrow_left);
        createAccountButton = findViewById(R.id.createaccountButton);
        nameEdit = findViewById(R.id.editTextText);
        emailEdit = findViewById(R.id.editTextTextEmailAddress2);
        passwordEdit = findViewById(R.id.editTextTextPassword2);
        confirmPasswordEdit = findViewById(R.id.editTextTextPassword3);
        spinnerGender = findViewById(R.id.spinnerGender);
        editAge = findViewById(R.id.editPatientAge);
        editStroke = findViewById(R.id.editStrokeDuration);
        layoutPatientInfo = findViewById(R.id.layoutPatientInfo);
        if (layoutPatientInfo != null) {
            layoutPatientInfo.setVisibility(View.VISIBLE);
        }

        arrow.setOnClickListener(v -> finish());

        createAccountButton.setOnClickListener(v -> {
            String name = nameEdit.getText().toString().trim();
            String email = emailEdit.getText().toString().trim();
            String password = passwordEdit.getText().toString().trim();
            String confirmPassword = confirmPasswordEdit.getText().toString().trim();
            String role = "Patient";

            if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!password.equals(confirmPassword)) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                return;
            }

            String age = editAge.getText().toString().trim();
            String gender = spinnerGender.getSelectedItem().toString();
            String stroke = editStroke.getText().toString().trim();

            performSignup(name, email, password, role, age, gender, stroke);
        });
    }

    private void performSignup(String name, String email, String password, String role, String age, String gender, String stroke) {
        SignupRequest request = new SignupRequest(name, email, password, role, age, gender, stroke);
        android.content.SharedPreferences prefs = getSharedPreferences(LoginActivity.PREFS_NAME, MODE_PRIVATE);
        String savedIp = prefs.getString(LoginActivity.KEY_SERVER_IP, RetrofitClient.getBaseUrl());

        if (savedIp == null || savedIp.trim().isEmpty()) {
            savedIp = RetrofitClient.getBaseUrl();
        }

        RetrofitClient.discoverPort(getApplicationContext(), savedIp, new RetrofitClient.PortDiscoveryCallback() {
            @Override
            public void onPortFound(String url) {
                attemptRemoteSignup(request, name, email, password, role);
            }

            @Override
            public void onDiscoveryFailed() {
                Toast.makeText(second.this, "Backend unreachable. Account was not created.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void attemptRemoteSignup(SignupRequest request, String name, String email, String password, String role) {
        VitalSignsApi api = RetrofitClient.getClient(getApplicationContext()).create(VitalSignsApi.class);

        api.signup(request).enqueue(new Callback<SignupResponse>() {
            @Override
            public void onResponse(Call<SignupResponse> call, Response<SignupResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().success) {
                    Toast.makeText(second.this, "Account Created! Please Login.", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    String msg = "Signup failed";
                    if (response.code() == 400) {
                        msg = "Email already exists";
                    }
                    Toast.makeText(second.this, msg, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<SignupResponse> call, Throwable t) {
                Toast.makeText(second.this, "Backend unavailable. Account was not created.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void saveUserLocally(String name, String email, String password, String role) {
        com.example.myapplication.db.AppDatabase db = com.example.myapplication.db.AppDatabase.getInstance(this);
        com.example.myapplication.db.LocalUser user = new com.example.myapplication.db.LocalUser(name, email, password, role);
        com.example.myapplication.db.AppDatabase.databaseWriteExecutor.execute(() -> db.userDao().insertUser(user));
    }

    public static class SignupRequest {
        String name, email, password, role, age, gender, stroke_duration;

        SignupRequest(String n, String e, String p, String r, String a, String g, String s) {
            this.name = n;
            this.email = e;
            this.password = p;
            this.role = r;
            this.age = a;
            this.gender = g;
            this.stroke_duration = s;
        }
    }

    public static class SignupResponse {
        boolean success;
        String message;
    }
}
