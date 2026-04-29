package com.example.myapplication;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.myapplication.network.RetrofitClient;

public class DashboardWebViewActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar progressBar;
    private String authToken;
    private String userJson;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard_webview);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Equipment Dashboard");
        }

        SharedPreferences prefs = getSharedPreferences(LoginActivity.PREFS_NAME, MODE_PRIVATE);
        authToken = prefs.getString(LoginActivity.KEY_TOKEN, "");
        String savedIp = prefs.getString(LoginActivity.KEY_SERVER_IP, "");
        userJson = "{\"email\":\"" + prefs.getString(LoginActivity.KEY_EMAIL, "") + "\",\"username\":\"" + prefs.getString(LoginActivity.KEY_USER_NAME, "User") + "\"}";

        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        configureWebView();

        if (savedIp == null || savedIp.trim().isEmpty()) {
            Toast.makeText(this, "No server IP saved. Login again first.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        progressBar.setVisibility(android.view.View.VISIBLE);
        RetrofitClient.discoverPort(getApplicationContext(), savedIp, new RetrofitClient.PortDiscoveryCallback() {
            @Override
            public void onPortFound(String url) {
                runOnUiThread(() -> loadDashboard(url));
            }

            @Override
            public void onDiscoveryFailed() {
                runOnUiThread(() -> {
                    Toast.makeText(DashboardWebViewActivity.this, "Could not reach backend at " + savedIp, Toast.LENGTH_LONG).show();
                    progressBar.setVisibility(android.view.View.GONE);
                });
            }
        });
    }

    private void loadDashboard(String baseUrl) {
        String safeToken = authToken == null ? "" : authToken.replace("\\", "\\\\").replace("'", "\\'");
        String safeUser = userJson == null ? "{}" : userJson.replace("\\", "\\\\").replace("'", "\\'");

        webView.loadUrl(baseUrl + "dashboard.html");
        webView.post(() -> webView.evaluateJavascript(
                "localStorage.setItem('token','" + safeToken + "');" +
                "localStorage.setItem('user','" + safeUser + "');",
                null
        ));
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                progressBar.setVisibility(android.view.View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(android.view.View.GONE);
            }

            @Override
            public void onReceivedError(WebView view, android.webkit.WebResourceRequest request,
                                        android.webkit.WebResourceError error) {
                Toast.makeText(DashboardWebViewActivity.this,
                        "Error loading dashboard: " + error.getDescription(),
                        Toast.LENGTH_SHORT).show();
                progressBar.setVisibility(android.view.View.GONE);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                if (newProgress == 100) {
                    progressBar.setVisibility(android.view.View.GONE);
                } else {
                    progressBar.setVisibility(android.view.View.VISIBLE);
                }
            }
        });

        webView.setOnKeyListener((v, keyCode, event) -> {
            if ((keyCode == android.view.KeyEvent.KEYCODE_BACK) && webView.canGoBack()) {
                webView.goBack();
                return true;
            }
            return false;
        });
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
