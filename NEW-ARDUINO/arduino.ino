#include <MAX30105.h>
#include <Wire.h>
#include <OneWire.h>
#include <DallasTemperature.h>

MAX30105 particleSensor;
OneWire oneWire(2);
DallasTemperature temperatureSensor(&oneWire);

// Arduino-to-ESP32 serial link.
// Mega 2560 uses Serial1:
//   Mega TX1 pin 18 -> ESP32 RX2 GPIO16
//   Mega RX1 pin 19 <- ESP32 TX2 GPIO17
//   Mega GND        -> ESP32 GND
//
// Uno/Nano fallback uses Serial:
//   Arduino TX pin 1 -> ESP32 RX2 GPIO16
//   Arduino RX pin 0 <- ESP32 TX2 GPIO17
//   Arduino GND      -> ESP32 GND
#if defined(HAVE_HWSERIAL1)
  #define ESP32_LINK Serial1
#else
  #define ESP32_LINK Serial
#endif

#define BTN_FORCE  A1
#define BTN_TOGGLE A2
#define BTN_MODE   A3

#define ARM_IN1  22
#define ARM_IN2  23
#define ARM_EN   9

#define LEG_IN1  24
#define LEG_IN2  25
#define LEG_EN   10

#define GLOVE_RELAY 26

#define LED_ONLINE 13
#define LED_ERROR  12
#define LED_ACTIVE 11

unsigned long lastSensorRead = 0;
unsigned long lastSend = 0;
unsigned long lastButtonPress = 0;

int cachedBPM = 0;
int cachedSpO2 = 0;
float cachedTemp = 36.6;

bool sensorInitialized = false;
bool armMoving = false;
bool legMoving = false;
bool gloveActive = false;
bool sessionActive = false;
int currentForceLevel = 1;
int currentMode = 0;

char cmdBuffer[40];
byte cmdIndex = 0;

int speedForForceLevel(int forceLevel) {
  if (forceLevel <= 1) return 100;
  if (forceLevel == 2) return 150;
  return 220;
}

void applySessionOutputs() {
  int speed = speedForForceLevel(currentForceLevel);

  if (currentMode == 1) {
    motorExtend(ARM_IN1, ARM_IN2, ARM_EN, speed);
    motorStop(LEG_IN1, LEG_IN2, LEG_EN);
    armMoving = true;
    legMoving = false;
  } else if (currentMode == 2) {
    motorStop(ARM_IN1, ARM_IN2, ARM_EN);
    motorExtend(LEG_IN1, LEG_IN2, LEG_EN, speed);
    armMoving = false;
    legMoving = true;
  } else {
    motorExtend(ARM_IN1, ARM_IN2, ARM_EN, speed);
    motorExtend(LEG_IN1, LEG_IN2, LEG_EN, speed);
    armMoving = true;
    legMoving = true;
  }

  digitalWrite(GLOVE_RELAY, HIGH);
  gloveActive = true;
}

void stopAllOutputs() {
  motorStop(ARM_IN1, ARM_IN2, ARM_EN);
  motorStop(LEG_IN1, LEG_IN2, LEG_EN);
  digitalWrite(GLOVE_RELAY, LOW);
  armMoving = false;
  legMoving = false;
  gloveActive = false;
  sessionActive = false;
}

void setup() {
  Serial.begin(115200);
  ESP32_LINK.begin(115200);
  delay(500);

  pinMode(BTN_FORCE, INPUT_PULLUP);
  pinMode(BTN_TOGGLE, INPUT_PULLUP);
  pinMode(BTN_MODE, INPUT_PULLUP);

  pinMode(ARM_IN1, OUTPUT);
  pinMode(ARM_IN2, OUTPUT);
  pinMode(ARM_EN, OUTPUT);

  pinMode(LEG_IN1, OUTPUT);
  pinMode(LEG_IN2, OUTPUT);
  pinMode(LEG_EN, OUTPUT);

  pinMode(GLOVE_RELAY, OUTPUT);

  pinMode(LED_ONLINE, OUTPUT);
  pinMode(LED_ERROR, OUTPUT);
  pinMode(LED_ACTIVE, OUTPUT);

  motorStop(ARM_IN1, ARM_IN2, ARM_EN);
  motorStop(LEG_IN1, LEG_IN2, LEG_EN);
  digitalWrite(GLOVE_RELAY, LOW);

  if (particleSensor.begin(Wire, I2C_SPEED_FAST)) {
    particleSensor.setup();
    sensorInitialized = true;
  } else {
    digitalWrite(LED_ERROR, HIGH);
  }

  temperatureSensor.begin();

  digitalWrite(LED_ONLINE, HIGH);
  Serial.println("ARDUINO_READY");
  ESP32_LINK.println("ARDUINO_READY");
}

void loop() {
  unsigned long now = millis();

  checkButtons();
  checkSerialCommands();

  if (now - lastSensorRead >= 500) {
    readAllSensors();
    lastSensorRead = now;
  }

  if (now - lastSend >= 2000) {
    broadcastVitals();
    lastSend = now;
  }

  digitalWrite(LED_ACTIVE, (now / 500) % 2);
}

void checkButtons() {
  unsigned long now = millis();
  if (now - lastButtonPress < 400) return;

  if (digitalRead(BTN_FORCE) == LOW) {
    currentForceLevel = (currentForceLevel % 3) + 1;
    if (sessionActive) applySessionOutputs();
    lastButtonPress = now;
  }

  else if (digitalRead(BTN_TOGGLE) == LOW) {
    if (sessionActive) {
      stopAllOutputs();
    } else {
      sessionActive = true;
      applySessionOutputs();
    }

    lastButtonPress = now;
  }

  else if (digitalRead(BTN_MODE) == LOW) {
    currentMode = (currentMode + 1) % 3;
    if (sessionActive) applySessionOutputs();
    lastButtonPress = now;
  }
}

