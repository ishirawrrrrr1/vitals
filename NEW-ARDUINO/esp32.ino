#include <WiFi.h>
#include <WebServer.h>
#include <Preferences.h>
#include <SocketIOclient.h>
#include "soc/soc.h"
#include "soc/rtc_cntl_reg.h"

#define STATUS_LED 2

#define RXD2 16
#define TXD2 17

// ESP32 hardware serial link to Arduino Mega:
// ESP32 RX2 GPIO16 <- Mega TX1 pin 18
// ESP32 TX2 GPIO17 -> Mega RX1 pin 19
// ESP32 GND        -> Mega GND

WebServer server(80);
Preferences prefs;
SocketIOclient socketIO;

String staSsid = "";
String staPass = "";
String backendHost = "";
uint16_t backendPort = 443;
bool backendSsl = true;
bool socketReady = false;

unsigned long lastWifiAttempt = 0;
unsigned long lastSocketRegister = 0;
unsigned long lastHeartbeat = 0;

const char* setupHtml =
"<html><head><meta name='viewport' content='width=device-width, initial-scale=1'>"
"<style>body{font-family:sans-serif;background:#0f172a;color:#fff;text-align:center;padding:28px;}"
"input{width:100%;max-width:360px;padding:12px;margin:8px 0;border-radius:8px;border:none;}"
"label{display:block;max-width:360px;margin:12px auto 2px;text-align:left;color:#cbd5e1;}"
"button{width:100%;max-width:360px;padding:14px;background:#38bdf8;border:none;border-radius:8px;color:#fff;font-weight:bold;}"
"</style></head><body>"
"<h1>Vitals Cloud Hub</h1>"
"<form method='POST' action='/save'>"
"<label>WiFi Name</label><input name='ssid' placeholder='WiFi SSID'><br>"
"<label>WiFi Password</label><input name='pass' type='password' placeholder='WiFi Password'><br>"
"<label>Backend Host</label><input name='host' placeholder='your-backend.up.railway.app'><br>"
"<label>Backend Port</label><input name='port' value='443'><br>"
"<label>SSL</label><input name='ssl' value='1' placeholder='1 for https, 0 for http'><br><br>"
"<button type='submit'>SAVE & CONNECT</button>"
"</form></body></html>";

String jsonEscape(const String& value) {
  String out = "";
  for (size_t i = 0; i < value.length(); i++) {
    char c = value.charAt(i);
    if (c == '\\' || c == '"') {
      out += '\\';
      out += c;
    } else if (c == '\n' || c == '\r') {
      // Skip line endings; Serial2 framing already uses them.
    } else {
      out += c;
    }
  }
  return out;
}

String extractCommand(uint8_t * payload, size_t length) {
  String text = "";
  for (size_t i = 0; i < length; i++) text += (char)payload[i];

  int key = text.indexOf("\"command\"");
  if (key < 0) return "";

  int colon = text.indexOf(':', key);
  int firstQuote = text.indexOf('"', colon + 1);
  int secondQuote = text.indexOf('"', firstQuote + 1);
  if (colon < 0 || firstQuote < 0 || secondQuote < 0) return "";

  return text.substring(firstQuote + 1, secondQuote);
}

void sendHardwareRegistration() {
  if (!socketReady) return;
  socketIO.sendEVENT("[\"join_hardware\",{\"device_id\":\"ESP32_CORE_V6\"}]");
  lastSocketRegister = millis();
  Serial.println("[CLOUD] Hardware registered");
}

void sendSensorUpdate(const String& raw) {
  if (!socketReady) return;
  String event = "[\"sensor_update\",{\"raw\":\"" + jsonEscape(raw) + "\"}]";
  socketIO.sendEVENT(event);
  Serial.println("[CLOUD] Sensor update sent");
}

void socketIOEvent(socketIOmessageType_t type, uint8_t * payload, size_t length) {
  switch (type) {
    case sIOtype_DISCONNECT:
      socketReady = false;
      Serial.println("[CLOUD] Socket disconnected");
      break;

    case sIOtype_CONNECT:
      socketReady = true;
      Serial.println("[CLOUD] Socket connected");
      socketIO.send(sIOtype_CONNECT, "/");
      sendHardwareRegistration();
      break;

    case sIOtype_EVENT: {
      String cmd = extractCommand(payload, length);
      if (cmd.length() > 0) {
        Serial.print("[CLOUD_CMD] ");
        Serial.println(cmd);
        Serial2.println(cmd);
      }
      break;
    }

    default:
      break;
  }
}

