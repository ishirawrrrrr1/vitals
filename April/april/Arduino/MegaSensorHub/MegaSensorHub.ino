#include <MAX30105.h>
#include "heartRate.h"
#include <Wire.h>
#include <OneWire.h>
#include <DallasTemperature.h>
#include <SoftwareSerial.h>

// Arduino Uno sensor + motor hub.
// Relay module is no longer controlled by the Uno.
// ESP32 handles the relay buttons.
//
// Uno to ESP32:
//   Uno D5 TX -> ESP32 RX2 GPIO16 through a 5V-to-3.3V divider
//   Uno D4 RX <- ESP32 TX2 GPIO17
//   Uno GND   -> ESP32 GND
#define ESP32_RX 4
#define ESP32_TX 5
SoftwareSerial esp32Link(ESP32_RX, ESP32_TX);

// MAX30102 heart sensor uses Uno I2C pins: SDA=A4, SCL=A5.
MAX30105 particleSensor;

// DS18B20 temperature sensor.
#define TEMP_PIN 2
OneWire oneWire(TEMP_PIN);
DallasTemperature temperatureSensor(&oneWire);

// Buttons.
#define BTN_FORCE  A1
#define BTN_TOGGLE A2
#define BTN_MODE   A3

// Arm motor driver.
#define ARM_IN1  6
#define ARM_IN2  7
#define ARM_EN   9

// Leg motor driver.
#define LEG_IN1  8
#define LEG_IN2  12
#define LEG_EN   10

// Status LEDs.
#define LED_ONLINE 13
#define LED_ERROR  A0

unsigned long lastSensorRead = 0;
unsigned long lastHeartbeat = 0;
unsigned long lastButtonPress = 0;
unsigned long lastMotionChange = 0;

int cachedBPM = 0;
float cachedSpO2 = 0.0;
float cachedTemp = 0.0;
bool sensorInitialized = false;
long lastBeat = 0;

bool armMoving = false;
bool legMoving = false;
bool sessionActive = false;
bool motionExtending = true;
int currentForceLevel = 1;
int currentMode = 0;

const unsigned long MOTION_RUN_MS = 2500;
const unsigned long MOTION_PAUSE_MS = 500;
bool motionPaused = false;

void setup() {
  Serial.begin(115200);
  esp32Link.begin(9600);
  delay(500);

  Serial.println("UNO_SENSOR_MOTOR_HUB_STARTING");

  pinMode(BTN_FORCE, INPUT_PULLUP);
  pinMode(BTN_TOGGLE, INPUT_PULLUP);
  pinMode(BTN_MODE, INPUT_PULLUP);

  pinMode(ARM_IN1, OUTPUT);
  pinMode(ARM_IN2, OUTPUT);
  pinMode(ARM_EN, OUTPUT);
  pinMode(LEG_IN1, OUTPUT);
  pinMode(LEG_IN2, OUTPUT);
  pinMode(LEG_EN, OUTPUT);

  pinMode(LED_ONLINE, OUTPUT);
  pinMode(LED_ERROR, OUTPUT);

  stopAllMotors();

  Wire.begin();
  if (!particleSensor.begin(Wire, I2C_SPEED_FAST)) {
    Serial.println("MAX30102_NOT_DETECTED");
    digitalWrite(LED_ERROR, HIGH);
  } else {
    particleSensor.setup();
    particleSensor.setPulseAmplitudeRed(0x0A);
    particleSensor.setPulseAmplitudeGreen(0);
    sensorInitialized = true;
    Serial.println("MAX30102_READY");
  }

  temperatureSensor.begin();
  digitalWrite(LED_ONLINE, HIGH);

  Serial.println("ARDUINO_READY");
  esp32Link.println("ARDUINO_READY");
}

void loop() {
  unsigned long now = millis();

  checkButtons();
  checkSerialCommands();
  updateMotionCycle(now);

  if (now - lastSensorRead >= 500) {
    readAllSensors();
    lastSensorRead = now;
  }

  if (now - lastHeartbeat >= 2000) {
    broadcastVitals();
    lastHeartbeat = now;
  }
}

