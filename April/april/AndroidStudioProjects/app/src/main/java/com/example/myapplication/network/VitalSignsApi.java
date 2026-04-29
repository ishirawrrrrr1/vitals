package com.example.myapplication.network;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import com.example.myapplication.LoginActivity.LoginRequest;
import com.example.myapplication.LoginActivity.LoginResponse;
import com.example.myapplication.second.SignupRequest;
import com.example.myapplication.second.SignupResponse;

public interface VitalSignsApi {
    @GET("api/admin/vitals?limit=1")
    Call<List<VitalSign>> getVitals();

    @GET("api/admin/vitals")
    Call<List<VitalSign>> getVitalsByUserId(@retrofit2.http.Query("userId") int userId);

    @POST("api/admin/vitals")
    Call<VitalSign> postVitals(@Body VitalSign vitalSign);

    @POST("api/auth/login")
    Call<LoginResponse> login(@Body LoginRequest request);

    @POST("api/auth/register")
    Call<SignupResponse> signup(@Body SignupRequest request);

    @POST("api/sessions/start")
    Call<SessionStartResponse> startSession(@Body SessionStartRequest request);

    @GET("api/sessions/current/{userId}")
    Call<SessionCurrentResponse> getCurrentSession(@Path("userId") int userId);

    @GET("api/admin/config")
    Call<ConfigResponse> getConfig();

    @GET("api/admin/sensors")
    Call<List<Sensor>> getSensors();

    @POST("api/sync/backup")
    Call<BulkBackupResponse> bulkBackup(@Body List<com.example.myapplication.db.LocalVitalSign> vitals);

    @POST("api/sessions/sync")
    Call<BulkBackupResponse> syncSessions(@Body List<com.example.myapplication.db.LocalSession> sessions);

    @GET("api/sync/recover/{userId}")
    Call<List<com.example.myapplication.db.LocalVitalSign>> recoverSync(@Path("userId") int userId);

    @POST("api/admin/control")
    Call<Void> postControl(@Body ControlRequest request);
}
