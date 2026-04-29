package com.example.myapplication.network;

public class UserProfileResponse {
    public boolean success;
    public User user;

    public static class User {
        public int id;
        public String username;
        public String email;
        public String role;
        public String age;
        public String gender;
        public String stroke_duration;
    }
}
