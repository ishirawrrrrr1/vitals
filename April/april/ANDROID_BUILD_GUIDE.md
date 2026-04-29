# 🔨 ANDROID APP BUILD GUIDE - WEBVIEW INTEGRATION

**Date:** April 26, 2026  
**Status:** Code Ready, Awaiting Build Environment

---

## ⚠️ CURRENT SITUATION

### Build Environment Issue
- ✅ Android SDK installed: `C:\Users\raiji\AppData\Local\Android\Sdk`
- ❌ Java JDK NOT installed on system
- ❌ Cannot execute gradle build without Java

### What This Means
- All source code is complete ✅
- All modifications are in place ✅
- APK exists from previous build (April 24, 2026)
- New WebView Activity code is NOT in current APK

---

## 📦 SOLUTION OPTIONS

### Option 1: Install Java JDK (Recommended - 10 minutes)

**Step 1: Download Java JDK 17**
```
Visit: https://www.oracle.com/java/technologies/downloads/
Download: JDK 17 (LTS) - Windows x64 Installer
Size: ~180 MB
```

**Step 2: Install Java**
```
Run the installer
Choose: C:\Program Files\Java\jdk-17.0.x
Check: "Set JAVA_HOME variable"
Continue installation
```

**Step 3: Verify Installation**
```powershell
java -version
javac -version
```

**Step 4: Build APK**
```powershell
cd "c:\xampp\htdocs\projects systems\April\april\AndroidStudioProjects"
.\gradlew.bat clean
.\gradlew.bat build
```

**Time Required:** ~15 minutes  
**Result:** New APK with WebView Dashboard

---

### Option 2: Use Android Studio to Build (2 minutes)

If Android Studio is already installed:

**Step 1: Open Android Studio**
```
File → Open → c:\xampp\htdocs\projects systems\April\april\AndroidStudioProjects
```

**Step 2: Build**
```
Build → Clean Project
Build → Build Bundles/APK → Build APK(s)
```

**Step 3: Locate APK**
```
app/build/outputs/apk/debug/app-debug.apk
```

**Time Required:** ~5 minutes  
**Result:** New APK with all changes

---

### Option 3: Use Pre-built APK (Testing Only)

The existing APK (`app-debug.apk` from April 24) can be used for testing but:
- ❌ Does NOT have new WebView Dashboard Activity
- ❌ Does NOT have menu item for Equipment Dashboard
- ✅ Can test existing functionality

**Located at:**
```
c:\xampp\htdocs\projects systems\April\april\AndroidStudioProjects\app\build\outputs\apk\debug\app-debug.apk
```

---

## 📋 WHAT'S READY TO BUILD

### New Code Files (Added Today)
```
✅ DashboardWebViewActivity.java
   Location: app/src/main/java/com/example/myapplication/
   Status: Complete, ready to include in build
   
✅ activity_dashboard_webview.xml
   Location: app/src/main/res/layout/
   Status: Complete, ready to include in build
```

### Modified Files (Updated Today)
```
✅ AndroidManifest.xml
   Status: Activity declaration added
   
✅ side_tabs.xml
   Status: Menu item "Equipment Dashboard" added
   
✅ MainActivity.java
   Status: Navigation case added
```

### Configuration
```
✅ Backend IP: Set in DashboardWebViewActivity.java line 36
   Current: http://192.168.1.1:3000
   Change to: Your actual backend IP
   Example: http://192.168.1.100:3001
```

---

## 🎯 BUILD CHECKLIST

### Before Building

- [ ] Decide which build method to use (Options 1, 2, or 3)
- [ ] If Option 1: Install Java JDK 17
- [ ] If Option 2: Open project in Android Studio
- [ ] Review DashboardWebViewActivity.java
- [ ] Verify backend IP is correct (line 36)

### During Build

- [ ] Run clean command
- [ ] Run build command
- [ ] Wait for build to complete (2-5 minutes)
- [ ] Check for errors (should be zero)

### After Build

- [ ] Locate APK file
- [ ] Install on device: `adb install -r app-debug.apk`
- [ ] Test app launch
- [ ] Test menu navigation
- [ ] Tap "Equipment Dashboard"
- [ ] Verify WebView loads

