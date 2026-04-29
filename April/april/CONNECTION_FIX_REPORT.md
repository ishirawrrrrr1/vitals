# Connection Diagnostics & Fixes

I have identified and resolved the following issues preventing the Android app from connecting to the backend:

### 1. Backend IP Mismatch (RESOLVED)
The Android application was hardcoded to look for the backend at `192.168.1.6`, but your computer's current IP address on the Wi-Fi network is `192.168.1.4`. 
- **Action Taken**: I updated the default IP in `RetrofitClient.java` and `LoginActivity.java` to `192.168.1.4`.
- **User Instruction**: If your IP changes again, please enter the correct IP in the **Server IP** field on the Login screen of the app.

### 2. Windows Firewall Block (RESOLVED)
The Windows Firewall did not have an exception for port **3000**, which blocked incoming connections from the Android app even if the IP was correct.
- **Action Taken**: I ran the `open_firewall.ps1` script which successfully created a rule to allow TCP traffic on port 3000.

### 3. Port Conflict (Zombie Processes)
I noticed "Port already in use" errors in your `backend_check.log`. This happens if the backend is opened multiple times or doesn't close properly.
- **Recommendation**: Always use `run_backend.bat` to start the system, as it automatically kills any "zombie" processes stuck on port 3000 before starting a fresh instance.

### 4. Hub Connectivity
The app is also configured to look for the ESP32 Hub at `192.168.1.5`. 
- If the "Hub Offline" warning persists in the app, ensure your ESP32 is powered on and connected to the same Wi-Fi.
- You can use the **Scan for Hub** button in the Monitoring screen to re-discover it.

---
**Status**: The backend is currently running on **192.168.1.4:3000** and the firewall is open. You should now be able to log in and connect from the Android app.
