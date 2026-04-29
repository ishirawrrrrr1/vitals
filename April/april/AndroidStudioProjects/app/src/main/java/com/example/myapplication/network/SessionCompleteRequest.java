package com.example.myapplication.network;

public class SessionCompleteRequest {
    public float hii_index;
    public String clinical_summary;

    public SessionCompleteRequest(float hiiIndex, String clinicalSummary) {
        this.hii_index = hiiIndex;
        this.clinical_summary = clinicalSummary;
    }
}
