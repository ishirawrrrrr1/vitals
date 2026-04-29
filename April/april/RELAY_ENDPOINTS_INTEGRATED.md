# Relay Control Endpoints - Integration Complete ✅

**Status:** Successfully integrated into `backend/index.js`  
**Date:** April 26, 2026  
**Changes Made:** 5 REST API endpoints + database tables

---

## 📋 Integration Summary

### **What Was Added**

#### **1. Five REST API Endpoints**
All endpoints require JWT authentication via `verifyToken` middleware.

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/relay/command` | POST | Send motor/relay commands to Arduino MEGA |
| `/api/relay/status` | GET | Get current actuator status (arm, leg, glove) |
| `/api/relay/history` | GET | Get command execution history |
| `/api/relay/emergency-stop` | POST | Emergency stop all actuators immediately |
| `/api/relay/commands` | GET | List available commands with descriptions |

#### **2. Database Tables Created**

**`control_logs` Table:**
```sql
CREATE TABLE control_logs (
  id INT AUTO_INCREMENT PRIMARY KEY,
  action VARCHAR(50) NOT NULL,
  status VARCHAR(20) DEFAULT 'PENDING',
  timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
  user_id INT,
  duration_ms INT DEFAULT NULL,
  notes TEXT,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
  INDEX idx_timestamp (timestamp),
  INDEX idx_user (user_id),
  INDEX idx_action (action)
);
```

**`vitals` Table Columns Added:**
```sql
ALTER TABLE vitals ADD COLUMN IF NOT EXISTS arm_moving BOOLEAN DEFAULT FALSE;
ALTER TABLE vitals ADD COLUMN IF NOT EXISTS leg_moving BOOLEAN DEFAULT FALSE;
ALTER TABLE vitals ADD COLUMN IF NOT EXISTS glove_active BOOLEAN DEFAULT FALSE;
```

---

## 🎮 Available Commands

Send via `POST /api/relay/command` with JSON body:

### **Arm Control**
- `ARM:EXT` - Extend arm (GPIO 22-23, PWM 9)
- `ARM:RET` - Retract arm
- `ARM:STOP` - Stop arm immediately

### **Leg Control**
- `LEG:EXT` - Extend leg (GPIO 24-25, PWM 10)
- `LEG:RET` - Retract leg
- `LEG:STOP` - Stop leg immediately

### **Glove Control**
- `GLOVE:ON` - Activate therapy glove (Relay GPIO 26)
- `GLOVE:OFF` - Deactivate therapy glove

### **Emergency**
- `POST /api/relay/emergency-stop` - Stop everything

---

## 📡 Protocol

### **Request Format**

#### Send Command
```bash
curl -X POST http://192.168.1.4:3000/api/relay/command \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"command": "ARM:EXT", "duration": 5000}'
```

#### Get Status
```bash
curl -X GET http://192.168.1.4:3000/api/relay/status \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

#### Get History
```bash
curl -X GET "http://192.168.1.4:3000/api/relay/history?limit=20" \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

#### Emergency Stop
```bash
curl -X POST http://192.168.1.4:3000/api/relay/emergency-stop \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

#### Available Commands
```bash
curl -X GET http://192.168.1.4:3000/api/relay/commands \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

---

## ✅ Response Examples

### **Successful Command**
```json
{
  "success": true,
  "command": "ARM:EXT",
  "timestamp": "2026-04-26T10:30:45.123Z",
  "message": "Command \"ARM:EXT\" sent to hardware"
}
```

### **Status Response**
```json
{
  "arm_moving": true,
  "leg_moving": false,
  "glove_active": true,
  "timestamp": "2026-04-26T10:30:42.456Z"
}
```

### **History Response**
```json
[
  {
    "action": "ARM:EXT",
    "status": "SENT",
    "timestamp": "2026-04-26T10:30:45",
    "user_id": 1
  },
  {
    "action": "GLOVE:ON",
    "status": "SENT",
    "timestamp": "2026-04-26T10:30:40",
    "user_id": 1
  }
]
```

---

## 🔌 Hardware Integration

### **Arduino MEGA Pin Assignments**
(From `MegaSensorHub.ino`)

| Device | GPIO | Type |
|--------|------|------|
| ARM Motor IN1 | 22 | GPIO |
| ARM Motor IN2 | 23 | GPIO |
| ARM PWM Enable | 9 | PWM |
| LEG Motor IN1 | 24 | GPIO |
| LEG Motor IN2 | 25 | GPIO |
| LEG PWM Enable | 10 | PWM |
| GLOVE Relay | 26 | GPIO |

### **Command Flow**
```
Android App
    ↓
POST /api/relay/command (JWT authenticated)
    ↓
Backend validates command
    ↓
Sends via Serial: "CMD:ARM:EXT\n"
    ↓
Arduino MEGA processes
    ↓
Motor/Relay activates
    ↓
Arduino broadcasts status
    ↓
Backend logs to control_logs table
    ↓
Status reflected in /api/relay/status
```

---

## 🧪 Testing Checklist

- [ ] Backend starts successfully: `npm start`
- [ ] Database tables created: `control_logs`, columns in `vitals`
- [ ] Get JWT token from `/api/auth/login`
- [ ] Test ARM:EXT command (should see in Arduino serial monitor)
- [ ] Test ARM:RET command
- [ ] Test ARM:STOP command
- [ ] Test LEG:EXT, LEG:RET, LEG:STOP
- [ ] Test GLOVE:ON, GLOVE:OFF
- [ ] Test `/api/relay/status` endpoint
- [ ] Test `/api/relay/history` endpoint
- [ ] Test `/api/relay/emergency-stop` endpoint
- [ ] Verify commands logged in `control_logs` table
- [ ] Test with Android app (when UI buttons added)

---

## 🚀 Next Steps

1. **Android UI Integration** - Add buttons to dashboard for actuator control
2. **WebSocket Real-time Updates** - Broadcast status changes to connected clients
3. **Command Validation** - Add command duration/safety checks
4. **Actuator Feedback** - Read GPIO status from Arduino to confirm execution
5. **History Analytics** - Track usage patterns and performance metrics

---

## 📝 Files Modified

✅ `backend/index.js` - Added 5 endpoints + table creation  
✅ Created this integration guide

## 🔗 Related Files

- `RELAY_CONTROL_ENDPOINTS.js` - Original endpoint documentation
- `MegaSensorHub.ino` - Arduino firmware
- `MEGA_UNIFIED_ARCHITECTURE.md` - Hardware architecture guide

---

**Status:** ✅ Backend ready for Android integration
