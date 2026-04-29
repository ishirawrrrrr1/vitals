// ESP32 full main code:
// - 3 relay outputs for glove/buttons
// - 2 L298N boards for arms and legs without ENA/ENB pins
// - MAX30102 heartbeat sensor
// - DS18B20 temperature sensor
//
// Serial Monitor:
//   Baud: 115200
//   Line ending: New Line

#include <Wire.h>
#include <MAX30105.h>
#include "heartRate.h"
#include <OneWire.h>
#include <DallasTemperature.h>

// Relay module pins.
#define RELAY_LEVEL 25
#define RELAY_POWER 26
#define RELAY_RESET 27

#define RELAY_ON LOW
#define RELAY_OFF HIGH

// MAX30102 heartbeat sensor pins.
#define MAX_SDA 32
#define MAX_SCL 33

// DS18B20 temperature sensor pin.
#define TEMP_PIN 14

// ARMS L298N pins.
// ENA and ENB are not used here because the jumper caps stay installed.
#define ARM_IN1 18
#define ARM_IN2 19
#define ARM_IN3 21
#define ARM_IN4 22

// LEGS L298N pins.
// For one leg actuator, use LEG_IN1/LEG_IN2 with OUT1/OUT2 first.
#define LEG_IN1 16
#define LEG_IN2 17
#define LEG_IN3 23
#define LEG_IN4 5

MAX30105 particleSensor;
OneWire oneWire(TEMP_PIN);
DallasTemperature tempSensor(&oneWire);

const byte RATE_SIZE = 4;
byte rates[RATE_SIZE];
byte rateSpot = 0;

long lastBeat = 0;
int bpm = 0;
int bpmAverage = 0;
float bodyTemp = 0.0;
unsigned long lastVitalsPrint = 0;
unsigned long lastTempRead = 0;
bool maxReady = false;
bool tempReady = false;

void setup() {
  Serial.begin(115200);
  delay(1000);

  pinMode(RELAY_LEVEL, OUTPUT);
  pinMode(RELAY_POWER, OUTPUT);
  pinMode(RELAY_RESET, OUTPUT);

  pinMode(ARM_IN1, OUTPUT);
  pinMode(ARM_IN2, OUTPUT);
  pinMode(ARM_IN3, OUTPUT);
  pinMode(ARM_IN4, OUTPUT);

  pinMode(LEG_IN1, OUTPUT);
  pinMode(LEG_IN2, OUTPUT);
  pinMode(LEG_IN3, OUTPUT);
  pinMode(LEG_IN4, OUTPUT);

  allRelaysOff();
  stopAllMotors();

  Wire.begin(MAX_SDA, MAX_SCL);
  if (!particleSensor.begin(Wire, I2C_SPEED_FAST)) {
    Serial.println("MAX30102 NOT FOUND");
    Serial.println("Check wiring: SDA=32 SCL=33 VCC=3.3V GND=GND");
  } else {
    particleSensor.setup(0x3F, 4, 2, 400, 411, 16384);
    particleSensor.setPulseAmplitudeRed(0x3F);
    particleSensor.setPulseAmplitudeIR(0x3F);
    particleSensor.setPulseAmplitudeGreen(0);
    maxReady = true;
    Serial.println("MAX30102 READY");
  }

  tempSensor.begin();
  if (tempSensor.getDeviceCount() > 0) {
    tempReady = true;
    Serial.print("DS18B20 READY, DEVICES FOUND: ");
    Serial.println(tempSensor.getDeviceCount());
  } else {
    Serial.println("DS18B20 NOT FOUND");
    Serial.println("Check wiring: RED=3.3V BLACK=GND YELLOW=GPIO14, 4.7k YELLOW to 3.3V");
  }

  Serial.println();
  Serial.println("ESP32 FULL MAIN CODE READY");
  Serial.println("Relay commands: LEVEL POWER RESET");
  Serial.println("Arms commands: ARMEXT ARMRET ARMAEXT ARMARET ARMBEXT ARMBRET");
  Serial.println("Legs commands: LEGEXT LEGRET LEG2EXT LEG2RET");
  Serial.println("Old shortcuts still work: AEXT ARET BEXT BRET EXT RET STOP");
  Serial.println("Vitals print as: IR BPM AVG TEMP");
}

void loop() {
  checkSerialCommand();
  readHeartbeat();
  readTemperature();
  printVitals();
  delay(5);
}

