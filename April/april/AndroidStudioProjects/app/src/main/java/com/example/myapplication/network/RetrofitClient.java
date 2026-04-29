package com.example.myapplication.network;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.myapplication.LoginActivity;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {
    private static Retrofit retrofit = null;
    private static String BASE_URL = "https://vitals-production-e304.up.railway.app/";
    private static Context appContext;

    public static String getBaseUrl() {
        return BASE_URL;
    }

    public static void init(Context context, String url) {
        BASE_URL = normalizeBaseUrl(url);
        retrofit = null;
        if (context != null) {
            appContext = context.getApplicationContext();
        }
    }
    public static String normalizeBaseUrl(String target) {
        if (target == null) {
            return BASE_URL;
        }

        String normalized = target.trim();
        if (normalized.isEmpty()) {
            return BASE_URL;
        }

        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "http://" + normalized;
        }

        if (!normalized.endsWith("/")) {
            normalized += "/";
        }

        return normalized;
    }

    public static boolean isFullUrl(String target) {
        return target != null && (target.startsWith("http://") || target.startsWith("https://"));
    }

    public static void discoverPort(final Context context, final String target, final PortDiscoveryCallback callback) {
        if (context != null && appContext == null) {
            appContext = context.getApplicationContext();
        }

        if (isFullUrl(target)) {
            final String baseUrl = normalizeBaseUrl(target);
            final OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .build();

            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Request request = new Request.Builder().url(baseUrl + "api/health").build();
                        Response response = client.newCall(request).execute();
                        if (response.isSuccessful() || response.code() == 401 || response.code() == 403) {
                            new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                                @Override
                                public void run() {
                                    BASE_URL = baseUrl;
                                    retrofit = null;
                                    callback.onPortFound(baseUrl);
                                }
                            });
                            return;
                        }
                    } catch (Exception e) {
                        // Fall through to discovery failure.
                    }

                    new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onDiscoveryFailed();
                        }
                    });
                }
            }).start();
            return;
        }

        final String ip = target.replace("http://", "").replace("https://", "").split(":")[0];
        final int[] ports = {3000, 3001, 3002, 3003, 3004, 3005};
        final OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(1, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(1, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int port : ports) {
                    try {
                        String testUrl = "http://" + ip + ":" + port + "/api/admin/stats";
                        Request request = new Request.Builder().url(testUrl).build();
                        Response response = client.newCall(request).execute();
                        if (response.isSuccessful() || response.code() == 401 || response.code() == 403) {
                            final String foundUrl = "http://" + ip + ":" + port + "/";
                            new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                                @Override
                                public void run() {
                                    BASE_URL = foundUrl;
                                    retrofit = null;
                                    callback.onPortFound(foundUrl);
                                }
                            });
                            return;
                        }
                    } catch (Exception e) {
                        // Continue to next port
                    }
                }
                new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onDiscoveryFailed();
                    }
                });
            }
        }).start();
    }

    public interface PortDiscoveryCallback {
        void onPortFound(String url);
        void onDiscoveryFailed();
    }

    public static Retrofit getClient(Context context) {
        if (context != null && appContext == null) {
            appContext = context.getApplicationContext();
        }

        if (retrofit == null) {
            // If BASE_URL still has a generic placeholder, try to load from prefs
            if ((BASE_URL.contains("192.168.1.6") || BASE_URL.endsWith(":3000/")) && appContext != null) {
                SharedPreferences prefs = appContext.getSharedPreferences(LoginActivity.PREFS_NAME, Context.MODE_PRIVATE);
                String savedTarget = prefs.getString(LoginActivity.KEY_SERVER_IP, null);
                if (savedTarget != null && !savedTarget.isEmpty()) {
                    // Note: We don't force :3000 here if discovery already set a different port
                    if (isFullUrl(savedTarget)) {
                        BASE_URL = normalizeBaseUrl(savedTarget);
                    } else if (BASE_URL.contains("192.168.1.6")) {
                        BASE_URL = "http://" + savedTarget + ":3000/";
                    }
                }
            }

            OkHttpClient.Builder httpClient = new OkHttpClient.Builder();
            
            httpClient.addInterceptor(new Interceptor() {
                @Override
                public Response intercept(Chain chain) throws IOException {
                    Request original = chain.request();
                    Request.Builder requestBuilder = original.newBuilder()
                            .header("Accept", "application/json");
                            
                    if (appContext != null) {
                        SharedPreferences prefs = appContext.getSharedPreferences(LoginActivity.PREFS_NAME, Context.MODE_PRIVATE);
                        String token = prefs.getString(LoginActivity.KEY_TOKEN, null);
                        if (token != null && !token.isEmpty()) {
                            requestBuilder.header("Authorization", "Bearer " + token);
                        }
                    }

                    Request request = requestBuilder.build();
                    return chain.proceed(request);
                }
            });

            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(httpClient.build())
                    .build();
        }
        return retrofit;
    }
}
