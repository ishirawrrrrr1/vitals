#include <Wire.h>
#include <Adafruit_MLX90614.h>
#include "MAX30105.h" // Commonly used for MAX30102 as well
#include "heartRate.h" // Needed for checkForBeat

// Initialize Sensor Objects
Adafruit_MLX90614 mlx = Adafruit_MLX90614();
MAX30105 particleSensor;

const byte RATE_SIZE = 4; // Averaging limit
byte rates[RATE_SIZE]; 
byte rateSpot = 0;
long lastBeat = 0; // Time of last beat

float beatsPerMinute;
int beatAvg;

void setup() {
  Serial.begin(115200);
  Serial.println("--- Booting Sensor Tester ---");

  // Keep attempting MLX90614 init
  if (!mlx.begin()) {
    Serial.println("Error: MLX90614 (Temperature) not found! Check I2C Wiring (SDA/SCL/3.3V/GND)");
  } else {
    Serial.println("Success: MLX90614 Temp Sensor connected!");
  }

  // Keep attempting MAX30102 init
  if (!particleSensor.begin(Wire, I2C_SPEED_FAST)) {
    Serial.println("Error: MAX30102 (Pulse) not found! Check I2C Wiring (SDA/SCL/3.3V/GND)");
  } else {
    Serial.println("Success: MAX30102 Pulse Sensor connected!");
    
    // Setup to sense a finger
    byte ledBrightness = 60; // Options: 0=Off to 255=50mA
    byte sampleAverage = 4; // Options: 1, 2, 4, 8, 16, 32
    byte ledMode = 2; // Options: 1 = Red only (HR), 2 = Red + IR (SpO2)
    int sampleRate = 100; // Options: 50, 100, 200, 400, 800, 1000, 1600, 3200
    int pulseWidth = 411; // Options: 69, 118, 215, 411
    int adcRange = 4096; // Options: 2048, 4096, 8192, 16384
    
    particleSensor.setup(ledBrightness, sampleAverage, ledMode, sampleRate, pulseWidth, adcRange);
  }
  
  Serial.println("Place your finger on the MAX30102 sensor...");
}

void loop() {
  // Read Temperature
  float objTemp = mlx.readObjectTempC();
  float ambTemp = mlx.readAmbientTempC();

  // Read IR value for Heart Rate
  long irValue = particleSensor.getIR();
  
  // Calculate BPM
  if (checkForBeat(irValue) == true) {
    long delta = millis() - lastBeat;
    lastBeat = millis();

    beatsPerMinute = 60 / (delta / 1000.0);

    // Filter valid bounds
    if (beatsPerMinute < 255 && beatsPerMinute > 20) {
      rates[rateSpot++] = (byte)beatsPerMinute;
      rateSpot %= RATE_SIZE;

      // Calculate average
      beatAvg = 0;
      for (byte x = 0 ; x < RATE_SIZE ; x++)
        beatAvg += rates[x];
      beatAvg /= RATE_SIZE;
    }
  }

  // Only spam Serial Monitor if someone is actually touching the sensor
  if (irValue > 50000) {
    Serial.print("IR: ");
    Serial.print(irValue);
    Serial.print("\t BPM: ");
    Serial.print(beatsPerMinute);
    Serial.print("\t Avg BPM: ");
    Serial.print(beatAvg);
    Serial.print("\t Body Temp: ");
    Serial.print(objTemp);
    Serial.println(" C");
  } else {
    // Print temp every 2 seconds if no finger is placed
    static unsigned long lastTempRead = 0;
    if (millis() - lastTempRead > 2000) {
      Serial.print("No finger detected. Ambient Temp: ");
      Serial.print(ambTemp);
      Serial.print(" C, Body Target Temp: ");
      Serial.print(objTemp);
      Serial.println(" C");
      lastTempRead = millis();
    }
  }
}
