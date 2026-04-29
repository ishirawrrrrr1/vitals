# 🔌 Relay Control Endpoints - Quick Start Guide

**Backend Status:** ✅ Running on `http://192.168.1.4:3001`  
**Database:** ✅ Connected (control_logs table ready)  
**Arduino:** ✅ Detected on COM11

---

## 🚀 Quick Test - Using cURL or Postman

### **Step 1: Get JWT Token**

```bash
curl -X POST http://192.168.1.4:3001/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "admin123"
  }'
```

**Response:**
```json
{
  "success": true,
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "user": {
    "id": 1,
    "username": "admin",
    "email": "admin@vitalsmonitor.local",
    "role": "admin"
  }
}
```

Save the `token` value for the next steps.

---

### **Step 2: List Available Commands**

```bash
curl -X GET http://192.168.1.4:3001/api/relay/commands \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

**Response:**
```json
{
  "available_commands": [
    { "code": "ARM:EXT", "label": "Extend Arm", "group": "Arm" },
    { "code": "ARM:RET", "label": "Retract Arm", "group": "Arm" },
    { "code": "ARM:STOP", "label": "Stop Arm", "group": "Arm" },
    { "code": "LEG:EXT", "label": "Extend Leg", "group": "Leg" },
    { "code": "LEG:RET", "label": "Retract Leg", "group": "Leg" },
    { "code": "LEG:STOP", "label": "Stop Leg", "group": "Leg" },
    { "code": "GLOVE:ON", "label": "Activate Glove", "group": "Glove" },
    { "code": "GLOVE:OFF", "label": "Deactivate Glove", "group": "Glove" }
  ],
  "notes": "Send command via POST /api/relay/command with {\"command\": \"CODE\"}"
}
```

---

### **Step 3: Send a Command**

**Extend the Arm:**
```bash
curl -X POST http://192.168.1.4:3001/api/relay/command \
  -H "Authorization: Bearer YOUR_TOKEN_HERE" \
  -H "Content-Type: application/json" \
  -d '{"command": "ARM:EXT"}'
```

**Response:**
```json
{
  "success": true,
  "command": "ARM:EXT",
  "timestamp": "2026-04-26T11:45:30.123Z",
  "message": "Command \"ARM:EXT\" sent to hardware"
}
```

**Other Commands:**
```bash
# Retract arm
curl -X POST http://192.168.1.4:3001/api/relay/command \
  -H "Authorization: Bearer YOUR_TOKEN_HERE" \
  -H "Content-Type: application/json" \
  -d '{"command": "ARM:RET"}'

# Extend leg
curl -X POST http://192.168.1.4:3001/api/relay/command \
  -H "Authorization: Bearer YOUR_TOKEN_HERE" \
  -H "Content-Type: application/json" \
  -d '{"command": "LEG:EXT"}'

# Activate glove
curl -X POST http://192.168.1.4:3001/api/relay/command \
  -H "Authorization: Bearer YOUR_TOKEN_HERE" \
  -H "Content-Type: application/json" \
  -d '{"command": "GLOVE:ON"}'
```

---

### **Step 4: Check Current Status**

```bash
curl -X GET http://192.168.1.4:3001/api/relay/status \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

**Response:**
```json
{
  "arm_moving": true,
  "leg_moving": false,
  "glove_active": false,
  "timestamp": "2026-04-26T11:45:35.456Z"
}
```

---

### **Step 5: View Command History**

```bash
curl -X GET "http://192.168.1.4:3001/api/relay/history?limit=10" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

**Response:**
```json
[
  {
    "action": "ARM:EXT",
    "status": "SENT",
    "timestamp": "2026-04-26 11:45:30",
    "user_id": 1
  },
  {
    "action": "GLOVE:ON",
    "status": "SENT",
    "timestamp": "2026-04-26 11:45:25",
    "user_id": 1
  }
]
```

---

### **Step 6: Emergency Stop**

```bash
curl -X POST http://192.168.1.4:3001/api/relay/emergency-stop \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

**Response:**
```json
{
  "success": true,
  "message": "Emergency stop executed",
  "commands": [
    "CMD:ARM:STOP",
    "CMD:LEG:STOP",
    "CMD:GLOVE:OFF"
  ]
}
```

