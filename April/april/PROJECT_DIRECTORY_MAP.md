# 🗺️ PROJECT DIRECTORY MAP: Clinical Vitals Hub (v6.8)
**Date:** April 20, 2026
**Standard:** Consolidated Breakthrough Architecture

Use this map to navigate the project structure and locate clinical data, firmware, and mobile applications.

---

## 📂 1. Project Root (`/`)
*The central command and documentation center.*

- **[MASTER_SYSTEM_MANUAL.md](file:///c:/xampp/htdocs/projects%20systems/April/april/MASTER_SYSTEM_MANUAL.md)**: Main operational & troubleshooting guide.
- **[HARDWARE_MASTER_WIRE_V6.7.md](file:///c:/xampp/htdocs/projects%20systems/April/april/HARDWARE_MASTER_WIRE_V6.7.md)**: The absolute wiring and pin mapping standard.
- **[THE_BIG_BANG.txt](file:///c:/xampp/htdocs/projects%20systems/April/april/THE_BIG_BANG.txt)**: Commemorative record of the project start date (March 23, 2026).
- **📁 [SHORTCUT_TO_THE_APK_HERE](file:///c:/xampp/htdocs/projects%20systems/April/april/SHORTCUT_TO_THE_APK_HERE/)**: Instant access to the stable v6.8 Android build.
---

## 📱 2. Android Module (`/AndroidStudioProjects/`)
*The clinical monitoring application source and outputs.*

- **Main Code**: `/app/src/main/java/com/example/myapplication/`
    - `monitoring.java`: Real-time sensor processing & actuator control.
    - `progress.java`: Clinical history, data seeding, and CSV import/export.
- **UI Layouts**: `/app/src/main/res/layout/`
- **Build Output (APK)**: `/app/build/outputs/apk/debug/app-debug.apk`

---

## 🔌 3. Firmware Module (`/Arduino/`)
*The source code for the physical hardware nodes.*

- **[ESP32 Hub]**: `/esp32_vitals/esp32_vitals.ino`
    - Handles WiFi, WebSockets, and high-power Actuators.
- **[Arduino Sensor Node]**: `/SensorSender/SensorSender.ino`
    - Handles Biometric sensors and Manual Hardware Buttons.

---

## 💻 4. Backend Module (`/backend/`)
*The data relay and administrator dashboard.*

- **Server Core**: `index.js` (Express-based WebSocket relay).
- **Control Scripts**:
    - `run_backend.bat / .ps1`: Starts the WebSocket server and stabilizes the data bridge.
    - `stop_backend.bat / .ps1`: Safely terminates the background Node.js process and frees port 3000.
    - `open_firewall.bat / .ps1`: Configures Windows Firewall to allow Hub & App traffic to reach the server.
- **Dashboards**: `/public/`
    - `monitoring.html`: PC-based vital monitoring dashboard.
    - `dashboard.html`: Administrator user & system management panel.

---

## 📊 5. Clinical Data & Backups (`/backend/migrations/DATA/`)
*Where patient records are archived as system-generated CSVs.*

- **Primary Patient**: **`Precilla.csv`** (Identity synced to Android app).
- **Archives**: Logs for all 20+ patients including Ronaldo, Rosalla, etc.

---

## 🧪 6. Scratch & Support (`/scratch/`)
*Temporary scripts and recovery tools.*

- `move_docs.ps1`: Automated documentation consolidation script.
