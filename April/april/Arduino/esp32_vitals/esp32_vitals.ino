#include <WiFi.h>
#include <WebServer.h>
#include <WebSocketsServer.h>
#include <WiFiUdp.h>
#include <Preferences.h>
#include <DNSServer.h>
#include <SocketIOclient.h>
#include "soc/soc.h"
#include "soc/rtc_cntl_reg.h"

// --- HARDWARE ---
#define STATUS_LED 2
#define PIN_HAND_EXT 13
#define PIN_HAND_RET 12
#define PIN_LEG_EXT 14
#define PIN_LEG_RET 27
#define PIN_GLOVE_PWR 26

// --- GLOBALS ---
WebSocketsServer webSocket = WebSocketsServer(8080);
SocketIOclient socketIO;
WebServer server(80);
DNSServer dnsServer;
WiFiUDP udp;
Preferences prefs;

unsigned long lastWebBroadcast = 0;
unsigned long lastDiscovery = 0;
String sta_ssid = "";
String sta_pass = "";

const char* setup_html = 
"<html><head><meta name='viewport' content='width=device-width, initial-scale=1'><style>"
"body{font-family:sans-serif; background:#0f172a; color:#fff; text-align:center; padding-top:50px;}"
"input{width:100%; max-width:300px; padding:12px; margin:10px 0; border-radius:8px; border:none;}"
"button{width:100%; max-width:300px; padding:15px; background:#38bdf8; border:none; border-radius:8px; color:#fff; font-weight:bold;}"
"</style></head><body>"
"<h1>Vitals Hub Setup</h1>"
"<form method='POST' action='/save'>"
"<input name='ssid' placeholder='WiFi Name'><br>"
"<input name='pass' type='password' placeholder='Password'><br><br>"
"<button type='submit'>SAVE & CONNECT</button>"
"</form></body></html>";

void setup() {
    WRITE_PERI_REG(RTC_CNTL_BROWN_OUT_REG, 0); 
    Serial.begin(9600);
    delay(2000); // Give power time to stabilize
    Serial.println("\n\n[BOOT] POWER STABILIZING...");

    pinMode(STATUS_LED, OUTPUT);
    digitalWrite(STATUS_LED, HIGH);

    // --- ACTUATOR OUTPUTS ---
    pinMode(PIN_HAND_EXT, OUTPUT);
    pinMode(PIN_HAND_RET, OUTPUT);
    pinMode(PIN_LEG_EXT, OUTPUT);
    pinMode(PIN_LEG_RET, OUTPUT);
    pinMode(PIN_GLOVE_PWR, OUTPUT);
    
    digitalWrite(PIN_HAND_EXT, LOW);
    digitalWrite(PIN_HAND_RET, LOW);
    digitalWrite(PIN_LEG_EXT, LOW);
    digitalWrite(PIN_LEG_RET, LOW);
    digitalWrite(PIN_GLOVE_PWR, LOW);

    // --- COMMUNICATION ---
    Serial2.begin(9600); // Arduino Logic Link (TX2=17, RX2=16)

    // --- WIFI STARTUP (STABILIZED) ---
    WiFi.mode(WIFI_AP);
    WiFi.softAP("Vitals-Hub-Direct");
    WiFi.setTxPower(WIFI_POWER_11dBm); // Reduce power consumption
    Serial.println("[WIFI] Access Point 'Vitals-Hub-Direct' Started");
    Serial.print("[WIFI] IP Address: ");
    Serial.println(WiFi.softAPIP());
    
    dnsServer.start(53, "*", WiFi.softAPIP());

    prefs.begin("vitals-hub", false);
    sta_ssid = prefs.getString("ssid", "");
    sta_pass = prefs.getString("pass", "");

    server.on("/", []() { server.send(200, "text/html", setup_html); });
    server.on("/setup", []() { server.send(200, "text/html", setup_html); });
    server.on("/save", HTTP_POST, handleSave);
    server.begin();

    webSocket.begin();
    webSocket.onEvent(webSocketEvent);
    
    Serial.println("[HUB] System Ready. Pins Initialized.");
    Serial.println("[HUB] Listening on Serial2 (RX2=16) for Arduino data.");
    digitalWrite(STATUS_LED, LOW);
}

