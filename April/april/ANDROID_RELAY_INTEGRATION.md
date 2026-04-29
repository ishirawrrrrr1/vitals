# Android App Integration Guide - Relay Control Endpoints

**Target:** Add motor/relay control UI to Android dashboard  
**Backend:** Running on `http://192.168.1.4:3001`  
**Endpoints:** 5 new REST APIs for actuator control

---

## 📋 Overview

The Android app needs to integrate with 5 new backend endpoints to control the rehabilitation equipment:

| Feature | Endpoint | Method |
|---------|----------|--------|
| Send Command | `/api/relay/command` | POST |
| Get Status | `/api/relay/status` | GET |
| View History | `/api/relay/history` | GET |
| Emergency Stop | `/api/relay/emergency-stop` | POST |
| List Commands | `/api/relay/commands` | GET |

---

## 🔧 Implementation Steps

### **1. Add Retrofit Interface (RetrofitClient.java)**

Add these methods to your Retrofit service interface:

```java
public interface ApiService {
    // ... existing methods ...

    // Relay Control Endpoints
    @POST("api/relay/command")
    Call<CommandResponse> sendCommand(
        @Header("Authorization") String token,
        @Body CommandRequest request
    );

    @GET("api/relay/status")
    Call<ActuatorStatus> getActuatorStatus(
        @Header("Authorization") String token
    );

    @GET("api/relay/history")
    Call<List<CommandLog>> getCommandHistory(
        @Header("Authorization") String token,
        @Query("limit") int limit
    );

    @POST("api/relay/emergency-stop")
    Call<EmergencyResponse> emergencyStop(
        @Header("Authorization") String token
    );

    @GET("api/relay/commands")
    Call<AvailableCommands> getAvailableCommands(
        @Header("Authorization") String token
    );
}
```

---

### **2. Create Data Models**

Create these Java classes in your `models` package:

#### **CommandRequest.java**
```java
package com.example.monitoring.models;

public class CommandRequest {
    private String command;
    private int duration;

    public CommandRequest(String command) {
        this.command = command;
        this.duration = 0;
    }

    public CommandRequest(String command, int duration) {
        this.command = command;
        this.duration = duration;
    }

    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }
    public int getDuration() { return duration; }
    public void setDuration(int duration) { this.duration = duration; }
}
```

#### **CommandResponse.java**
```java
package com.example.monitoring.models;

public class CommandResponse {
    private boolean success;
    private String command;
    private String timestamp;
    private String message;

    public boolean isSuccess() { return success; }
    public String getCommand() { return command; }
    public String getTimestamp() { return timestamp; }
    public String getMessage() { return message; }
}
```

#### **ActuatorStatus.java**
```java
package com.example.monitoring.models;

public class ActuatorStatus {
    private boolean arm_moving;
    private boolean leg_moving;
    private boolean glove_active;
    private String timestamp;

    public boolean isArmMoving() { return arm_moving; }
    public boolean isLegMoving() { return leg_moving; }
    public boolean isGloveActive() { return glove_active; }
    public String getTimestamp() { return timestamp; }
}
```

#### **CommandLog.java**
```java
package com.example.monitoring.models;

public class CommandLog {
    private String action;
    private String status;
    private String timestamp;
    private int user_id;

    public String getAction() { return action; }
    public String getStatus() { return status; }
    public String getTimestamp() { return timestamp; }
    public int getUserId() { return user_id; }
}
```

#### **AvailableCommands.java**
```java
package com.example.monitoring.models;

import java.util.List;

public class AvailableCommands {
    public static class Command {
        public String code;
        public String label;
        public String group;

        public String getCode() { return code; }
        public String getLabel() { return label; }
        public String getGroup() { return group; }
    }

    private List<Command> available_commands;
    private String notes;

    public List<Command> getAvailableCommands() { return available_commands; }
    public String getNotes() { return notes; }
}
```

#### **EmergencyResponse.java**
```java
package com.example.monitoring.models;

import java.util.List;

public class EmergencyResponse {
    private boolean success;
    private String message;
    private List<String> commands;

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public List<String> getCommands() { return commands; }
}
```

---

### **3. Create UI Fragment (ActuatorControlFragment.java)**

