# Arduino MEGA Setup Checklist

## Phase 1: Hardware Assembly ✓

### Components Needed
- [ ] Arduino MEGA 2560 (or compatible)
- [ ] MAX30102 Heart Rate Sensor
- [ ] DS18B20 Temperature Sensor
- [ ] 2x L298N Motor Drivers
- [ ] HW-316 Relay Module (8-channel recommended)
- [ ] 3x Push Buttons (for manual control)
- [ ] 3x LEDs (Red, Green, Blue)
- [ ] 3x 220Ω Resistors (for LEDs)
- [ ] 1x 4.7kΩ Resistor (for DS18B20)
- [ ] USB-A to Micro USB cable
- [ ] 12V/5A Power Supply (for motors)
- [ ] Breadboard + Jumper Wires

### Wiring Assembly
- [ ] Connect sensors to MEGA (see MEGA_UNIFIED_ARCHITECTURE.md)
- [ ] Connect L298N motor drivers
- [ ] Connect HW-316 relay module
- [ ] Connect push buttons
- [ ] Connect status LEDs
- [ ] Connect 12V power supply to motors
- [ ] **VERIFY COMMON GROUND** between 5V and 12V supplies

### Power Testing
- [ ] Arduino MEGA powers on
- [ ] Motors powered (verify 12V supply)
- [ ] Relay module powered
- [ ] All LEDs light up when tested

---

## Phase 2: Arduino Code Upload ✓

### Arduino IDE Setup
- [ ] Download Arduino IDE from arduino.cc
- [ ] Open Arduino IDE
- [ ] Go to Tools → Board → Select "Arduino Mega 2560"
- [ ] Go to Tools → Port → Select COM port (where Arduino is connected)

### Library Installation
- [ ] Sketch → Include Library → Manage Libraries
- [ ] Search for "MAX30105" → Install by SparkFun
- [ ] Search for "OneWire" → Install by Jim Studt
- [ ] Search for "DallasTemperature" → Install
- [ ] Close library manager

### Upload Code
- [ ] Open file: `Arduino/MegaSensorHub/MegaSensorHub.ino`
- [ ] Copy entire code into Arduino IDE
- [ ] Click Sketch → Verify (compile check)
- [ ] Click Upload
- [ ] Wait for "Upload successful" message

### Serial Monitor Test
- [ ] Open Tools → Serial Monitor
- [ ] Set baud rate to 115200
- [ ] Press Arduino MEGA reset button
- [ ] You should see:
  ```
  [SYSTEM] ARDUINO MEGA REHABILITATION HUB STARTING
  [SUCCESS] MAX30102 initialized
  [SUCCESS] DS18B20 initialized
  [READY] System online and waiting for commands...
  ```

---

## Phase 3: Backend Setup ✓

