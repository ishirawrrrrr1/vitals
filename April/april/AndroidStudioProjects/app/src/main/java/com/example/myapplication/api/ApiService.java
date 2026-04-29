package com.example.myapplication.api;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

public interface ApiService {
    @GET("/api/vitals/{userId}")
    Call<List<VitalSigns>> getVitals(@Path("userId") int userId);
}