This immediately stops all actuators and broadcasts an alert to all connected clients.

---

## 📊 Command Reference

### **Arm Control** (L298N Motor Driver 1)
- `ARM:EXT` - Extend arm (GPIO IN1=22, IN2=23 LOW → HIGH, PWM 9 = 255)
- `ARM:RET` - Retract arm (GPIO IN1=22 HIGH, IN2=23 LOW)
- `ARM:STOP` - Stop arm (PWM 9 = 0)

### **Leg Control** (L298N Motor Driver 2)
- `LEG:EXT` - Extend leg (GPIO IN1=24, IN2=25 LOW → HIGH, PWM 10 = 255)
- `LEG:RET` - Retract leg (GPIO IN1=24 HIGH, IN2=25 LOW)
- `LEG:STOP` - Stop leg (PWM 10 = 0)

### **Glove Control** (HW-316 Relay)
- `GLOVE:ON` - Activate relay (GPIO 26 = HIGH)
- `GLOVE:OFF` - Deactivate relay (GPIO 26 = LOW)

---

## 🔍 Verify in Arduino Serial Monitor

When you send a command, you should see in the Arduino IDE Serial Monitor (115200 baud):

```
[CMD] Received: ARM:EXT
[MOTOR] Arm extending...
[GPIO] OUT: 22=HIGH, 23=LOW, PWM9=255
[MOTOR] Arm extended successfully
```

---

## 📱 Android App Integration (Next Steps)

Add these UI elements to the Android dashboard:

1. **Arm Controls (Group)**
   - Button: "Extend" → POST /api/relay/command with "ARM:EXT"
   - Button: "Retract" → POST /api/relay/command with "ARM:RET"
   - Button: "Stop" → POST /api/relay/command with "ARM:STOP"

2. **Leg Controls (Group)**
   - Button: "Extend" → POST /api/relay/command with "LEG:EXT"
   - Button: "Retract" → POST /api/relay/command with "LEG:RET"
   - Button: "Stop" → POST /api/relay/command with "LEG:STOP"

3. **Glove Controls (Toggle)**
   - Toggle: "Glove" → POST /api/relay/command with "GLOVE:ON" or "GLOVE:OFF"

4. **Status Display**
   - Arm Status: GET /api/relay/status → display `arm_moving`
   - Leg Status: GET /api/relay/status → display `leg_moving`
   - Glove Status: GET /api/relay/status → display `glove_active`

5. **Emergency Button**
   - Red Button: "EMERGENCY STOP" → POST /api/relay/emergency-stop

6. **History Log**
   - List View: GET /api/relay/history?limit=20 → display recent commands

---

## ✅ Integration Checklist

- [x] 5 relay endpoints added to backend
- [x] `control_logs` table created
- [x] Actuator columns added to `vitals` table
- [x] Backend running successfully
- [x] No syntax errors
- [x] Database initialized with tables
- [ ] Android UI buttons added
- [ ] WebSocket real-time status updates (optional)
- [ ] Physical hardware testing with Arduino
- [ ] End-to-end testing with Android app

---

## 🐛 Troubleshooting

### **"Arduino not connected" error**
- Check if Arduino is connected to USB
- Verify COM port in backend logs
- Make sure `MegaSensorHub.ino` is uploaded to Arduino MEGA

### **"Invalid command" error**
- Use only commands from the available list
- Command names are case-sensitive (use uppercase)
- Format: exactly "ARM:EXT", not "ARM_EXT" or "arm:ext"

### **Commands not being logged**
- Verify `control_logs` table exists in MySQL
- Check user_id is valid in users table
- Review backend logs for database errors

### **Emergency stop not working**
- Verify all 3 commands sent to Arduino
- Check if Arduino is in flash mode (wait 30 seconds)
- Ensure buttons on Arduino are not pressed

---

## 📞 Support

For issues, check:
1. Backend logs: `npm start` terminal output
2. Arduino Serial Monitor: 115200 baud
3. MySQL: `SELECT * FROM control_logs;`
4. Database: Verify tables exist with correct schema

---

**Status:** ✅ Ready for Android integration  
**Last Updated:** April 26, 2026
