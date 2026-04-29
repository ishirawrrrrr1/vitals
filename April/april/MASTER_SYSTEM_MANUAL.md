# 🚑 MASTER OPERATION & RECOVERY MANUAL: VITALS Health System
**Version:** Vital Breakthrough (v6.8)
**Hardware Environment:** ESP32 Hub + Arduino Sensor Node + Android App

This manual is the definitive guide for operating, maintaining, and fixing the VITALS Health System. It consolidates all legacy documentation into a single actionable resource.

> [!IMPORTANT]
> **CRITICAL RE-AUTHENTICATION REQUIRED:**
> If the backend connection fails after a reset: **LOGOUT from the Android App AND Restart the Backend Server**. The security tokens must be re-synchronized to ensure data integrity.

---

## 🏗️ 1. System Architecture: The Triple-Link
The system operates as a distributed network of three primary nodes:

1.  **Sensor Node (Arduino Uno):** Collects precision data (Heart Rate, Temp, SpO2) and handles manual physical buttons.
2.  **Actuator Hub (ESP32):** The "Traffic Controller." It manages WiFi/WebSockets, processes commands from the App, and drives the high-power linear actuators (Motors/Glove).
3.  **Command Center (Android App):** The user interface used to initialize sessions, monitor live vitals, and adjust vital intensity.
4.  **Data Relay (Laptop Backend):** An optional Node.js server that archives all session data into persistent CSV files.

---

## 🚀 2. Quick Start Sequence
Follow this exact order to ensure successful connectivity:

1.  **Power Hardware:** Plug in the 12V Actuator power first, then the 5V USB-C control power.
2.  **Join Network:** On the Android tablet, connect to the WiFi network: `Vitals-Hub-Direct`.
3.  **Launch App:** Open the "Health Application" on Android. The status dot should turn **GREEN** (HUB: ONLINE).
4.  **Start Relay (Optional):** Run `run_backend.bat` in the `backend` folder to enable database logging.

---

## 🛠️ 3. Hardware Maintenance (v6.7 Wiring)
Refer to `HARDWARE_MASTER_WIRE_V6.7.md` for full diagrams. Key pins:
- **Arduino Buttons:** A1 (Force), A2 (Toggle), A3 (Mode).
- **ESP32 Actuators:** Pins 12, 13 (Arm) | Pins 14, 27 (Leg) | Pin 26 (Glove).
- **Communication:** SoftwareSerial (10, 11) from Arduino to ESP32 (16, 17).

---

## 📊 4. Data Persistence & Session Rules
The system maintains a **Unified Health History** for the patient "Precilla". To ensure a clear recovery trajectory, the following rules apply:

1.  **Daily Overlap Policy:** Only **one definitive session** is archived per calendar day. 
    - If a session is retaken on the same day, the new results (Vitals + Recovery Score) will **OVERLAP** and replace the previous record.
    - This ensures the Progress tab always reflects the most recent and complete recovery attempt for that date.
2.  **Identity Enforcement:** All sessions are automatically attributed to "Precilla" to maintain data integrity for the panel demonstration.
3.  **Future-Dating:** For demonstration purposes, the system supports future-dated records (up to April 28, 2026) which can be managed via the Progress module.

## 💻 5. Backend & Data Management
The backend stores health records in `backend/migrations/DATA/`.
- **Log Format:** Files are named by patient (e.g., `Precila.csv`).
- **Admin Dashboard:** Access `http://localhost:3000/dashboard.html` for user management.
- **Credentials:** Default admin is `admin` / `admin123`.

---

## ⚠️ 5. Troubleshooting (The "Fix-It" Guide)

| Issue | Root Cause | Solution |
| :--- | :--- | :--- |
| **HUB: DISCONNECTED** | App not on Hub WiFi | Reconnect to `Vitals-Hub-Direct`. |
| **NO VITALS (0 BPM)** | Sensor not detected | Check MAX30102 wiring (SDA/SCL) on Arduino. |
| **ACTUATORS STUCK** | Brown-out / No 12V | Ensure the 12V power brick is connected to L298N. |
| **APP CRASHING** | Resource leak | Restart the App. The system is designed to recover status. |
| **SYNC FAILURE** | Serial Link Down | Check the Logic Level Shifter wiring between Arduino and ESP32. |

---

## 📜 6. Legacy Cleanup Note
All previous `.md` files in the `backend` directory have been purged as of April 21, 2026, to prevent version confusion. This document serves as the sole manual for the VITALS v6.8 architecture.