void checkButtons() {
  unsigned long now = millis();
  if (now - lastButtonPress < 400) return;

  if (digitalRead(BTN_FORCE) == LOW) {
    currentForceLevel = (currentForceLevel % 3) + 1;
    if (armMoving || legMoving) applyCurrentMotion();
    lastButtonPress = now;
  } else if (digitalRead(BTN_TOGGLE) == LOW) {
    if (sessionActive) {
      stopAllMotors();
    } else {
      startMotionCycle();
    }
    lastButtonPress = now;
  } else if (digitalRead(BTN_MODE) == LOW) {
    currentMode = (currentMode + 1) % 3;
    if (armMoving || legMoving) applyCurrentMotion();
    lastButtonPress = now;
  }
}

void checkSerialCommands() {
  if (esp32Link.available() > 0) {
    String cmd = esp32Link.readStringUntil('\n');
    handleCommand(cmd);
  }

  if (Serial.available() > 0) {
    String cmd = Serial.readStringUntil('\n');
    handleCommand(cmd);
  }
}

void handleCommand(String cmd) {
  cmd.trim();
  if (cmd.length() == 0) return;

  Serial.print("CMD:");
  Serial.println(cmd);

  if (cmd.startsWith("CMD:START") || cmd.startsWith("CMD:ALL:ON")) {
    startMotionCycle();
  } else if (cmd.startsWith("CMD:SET_FORCE:")) {
    currentForceLevel = cmd.substring(14).toInt();
    if (currentForceLevel < 1) currentForceLevel = 1;
    if (currentForceLevel > 3) currentForceLevel = 3;
    if (armMoving || legMoving) applyCurrentMotion();
  } else if (cmd.startsWith("CMD:SET_MODE:")) {
    currentMode = cmd.substring(13).toInt();
    if (currentMode < 0 || currentMode > 2) currentMode = 0;
    if (armMoving || legMoving) applyCurrentMotion();
  } else if (cmd.startsWith("CMD:ARM:EXT") || cmd.startsWith("CMD:HAND:EXT")) {
    sessionActive = false;
    motorExtend(ARM_IN1, ARM_IN2, ARM_EN, 120);
    armMoving = true;
  } else if (cmd.startsWith("CMD:ARM:RET") || cmd.startsWith("CMD:HAND:RET")) {
    sessionActive = false;
    motorRetract(ARM_IN1, ARM_IN2, ARM_EN, 120);
    armMoving = true;
  } else if (cmd.startsWith("CMD:ARM:STOP") || cmd.startsWith("CMD:HAND:STP")) {
    motorStop(ARM_IN1, ARM_IN2, ARM_EN);
    armMoving = false;
  } else if (cmd.startsWith("CMD:LEG:EXT")) {
    sessionActive = false;
    motorExtend(LEG_IN1, LEG_IN2, LEG_EN, 120);
    legMoving = true;
  } else if (cmd.startsWith("CMD:LEG:RET")) {
    sessionActive = false;
    motorRetract(LEG_IN1, LEG_IN2, LEG_EN, 120);
    legMoving = true;
  } else if (cmd.startsWith("CMD:LEG:STOP") || cmd.startsWith("CMD:LEG:STP")) {
    motorStop(LEG_IN1, LEG_IN2, LEG_EN);
    legMoving = false;
  } else if (cmd.startsWith("CMD:STOP") || cmd.startsWith("CMD:RESET")) {
    stopAllMotors();
  }
}

void readAllSensors() {
  if (sensorInitialized) {
    long irValue = particleSensor.getIR();

    if (irValue > 50000) {
      if (checkForBeat(irValue)) {
        long delta = millis() - lastBeat;
        lastBeat = millis();

        float bpm = 60 / (delta / 1000.0);
        if (bpm >= 40 && bpm <= 180) {
          cachedBPM = (int)bpm;
        }
      }

      cachedSpO2 = 0.0;
    } else {
      cachedBPM = 0;
      cachedSpO2 = 0.0;
      lastBeat = 0;
    }
  } else {
    cachedBPM = 0;
    cachedSpO2 = 0.0;
  }

  temperatureSensor.requestTemperatures();
  cachedTemp = temperatureSensor.getTempCByIndex(0);

  if (cachedTemp < 30.0 || cachedTemp > 43.0) {
    cachedTemp = 0.0;
  }
}

