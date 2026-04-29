# Arduino MEGA Unified Hub Architecture (Simplified)

## Overview
All sensors and actuators are now controlled by **Arduino MEGA 2560**, eliminating the need for ESP32. The backend receives data and sends commands.

---

## Hardware Wiring

### Sensors
| Sensor | Arduino Pin | Type |
|--------|------------|------|
| MAX30102 SDA | A4 | I2C |
| MAX30102 SCL | A5 | I2C |
| DS18B20 Data | 2 | 1-Wire |
| Button 1 (Force) | A1 | Digital Input |
| Button 2 (Toggle) | A2 | Digital Input |
| Button 3 (Mode) | A3 | Digital Input |

### Actuators (Motor Drivers)
| Component | Arduino Pin | Purpose |
|-----------|------------|---------|
| L298N #1 IN1 | 22 | Arm Extend |
| L298N #1 IN2 | 23 | Arm Retract |
| L298N #1 EN | 9 | Arm Speed (PWM) |
| L298N #2 IN1 | 24 | Leg Extend |
| L298N #2 IN2 | 25 | Leg Retract |
| L298N #2 EN | 10 | Leg Speed (PWM) |
| HW-316 Relay | 26 | Glove Power |

### Status Indicators
| LED | Arduino Pin | Meaning |
|-----|------------|---------|
| Green (Online) | 13 | System Online |
| Red (Error) | 12 | Error Status |
| Blue (Active) | 11 | Data Transmission |

### Power Supply
- **Arduino MEGA**: 5V/2A USB
- **L298N Motors**: 12V/5A separate power supply
- **Common Ground**: Must be shared between 5V system and 12V motor supply

---

## Data Flow

### 1. Arduino → Backend (Every 2 seconds)
Arduino sends:
```
DATA:{"heart_rate":75, "spo2":98, "body_temp":36.6, "arm_moving":false, "leg_moving":false, "glove_active":false}
```

### 2. Backend receives and parses
```javascript
// Backend parses the JSON and saves to database
{
  heart_rate: 75,
  spo2: 98,
  body_temp: 36.6,
  arm_moving: false,
  leg_moving: false,
  glove_active: false,
  timestamp: "2026-04-26T10:30:45Z"
}
```

### 3. Backend → Arduino (On demand from app)
Android app sends command → Backend routes to Arduino:
```
CMD:ARM:EXT      # Extend arm
CMD:ARM:RET      # Retract arm
CMD:LEG:EXT      # Extend leg
CMD:LEG:RET      # Retract leg
CMD:ARM:STOP     # Stop arm
CMD:LEG:STOP     # Stop leg
CMD:GLOVE:ON     # Activate glove
CMD:GLOVE:OFF    # Deactivate glove
```

---

## Button Controls (Manual Override)

| Button | Action |
|--------|--------|
| **K2 (Force - A1)** | Increase motor speed to 150 |
| **K3 (Toggle - A2)** | Start/Stop entire session |
| **K4 (Mode - A3)** | Cycle between modes (reserved for future) |

---

## Backend API Endpoints for Control

Add these to `backend/index.js`:

```javascript
// Send command to Arduino
app.post('/api/relay/command', verifyToken, async (req, res) => {
    const { command, duration } = req.body;
    // Valid commands: ARM:EXT, ARM:RET, LEG:EXT, LEG:RET, GLOVE:ON, GLOVE:OFF
    
    const fullCmd = `CMD:${command}`;
    
    // Send to Arduino
    if (port && port.isOpen) {
        port.write(fullCmd + '\n', (err) => {
            if (err) {
                return res.status(500).json({ error: 'Failed to send command' });
            }
            
            // Log to database
            await pool.query(
                'INSERT INTO control_logs (action, status) VALUES (?, ?)',
                [command, 'SENT']
            );
            
            res.json({ success: true, command, timestamp: new Date() });
        });
    }
});

// Get actuator status
app.get('/api/relay/status', verifyToken, async (req, res) => {
    const result = await pool.query(
        'SELECT * FROM vitals WHERE sensor_type="actuator" ORDER BY timestamp DESC LIMIT 1'
    );
    res.json(result[0][0] || {});
});
```

---

## Setup Instructions

### 1. Hardware Assembly
1. Connect all sensors to Arduino MEGA as per wiring table above
2. Connect L298N motor drivers to Arduino MEGA pins 22-25 and 9-10
3. Connect HW-316 relay to pin 26
4. Connect 12V power supply to L298N modules (separate from Arduino power)
5. **CRITICAL**: Connect common ground between 5V Arduino GND and 12V motor supply GND

### 2. Arduino Code
1. Upload `MegaSensorHub.ino` to Arduino MEGA 2560
2. Install required libraries in Arduino IDE:
   - MAX30105 library (by SparkFun)
   - OneWire library
   - DallasTemperature library
3. Select Board: "Arduino Mega 2560"
4. Select COM port where Arduino is connected
5. Click Upload

### 3. Backend Configuration
1. Backend automatically detects Arduino on any COM port
2. Receives sensor data and stores in MySQL
3. Listens for API commands from Android app

### 4. Android App
1. Login to dashboard
2. Go to "Monitoring" or "Control" section
3. Buttons appear to control arm/leg/glove
4. Real-time vitals update automatically

---

## Command Examples

### Via Backend API
```bash
# Extend arm
curl -X POST http://192.168.1.4:3000/api/relay/command \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"command": "ARM:EXT"}'

# Activate glove
curl -X POST http://192.168.1.4:3000/api/relay/command \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{"command": "GLOVE:ON"}'
```

### Via Arduino Serial Monitor (for testing)
```
CMD:ARM:EXT
CMD:LEG:EXT
CMD:GLOVE:ON
CMD:ARM:STOP
```

---

## Troubleshooting

### Arduino not detected
- Check USB connection
- Verify COM port in Device Manager
- Ensure CH340 drivers installed (if using cloned board)

### Motors not moving
- Verify 12V power supply connected to L298N
- Check common ground is connected
- Ensure pin connections match wiring table

### Sensors reading zero
- Check I2C/1-Wire connections
- Verify pull-up resistors (4.7kΩ for DS18B20, OneWire)
- Check Arduino IDE console for initialization errors

### Backend not receiving data
- Verify backend is running: `node index.js`
- Check COM port in Serial scan (should print in console)
- Open Arduino IDE Serial Monitor to verify data output

---

## System Benefits

✅ **Simplified wiring** - No level shifters needed  
✅ **Reduced latency** - Direct USB to backend  
✅ **Easier debugging** - One microcontroller to manage  
✅ **Scalable** - MEGA has 54+ digital pins  
✅ **Cost effective** - MEGA ~$20 vs MEGA+ESP32 ~$40  
✅ **Single point of failure** - But easier to replace  

---

## Next Steps

1. Update Arduino code to your specific requirements
2. Test motor controls with manual button presses
3. Verify sensor readings in Arduino IDE Serial Monitor
4. Test backend relay endpoints
5. Integrate with Android app UI

For questions, check the backend console logs or Arduino IDE Serial Monitor!