---

## 🔍 DETAILED BUILD INSTRUCTIONS

### OPTION 1: Command Line Build (Recommended)

**Step 1: Install Java JDK**

```powershell
# Download from: https://www.oracle.com/java/technologies/downloads/
# Install to: C:\Program Files\Java\jdk-17
# Add to system PATH
```

**Step 2: Set JAVA_HOME Environment Variable**

```powershell
# Add to system environment variables:
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"

# Verify:
echo $env:JAVA_HOME
java -version
```

**Step 3: Navigate to Project**

```powershell
cd "c:\xampp\htdocs\projects systems\April\april\AndroidStudioProjects"
```

**Step 4: Clean Previous Build**

```powershell
.\gradlew.bat clean
```

Expected output:
```
...
BUILD SUCCESSFUL in 10s
```

**Step 5: Build APK**

```powershell
.\gradlew.bat build
```

Expected output:
```
...
BUILD SUCCESSFUL in 2m 30s
127 actionable tasks: 15 executed, 112 up-to-date
```

**Step 6: Locate APK**

```powershell
Get-ChildItem -Path "app\build\outputs\apk\debug\app-debug.apk"
```

Output:
```
app-debug.apk (size ~7.3 MB)
```

---

### OPTION 2: Android Studio Build

**Step 1: Open Project**

```
Launch Android Studio
File → Open
Select: c:\xampp\htdocs\projects systems\April\april\AndroidStudioProjects
```

**Step 2: Clean Project**

```
Build → Clean Project
(Wait for "Build finished")
```

**Step 3: Build**

```
Build → Build Bundles/APK → Build APK(s)
(Wait for build to complete - usually 2-5 minutes)
```

**Step 4: Locate APK**

```
Bottom right: "Build successful"
Click: "locate" link
Or navigate to: app/build/outputs/apk/debug/app-debug.apk
```

---

### OPTION 3: Using build_me.bat Script

There's a batch script that might help:

```powershell
cd "c:\xampp\htdocs\projects systems\April\april\AndroidStudioProjects"
.\build_me.bat
```

(Note: This will only work if Java JDK is installed)

---

## 📲 INSTALLING THE APK

### Via adb (Recommended)

```powershell
# Ensure device is connected via USB
# Enable USB debugging on device

# Install APK
adb install -r "c:\xampp\htdocs\projects systems\April\april\AndroidStudioProjects\app\build\outputs\apk\debug\app-debug.apk"

# Result:
# Success
```

### Via File Transfer

1. Copy APK to device via USB
2. Tap APK file on device
3. Accept permissions
4. Install

### Via Android Studio

1. Build in Android Studio
2. Click "Run" or press Shift+F10
3. Select device/emulator
4. App auto-installs and launches

---

## ✅ POST-BUILD TESTING

### Test 1: App Launches
```
1. Open app on device
2. See login screen
3. Enter credentials
4. Tap Login
5. See main dashboard
Status: ✅ PASS if dashboard appears
```

### Test 2: Menu Item Appears
```
1. Tap menu icon (≡) top-left
2. Look for new item
3. Should see: "Equipment Dashboard"
   Between: "Monitoring" and "Progress"
Status: ✅ PASS if item visible
```

### Test 3: Dashboard Loads
```
1. Tap "Equipment Dashboard"
2. Loading progress bar appears
3. Wait 2-3 seconds
4. Dashboard loads with:
   - Toolbar with back button
   - Vital signs display
   - Motor/relay controls
   - Emergency stop button
Status: ✅ PASS if dashboard appears
```

### Test 4: Controls Work
```
1. Tap "ARM" Extend button
2. See feedback/status update
3. Tap other buttons
4. Verify responses
Status: ✅ PASS if buttons respond
```

---

## 🐛 TROUBLESHOOTING BUILD ERRORS

### Error: "JAVA_HOME is not set"
```
Solution: 
  1. Install Java JDK 17
  2. Set JAVA_HOME environment variable
  3. Restart terminal/command prompt
  4. Try build again
```

### Error: "gradlew command not found"
```
Solution:
  1. Ensure you're in correct directory:
     c:\xampp\htdocs\projects systems\April\april\AndroidStudioProjects
  2. File should exist: gradlew.bat
  3. Use: .\gradlew.bat (not just gradlew)
```