```java
package com.example.monitoring.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.fragment.app.Fragment;
import com.example.monitoring.R;
import com.example.monitoring.models.ActuatorStatus;
import com.example.monitoring.models.CommandResponse;
import com.example.monitoring.network.RetrofitClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ActuatorControlFragment extends Fragment {
    
    private String jwtToken;
    private Button armExtBtn, armRetBtn, armStopBtn;
    private Button legExtBtn, legRetBtn, legStopBtn;
    private Button gloveOnBtn, gloveOffBtn, emergencyBtn;
    private TextView armStatusTv, legStatusTv, gloveStatusTv;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_actuator_control, container, false);

        // Get token from SharedPreferences or activity
        jwtToken = getActivity().getSharedPreferences("auth", 0).getString("token", "");

        // Initialize buttons
        armExtBtn = view.findViewById(R.id.btn_arm_extend);
        armRetBtn = view.findViewById(R.id.btn_arm_retract);
        armStopBtn = view.findViewById(R.id.btn_arm_stop);
        legExtBtn = view.findViewById(R.id.btn_leg_extend);
        legRetBtn = view.findViewById(R.id.btn_leg_retract);
        legStopBtn = view.findViewById(R.id.btn_leg_stop);
        gloveOnBtn = view.findViewById(R.id.btn_glove_on);
        gloveOffBtn = view.findViewById(R.id.btn_glove_off);
        emergencyBtn = view.findViewById(R.id.btn_emergency_stop);

        // Initialize status displays
        armStatusTv = view.findViewById(R.id.tv_arm_status);
        legStatusTv = view.findViewById(R.id.tv_leg_status);
        gloveStatusTv = view.findViewById(R.id.tv_glove_status);

        // Set click listeners
        armExtBtn.setOnClickListener(v -> sendCommand("ARM:EXT"));
        armRetBtn.setOnClickListener(v -> sendCommand("ARM:RET"));
        armStopBtn.setOnClickListener(v -> sendCommand("ARM:STOP"));
        legExtBtn.setOnClickListener(v -> sendCommand("LEG:EXT"));
        legRetBtn.setOnClickListener(v -> sendCommand("LEG:RET"));
        legStopBtn.setOnClickListener(v -> sendCommand("LEG:STOP"));
        gloveOnBtn.setOnClickListener(v -> sendCommand("GLOVE:ON"));
        gloveOffBtn.setOnClickListener(v -> sendCommand("GLOVE:OFF"));
        emergencyBtn.setOnClickListener(v -> emergencyStop());

        // Start polling status
        startStatusPolling();

        return view;
    }

    private void sendCommand(String command) {
        CommandRequest request = new CommandRequest(command);
        
        RetrofitClient.getApiService().sendCommand("Bearer " + jwtToken, request)
            .enqueue(new Callback<CommandResponse>() {
                @Override
                public void onResponse(Call<CommandResponse> call, Response<CommandResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        Toast.makeText(getContext(), 
                            "✓ " + response.body().getMessage(), 
                            Toast.LENGTH_SHORT).show();
                        updateStatus();
                    } else {
                        Toast.makeText(getContext(), "Error: Command failed", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<CommandResponse> call, Throwable t) {
                    Toast.makeText(getContext(), "Network error: " + t.getMessage(), 
                        Toast.LENGTH_SHORT).show();
                }
            });
    }

    private void emergencyStop() {
        RetrofitClient.getApiService().emergencyStop("Bearer " + jwtToken)
            .enqueue(new Callback<EmergencyResponse>() {
                @Override
                public void onResponse(Call<EmergencyResponse> call, Response<EmergencyResponse> response) {
                    if (response.isSuccessful()) {
                        Toast.makeText(getContext(), 
                            "🚨 EMERGENCY STOP EXECUTED", 
                            Toast.LENGTH_LONG).show();
                        updateStatus();
                    }
                }

                @Override
                public void onFailure(Call<EmergencyResponse> call, Throwable t) {
                    Toast.makeText(getContext(), "Emergency stop failed", Toast.LENGTH_SHORT).show();
                }
            });
    }

    private void updateStatus() {
        RetrofitClient.getApiService().getActuatorStatus("Bearer " + jwtToken)
            .enqueue(new Callback<ActuatorStatus>() {
                @Override
                public void onResponse(Call<ActuatorStatus> call, Response<ActuatorStatus> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        ActuatorStatus status = response.body();
                        armStatusTv.setText("Arm: " + (status.isArmMoving() ? "MOVING" : "STOPPED"));
                        legStatusTv.setText("Leg: " + (status.isLegMoving() ? "MOVING" : "STOPPED"));
                        gloveStatusTv.setText("Glove: " + (status.isGloveActive() ? "ACTIVE" : "INACTIVE"));
                    }
                }

                @Override
                public void onFailure(Call<ActuatorStatus> call, Throwable t) {
                    // Silently fail status updates
                }
            });
    }

    private void startStatusPolling() {
        // Poll every 2 seconds
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(2000);
                    getActivity().runOnUiThread(this::updateStatus);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();
    }
}
```

---

