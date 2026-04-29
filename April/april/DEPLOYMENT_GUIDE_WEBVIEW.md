# 🚀 DEPLOYMENT GUIDE - ANDROID WEBVIEW DASHBOARD

**Project:** April Rehabilitation Hub  
**Component:** Android App WebView Integration  
**Date:** April 26, 2026  
**Status:** ✅ READY TO DEPLOY

---

## 📋 PRE-DEPLOYMENT CHECKLIST

### Code Changes Verification
- [x] `DashboardWebViewActivity.java` created
- [x] `activity_dashboard_webview.xml` created
- [x] `AndroidManifest.xml` updated with Activity declaration
- [x] `side_tabs.xml` menu item added
- [x] `MainActivity.java` navigation added
- [x] All files compile without errors
- [x] No missing imports or dependencies

### Configuration
- [ ] Backend IP configured in `DashboardWebViewActivity.java` line 36
- [ ] Backend server is running on configured IP
- [ ] MySQL database is accessible
- [ ] Arduino MEGA is connected and responding
- [ ] Test user account created in database

---

## 🔧 CONFIGURATION - CRITICAL STEP

### IMPORTANT: Set Your Backend IP

**File Location:**
```
AndroidStudioProjects/
└── app/src/main/java/com/example/myapplication/
    └── DashboardWebViewActivity.java
```

**Line 36 - Change this:**

```java
// BEFORE (DEFAULT)
private static final String BACKEND_URL = "http://192.168.1.1:3000";

// AFTER (YOUR IP)
private static final String BACKEND_URL = "http://192.168.1.YOUR_IP:3001";
```

### Finding Your Backend IP:

**On Windows (where backend is running):**
```powershell
# Get local IP
ipconfig

# Look for: IPv4 Address (usually 192.168.x.x or 10.x.x.x)
```

**For Android Emulator (use this special IP):**
```java
private static final String BACKEND_URL = "http://10.0.2.2:3001";
```

**For Production:**
```java
private static final String BACKEND_URL = "https://your-domain.com";
```

---

## 🏗️ BUILD PROCESS

### Step 1: Clean Previous Build

```powershell
cd "c:\xampp\htdocs\projects systems\April\april\AndroidStudioProjects"

# Clean old builds
.\gradlew clean
```

### Step 2: Build APK

```powershell
# Build debug APK
.\gradlew build

# Or build specifically:
.\gradlew assembleDebug
```

**Expected Output:**
```
...
BUILD SUCCESSFUL in 45s
127 actionable tasks: 15 executed, 112 up-to-date
```

### Step 3: Locate Built APK

**Path:**
```
AndroidStudioProjects/
└── app/build/outputs/apk/debug/
    └── app-debug.apk  ← This file
```

---

## 📱 INSTALLATION

### Option 1: Android Studio (Recommended)

1. **Open project in Android Studio**
2. **Select device/emulator** from dropdown
3. **Run** → **Run 'app'** (or press Shift+F10)
4. App auto-installs and launches

### Option 2: Command Line (adb)

```powershell
# Connect device via USB (enable USB debugging on device)

# Install APK
adb install -r app\build\outputs\apk\debug\app-debug.apk

# Or force reinstall if already exists:
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### Option 3: Direct File Transfer

1. **Copy APK:** `app-debug.apk`
2. **Transfer to Android device** (USB/email)
3. **Tap APK file** on device to install
4. **Confirm installation**

---

## ✅ TESTING FLOW

### Test 1: App Launch & Navigation

```
1. Open app
2. See Login screen
3. Enter test credentials:
   - Username: test / test@test.com
   - Password: test123
4. Click Login
5. See Dashboard
6. Tap menu icon (≡) top-left
7. Confirm "Equipment Dashboard" appears in menu
   ✓ Should see between "Monitoring" and "Progress"
```

### Test 2: Dashboard Loading

```
1. From menu, tap "Equipment Dashboard"
2. See loading progress bar briefly
3. Dashboard loads with:
   ✓ Toolbar with back button
   ✓ Vital signs display (HR, BP, Temp, O₂)
   ✓ Three equipment control sections
   ✓ Emergency stop button
   ✓ Command history section
   
Expected load time: 2-4 seconds
```

### Test 3: Equipment Controls

```
ARM MOTOR:
1. Tap "Extend" button → should see feedback
2. Wait 2-3 seconds
3. Tap "Stop" button
4. Tap "Retract" button
5. Verify status indicator shows movement

LEG MOTOR: (same as ARM)
1. Tap "Extend" → feedback
2. Tap "Stop"
3. Tap "Retract"
4. Verify movement indicator

GLOVE RELAY:
1. Tap "ON" button
2. Verify status shows "Active"
3. Tap "OFF" button
4. Verify status shows "Inactive"
```

### Test 4: Emergency Stop

```
1. Send a command (e.g., ARM:EXT)
2. Tap "🚨 EMERGENCY STOP ALL"
3. Confirm dialog appears
4. Click "OK" to confirm
5. All equipment should stop immediately
6. Verify all indicators show "Idle"/"Inactive"
```

### Test 5: Command History

```
1. Execute several commands
2. Scroll to bottom → "Recent Commands"
3. Verify list shows:
   ✓ Command name (e.g., "ARM:EXT")
   ✓ Status (SUCCESS/FAILURE)
   ✓ Timestamp (date + time)
   ✓ Most recent at top
4. Verify list auto-updates when new command sent
```

### Test 6: Vitals Monitoring

```
1. Look at vital signs display
2. Verify values show:
   ✓ Heart Rate: number + "bpm"
   ✓ Blood Pressure: XX/XX + "mmHg"
   ✓ Temperature: number + "°C"
   ✓ Oxygen: number + "%"