void handleSave() {
    String s = server.arg("ssid");
    String p = server.arg("pass");
    if (s != "") {
        prefs.putString("ssid", s);
        prefs.putString("pass", p);
        server.send(200, "text/plain", "WiFi Details Saved. The Hub will now restart.");
        delay(2000);
        ESP.restart();
    }
}

void loop() {
    unsigned long now = millis();
    dnsServer.processNextRequest();
    server.handleClient();
    webSocket.loop();

    // 📡 IDENTITY BROADCAST (For Android Auto-Discovery)
    if (now - lastDiscovery >= 5000) {
        lastDiscovery = now;
        udp.beginPacket("255.255.255.255", 12345);
        udp.print("VITALS_HUB_IDENTITY");
        udp.endPacket();
    }

    // 📡 SERIAL RELAY (Listen to Arduino)
    static String rxBuffer = "";
    while (Serial2.available()) {
        char c = Serial2.read();
        if (c == '\n') {
            rxBuffer.trim();
            if (rxBuffer.length() > 0) {
                // Broadcast raw sensor/command data to Android
                webSocket.broadcastTXT(rxBuffer);
                digitalWrite(STATUS_LED, HIGH);
                delay(10);
                digitalWrite(STATUS_LED, LOW);
            }
            rxBuffer = "";
        } else if (c != '\r') {
            rxBuffer += c;
        }
    }

    // 📡 HUB HEARTBEAT (Every 3 seconds)
    static unsigned long lastHeartbeat = 0;
    if (now - lastHeartbeat >= 3000) {
        lastHeartbeat = now;
        webSocket.broadcastTXT("HUB_HEARTBEAT:OK");
    }
}

void webSocketEvent(uint8_t num, WStype_t type, uint8_t * payload, size_t length) {
    if (type == WStype_TEXT) {
        String cmd = (char*)payload;
        Serial.print("[CMD] Received: ");
        Serial.println(cmd);
        
        if (cmd == "CMD:HAND:EXT") { digitalWrite(PIN_HAND_EXT, HIGH); digitalWrite(PIN_HAND_RET, LOW); }
        else if (cmd == "CMD:HAND:RET") { digitalWrite(PIN_HAND_EXT, LOW); digitalWrite(PIN_HAND_RET, HIGH); }
        else if (cmd == "CMD:HAND:STP") { digitalWrite(PIN_HAND_EXT, LOW); digitalWrite(PIN_HAND_RET, LOW); }
        else if (cmd == "CMD:LEG:EXT") { digitalWrite(PIN_LEG_EXT, HIGH); digitalWrite(PIN_LEG_RET, LOW); }
        else if (cmd == "CMD:LEG:RET") { digitalWrite(PIN_LEG_EXT, LOW); digitalWrite(PIN_LEG_RET, HIGH); }
        else if (cmd == "CMD:LEG:STP") { digitalWrite(PIN_LEG_EXT, LOW); digitalWrite(PIN_LEG_RET, LOW); }
        else if (cmd == "GLOVE:ON") { digitalWrite(PIN_GLOVE_PWR, HIGH); }
        else if (cmd == "GLOVE:OFF") { digitalWrite(PIN_GLOVE_PWR, LOW); }
        else if (cmd == "CMD:STOP") { 
            digitalWrite(PIN_HAND_EXT, LOW); digitalWrite(PIN_HAND_RET, LOW);
            digitalWrite(PIN_LEG_EXT, LOW); digitalWrite(PIN_LEG_RET, LOW);
            digitalWrite(PIN_GLOVE_PWR, LOW);
        }
    }
}