void broadcastVitals() {
  String json = "DATA:{\"heart_rate\":" + String(cachedBPM) +
                ",\"spo2\":" + String((int)cachedSpO2) +
                ",\"body_temp\":" + String(cachedTemp, 1) +
                ",\"arm_moving\":" + (armMoving ? "true" : "false") +
                ",\"leg_moving\":" + (legMoving ? "true" : "false") +
                "}";

  Serial.println(json);
  esp32Link.println(json);
}

int speedForForceLevel(int forceLevel) {
  if (forceLevel <= 1) return 100;
  if (forceLevel == 2) return 150;
  return 220;
}

void applyCurrentMotion() {
  int speed = speedForForceLevel(currentForceLevel);

  if (motionExtending) {
    applyMotionDirection(speed, true);
  } else {
    applyMotionDirection(speed, false);
  }
}

void applyMotionDirection(int speed, bool extending) {
  if (currentMode == 1) {
    if (extending) {
      motorExtend(ARM_IN1, ARM_IN2, ARM_EN, speed);
    } else {
      motorRetract(ARM_IN1, ARM_IN2, ARM_EN, speed);
    }
    motorStop(LEG_IN1, LEG_IN2, LEG_EN);
    armMoving = true;
    legMoving = false;
  } else if (currentMode == 2) {
    motorStop(ARM_IN1, ARM_IN2, ARM_EN);
    if (extending) {
      motorExtend(LEG_IN1, LEG_IN2, LEG_EN, speed);
    } else {
      motorRetract(LEG_IN1, LEG_IN2, LEG_EN, speed);
    }
    armMoving = false;
    legMoving = true;
  } else {
    if (extending) {
      motorExtend(ARM_IN1, ARM_IN2, ARM_EN, speed);
      motorExtend(LEG_IN1, LEG_IN2, LEG_EN, speed);
    } else {
      motorRetract(ARM_IN1, ARM_IN2, ARM_EN, speed);
      motorRetract(LEG_IN1, LEG_IN2, LEG_EN, speed);
    }
    armMoving = true;
    legMoving = true;
  }
}

void startMotionCycle() {
  sessionActive = true;
  motionExtending = true;
  motionPaused = false;
  lastMotionChange = millis();
  applyCurrentMotion();
}

void updateMotionCycle(unsigned long now) {
  if (!sessionActive) return;

  if (!motionPaused && now - lastMotionChange >= MOTION_RUN_MS) {
    motorStop(ARM_IN1, ARM_IN2, ARM_EN);
    motorStop(LEG_IN1, LEG_IN2, LEG_EN);
    motionPaused = true;
    lastMotionChange = now;
    return;
  }

  if (motionPaused && now - lastMotionChange >= MOTION_PAUSE_MS) {
    motionExtending = !motionExtending;
    motionPaused = false;
    lastMotionChange = now;
    applyCurrentMotion();
  }
}

void motorExtend(int pin1, int pin2, int enablePin, int speed) {
  digitalWrite(pin1, HIGH);
  digitalWrite(pin2, LOW);
  analogWrite(enablePin, speed);
}

void motorRetract(int pin1, int pin2, int enablePin, int speed) {
  digitalWrite(pin1, LOW);
  digitalWrite(pin2, HIGH);
  analogWrite(enablePin, speed);
}

void motorStop(int pin1, int pin2, int enablePin) {
  digitalWrite(pin1, LOW);
  digitalWrite(pin2, LOW);
  analogWrite(enablePin, 0);
}

void stopAllMotors() {
  motorStop(ARM_IN1, ARM_IN2, ARM_EN);
  motorStop(LEG_IN1, LEG_IN2, LEG_EN);
  armMoving = false;
  legMoving = false;
  sessionActive = false;
  motionPaused = false;
}