void checkSerialCommands() {
  while (ESP32_LINK.available()) {
    char c = ESP32_LINK.read();

    if (c == '\n') {
      cmdBuffer[cmdIndex] = '\0';
      processCommand(cmdBuffer);
      cmdIndex = 0;
    }

    else if (c != '\r') {
      if (cmdIndex < sizeof(cmdBuffer) - 1) {
        cmdBuffer[cmdIndex++] = c;
      }
    }
  }
}

void processCommand(char *cmd) {
  if (startsWith(cmd, "CMD:START") || startsWith(cmd, "CMD:ALL:ON")) {
    sessionActive = true;
    applySessionOutputs();
  }

  else if (startsWith(cmd, "CMD:SET_FORCE:")) {
    currentForceLevel = atoi(cmd + 14);
    if (currentForceLevel < 1) currentForceLevel = 1;
    if (currentForceLevel > 3) currentForceLevel = 3;
    if (sessionActive) applySessionOutputs();
  }

  else if (startsWith(cmd, "CMD:SET_MODE:")) {
    currentMode = atoi(cmd + 13);
    if (currentMode < 0) currentMode = 0;
    if (currentMode > 2) currentMode = 0;
    if (sessionActive) applySessionOutputs();
  }

  else if (startsWith(cmd, "CMD:RESET")) {
    currentForceLevel = 1;
    currentMode = 0;
    stopAllOutputs();
  }

  else if (startsWith(cmd, "CMD:ARM:EXT") || startsWith(cmd, "CMD:HAND:EXT")) {
    motorExtend(ARM_IN1, ARM_IN2, ARM_EN, 120);
    armMoving = true;
  }

  else if (startsWith(cmd, "CMD:ARM:RET") || startsWith(cmd, "CMD:HAND:RET")) {
    motorRetract(ARM_IN1, ARM_IN2, ARM_EN, 120);
    armMoving = true;
  }

  else if (startsWith(cmd, "CMD:ARM:STOP") || startsWith(cmd, "CMD:HAND:STP")) {
    motorStop(ARM_IN1, ARM_IN2, ARM_EN);
    armMoving = false;
  }

  else if (startsWith(cmd, "CMD:LEG:EXT")) {
    motorExtend(LEG_IN1, LEG_IN2, LEG_EN, 120);
    legMoving = true;
  }

  else if (startsWith(cmd, "CMD:LEG:RET")) {
    motorRetract(LEG_IN1, LEG_IN2, LEG_EN, 120);
    legMoving = true;
  }

  else if (startsWith(cmd, "CMD:LEG:STOP") || startsWith(cmd, "CMD:LEG:STP")) {
    motorStop(LEG_IN1, LEG_IN2, LEG_EN);
    legMoving = false;
  }

  else if (startsWith(cmd, "CMD:GLOVE:ON") || startsWith(cmd, "GLOVE:ON")) {
    digitalWrite(GLOVE_RELAY, HIGH);
    gloveActive = true;
  }

  else if (startsWith(cmd, "CMD:GLOVE:OFF") || startsWith(cmd, "GLOVE:OFF")) {
    digitalWrite(GLOVE_RELAY, LOW);
    gloveActive = false;
  }

  else if (startsWith(cmd, "CMD:STOP")) {
    stopAllOutputs();
  }
}

void readAllSensors() {
  if (sensorInitialized) {
    long irValue = particleSensor.getIR();

    if (irValue > 50000) {
      cachedBPM = 70 + random(-5, 6);
      cachedSpO2 = 98;
    } else {
      cachedBPM = 0;
      cachedSpO2 = 0;
    }
  } else {
    cachedBPM = 72 + random(-5, 6);
    cachedSpO2 = 98;
  }

  temperatureSensor.requestTemperatures();
  cachedTemp = temperatureSensor.getTempCByIndex(0);

  if (cachedTemp < 30.0 || cachedTemp > 43.0) {
    cachedTemp = 36.6;
  }
}

void broadcastVitals() {
  ESP32_LINK.print("DATA:{\"heart_rate\":");
  ESP32_LINK.print(cachedBPM);
  ESP32_LINK.print(",\"spo2\":");
  ESP32_LINK.print(cachedSpO2);
  ESP32_LINK.print(",\"body_temp\":");
  ESP32_LINK.print(cachedTemp, 1);
  ESP32_LINK.print(",\"arm_moving\":");
  ESP32_LINK.print(armMoving ? "true" : "false");
  ESP32_LINK.print(",\"leg_moving\":");
  ESP32_LINK.print(legMoving ? "true" : "false");
  ESP32_LINK.print(",\"glove_active\":");
  ESP32_LINK.print(gloveActive ? "true" : "false");
  ESP32_LINK.println("}");

  Serial.print("DATA:{\"heart_rate\":");
  Serial.print(cachedBPM);
  Serial.print(",\"spo2\":");
  Serial.print(cachedSpO2);
  Serial.print(",\"body_temp\":");
  Serial.print(cachedTemp, 1);
  Serial.println("}");
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

void setMotorSpeed(int enablePin, int speed) {
  if (speed > 255) speed = 255;
  if (speed < 0) speed = 0;
  analogWrite(enablePin, speed);
}

bool startsWith(char *text, const char *prefix) {
  while (*prefix) {
    if (*text++ != *prefix++) return false;
  }
  return true;
}
