#include <OneWire.h>
#include <DallasTemperature.h>

#define TEMP_PIN 4

OneWire oneWire(TEMP_PIN);
DallasTemperature tempSensor(&oneWire);

void setup() {
  Serial.begin(115200);
  delay(1000);

  Serial.println("DS18B20 TEMP TEST");
  Serial.println("Wiring:");
  Serial.println("RED    -> ESP32 3.3V");
  Serial.println("BLACK  -> ESP32 GND");
  Serial.println("YELLOW -> ESP32 GPIO4");
  Serial.println("Using ESP32 internal pull-up first.");
  Serial.println("If SENSOR NOT FOUND, use 4.7k resistor between YELLOW and 3.3V.");

  pinMode(TEMP_PIN, INPUT_PULLUP);
  tempSensor.begin();

  Serial.print("DS18B20 DEVICES FOUND: ");
  Serial.println(tempSensor.getDeviceCount());
}

void loop() {
  tempSensor.requestTemperatures();
  float tempC = tempSensor.getTempCByIndex(0);

  if (tempC == DEVICE_DISCONNECTED_C) {
    Serial.println("TEMP ERROR: SENSOR NOT FOUND");
    Serial.println("Check DATA wire, GND, 3.3V, and 4.7k resistor.");
  } else if (tempC == 85.0) {
    Serial.println("TEMP ERROR: 85.0 C startup/default reading");
    Serial.println("Wait or check wiring/resistor.");
  } else {
    Serial.print("TEMP=");
    Serial.print(tempC, 2);
    Serial.println(" C");
  }

  delay(1000);
}