void checkSerialCommand() {
  if (!Serial.available()) return;

  String cmd = Serial.readStringUntil('\n');
  cmd.trim();
  cmd.toUpperCase();

  if (cmd.length() == 0) return;

  if (cmd == "LEVEL") {
    pulseRelay(RELAY_LEVEL, "LEVEL");
  } else if (cmd == "POWER") {
    pulseRelay(RELAY_POWER, "POWER");
  } else if (cmd == "RESET") {
    pulseRelay(RELAY_RESET, "RESET");

  } else if (cmd == "AEXT" || cmd == "ARMAEXT") {
    motorExtend(ARM_IN1, ARM_IN2);
    Serial.println("ARM A EXT");
  } else if (cmd == "ARET" || cmd == "ARMARET") {
    motorRetract(ARM_IN1, ARM_IN2);
    Serial.println("ARM A RET");
  } else if (cmd == "BEXT" || cmd == "EXT" || cmd == "ARMEXT" || cmd == "ARMBEXT") {
    motorRetract(ARM_IN3, ARM_IN4);
    Serial.println("ARM B EXT");
  } else if (cmd == "BRET" || cmd == "RET" || cmd == "ARMRET" || cmd == "ARMBRET") {
    motorExtend(ARM_IN3, ARM_IN4);
    Serial.println("ARM B RET");
  } else if (cmd == "LEGEXT") {
    motorExtend(LEG_IN1, LEG_IN2);
    Serial.println("LEG EXT");
  } else if (cmd == "LEGRET") {
    motorRetract(LEG_IN1, LEG_IN2);
    Serial.println("LEG RET");
  } else if (cmd == "LEG2EXT") {
    motorExtend(LEG_IN3, LEG_IN4);
    Serial.println("LEG 2 EXT");
  } else if (cmd == "LEG2RET") {
    motorRetract(LEG_IN3, LEG_IN4);
    Serial.println("LEG 2 RET");
  } else if (cmd == "STOP" || cmd == "ALLSTOP") {
    stopAllMotors();
    Serial.println("STOP");

  } else {
    Serial.print("UNKNOWN COMMAND: ");
    Serial.println(cmd);
    Serial.println("Use: LEVEL POWER RESET ARMEXT ARMRET LEGEXT LEGRET STOP");
  }
}

void readHeartbeat() {
  if (!maxReady) return;

  long irValue = particleSensor.getIR();

  if (irValue < 30000) {
    bpm = 0;
    bpmAverage = 0;
    rateSpot = 0;
    return;
  }

  if (checkForBeat(irValue)) {
    long delta = millis() - lastBeat;
    lastBeat = millis();

    float currentBPM = 60.0 / (delta / 1000.0);

    if (currentBPM >= 40 && currentBPM <= 180) {
      bpm = (int)currentBPM;

      rates[rateSpot++] = (byte)bpm;
      rateSpot %= RATE_SIZE;

      int total = 0;
      for (byte i = 0; i < RATE_SIZE; i++) {
        total += rates[i];
      }
      bpmAverage = total / RATE_SIZE;
    }
  }
}

void readTemperature() {
  if (!tempReady) return;
  if (millis() - lastTempRead < 1000) return;

  tempSensor.requestTemperatures();
  float tempC = tempSensor.getTempCByIndex(0);

  if (tempC == DEVICE_DISCONNECTED_C || tempC == 85.0 || tempC < -20 || tempC > 80) {
    bodyTemp = 0.0;
  } else {
    bodyTemp = tempC;
  }

  lastTempRead = millis();
}

void printVitals() {
  if (millis() - lastVitalsPrint < 500) return;

  long irValue = 0;
  if (maxReady) {
    irValue = particleSensor.getIR();
  }

  Serial.print("IR=");
  Serial.print(irValue);
  Serial.print(" | BPM=");
  Serial.print(bpm);
  Serial.print(" | AVG=");
  Serial.print(bpmAverage);
  Serial.print(" | TEMP=");
  if (tempReady && bodyTemp > 0.0) {
    Serial.print(bodyTemp, 1);
    Serial.println(" C");
  } else {
    Serial.println("N/A");
  }

  lastVitalsPrint = millis();
}

void pulseRelay(int pin, const char* name) {
  Serial.print(name);
  Serial.println(" ON");

  digitalWrite(pin, RELAY_ON);
  delay(500);
  digitalWrite(pin, RELAY_OFF);

  Serial.print(name);
  Serial.println(" OFF");
}

void allRelaysOff() {
  digitalWrite(RELAY_LEVEL, RELAY_OFF);
  digitalWrite(RELAY_POWER, RELAY_OFF);
  digitalWrite(RELAY_RESET, RELAY_OFF);
}

void motorExtend(int a, int b) {
  digitalWrite(a, HIGH);
  digitalWrite(b, LOW);
}

void motorRetract(int a, int b) {
  digitalWrite(a, LOW);
  digitalWrite(b, HIGH);
}

void motorStop(int a, int b) {
  digitalWrite(a, LOW);
  digitalWrite(b, LOW);
}

void stopAllMotors() {
  motorStop(ARM_IN1, ARM_IN2);
  motorStop(ARM_IN3, ARM_IN4);
  motorStop(LEG_IN1, LEG_IN2);
  motorStop(LEG_IN3, LEG_IN4);
}
