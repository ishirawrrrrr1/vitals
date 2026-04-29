# 📚 Required Arduino & ESP Libraries

To successfully compile and flash the hardware for the **Clinical Vitals System**, you must install the following libraries via the Arduino IDE Library Manager (**Tools > Manage Libraries**) or manually.

---

## 1. Sensor Node (Arduino Uno/Mega/Nano)
These libraries are required for the `SensorSender.ino` sketch which handles physical data collection.

| Library Name | Author | Purpose |
|--------------|--------|---------|
| **SparkFun MAX3010x Pulse and Proximity Sensor** | SparkFun | Driver for the MAX30105 Heart Rate & SpO2 sensor. |
| **SoftwareSerial** | Built-in | Used for communication with the ESP32 Hub. |
| **Wire** | Built-in | I2C communication for the sensor. |

---

## 2. Hub Portal (ESP32)
These libraries are required for the `esp32_vitals.ino` sketch which handles the WiFi bridge, WebSocket server, and Actuator control.

| Library Name | Author | Purpose |
|--------------|--------|---------|
| **WebSockets** | Markus Sattler | Provides the `WebSocketsServer` and `SocketIOclient` functionality. |
| **WiFi** | Built-in (ESP32) | Core WiFi connectivity. |
| **WebServer** | Built-in (ESP32) | Local setup portal for WiFi configuration. |
| **DNSServer** | Built-in (ESP32) | Captive portal redirection. |
| **Preferences** | Built-in (ESP32) | Non-volatile storage for WiFi credentials. |
| **WiFiUdp** | Built-in (ESP32) | Identity broadcasting for Android auto-discovery. |

---

## ⚙️ Installation Instructions

1.  **Open Arduino IDE**.
2.  Go to **Sketch > Include Library > Manage Libraries...**
3.  Search for **"MAX3010x"** and install the version by **SparkFun**.
4.  Search for **"WebSockets"** and install the version by **Markus Sattler**.
5.  **For ESP32 Boards**: Ensure you have the ESP32 Board URL added in **File > Preferences** and the ESP32 package installed in **Tools > Board > Boards Manager**.
    *   *URL*: `https://raw.githubusercontent.com/espressif/arduino-esp32/gh-pages/package_esp32_index.json`

---

## 🔌 Hardware Wiring Reference (TX/RX)
*   **Arduino TX (Pin 10)** -> **ESP32 RX2 (Pin 16)**
*   **Arduino RX (Pin 11)** -> **ESP32 TX2 (Pin 17)**
*   *Note: Use a Logic Level Shifter if using 5V Arduino with 3.3V ESP32.*
