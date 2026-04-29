package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.google.android.material.button.MaterialButton;

public class HubPortalActivity extends AppCompatActivity {

    private WebView webView;
    private View errorView;
    private View progressIndicator;
    private final String PORTAL_URL = "http://192.168.4.1";
    
    private com.google.android.material.textfield.TextInputEditText etHubIp;
    private android.content.SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        androidx.activity.EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hub_portal);

        prefs = getSharedPreferences(LoginActivity.PREFS_NAME, MODE_PRIVATE);

        // Standard padding fix
        View decorView = getWindow().getDecorView();
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(decorView, (v, insets) -> {
            androidx.core.graphics.Insets systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Toolbar toolbar = findViewById(R.id.toolbarPortal);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
            toolbar.setNavigationOnClickListener(v -> onBackPressed());
        }

        // Hub IP Configuration
        etHubIp = findViewById(R.id.etHubIp);
        MaterialButton btnSaveHubIp = findViewById(R.id.btnSaveHubIp);
        
        String currentHubIp = prefs.getString("hub_ip", "192.168.1.5");
        if (etHubIp != null) etHubIp.setText(currentHubIp);

        if (btnSaveHubIp != null) {
            btnSaveHubIp.setOnClickListener(v -> {
                String newIp = etHubIp.getText().toString().trim();
                if (!newIp.isEmpty()) {
                    prefs.edit().putString("hub_ip", newIp).apply();
                    android.widget.Toast.makeText(this, "Device IP Saved: " + newIp, android.widget.Toast.LENGTH_SHORT).show();
                    // Restart service to connect to new IP
                    startService(new Intent(this, VitalsMonitoringService.class));
                }
            });
        }

        webView = findViewById(R.id.webViewPortal);
        errorView = findViewById(R.id.errorView);
        progressIndicator = findViewById(R.id.portalProgress);
        MaterialButton btnRetry = findViewById(R.id.btnRetry);

        if (webView != null) {
            webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            WebSettings webSettings = webView.getSettings();
            webSettings.setJavaScriptEnabled(true);
            webSettings.setUseWideViewPort(true);
            webSettings.setLoadWithOverviewMode(true);

            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                    if (progressIndicator != null) progressIndicator.setVisibility(View.VISIBLE);
                    if (errorView != null) errorView.setVisibility(View.GONE);
                    if (webView != null) webView.setVisibility(View.VISIBLE);
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    if (progressIndicator != null) progressIndicator.setVisibility(View.GONE);
                }

                @Override
                public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                    if (request.isForMainFrame()) {
                        if (progressIndicator != null) progressIndicator.setVisibility(View.GONE);
                        if (webView != null) webView.setVisibility(View.GONE);
                        if (errorView != null) errorView.setVisibility(View.VISIBLE);
                    }
                }
            });
        }

        if (btnRetry != null) {
            btnRetry.setOnClickListener(v -> loadPortal());
        }

        loadPortal();
    }

    private void loadPortal() {
        if (webView != null) {
            webView.loadUrl(PORTAL_URL);
        }
    }
}
