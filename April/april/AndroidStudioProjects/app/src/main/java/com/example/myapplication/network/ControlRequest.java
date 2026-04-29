package com.example.myapplication.network;

public class ControlRequest {
    public String device;
    public String action;
    public Object params;

    public ControlRequest(String device, String action, Object params) {
        this.device = device;
        this.action = action;
        this.params = params;
    }
}
