#include <Wire.h>
#include <MAX30105.h>
#include "heartRate.h"

#define MAX_SDA 32
#define MAX_SCL 33

MAX30105 particleSensor;

const byte RATE_SIZE = 4;
byte rates[RATE_SIZE];
byte rateSpot = 0;

long lastBeat = 0;
int bpm = 0;
int bpmAverage = 0;
unsigned long lastPrint = 0;

void setup() {
  Serial.begin(115200);
  delay(1000);

  Serial.println("MAX30102 BPM TEST");
  Serial.println("Wiring: SDA=32 SCL=33 VCC=3.3V GND=GND");

  Wire.begin(MAX_SDA, MAX_SCL);

  if (!particleSensor.begin(Wire, I2C_SPEED_FAST)) {
    Serial.println("MAX30102 NOT FOUND");
    while (1) {
      delay(1000);
    }
  }

  // ledBrightness, sampleAverage, ledMode, sampleRate, pulseWidth, adcRange
  // Higher sample rate + stronger IR helps when the sensor is on fabric/wrist.
  particleSensor.setup(0x3F, 4, 2, 400, 411, 16384);
  particleSensor.setPulseAmplitudeRed(0x3F);
  particleSensor.setPulseAmplitudeIR(0x3F);
  particleSensor.setPulseAmplitudeGreen(0);

  Serial.println("MAX30102 READY");
  Serial.println("Put fingertip on sensor and keep still.");
}

void loop() {
  long irValue = particleSensor.getIR();

  if (irValue < 30000) {
    bpm = 0;
    bpmAverage = 0;
    rateSpot = 0;

    if (millis() - lastPrint >= 500) {
      Serial.print("IR=");
      Serial.print(irValue);
      Serial.println(" | No finger | BPM=0");
      lastPrint = millis();
    }
    delay(5);
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

      Serial.print("BEAT DETECTED | ");
    }
  }

  if (millis() - lastPrint >= 250) {
    Serial.print("IR=");
    Serial.print(irValue);
    Serial.print(" | BPM=");
    Serial.print(bpm);
    Serial.print(" | AVG=");
    Serial.println(bpmAverage);
    lastPrint = millis();
  }

  delay(5);
}
