package com.example.myapplication;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.myapplication.db.AppDatabase;
import com.example.myapplication.db.LocalVitalSign;
import com.example.myapplication.network.RetrofitClient;

import org.json.JSONObject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;

import io.socket.client.IO;
import io.socket.client.Socket;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class VitalsMonitoringService extends Service {
    private static final String TAG = "VitalsService";
    public static final String ACTION_VITALS_UPDATE = "com.example.myapplication.VITALS_UPDATE";
    public static final String ACTION_HUB_STATUS = "com.example.myapplication.HUB_STATUS";
    public static final String ACTION_HARDWARE_BUTTON = "com.example.myapplication.HARDWARE_BUTTON";
    private static final String CHANNEL_ID = "VitalsServiceChannel";

    private static VitalsMonitoringService instance;

    private Socket relaySocket;
    private WebSocket directSocket;
    private boolean isConnected = false;
    private String foundIp = null;
    private String lastVitalsPayload = null;
    private Thread discoveryThread;
    private AppDatabase db;
    private int activeSessionId = -1;

    public static VitalsMonitoringService getInstance() {
        return instance;
    }

    public boolean isHubConnected() {
        return isConnected;
    }

    public String getLastVitalsPayload() {
        return lastVitalsPayload;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        db = AppDatabase.getInstance(this);
        createNotificationChannel();
        startForeground(1, createNotification("Initializing Vitals Bridge..."));

        startDiscovery();
        tryDirectLink("192.168.4.1");
        initRelay();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Clinical Monitoring Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private Notification createNotification(String content) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Clinical Vitals Monitoring")
                .setContentText(content)
                .setSmallIcon(R.drawable.logo_vitals)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }

    private void startDiscovery() {
        if (discoveryThread != null && discoveryThread.isAlive()) {
            return;
        }

        discoveryThread = new Thread(() -> {
            android.net.wifi.WifiManager wifi = (android.net.wifi.WifiManager) getSystemService(android.content.Context.WIFI_SERVICE);
            android.net.wifi.WifiManager.MulticastLock lock = wifi.createMulticastLock("VitalsDiscovery");
            lock.acquire();

            try (DatagramSocket socket = new DatagramSocket(12345)) {
                byte[] buffer = new byte[1024];
                while (!Thread.currentThread().isInterrupted()) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    String message = new String(packet.getData(), 0, packet.getLength()).trim();

                    if ("VITALS_HUB_IDENTITY".equals(message)) {
                        String detectedIp = packet.getAddress().getHostAddress();
                        if (foundIp == null || !foundIp.equals(detectedIp)) {
                            foundIp = detectedIp;
                            getSharedPreferences(LoginActivity.PREFS_NAME, MODE_PRIVATE)
                                    .edit()
                                    .putString("hub_ip", foundIp)
                                    .apply();
                            Log.d(TAG, "Hub discovered at " + foundIp);
                            tryDirectLink(foundIp);
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Discovery Thread Error", e);
            } finally {
                lock.release();
            }
        });
        discoveryThread.start();
    }

    private synchronized void tryDirectLink(String ip) {
        if (directSocket != null) {
            directSocket.close(1000, "Re-mapping");
            directSocket = null;
        }

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url("ws://" + ip + ":8080").build();
        directSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                isConnected = true;
                broadcastStatus();
                updateNotification("Linked to Hub: " + ip);
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                String raw = text.trim();

                if ("HUB_HEARTBEAT:OK".equals(raw)) {
                    isConnected = true;
                    broadcastStatus();
                    return;
                }

                if (raw.startsWith("USER_CMD:")) {
                    broadcastHardwareButton(raw.substring(9));
                    return;
                }

                if (raw.equals("FORCE") || raw.equals("TOGGLE") || raw.equals("MODE")) {
                    broadcastHardwareButton(raw);
                    return;
                }

                broadcastVitals(raw);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                isConnected = false;
                broadcastStatus();
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> tryDirectLink(ip), 5000);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                isConnected = false;
                broadcastStatus();
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> tryDirectLink(ip), 5000);
            }
        });
    }

    private void initRelay() {
        android.content.SharedPreferences prefs = getSharedPreferences(LoginActivity.PREFS_NAME, MODE_PRIVATE);
        String savedIp = prefs.getString(LoginActivity.KEY_SERVER_IP, null);
        if (savedIp == null || savedIp.isEmpty()) {
            return;
        }

        RetrofitClient.discoverPort(this, savedIp, new RetrofitClient.PortDiscoveryCallback() {
            @Override
            public void onPortFound(String url) {
                try {
                    relaySocket = IO.socket(url);
                    relaySocket.on(Socket.EVENT_CONNECT, args -> {
                        isConnected = true;
                        broadcastStatus();
                    });
                    relaySocket.on("vitals_update", args -> {
                        if (args.length > 0 && args[0] != null) {
                            broadcastVitals(args[0].toString());
                        }
                    });
                    relaySocket.on("hub_status", args -> {
                        if (args.length > 0 && args[0] instanceof JSONObject) {
                            JSONObject obj = (JSONObject) args[0];
                            isConnected = obj.optBoolean("connected", false);
                            broadcastStatus();
                        }
                    });
                    relaySocket.on("hardware_button", args -> {
                        if (args.length > 0 && args[0] instanceof JSONObject) {
                            String cmd = ((JSONObject) args[0]).optString("command", "");
                            if (!cmd.isEmpty()) {
                                broadcastHardwareButton(cmd);
                            }
                        }
                    });
                    relaySocket.connect();
                } catch (Exception e) {
                    Log.e(TAG, "Relay init failed", e);
                }
            }

            @Override
            public void onDiscoveryFailed() {
            }
        });
    }

    private void broadcastHardwareButton(String cmd) {
        Intent intent = new Intent(ACTION_HARDWARE_BUTTON);
        intent.putExtra("command", cmd);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void broadcastVitals(String data) {
        lastVitalsPayload = data;

        if (activeSessionId != -1) {
            final long timestamp = System.currentTimeMillis();
            AppDatabase.databaseWriteExecutor.execute(() -> {
                try {
                    String cleaned = data.trim();
                    if (cleaned.startsWith("DATA:")) {
                        cleaned = cleaned.substring(5).trim();
                    }

                    if (cleaned.startsWith("{")) {
                        JSONObject json = new JSONObject(cleaned);
                        persistJsonMetric("HeartRate", json.optDouble("heart_rate", json.optDouble("BPM", 0)), "bpm", timestamp);
                        persistJsonMetric("Spo2", json.optDouble("spo2", json.optDouble("SPO2", 0)), "%", timestamp);
                        persistJsonMetric("Temperature", json.optDouble("body_temp", json.optDouble("temperature", 0)), "C", timestamp);
                    } else {
                        String[] parts = cleaned.split(",");
                        for (String part : parts) {
                            String[] kv = part.split(":");
                            if (kv.length == 2) {
                                String type = kv[0].trim();
                                String valueStr = kv[1].trim();
                                if (type.equals("BP")) {
                                    valueStr = valueStr.split("/")[0];
                                }
                                float value = 0;
                                try {
                                    value = Float.parseFloat(valueStr);
                                } catch (Exception ignored) {
                                }
                                db.vitalSignDao().insert(new LocalVitalSign(type, "", value, "", timestamp, activeSessionId));
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Persistence Error: " + e.getMessage());
                }
            });
        }

        Intent intent = new Intent(ACTION_VITALS_UPDATE);
        intent.putExtra("data", data);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void persistJsonMetric(String type, double value, String unit, long timestamp) {
        if (value > 0) {
            db.vitalSignDao().insert(new LocalVitalSign(type, "", (float) value, unit, timestamp, activeSessionId));
        }
    }

    private void broadcastStatus() {
        Intent intent = new Intent(ACTION_HUB_STATUS);
        intent.putExtra("connected", isConnected);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    public void triggerManualHubScan() {
        foundIp = null;
        startDiscovery();
    }

    public void sendHubCommand(String cmd) {
        if (directSocket != null && isConnected) {
            new Thread(() -> {
                try {
                    directSocket.send(cmd);
                } catch (Exception ignored) {
                }
            }).start();
        } else if (relaySocket != null && relaySocket.connected()) {
            try {
                relaySocket.emit("app_command", new JSONObject().put("command", cmd));
            } catch (Exception e) {
                Log.e(TAG, "Relay command failed", e);
            }
        }

        if (cmd.startsWith("CMD:START_SESSION:")) {
            try {
                activeSessionId = Integer.parseInt(cmd.substring(18));
                updateNotification("Session Active (ID: " + activeSessionId + ")");
            } catch (Exception ignored) {
            }
        } else if (cmd.equals("CMD:STOP")) {
            activeSessionId = -1;
            updateNotification("Monitoring Standby (Ready)");
        }
    }

    private void updateNotification(String content) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(1, createNotification(content));
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (discoveryThread != null) {
            discoveryThread.interrupt();
        }
        if (relaySocket != null) {
            relaySocket.disconnect();
        }
        if (directSocket != null) {
            directSocket.close(1000, "Service Destroyed");
        }
        super.onDestroy();
    }
}