### Database Migration
- [ ] Open phpMyAdmin (http://localhost/phpmyadmin)
- [ ] Select database: `vitals_db`
- [ ] Go to SQL tab
- [ ] Copy and paste the migration SQL from `RELAY_CONTROL_ENDPOINTS.js`
- [ ] Execute

### Add API Endpoints
- [ ] Open file: `backend/index.js`
- [ ] Find line ~1000 (after existing sensor endpoints)
- [ ] Copy all endpoint code from `RELAY_CONTROL_ENDPOINTS.js`
- [ ] Paste into `index.js`
- [ ] Save file

### Start Backend
- [ ] Open PowerShell in `backend` folder
- [ ] Run: `.\run_backend.ps1`
- [ ] Verify:
  ```
  [SERIAL_SCAN] Arduino detected on COM##
  [HARDWARE_LINK] ✓ Serial connected to COM##
  [SERVER] Listening on port 3000
  ```

### Test Serial Connection
- [ ] Open another PowerShell
- [ ] Run: `node test_serial.js`
- [ ] You should see sensor data being received

---

## Phase 4: Testing ✓

### Manual Tests (Arduino)
- [ ] Press BTN_FORCE (Pin A1) → Motors should increase speed
- [ ] Press BTN_TOGGLE (Pin A2) → Motors should toggle on/off
- [ ] Check Serial Monitor for button press logs

### Backend API Tests
- [ ] Get JWT token via login endpoint
- [ ] Test relay command:
  ```bash
  curl -X POST http://localhost:3000/api/relay/command \
    -H "Authorization: Bearer YOUR_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"command": "ARM:EXT"}'
  ```
- [ ] Verify response: `{"success": true}`
- [ ] **Verify motors actually move**

- [ ] Test getting status:
  ```bash
  curl http://localhost:3000/api/relay/status \
    -H "Authorization: Bearer YOUR_TOKEN"
  ```

- [ ] Test emergency stop:
  ```bash
  curl -X POST http://localhost:3000/api/relay/emergency-stop \
    -H "Authorization: Bearer YOUR_TOKEN"
  ```

### Dashboard Tests
- [ ] Open web dashboard: http://localhost:3000
- [ ] Login with your credentials
- [ ] Go to "Monitoring" section
- [ ] You should see buttons:
  - [ ] Arm Extend
  - [ ] Arm Retract
  - [ ] Leg Extend
  - [ ] Leg Retract
  - [ ] Glove On
  - [ ] Glove Off
- [ ] Click each button
- [ ] **Verify corresponding motor/relay moves**
- [ ] Check sensor readings update in real-time

---

## Phase 5: Android App ✓

### Build and Deploy
- [ ] Open Android Studio
- [ ] Ensure `RetrofitClient.java` has correct backend IP
- [ ] Build and run app on phone
- [ ] Login with credentials

### Test Controls
- [ ] Navigate to control/monitoring screen
- [ ] Tap "Arm Extend" button
- [ ] **Verify arm motor extends**
- [ ] Tap "Arm Retract" button
- [ ] **Verify arm motor retracts**
- [ ] Repeat for leg and glove controls
- [ ] Check real-time vitals update

### Test Sensors
- [ ] Place finger on MAX30102 sensor
- [ ] Wait 5 seconds
- [ ] App should show Heart Rate and SpO2
- [ ] Dashboard should show live body temperature

---

## Phase 6: Performance Tuning ✓

### Fine-Tune Motor Speeds
- [ ] In `MegaSensorHub.ino`, adjust these values:
  ```cpp
  motorExtend(ARM_EN, 120);  // 0-255, try 100-200
  motorExtend(LEG_EN, 120);
  ```

### Adjust Sensor Sampling
- [ ] Modify these intervals in `loop()`:
  ```cpp
  if (now - lastSensorRead >= 500)     // Change to 250-1000ms
  if (now - lastHeartbeat >= 2000)     // Change to 1000-5000ms
  ```

### Database Optimization
- [ ] Add indexes to frequently queried tables:
  ```sql
  CREATE INDEX idx_sensor_type ON vitals(sensor_type);
  CREATE INDEX idx_action ON control_logs(action);
  ```

---

## Troubleshooting Reference

### Arduino Not Detected
```
SYMPTOM: "[SERIAL_SCAN] No Arduino detected on any port"
FIX:
1. Check USB cable connection
2. Verify COM port in Device Manager
3. Install CH340 drivers if using clone board
4. Try different USB port on PC
```

### Motors Not Moving
```
SYMPTOM: Command sent successfully but motors don't move
FIX:
1. Verify 12V power supply is connected
2. Check L298N GND is connected to Arduino GND
3. Test L298N directly with multimeter
4. Check wiring matches pin assignments
```

### Sensors Reading Zero
```
SYMPTOM: Heart rate shows 0, temp shows 0
FIX:
1. Check I2C connections (A4/A5)
2. Verify pull-up resistors installed
3. Check Arduino Serial Monitor for init errors
4. Try sensor individually with example sketch
```

### Backend Not Receiving Data
```
SYMPTOM: "Arduino not connected" error from API
FIX:
1. Check backend is running: node index.js
2. Verify Arduino COM port in console
3. Test with Arduino IDE Serial Monitor first
4. Check serial baud rate is 115200
```

---

## Success Criteria ✅

- [ ] Arduino MEGA powers on
- [ ] All sensors initialize successfully
- [ ] Backend receives sensor data every 2 seconds
- [ ] Motor commands sent from backend execute correctly
- [ ] Android app displays real-time vitals
- [ ] Android app can control all actuators
- [ ] Manual buttons work as overrides
- [ ] Emergency stop works immediately
- [ ] No error messages in console

---

## Next: Production Deployment

Once everything works locally:
1. Document any custom pin assignments
2. Create backup of working Arduino code
3. Test on different patient (if applicable)
4. Monitor for 1 hour continuous operation
5. Document any issues found
6. Create deployment guide for field technicians

---

**Questions?** Check the backend console logs (`run_backend.ps1`) or Arduino Serial Monitor (`Tools → Serial Monitor`) for detailed error messages.

**Emergency Contact:** If system fails, press BTN_TOGGLE to stop all motors immediately.
