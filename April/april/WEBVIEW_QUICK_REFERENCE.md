# Android WebView Dashboard - Quick Developer Reference

**Implementation Date:** April 26, 2026  
**Status:** ✅ READY FOR DEPLOYMENT

---

## WHAT WAS ADDED

### 3 New Java/XML Files Created:
1. ✅ `DashboardWebViewActivity.java` - WebView container
2. ✅ `activity_dashboard_webview.xml` - Layout
3. ✅ `side_tabs.xml` - Menu item added (updated)

### 2 Existing Files Modified:
1. ✅ `AndroidManifest.xml` - Added Activity
2. ✅ `MainActivity.java` - Added navigation

---

## FILE LOCATIONS

```
AndroidStudioProjects/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/myapplication/
│   │   │   ├── DashboardWebViewActivity.java          [NEW]
│   │   │   └── MainActivity.java                       [MODIFIED]
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   └── activity_dashboard_webview.xml     [NEW]
│   │   │   └── menu/
│   │   │       └── side_tabs.xml                       [MODIFIED]
│   │   └── AndroidManifest.xml                         [MODIFIED]
```

---

## CONFIGURATION NEEDED

### CRITICAL: Set Backend IP Address

**File:** `DashboardWebViewActivity.java` - Line 36

**Current:**
```java
private static final String BACKEND_URL = "http://192.168.1.1:3000";
```

**For Local Testing (Emulator):**
```java
private static final String BACKEND_URL = "http://10.0.2.2:3001";
```

**For Local Testing (Device on LAN):**
```java
private static final String BACKEND_URL = "http://192.168.1.X:3001";  // Replace X with your IP
```

**For Production:**
```java
private static final String BACKEND_URL = "https://yourdomain.com";
```

---

## BUILD & DEPLOY

### Build APK

```powershell
# Navigate to app directory
cd "c:\xampp\htdocs\projects systems\April\april\AndroidStudioProjects"

# Build debug APK
.\gradlew build

# Or use Android Studio:
# Build → Build Bundles/APK → Build APK(s)
```

### Install on Device

```powershell
# Via adb
adb install -r build\outputs\apk\debug\app-debug.apk

# Or via Android Studio:
# Run → Run 'app'
```

---

## TESTING FLOW

1. **Login** with test account
2. **Tap Menu** (hamburger icon)
3. **Select** "Equipment Dashboard"
4. **Verify** Dashboard loads
5. **Test Commands:**
   - Click "Extend" on Arm Motor
   - Click "Retract" to pull back
   - Click "Stop" to halt
   - Same for Leg Motor
   - Toggle Glove ON/OFF
6. **Emergency Stop** - Should halt all
7. **History** - Should show recent commands

---

## PERMISSIONS

✅ Already added to `AndroidManifest.xml`:
- `android.permission.INTERNET` - Required for WebView content loading
- All network permissions already present

---

## WHAT HAPPENS WHEN USER TAPS "Equipment Dashboard"

1. **Menu Item Click** → `side_tabs.xml` item `snv_dashboard_web` triggers
2. **MainActivity Navigation** → Routes to `DashboardWebViewActivity`
3. **Activity Launches** → `DashboardWebViewActivity.onCreate()` called
4. **Auth Token Retrieved** → From SharedPreferences (LoginActivity.PREFS_NAME)
5. **WebView Configured** → JavaScript enabled, settings applied
6. **Dashboard Loads** → URL: `http://192.168.1.1:3000/dashboard?token=YOUR_TOKEN`
7. **Dashboard Renders** → User sees:
   - Vital signs (heart rate, BP, temp, O₂)
   - Motor controls (ARM, LEG, GLOVE)
   - Emergency stop
   - Command history

---

## API CALLS MADE BY DASHBOARD

From JavaScript in the browser:

### 1. Get Status
```javascript
GET /api/relay/status
Headers: Authorization: Bearer {token}
```

### 2. Send Command
```javascript
POST /api/relay/command
Body: { "command": "ARM:EXT" }
Headers: Authorization: Bearer {token}
```

### 3. Get History
```javascript
GET /api/relay/history?limit=10
Headers: Authorization: Bearer {token}
```

### 4. Emergency Stop
```javascript
POST /api/relay/emergency-stop
Headers: Authorization: Bearer {token}
```

---

## DEBUGGING

### Check WebView Loading
```java
// In DashboardWebViewActivity
// Enable logging:
WebView.setWebContentsDebuggingEnabled(true);  // In onCreate
```

### Monitor Network Calls
```powershell
# Check if backend is accessible
curl http://192.168.1.1:3001/api/relay/status

# Or from device/emulator:
adb shell curl http://10.0.2.2:3001/api/relay/status
```

### Logcat Commands
```powershell
# View all WebView logs
adb logcat | findstr WebView

# View app-specific logs
adb logcat | findstr myapplication

# View authentication errors
adb logcat | findstr Authorization
```

---

## KNOWN LIMITATIONS & SOLUTIONS

| Issue | Solution |
|-------|----------|
| Dashboard blank | Check backend IP in code |
| Commands not working | Verify token is valid/not expired |
| WebView crashes | Check cleartext traffic allowed in API 28+ |
| Auth fails | Ensure token returned from login endpoint |

---

## PRODUCTION CHECKLIST

Before releasing to App Store/Play Store:

- [ ] Backend IP changed to production domain
- [ ] HTTPS enforced (remove `usesCleartextTraffic`)
- [ ] Error handling improved
- [ ] Logging disabled (remove WebView.setWebContentsDebuggingEnabled(true))
- [ ] User feedback messages polished
- [ ] Performance tested on low-end devices
- [ ] Network timeout handling added
- [ ] Token refresh logic implemented

---

## QUICK COMMAND REFERENCE

```
Available Relay Commands:
├── ARM:EXT      - Extend arm motor
├── ARM:RET      - Retract arm motor
├── ARM:STOP     - Stop arm motor
├── LEG:EXT      - Extend leg motor
├── LEG:RET      - Retract leg motor
├── LEG:STOP     - Stop leg motor
├── GLOVE:ON     - Activate glove relay
└── GLOVE:OFF    - Deactivate glove relay

Special:
└── EMERGENCY_STOP - Stop ALL equipment immediately
```

---

## SUPPORT CONTACTS

- **Backend Issues:** Check Node.js server logs
- **Database Issues:** Check MySQL error logs
- **Hardware Issues:** Check Arduino serial output
- **WebView Issues:** Check Android Logcat

---

**Last Updated:** April 26, 2026  
**Next Review:** After first production deployment