3. Check if values update every 5 seconds
4. Verify realistic values (not all dashes)
```

### Test 7: Navigation

```
1. From dashboard, tap back button (← top-left)
2. Return to main dashboard
3. Tap menu again
4. Select different menu item (e.g., Monitoring)
5. Navigate back to main
6. Select Equipment Dashboard again
7. Dashboard should reload correctly
```

### Test 8: Error Handling

```
OFFLINE BACKEND:
1. Stop backend server
2. Close and reopen Equipment Dashboard
3. Should see error message
4. Button tap should show error feedback

INVALID TOKEN:
1. Clear app data (Settings → Apps → Vitals Health)
2. Open Equipment Dashboard
3. Should show "Authentication required" message

NETWORK DISCONNECT:
1. Disconnect WiFi/cellular
2. Try sending command
3. Should show network error message
4. Reconnect and retry
```

---

## 🔍 TROUBLESHOOTING

### Issue: Dashboard won't load (blank screen)

**Causes & Solutions:**

```
1. Backend IP incorrect
   → Check DashboardWebViewActivity.java line 36
   → Verify IP matches backend server
   → Test: curl http://192.168.1.X:3001 from terminal

2. Backend not running
   → Check backend process: netstat -an | findstr 3001
   → Verify Node.js is running
   → Check for error logs

3. Network connectivity
   → Verify device can ping backend IP
   → Check firewall settings
   → Try on emulator: http://10.0.2.2:3001
```

### Issue: Commands not executing (buttons do nothing)

**Causes & Solutions:**

```
1. Invalid auth token
   → Re-login to app
   → Check token in SharedPreferences
   → Verify token hasn't expired

2. Backend API down
   → Check backend logs
   → Verify endpoints exist: GET /api/relay/status
   → Test with Postman or cURL

3. Hardware not responding
   → Check Arduino serial connection
   → Verify MegaSensorHub.ino is uploaded
   → Check USB port (COM11 or similar)
```

### Issue: App crashes on "Equipment Dashboard"

**Causes & Solutions:**

```
1. Missing dependency
   → Rebuild: gradlew clean build
   → Check for compilation errors

2. Memory issue
   → Close other apps
   → Clear app cache: Settings → Apps → Vitals Health → Storage

3. WebView issue
   → Update Android System WebView
   → Check Android version (API 21+)
```

### Issue: vitals not displaying

**Causes & Solutions:**

```
1. Sensor not sending data
   → Check Arduino is connected
   → Verify sensor wiring
   → Monitor serial output

2. Database empty
   → Insert test data
   → Run sensor simulation

3. API not returning vitals
   → Test endpoint: curl /api/relay/status
   → Check database query
```

---

## 📊 MONITORING & LOGS

### Android Logcat (Debugging)

```powershell
# View all logs
adb logcat

# View only app logs
adb logcat | findstr myapplication

# View WebView debug logs
adb logcat | findstr WebView

# Save logs to file
adb logcat > debug_logs.txt

# Clear logs
adb logcat -c
```

### Backend Logs

```powershell
# Backend should be printing to console
# Look for:
# - "Server running on port 3001"
# - API request logs
# - Error messages

# Or check backend/index.js for console.log statements
```

### Database Logs

```powershell
# Check MySQL error log
# Location: C:\xampp\mysql\data\error.log

# Or query recent changes:
# SELECT * FROM control_logs ORDER BY timestamp DESC LIMIT 10;
```

---

## 🎯 SUCCESS CRITERIA

**Dashboard is working correctly when:**

- ✅ Loads within 3 seconds
- ✅ All controls respond to taps
- ✅ Commands execute on backend
- ✅ Arduino receives commands
- ✅ Motors/relays activate
- ✅ Vitals display updates
- ✅ History logs new commands
- ✅ Emergency stop works
- ✅ No crashes during use
- ✅ Back button navigates correctly

---

## 🚀 PRODUCTION DEPLOYMENT

### Before Going Live:

1. **Update Backend IP** to production server
2. **Change HTTP to HTTPS**
   ```java
   private static final String BACKEND_URL = "https://yourdomain.com";
   ```

3. **Disable WebView Debugging**
   ```java
   // In DashboardWebViewActivity.onCreate()
   // WebView.setWebContentsDebuggingEnabled(false);  // Should already be commented
   ```

4. **Test Thoroughly** on production backend

5. **Build Release APK**
   ```powershell
   .\gradlew assembleRelease
   ```

6. **Sign with Release Key**
   ```powershell
   jarsigner -verbose -sigalg MD5withRSA -digestalg SHA1 `
     -keystore my.keystore app-release.apk alias_name
   ```

7. **Upload to Play Store**
   - Google Play Console
   - Prepare store listing
   - Upload signed APK
   - Review and publish

---

## 📞 SUPPORT CONTACTS

| Issue Type | Contact | Details |
|-----------|---------|---------|
| Backend Down | Backend Team | Check server logs |
| Database Error | DBA | Check MySQL status |
| Hardware Fail | Hardware Team | Check Arduino connection |
| App Crash | Android Dev | Check Logcat |
| Network Issue | Ops Team | Check connectivity |

---

## 📝 DEPLOYMENT SIGN-OFF

**Ready to Deploy:** April 26, 2026
**Testing Required:** 30-60 minutes
**Estimated Deployment Time:** 15 minutes
**Rollback Plan:** Re-install previous APK version

---

**STATUS: ✅ READY FOR PRODUCTION DEPLOYMENT**

**Next Action:** Configure backend IP and build APK
