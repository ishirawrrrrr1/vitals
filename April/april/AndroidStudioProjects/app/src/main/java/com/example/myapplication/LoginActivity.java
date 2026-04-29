package com.example.myapplication;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;
import android.view.View;

import com.example.myapplication.network.RetrofitClient;
import com.example.myapplication.network.VitalSignsApi;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    public static final String PREFS_NAME = "LoginPrefs";
    public static final String KEY_REMEMBER = "rememberMe";
    public static final String KEY_REMEMBERED_EMAIL = "rememberedEmail";
    public static final String KEY_REMEMBERED_PASSWORD = "rememberedPassword";
    
    // Offline Cache keys (Always saved on success)
    public static final String KEY_LAST_SUCCESS_EMAIL = "lastEmail";
    public static final String KEY_LAST_SUCCESS_PASS = "lastPass";

    public static final String KEY_EMAIL = "email";
    public static final String KEY_PASSWORD = "password";
    public static final String KEY_USER_ID = "userId";
    public static final String KEY_USER_NAME = "userName";
    public static final String KEY_TOKEN = "token";
    public static final String KEY_SERVER_IP = "serverIp";
    public static final String KEY_ROLE = "userRole";
    public static final String KEY_AGE = "userAge";
    public static final String KEY_GENDER = "userGender";
    public static final String KEY_STROKE = "userStroke";

    TextView signup;
    Button login;
    EditText emailEdit, passwordEdit, serverIpEdit;
    CheckBox rememberMe;
    Button btnGuestLogin;
    SharedPreferences sharedPreferences;
    int loginFailCount = 0;
    TextView txtForgotPass;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        View root = findViewById(R.id.main);
        if (root != null) {
            ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom); 
                return insets;
            });
        }

        emailEdit = findViewById(R.id.editTextTextEmailAddress);
        passwordEdit = findViewById(R.id.editTextTextPassword);
        serverIpEdit = findViewById(R.id.editTextServerIp);
        signup = findViewById(R.id.txtSignup);
        login = findViewById(R.id.button1);
        rememberMe = findViewById(R.id.checkBox);
        txtForgotPass = findViewById(R.id.txtForgotPass);
        btnGuestLogin = findViewById(R.id.btnGuestLogin);

        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Recovery Logic
        if (txtForgotPass != null) {
            txtForgotPass.setOnClickListener(v -> {
                String recovered = sharedPreferences.getString("KEY_CACHED_PWD", null);
                if (recovered != null) {
                    passwordEdit.setText(recovered);
                    Toast.makeText(this, "✅ Cached password recovered!", Toast.LENGTH_SHORT).show();
                    txtForgotPass.setVisibility(View.GONE);
                } else {
                    Toast.makeText(this, "No local recovery key found.", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Priority 1: Remember Me
        if (sharedPreferences.getBoolean(KEY_REMEMBER, false)) {
            emailEdit.setText(sharedPreferences.getString(KEY_REMEMBERED_EMAIL, ""));
            passwordEdit.setText(sharedPreferences.getString(KEY_REMEMBERED_PASSWORD, ""));
            rememberMe.setChecked(true);
        }
        
        String savedIp = sharedPreferences.getString(KEY_SERVER_IP, "https://vitals-production-e304.up.railway.app/");
        if (serverIpEdit != null) serverIpEdit.setText(savedIp);

        signup.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, second.class);
            startActivity(intent);
        });

        login.setOnClickListener(v -> {
            String email = emailEdit.getText().toString().trim();
            String password = passwordEdit.getText().toString().trim();
            String serverIp = serverIpEdit != null ? serverIpEdit.getText().toString().trim() : "";

            if (email.isEmpty() || password.isEmpty() || serverIp.isEmpty()) {
                Toast.makeText(LoginActivity.this, R.string.please_enter_email_password, Toast.LENGTH_SHORT).show();
                return;
            }
            
            login.setEnabled(false);
            login.setText("Connecting...");

            final String finalEmail = email;
            final String finalPassword = password;
            final String finalServerIp = serverIp;

            RetrofitClient.discoverPort(getApplicationContext(), serverIp, new RetrofitClient.PortDiscoveryCallback() {
                @Override
                public void onPortFound(String url) {
                    runOnUiThread(() -> performLogin(finalEmail, finalPassword, url));
                }

                @Override
                public void onDiscoveryFailed() {
                    runOnUiThread(() -> checkOfflineLogin(finalEmail, finalPassword));
                }
            });
        });

        if (btnGuestLogin != null) {
            btnGuestLogin.setOnClickListener(v -> performGuestLogin());
        }

        seedPrecillaLegacy();
    }

    private void performGuestLogin() {
        com.example.myapplication.db.AppDatabase db = com.example.myapplication.db.AppDatabase.getInstance(this);
        com.example.myapplication.db.AppDatabase.databaseWriteExecutor.execute(() -> {
            com.example.myapplication.db.LocalUser guest = db.userDao().findByEmail("guest@local.host");
            if (guest == null) {
                guest = new com.example.myapplication.db.LocalUser("Guest User", "guest@local.host", "guest", "Patient");
                guest.id = (int) db.userDao().insertUser(guest);
            }
            
            final com.example.myapplication.db.LocalUser finalGuest = guest;
            runOnUiThread(() -> {
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putInt(KEY_USER_ID, finalGuest.id);
                editor.putString(KEY_USER_NAME, finalGuest.username);
                editor.putString(KEY_EMAIL, finalGuest.email);
                editor.putString(KEY_ROLE, "Guest");
                editor.apply();

                Toast.makeText(this, "Logged in as Guest", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
            });
        });
    }

    private void seedPrecillaLegacy() {
        com.example.myapplication.db.AppDatabase db = com.example.myapplication.db.AppDatabase.getInstance(this);
        com.example.myapplication.db.VitalSignDao vitalSignDao = db.vitalSignDao();

        com.example.myapplication.db.AppDatabase.databaseWriteExecutor.execute(() -> {
            if (db.userDao().findByEmail("precilla@health.com") == null) {
                // Create Precilla Master Profile
                com.example.myapplication.db.LocalUser precilla = new com.example.myapplication.db.LocalUser(
                    "Precilla", "precilla@health.com", "precilla123", "Patient"
                );
                precilla.isSynced = false; 
                long userId = db.userDao().insertUser(precilla);

                // Seed Historical Recovery Data (20 Days)
                long now = System.currentTimeMillis();
                long dayMs = 24 * 60 * 60 * 1000;
                String[] modes = {"ARM ONLY", "LEG ONLY", "BOTH (Arm+Leg)"};
                
                for (int i = 20; i >= 0; i--) {
                    long timestamp = now - (i * dayMs);
                    String mode = modes[new java.util.Random().nextInt(modes.length)];
                    com.example.myapplication.db.LocalSession session = new com.example.myapplication.db.LocalSession(
                        (int)userId, "Precilla", "Med Intensity [" + mode + "]", 15, timestamp
                    );
                    session.hiiIndex = 6.0f + (new java.util.Random().nextFloat() * 2.5f);
                    long sessionId = vitalSignDao.insertSession(session);

                    vitalSignDao.insert(new com.example.myapplication.db.LocalVitalSign("HeartRate", "bpm", 65 + (new java.util.Random().nextFloat()*10), "bpm", timestamp, (int)sessionId));
                    vitalSignDao.insert(new com.example.myapplication.db.LocalVitalSign("Temperature", "Celcius", 36.5f + (new java.util.Random().nextFloat()*0.5f), "C", timestamp, (int)sessionId));
                }
                android.util.Log.d("PRECILLA_LEGACY", "Master Profile & History Embedded Successfully @ Login UI.");
            }
        });
    }

    private void syncOfflineAccounts() {
        com.example.myapplication.db.AppDatabase db = com.example.myapplication.db.AppDatabase.getInstance(this);
        com.example.myapplication.db.AppDatabase.databaseWriteExecutor.execute(() -> {
            // Logic to seed or sync legacy accounts
        });
    }

    private void checkOfflineLogin(String email, String password) {
        com.example.myapplication.db.AppDatabase db = com.example.myapplication.db.AppDatabase.getInstance(this);
        com.example.myapplication.db.AppDatabase.databaseWriteExecutor.execute(() -> {
            com.example.myapplication.db.LocalUser localUser = db.userDao().login(email, password);
            runOnUiThread(() -> {
                login.setEnabled(true);
                login.setText(R.string.login);

                if (localUser != null) {
                    Log.d("LOGIN_OFFLINE", "Local DB login successful for: " + email);
                    Toast.makeText(this, "OFFLINE MODE: Local authentication successful.", Toast.LENGTH_LONG).show();

                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putInt(KEY_USER_ID, localUser.id);
                    editor.putString(KEY_USER_NAME, localUser.username != null ? localUser.username : "User");
                    editor.putString(KEY_EMAIL, localUser.email);
                    editor.putString(KEY_ROLE, localUser.role != null ? localUser.role : "Patient");
                    editor.apply();

                    forceConnectToDirectHub();

                    Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                    startActivity(intent);
                    finish();
                } else {
                    Toast.makeText(this, "Login Failed: User not found in Local Database or Server offline.", Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void forceConnectToDirectHub() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            Toast.makeText(this, "Requesting Hub Direct Connection...", Toast.LENGTH_SHORT).show();
            android.net.wifi.WifiNetworkSpecifier specifier = new android.net.wifi.WifiNetworkSpecifier.Builder()
                    .setSsid("Vitals-Hub-Direct")
                    .build();

            android.net.NetworkRequest request = new android.net.NetworkRequest.Builder()
                    .addTransportType(android.net.NetworkCapabilities.TRANSPORT_WIFI)
                    .removeCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .setNetworkSpecifier(specifier)
                    .build();

            android.net.ConnectivityManager connectivityManager = (android.net.ConnectivityManager) getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
            
            android.net.ConnectivityManager.NetworkCallback networkCallback = new android.net.ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(@androidx.annotation.NonNull android.net.Network network) {
                    super.onAvailable(network);
                    connectivityManager.bindProcessToNetwork(network);
                    Log.d("LOGIN_OFFLINE", "Successfully bound to Vitals-Hub-Direct!");
                    
                    runOnUiThread(() -> {
                        Toast.makeText(LoginActivity.this, "🔗 Bound to Vitals Hub Direct!", Toast.LENGTH_SHORT).show();
                        sharedPreferences.edit().putString("hub_ip", "192.168.4.1").apply();
                    });
                }
            };
            
            try {
                connectivityManager.requestNetwork(request, networkCallback);
            } catch (SecurityException e) {
                Log.e("LOGIN_OFFLINE", "Missing location permissions", e);
            }
        } else {
            Toast.makeText(this, "Please connect to 'Vitals-Hub-Direct' Wi-Fi manually.", Toast.LENGTH_LONG).show();
        }
    }

    private void performLogin(String email, String password, String serverIp) {
        VitalSignsApi api = RetrofitClient.getClient(getApplicationContext()).create(VitalSignsApi.class);
        LoginRequest request = new LoginRequest(email, password);

        api.login(request).enqueue(new Callback<LoginResponse>() {
            @Override
            public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                login.setEnabled(true);
                login.setText(R.string.login);
                
                if (response.isSuccessful() && response.body() != null && response.body().success) {
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    
                    // Always cache for offline fallback
                    editor.putString(KEY_LAST_SUCCESS_EMAIL, email);
                    editor.putString(KEY_LAST_SUCCESS_PASS, password);

                    if (response.body().user != null) {
                        editor.putInt(KEY_USER_ID, response.body().user.id);
                        editor.putString(KEY_USER_NAME, response.body().user.name != null ? response.body().user.name : "User");
                        editor.putString(KEY_EMAIL, response.body().user.email);
                        editor.putString(KEY_ROLE, response.body().user.role != null ? response.body().user.role : "Patient");
                        editor.putString(KEY_AGE, response.body().user.age != null ? response.body().user.age : "");
                        editor.putString(KEY_GENDER, response.body().user.gender != null ? response.body().user.gender : "");
                        editor.putString(KEY_STROKE, response.body().user.stroke_duration != null ? response.body().user.stroke_duration : "");
                    }
                    editor.putString(KEY_TOKEN, response.body().token);
                    editor.putString(KEY_SERVER_IP, serverIp);

                    if (rememberMe.isChecked()) {
                        editor.putBoolean(KEY_REMEMBER, true);
                        editor.putString(KEY_REMEMBERED_EMAIL, email);
                        editor.putString(KEY_REMEMBERED_PASSWORD, password);
                    } else {
                        editor.putBoolean(KEY_REMEMBER, false);
                    }
                    editor.apply();

                    Toast.makeText(LoginActivity.this, R.string.login_successful, Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                    startActivity(intent);
                    finish();
                } else {
                    loginFailCount++;
                    if (loginFailCount >= 2 && txtForgotPass != null) {
                        txtForgotPass.setVisibility(View.VISIBLE);
                    }
                    Toast.makeText(LoginActivity.this, R.string.invalid_credentials, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<LoginResponse> call, Throwable t) {
                checkOfflineLogin(email, password);
            }
        });
    }

    public static class LoginRequest {
        String email, password;
        LoginRequest(String e, String p) { this.email = e; this.password = p; }
    }

    public static class LoginResponse {
        boolean success;
        String token;
        User user;
        public static class User {
            int id;
            String email, role, age, gender;
            @com.google.gson.annotations.SerializedName("username") String name;
            @com.google.gson.annotations.SerializedName("stroke_duration") String stroke_duration;
        }
    }
}