### **4. Create Layout File (fragment_actuator_control.xml)**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp"
    android:background="#f5f5f5">

    <!-- ARM CONTROLS -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:background="@drawable/card_background"
        android:padding="12dp"
        android:layout_marginBottom="12dp">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="ARM CONTROL"
            android:textStyle="bold"
            android:textSize="16sp"
            android:layout_marginBottom="8dp" />

        <TextView
            android:id="@+id/tv_arm_status"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Status: STOPPED"
            android:textSize="12sp"
            android:layout_marginBottom="8dp" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:spacing="8dp">

            <Button
                android:id="@+id/btn_arm_extend"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="Extend"
                android:backgroundTint="#4CAF50" />

            <Button
                android:id="@+id/btn_arm_retract"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="Retract"
                android:backgroundTint="#2196F3" />

            <Button
                android:id="@+id/btn_arm_stop"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="Stop"
                android:backgroundTint="#FF9800" />
        </LinearLayout>
    </LinearLayout>

    <!-- LEG CONTROLS -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:background="@drawable/card_background"
        android:padding="12dp"
        android:layout_marginBottom="12dp">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="LEG CONTROL"
            android:textStyle="bold"
            android:textSize="16sp"
            android:layout_marginBottom="8dp" />

        <TextView
            android:id="@+id/tv_leg_status"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Status: STOPPED"
            android:textSize="12sp"
            android:layout_marginBottom="8dp" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:spacing="8dp">

            <Button
                android:id="@+id/btn_leg_extend"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="Extend"
                android:backgroundTint="#4CAF50" />

            <Button
                android:id="@+id/btn_leg_retract"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="Retract"
                android:backgroundTint="#2196F3" />

            <Button
                android:id="@+id/btn_leg_stop"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="Stop"
                android:backgroundTint="#FF9800" />
        </LinearLayout>
    </LinearLayout>

    <!-- GLOVE CONTROLS -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:background="@drawable/card_background"
        android:padding="12dp"
        android:layout_marginBottom="12dp">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="GLOVE THERAPY"
            android:textStyle="bold"
            android:textSize="16sp"
            android:layout_marginBottom="8dp" />

        <TextView
            android:id="@+id/tv_glove_status"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Status: INACTIVE"
            android:textSize="12sp"
            android:layout_marginBottom="8dp" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:spacing="8dp">

            <Button
                android:id="@+id/btn_glove_on"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="Activate"
                android:backgroundTint="#4CAF50" />

            <Button
                android:id="@+id/btn_glove_off"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="Deactivate"
                android:backgroundTint="#F44336" />
        </LinearLayout>
    </LinearLayout>

    <!-- EMERGENCY STOP -->
    <Button
        android:id="@+id/btn_emergency_stop"
        android:layout_width="match_parent"
        android:layout_height="56dp"
        android:text="🚨 EMERGENCY STOP"
        android:textSize="16sp"
        android:textStyle="bold"
        android:backgroundTint="#D32F2F" />

</LinearLayout>
```

---

### **5. Add Fragment to Navigation**

In `activity_dashboard.xml` or your navigation file, add:

```xml
<fragment
    android:id="@+id/actuator_control_fragment"
    android:name="com.example.monitoring.ui.ActuatorControlFragment"
    android:label="Actuator Control" />
```

---

### **6. Connect to Main Activity**

In your `DashboardActivity.java`, add a tab or menu option:

```java
// Add to your bottom navigation menu
MenuItem actuatorItem = menu.add(0, R.id.nav_actuators, 0, "Actuators");
actuatorItem.setIcon(R.drawable.ic_actuator);

// In onNavigationItemSelected():
case R.id.nav_actuators:
    fragment = new ActuatorControlFragment();
    break;
```

---

## 🔌 Hardware Communication

The command flow is:

```
Android App Button Click
      ↓
POST /api/relay/command
      ↓
Backend validates
      ↓
Serial: "CMD:ARM:EXT\n" → Arduino MEGA
      ↓
Arduino parses command
      ↓
GPIO / PWM activation
      ↓
Motor/Relay response
      ↓
Arduino status broadcast
      ↓
App polls /api/relay/status
      ↓
UI updates
```

---

## ✅ Testing Checklist

- [ ] Models compile without errors
- [ ] Retrofit interface methods added
- [ ] Fragment layout displays correctly
- [ ] Buttons click and send HTTP requests
- [ ] Backend receives commands (check logs)
- [ ] Arduino responds (check serial monitor)
- [ ] Status updates reflect motor state
- [ ] Emergency stop works
- [ ] Network errors handled gracefully
- [ ] Token refresh works (if expired)

---

## 🚀 Deployment

1. Build APK: `./gradlew assembleRelease`
2. Sign APK with your keystore
3. Install on test devices
4. Verify with Arduino on different networks
5. Test with multiple users simultaneously
6. Monitor backend logs for errors

---

## 📞 Troubleshooting

**Commands not sending:**
- Verify JWT token is valid
- Check network connectivity
- Ensure backend is running

**Status not updating:**
- Verify database has actuator columns
- Check Arduino is broadcasting status
- Review backend serial logs

**Arduino not responding:**
- Verify `MegaSensorHub.ino` uploaded
- Check serial connection at 115200 baud
- Ensure buttons not stuck/short-circuited

---

**Status:** ✅ Ready for implementation  
**Last Updated:** April 26, 2026