### Error: "Gradle sync failed"
```
Solution:
  1. Delete .gradle folder
  2. Run: .\gradlew.bat clean
  3. Wait for download
  4. Try build again
```

### Error: "Java version mismatch"
```
Solution:
  1. Check Java version: java -version
  2. Should be 11+
  3. If older, upgrade to JDK 17
  4. Update JAVA_HOME variable
```

### Build Hangs
```
Solution:
  1. Press Ctrl+C to cancel
  2. Delete: .gradle folder
  3. Run: .\gradlew.bat --stop
  4. Try build again
```

---

## 📊 BUILD TIME EXPECTATIONS

| Step | Time | Notes |
|------|------|-------|
| First gradle sync | 2-5 min | Downloads dependencies |
| Clean | 30 sec | Removes old build |
| Build | 2-3 min | Compiles code |
| **Total** | **5-10 min** | Subsequent builds faster |

---

## 🎯 VERIFICATION CHECKLIST

### Code Files Exist
- [ ] `DashboardWebViewActivity.java` exists
- [ ] `activity_dashboard_webview.xml` exists
- [ ] `AndroidManifest.xml` modified
- [ ] `side_tabs.xml` modified
- [ ] `MainActivity.java` modified

### Configuration Correct
- [ ] Backend IP set in DashboardWebViewActivity.java
- [ ] No hardcoded test values
- [ ] All imports present
- [ ] No syntax errors visible

### Build Ready
- [ ] Java JDK installed (if using command line)
- [ ] Android SDK installed
- [ ] Project opens in Android Studio
- [ ] No missing dependencies

### Build Successful
- [ ] `gradlew build` completes without errors
- [ ] APK generated: `app-debug.apk`
- [ ] APK size: ~7.3 MB
- [ ] APK can be installed

---

## 📞 NEXT STEPS

### Immediate (Today)
1. Choose build method (Option 1, 2, or 3)
2. Execute build steps
3. Verify APK is created
4. Install on test device

### Short Term (This Week)
1. Test all functionality
2. Document test results
3. Fix any issues
4. Prepare for production

### Medium Term (This Month)
1. Deploy to production backend
2. Release on Play Store
3. Monitor usage

---

## 🔗 RELATED FILES

**Documentation:**
- `START_HERE.md` - Navigation guide
- `WEBVIEW_QUICK_REFERENCE.md` - Developer guide
- `DEPLOYMENT_GUIDE_WEBVIEW.md` - Testing procedures

**Source Code:**
- `DashboardWebViewActivity.java` - WebView Activity
- `activity_dashboard_webview.xml` - Layout
- `MainActivity.java` - Navigation
- `AndroidManifest.xml` - Activity declaration

**Build Scripts:**
- `gradlew.bat` - Build wrapper
- `build_me.bat` - Custom build script
- `gradle.properties` - Configuration

---

## ✨ SUCCESS INDICATORS

### Build Complete When You See:
```
BUILD SUCCESSFUL in 2m 30s
127 actionable tasks: 15 executed, 112 up-to-date
```

### APK Ready When:
```
File: app\build\outputs\apk\debug\app-debug.apk
Size: ~7.3 MB
Modified: Today's date
```

### Installation Successful When:
```
adb install -r app-debug.apk
Success
```

### App Works When:
```
1. App launches
2. Menu shows "Equipment Dashboard"
3. Dashboard opens
4. Controls respond
```

---

**STATUS: READY TO BUILD**

**Next Action: Install Java JDK, then run build command**

---

## 💡 QUICK REFERENCE

```powershell
# If Java is installed, use these commands:

# Navigate to project
cd "c:\xampp\htdocs\projects systems\April\april\AndroidStudioProjects"

# Clean old build
.\gradlew.bat clean

# Build APK
.\gradlew.bat build

# Check if successful
Get-ChildItem -Path "app\build\outputs\apk\debug\app-debug.apk"

# Install on device
adb install -r "app\build\outputs\apk\debug\app-debug.apk"
```

---

**For detailed help, see the appropriate section above or read START_HERE.md**