void startSetupPortal() {
  WiFi.mode(WIFI_AP_STA);
  WiFi.softAP("Vitals-Hub-Setup");

  server.on("/", []() {
    server.send(200, "text/html", setupHtml);
  });

  server.on("/save", HTTP_POST, []() {
    String ssid = server.arg("ssid");
    String pass = server.arg("pass");
    String host = server.arg("host");
    String port = server.arg("port");
    String ssl = server.arg("ssl");

    if (ssid.length() > 0) prefs.putString("ssid", ssid);
    if (pass.length() > 0) prefs.putString("pass", pass);
    if (host.length() > 0) prefs.putString("host", host);
    if (port.length() > 0) prefs.putUInt("port", port.toInt());
    if (ssl.length() > 0) prefs.putBool("ssl", ssl != "0");

    server.send(200, "text/plain", "Saved. Restarting...");
    delay(1200);
    ESP.restart();
  });

  server.begin();

  Serial.print("[SETUP] Portal SSID: Vitals-Hub-Setup, IP: ");
  Serial.println(WiFi.softAPIP());
}

void connectWifi() {
  if (staSsid.length() == 0) return;
  if (WiFi.status() == WL_CONNECTED) return;
  if (millis() - lastWifiAttempt < 10000) return;

  lastWifiAttempt = millis();
  Serial.print("[WIFI] Connecting to ");
  Serial.println(staSsid);
  WiFi.begin(staSsid.c_str(), staPass.c_str());
}

void connectSocket() {
  if (backendHost.length() == 0) return;
  if (WiFi.status() != WL_CONNECTED) return;

  socketIO.disconnect();
  if (backendSsl) {
    socketIO.beginSSL(backendHost.c_str(), backendPort, "/socket.io/?EIO=4");
  } else {
    socketIO.begin(backendHost.c_str(), backendPort, "/socket.io/?EIO=4");
  }
  socketIO.onEvent(socketIOEvent);

  Serial.print("[CLOUD] Connecting to ");
  Serial.print(backendSsl ? "https://" : "http://");
  Serial.print(backendHost);
  Serial.print(":");
  Serial.println(backendPort);
}

void readArduinoData() {
  static String rxBuffer = "";

  while (Serial2.available()) {
    char c = Serial2.read();

    if (c == '\n') {
      rxBuffer.trim();
      if (rxBuffer.length() > 0) {
        Serial.print("[FROM_ARDUINO] ");
        Serial.println(rxBuffer);
        sendSensorUpdate(rxBuffer);
        digitalWrite(STATUS_LED, HIGH);
        delay(10);
        digitalWrite(STATUS_LED, LOW);
      }
      rxBuffer = "";
    } else if (c != '\r') {
      rxBuffer += c;
    }
  }
}

void setup() {
  WRITE_PERI_REG(RTC_CNTL_BROWN_OUT_REG, 0);

  Serial.begin(115200);
  Serial2.begin(115200, SERIAL_8N1, RXD2, TXD2);
  delay(1000);

  pinMode(STATUS_LED, OUTPUT);
  digitalWrite(STATUS_LED, HIGH);

  prefs.begin("vitals-cloud", false);
  staSsid = prefs.getString("ssid", "");
  staPass = prefs.getString("pass", "");
  backendHost = prefs.getString("host", "");
  backendPort = prefs.getUInt("port", 443);
  backendSsl = prefs.getBool("ssl", true);

  Serial.println("[BOOT] ESP32 VITALS CLOUD HUB");
  startSetupPortal();
  connectWifi();
  connectSocket();

  digitalWrite(STATUS_LED, LOW);
}

void loop() {
  server.handleClient();
  connectWifi();

  if (WiFi.status() == WL_CONNECTED) {
    socketIO.loop();

    if (!socketReady && backendHost.length() > 0 && millis() - lastHeartbeat > 15000) {
      connectSocket();
      lastHeartbeat = millis();
    }

    if (socketReady && millis() - lastSocketRegister > 30000) {
      sendHardwareRegistration();
    }
  }

  readArduinoData();
}
