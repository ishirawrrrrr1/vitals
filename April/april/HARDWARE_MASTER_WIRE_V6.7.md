# 🚀 MASTER HARDWARE GUIDE (v6.7): Clinical Rehabilitation Hub
**Hardware Environment:** April 2026 Breakthrough v6.7

This document is the absolute "Ground Truth" for the wiring configuration of the Clinical Rehabilitation System. It merges the sensor-precision of the Arduino Uno with the high-performance actuator control of the ESP32.

---

## 🛠️ Hardware Inventory
- **Hub Alpha:** ENGLAB ESP32-30P (WROOM 32)
- **Sensor Node:** Arduino Uno R3 (Logic Master)
- **Actuators:** 2x HW-095 (L298N) Motor Drivers
- **Switching:** HW-316 Relay Module (Glove Master)
- **Security:** Logic Level Shifter (4-Channel)

---

## 1️⃣ [LINK] - The Brain Stem (Serial Bridge)
*Connects the ESP32 Hub to the Arduino Sensor Node.*

| Port Label | Arduino Uno (5V) | → | ESP32 Hub (3.3V) |
| :--- | :--- | :---: | :--- |
| **Ground** | **GND** | ↔ | **GND** (Shared) |
| **Data Out** | **Pin 11 (TX)** | → | **Pin RX2 (16)** |
| **Data In** | **Pin 10 (RX)** | ← | **Pin TX2 (17)** |

> [!CAUTION]
> **Logic Level Protection Required!** Do not connect Pin 11 directly to RX2 without the shifter (see Section 2).

---

## 2️⃣ [PROTECTION] - Logic Level Shifter
*Protects the ESP32 (3.3V) from Arduino (5V) logic spikes.*

| Side | Pin | Connected To | Wire Color |
| :--- | :--- | :--- | :--- |
| **High Side (HV)** | **HV** | **Arduino 5V** | Red |
| | **GND** | **Common GND** | Black |
| | **HV1** | **Arduino Pin 11 (TX)** | Blue |
| | **HV2** | **Arduino Pin 10 (RX)** | Yellow |
| **Low Side (LV)** | **LV** | **ESP32 3.3V** | Orange |
| | **GND** | **Common GND** | Black |
| | **LV1** | **ESP32 RX2 (16)** | Blue |
| | **LV2** | **ESP32 TX2 (17)** | Yellow |

---

## 3️⃣ [SENSORS] - Clinical Biometrics
*Precision sensors connected directly to the Arduino Uno.*

### • MAX30102 (Heart Rate & SpO2)
| Sensor Pin | Arduino Pin | Logic Type |
| :--- | :--- | :--- |
| **VCC** | **3.3V** | Power |
| **GND** | **GND** | Ground |
| **SDA** | **Pin A4** | I2C Data |
| **SCL** | **Pin A5** | I2C Clock |

### • DS18B20 (Body Temperature)
| Sensor Wire | Arduino Pin | Logic Type |
| :--- | :--- | :--- |
| **RED (VCC)** | **5V** | Power |
| **YELLOW (DQ)** | **Pin 2** | OneWire Data |
| **BLACK (GND)** | **GND** | Ground |
*Note: Ensure a 4.7kΩ resistor is bridged between VCC and Data.*

---

## 4️⃣ [ACTUATORS] - ESP32 Actuator Hub (v6.7)
*High-power mechanics moved to the ESP32 for zero-latency response.*

### • L298N Dual Motor Driver (Limbs)
| L298N Pin | ESP32 Pin | Logic Label | Purpose |
| :--- | :---: | :--- | :--- |
| **IN1** | **GPIO 13** | ARM_EXT | Extend Arm Actuator |
| **IN2** | **GPIO 12** | ARM_RET | Retract Arm Actuator |
| **IN3** | **GPIO 14** | LEG_EXT | Extend Leg Actuator |
| **IN4** | **GPIO 27** | LEG_RET | Retract Leg Actuator |

### • HW-316 Relay Module (Robotic Glove)
| Relay Pin | ESP32 Pin | Logic Label | Purpose |
| :--- | :---: | :--- | :--- |
| **IN1** | **GPIO 26** | GLOVE_PWR | Master Power Toggle |

> [!IMPORTANT]
> **Power Isolation:** The L298N must be powered by a separate 12V supply. Ensure the **L298N GND** is wired to the **ESP32 GND** to complete the logic circuit.

---

## 5️⃣ [OVERRIDE] - Hardware Control Panel
*Analog override buttons connected to the Arduino Uno. These sync with the Android UI.*

| Button | Arduino Pin | Command String |
| :--- | :--- | :--- |
| **K2 (Red)** | **Pin A1** | `FORCE` (Cycle Intensity) |
| **K3 (Green)**| **Pin A2** | `TOGGLE` (Start/Stop Session) |
| **K4 (Blue)** | **Pin A3** | `MODE` (Cycle Both/Arm/Leg) |

---

## 6️⃣ [STATUS] - Diagnostic Interface
| Indicator | ESP32 Pin | Behavior |
| :--- | :--- | :--- |
| **System LED** | **GPIO 2** | **Fast Blink:** Connecting |
| | | **Solid:** Hub Online |
| | | **Flicker:** Data Packet Sent |

---

## ⚡ Power Supply Grid
1. **Control Power (ESP32/Arduino):** 5V/2A via primary USB-C Hub.
2. **Actuator Power (L298N):** 12V/5A dedicated power brick.
3. **Common Ground:** All modules must share a single GND reference point.

---

## 🔄 System Flow Summary
1.  **Input:** Android App or Hardware Buttons send command to **ESP32 Hub**.
2.  **Execution:** **ESP32** triggers GPIOs 12-27 for instant mechanical movement.
3.  **Observation:** **Arduino** collects vitals (HR/Temp) every 500ms.
4.  **Feedback:** **Arduino** sends byte-stream via Serial P11 to **ESP32**, which relays it to the **Android Dashboard** via WebSocket.
