#include <Wire.h>
#include <MAX30105.h>
#include "heartRate.h"
#include <OneWire.h>
#include <DallasTemperature.h>

// MAX30102 heartbeat sensor pins.
#define MAX_SDA 32
#define MAX_SCL 33

// DS18B20 temperature sensor pin.
#define TEMP_PIN 4

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
bool tempOk = false;

unsigned long lastPrint = 0;
unsigned long lastTempRead = 0;

void setup() {
  Serial.begin(115200);
  delay(1000);

  Serial.println("ESP32 HEARTBEAT + TEMP TEST");
  Serial.println("MAX30102: SDA=32 SCL=33 VCC=3.3V GND=GND");
  Serial.println("DS18B20: DATA=GPIO4 VCC=3.3V GND=GND");

  Wire.begin(MAX_SDA, MAX_SCL);

  if (!particleSensor.begin(Wire, I2C_SPEED_FAST)) {
    Serial.println("MAX30102 NOT FOUND");
    Serial.println("Check MAX30102 wiring.");
  } else {
    // ledBrightness, sampleAverage, ledMode, sampleRate, pulseWidth, adcRange
    particleSensor.setup(0x3F, 4, 2, 400, 411, 16384);
    particleSensor.setPulseAmplitudeRed(0x3F);
    particleSensor.setPulseAmplitudeIR(0x3F);
    particleSensor.setPulseAmplitudeGreen(0);
    Serial.println("MAX30102 READY");
  }

  tempSensor.begin();
  Serial.print("DS18B20 DEVICES FOUND: ");
  Serial.println(tempSensor.getDeviceCount());
  Serial.println("Put fingertip on MAX30102 and keep still.");
}

void loop() {
  readHeartbeat();
  readTemperature();
  printVitals();
  delay(5);
}

void readHeartbeat() {
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
  if (millis() - lastTempRead < 1000) return;

  tempSensor.requestTemperatures();
  float tempC = tempSensor.getTempCByIndex(0);

  if (tempC == DEVICE_DISCONNECTED_C) {
    bodyTemp = 0.0;
    tempOk = false;
  } else if (tempC == 85.0) {
    bodyTemp = tempC;
    tempOk = false;
  } else if (tempC < -20 || tempC > 80) {
    bodyTemp = tempC;
    tempOk = false;
  } else {
    bodyTemp = tempC;
    tempOk = true;
  }

  lastTempRead = millis();
}

void printVitals() {
  if (millis() - lastPrint < 500) return;

  long irValue = particleSensor.getIR();

  Serial.print("IR=");
  Serial.print(irValue);
  Serial.print(" | BPM=");
  Serial.print(bpm);
  Serial.print(" | AVG=");
  Serial.print(bpmAverage);
  Serial.print(" | TEMP=");
  if (tempOk) {
    Serial.print(bodyTemp, 1);
    Serial.println(" C");
  } else {
    Serial.print("ERROR(");
    Serial.print(bodyTemp, 1);
    Serial.println(")");
  }

  lastPrint = millis();
}
