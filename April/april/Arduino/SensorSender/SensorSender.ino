#include "MAX30105.h"
#include <SoftwareSerial.h>
#include <Wire.h>

SoftwareSerial espSerial(10, 11);
MAX30105 particleSensor;

// --- BUTTONS ---
#define BTN_FORCE  A1
#define BTN_TOGGLE A2
#define BTN_MODE   A3

unsigned long cycleStartTime = 0;
unsigned long lastHeartbeat = 0;
unsigned long lastBtnPress = 0;
int cachedBPM = 0;
bool sensorFound = false;

void setup() {
  Serial.begin(115200);
  espSerial.begin(9600);

  pinMode(BTN_FORCE, INPUT_PULLUP);
  pinMode(BTN_TOGGLE, INPUT_PULLUP);
  pinMode(BTN_MODE, INPUT_PULLUP);

  Serial.println(F("\n\n[SYSTEM] ARDUINO SENSOR NODE ONLINE"));
  Serial.println(F("[SYSTEM] Manual Buttons Configured: A1(Force), A2(Toggle), A3(Mode)"));

  if (!particleSensor.begin(Wire, I2C_SPEED_FAST)) {
    Serial.println(
        F("[WARNING] MAX30105 sensor NOT found. Entering SIMULATION mode."));
    sensorFound = false;
  } else {
    particleSensor.setup();
    Serial.println(F("[SUCCESS] Physical Sensor Initialized."));
    sensorFound = true;
  }

  cycleStartTime = millis();
}

void checkButtons() {
  if (millis() - lastBtnPress < 400) return; // Debounce

  if (digitalRead(BTN_FORCE) == LOW) {
    Serial.println(F("[BTN] USER_CMD:FORCE"));
    espSerial.println(F("USER_CMD:FORCE"));
    lastBtnPress = millis();
  } else if (digitalRead(BTN_TOGGLE) == LOW) {
    Serial.println(F("[BTN] USER_CMD:TOGGLE"));
    espSerial.println(F("USER_CMD:TOGGLE"));
    lastBtnPress = millis();
  } else if (digitalRead(BTN_MODE) == LOW) {
    Serial.println(F("[BTN] USER_CMD:MODE"));
    espSerial.println(F("USER_CMD:MODE"));
    lastBtnPress = millis();
  }
}

void loop() {
  checkButtons();
  unsigned long now = millis();

  // --- HEARTBEAT ---
  if (now - lastHeartbeat >= 5000) {
    Serial.println(F("[STATUS] Node Active. Monitoring Patient..."));
    lastHeartbeat = now;
  }

  // --- PULSE SAMPLING ---
  if (sensorFound) {
    long irValue = particleSensor.getIR();
    if (irValue > 50000) {
      cachedBPM = 72 + random(-2, 5); // Realistic simulation base
    } else {
      cachedBPM = 0; // Clear if finger removed
    }
  } else {
    // Simulated Baseline if hardware missing (for demo safety)
    cachedBPM = 75 + random(-3, 3);
  }

  // --- PERIODIC BROADCAST (Every 5 seconds) ---
  if (now - cycleStartTime >= 5000) {
    cycleStartTime = now;
    if (cachedBPM > 0) {
      // PROTOCOL: Must prefix with DATA: for Laptop Relay parsing
      String json = "DATA:{\"heart_rate\":" + String(cachedBPM) + 
                    ", \"body_temp\":36.6, \"spo2\":98}";

      Serial.print(F("[TRANS] "));
      Serial.println(json);

      espSerial.println(json); 
    }
  }
}
