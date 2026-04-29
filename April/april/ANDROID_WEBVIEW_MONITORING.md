# 📱 Android App - Open Web Dashboard Feature

**Goal:** Add "Monitoring" button to Android app that opens the backend web dashboard in-app via WebView

---

## 🎯 Implementation Plan

### Option 1: WebView (In-App Browser) - RECOMMENDED
- Opens dashboard inside the Android app
- Uses local IP: `http://192.168.1.4:3001`
- No extra app launch
- User can control motors while viewing app

### Option 2: External Browser (Simple)
- Opens default browser with dashboard URL
- Less control but simpler to implement
- User leaves app to access dashboard

**Recommendation:** Use **Option 1 (WebView)** for better user experience.

---

## 🔧 Implementation: Option 1 - WebView

### **Step 1: Add WebView Permission to AndroidManifest.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Add these permissions -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <!-- ... rest of manifest ... -->
</manifest>
```

---

### **Step 2: Create MonitoringFragment.java**

```java
package com.example.monitoring.ui;

import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.fragment.app.Fragment;
import com.example.monitoring.R;

public class MonitoringFragment extends Fragment {
    
    private WebView webView;
    private ProgressBar loadingSpinner;
    private String backendIP;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_monitoring, container, false);
        
        // Get stored IP from SharedPreferences (from login screen)
        backendIP = getActivity().getSharedPreferences("auth", 0).getString("server_ip", "192.168.1.4");
        
        webView = view.findViewById(R.id.web_view);
        loadingSpinner = view.findViewById(R.id.loading_spinner);
        
        setupWebView();
        loadDashboard();
        
        return view;
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        
        // Enable JavaScript (dashboard needs it)
        settings.setJavaScriptEnabled(true);
        
        // Other important settings
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        
        // Allow camera/mic if needed (optional)
        settings.setMediaPlaybackRequiresUserGesture(false);
        
        // Zoom settings
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        
        // Set user agent to identify as mobile
        settings.setUserAgentString("Android Vitals Monitor App");
        
        // Set custom WebViewClient to handle loading
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                loadingSpinner.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                loadingSpinner.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                loadingSpinner.setVisibility(View.GONE);
                Toast.makeText(getContext(), 
                    "Error loading dashboard: " + description, 
                    Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadDashboard() {
        String dashboardURL = "http://" + backendIP + ":3001";
        
        try {
            webView.loadUrl(dashboardURL);
        } catch (Exception e) {
            Toast.makeText(getContext(), 
                "Cannot connect to: " + dashboardURL, 
                Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Cleanup WebView
        if (webView != null) {
            webView.stopLoading();
            webView.clearHistory();
            webView.clearCache(true);
        }
    }
}
```

---

### **Step 3: Create Layout - fragment_monitoring.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#ffffff">

    <!-- WebView for displaying dashboard -->
    <WebView
        android:id="@+id/web_view"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

    <!-- Loading Spinner -->
    <ProgressBar
        android:id="@+id/loading_spinner"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_centerInParent="true"
        android:visibility="gone"
        style="?android:attr/progressBarStyle" />

    <!-- Error Message (Optional) -->
    <TextView
        android:id="@+id/error_text"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_centerInParent="true"
        android:text="Failed to load dashboard"
        android:textAlignment="center"
        android:textSize="18sp"
        android:visibility="gone"
        android:padding="16dp" />

</RelativeLayout>
```

---

### **Step 4: Add to Navigation Menu**

In your navigation menu file (e.g., `bottom_nav_menu.xml`):

```xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    
    <item
        android:id="@+id/nav_vitals"
        android:icon="@drawable/ic_vitals"
        android:title="Vitals" />
    
    <item
        android:id="@+id/nav_actuators"
        android:icon="@drawable/ic_motor"
        android:title="Actuators" />
    
    <item
        android:id="@+id/nav_monitoring"
        android:icon="@drawable/ic_dashboard"
        android:title="Monitoring" />
    
    <item
        android:id="@+id/nav_history"
        android:icon="@drawable/ic_history"
        android:title="History" />

</menu>
```

---

### **Step 5: Update Activity (DashboardActivity.java)**

In your main activity, handle the navigation:

```java
public class DashboardActivity extends AppCompatActivity {
    
    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);
        
        bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnItemSelectedListener(item -> {
            Fragment fragment = null;
            
            switch (item.getItemId()) {
                case R.id.nav_vitals:
                    fragment = new VitalsFragment();
                    break;
                case R.id.nav_actuators:
                    fragment = new ActuatorControlFragment();
                    break;
                case R.id.nav_monitoring:
                    fragment = new MonitoringFragment();  // NEW!
                    break;
                case R.id.nav_history:
                    fragment = new HistoryFragment();
                    break;
            }
            
            if (fragment != null) {
                getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .commit();
            }
            return true;
        });
        
        // Set default fragment
        if (savedInstanceState == null) {
            bottomNav.setSelectedItemId(R.id.nav_vitals);
        }
    }
}
```

---

## 🔧 Implementation: Option 2 - External Browser (Simple)

If you prefer to open the dashboard in the default browser instead:

```java
package com.example.monitoring.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import androidx.fragment.app.Fragment;
import com.example.monitoring.R;

public class MonitoringFragment extends Fragment {
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_monitoring_simple, container, false);
        
        String backendIP = getActivity().getSharedPreferences("auth", 0)
            .getString("server_ip", "192.168.1.4");
        
        Button openDashboard = view.findViewById(R.id.btn_open_dashboard);
        openDashboard.setOnClickListener(v -> {
            String dashboardURL = "http://" + backendIP + ":3001";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(dashboardURL));
            startActivity(intent);
        });
        
        return view;
    }
}
```

Simple layout for Option 2:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:gravity="center"
    android:padding="16dp">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Live Dashboard"
        android:textSize="24sp"
        android:textStyle="bold"
        android:layout_marginBottom="16dp" />

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Open the web dashboard on your laptop to:\n\n• View real-time vitals\n• Control motors and relay\n• View command history\n• Emergency stop all actuators"
        android:textSize="16sp"
        android:layout_marginBottom="24dp" />

    <Button
        android:id="@+id/btn_open_dashboard"
        android:layout_width="match_parent"
        android:layout_height="56dp"
        android:text="OPEN MONITORING DASHBOARD"
        android:textSize="16sp"
        android:backgroundTint="#2196F3" />

</LinearLayout>
```

---

## 📊 Comparison

| Feature | WebView (Option 1) | External Browser (Option 2) |
|---------|-------------------|------------------------------|
| User Experience | Better (in-app) | Simpler (app opens browser) |
| Complexity | Medium | Low |
| Customization | Full control | None |
| Performance | Slightly slower | Slightly faster |
| Back Button | Custom handling | Native |
| Recommended | ✅ YES | Basic use |

---

## 🔐 Security Considerations

### For WebView:

1. **Never expose sensitive data** in URLs
2. **Use HTTPS** in production (requires SSL certificate)
3. **Validate all inputs** from JavaScript
4. **Disable file access** if not needed:
   ```java
   settings.setAllowFileAccess(false);
   settings.setAllowContentAccess(false);
   ```

### Current Setup:
```java
// Safe for local network (192.168.x.x)
// In production, switch to HTTPS
String dashboardURL = "http://" + backendIP + ":3001";
```

---

## 🚀 Features You Can Add

### 1. **Offline Support** - Cache dashboard
```java
settings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
```

### 2. **Custom Buttons** - Control from app
```java
// Inject JavaScript to click buttons on dashboard
webView.evaluateJavascript("document.getElementById('btn-arm-ext').click();", null);
```

### 3. **Full-Screen Toggle** - Better viewing
```java
webView.setWebChromeClient(new WebChromeClient() {
    @Override
    public void onShowCustomView(View view, CustomViewCallback callback) {
        // Handle fullscreen
    }
});
```

### 4. **Refresh Button** - Pull down to refresh
```java
swipeRefresh.setOnRefreshListener(() -> {
    webView.reload();
    swipeRefresh.setRefreshing(false);
});
```

### 5. **Back Button Override** - WebView history
```java
@Override
public void onBackPressed() {
    if (webView.canGoBack()) {
        webView.goBack();
    } else {
        super.onBackPressed();
    }
}
```

---

## 📱 Step-by-Step Integration

### 1. Add WebView dependency (if needed)
In `build.gradle` (Module: app):
```gradle
dependencies {
    implementation 'androidx.webkit:webkit:1.6.0'  // For latest WebView features
}
```

### 2. Create the three files:
- `MonitoringFragment.java` (Java class)
- `fragment_monitoring.xml` (Layout)
- Update `DashboardActivity.java` (Navigation)

### 3. Add to navigation menu:
- `bottom_nav_menu.xml` or similar

### 4. Update AndroidManifest.xml:
- Add INTERNET permission

### 5. Build and test:
```bash
./gradlew assembleDebug
```

---

## 🧪 Testing

### Test URLs to try:
```
http://192.168.1.4:3001                    # Main dashboard
http://192.168.1.4:3001/admin              # Admin panel
http://192.168.1.4:3001/api/relay/commands # Commands endpoint (JSON)
```

### Expected Behavior:
1. Click "Monitoring" tab
2. WebView loads dashboard
3. Login page appears (or dashboard if cached)
4. Can control motors, see vitals
5. All responsive and interactive

---

## ⚠️ Common Issues & Solutions

| Issue | Cause | Solution |
|-------|-------|----------|
| **Blank white screen** | JavaScript disabled | Enable in WebSettings |
| **"Unable to connect"** | Wrong IP address | Verify server_ip in SharedPreferences |
| **Dashboard not loading** | Backend not running | Start backend with `npm start` |
| **Mixed content errors** | HTTP on HTTPS page | Use `MIXED_CONTENT_ALWAYS_ALLOW` |
| **Buttons not clickable** | Touch events blocked | Enable JavaScript and DOM storage |

---

## 🔄 Enhanced Version - With Refresh & Back Controls

```java
public class MonitoringFragment extends Fragment {
    
    private WebView webView;
    private ProgressBar loadingSpinner;
    private Button backBtn, refreshBtn;
    private String backendIP;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_monitoring_enhanced, container, false);
        
        backendIP = getActivity().getSharedPreferences("auth", 0).getString("server_ip", "192.168.1.4");
        
        webView = view.findViewById(R.id.web_view);
        loadingSpinner = view.findViewById(R.id.loading_spinner);
        backBtn = view.findViewById(R.id.btn_back);
        refreshBtn = view.findViewById(R.id.btn_refresh);
        
        setupWebView();
        setupControls();
        loadDashboard();
        
        return view;
    }

    private void setupControls() {
        backBtn.setOnClickListener(v -> {
            if (webView.canGoBack()) {
                webView.goBack();
            }
        });
        
        refreshBtn.setOnClickListener(v -> {
            webView.reload();
        });
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                loadingSpinner.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                loadingSpinner.setVisibility(View.GONE);
                backBtn.setEnabled(view.canGoBack());
            }
        });
    }

    private void loadDashboard() {
        String dashboardURL = "http://" + backendIP + ":3001";
        webView.loadUrl(dashboardURL);
    }
}
```

Enhanced layout:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <!-- Top controls -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="48dp"
        android:orientation="horizontal"
        android:background="@color/primary"
        android:padding="8dp">
        
        <Button
            android:id="@+id/btn_back"
            android:layout_width="0dp"
            android:layout_height="match_parent"
            android:layout_weight="1"
            android:text="← Back"
            android:enabled="false" />
        
        <Button
            android:id="@+id/btn_refresh"
            android:layout_width="0dp"
            android:layout_height="match_parent"
            android:layout_weight="1"
            android:text="🔄 Refresh" />
    </LinearLayout>

    <!-- WebView -->
    <RelativeLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent">
        
        <WebView
            android:id="@+id/web_view"
            android:layout_width="match_parent"
            android:layout_height="match_parent" />

        <ProgressBar
            android:id="@+id/loading_spinner"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_centerInParent="true"
            android:visibility="gone" />
    </RelativeLayout>

</LinearLayout>
```

---

## ✅ Final Checklist

- [ ] Add WebView permission to AndroidManifest.xml
- [ ] Create MonitoringFragment.java
- [ ] Create fragment_monitoring.xml
- [ ] Update DashboardActivity.java with navigation
- [ ] Update bottom_nav_menu.xml with Monitoring item
- [ ] Test with backend running
- [ ] Verify IP address is correct in SharedPreferences
- [ ] Test all dashboard functions work in WebView
- [ ] Test back button functionality
- [ ] Build APK and test on device

---

## 🚀 Summary

**When user clicks "Monitoring" button:**
1. MonitoringFragment opens
2. WebView loads `http://192.168.1.4:3001` (dashboard)
3. User can see live vitals, control motors, check history
4. All from within the Android app
5. Full control and monitoring from phone!

---

**Status:** ✅ Ready to implement  
**Estimated Time:** 1-2 hours  
**Difficulty:** Medium  
**User Experience:** Excellent
