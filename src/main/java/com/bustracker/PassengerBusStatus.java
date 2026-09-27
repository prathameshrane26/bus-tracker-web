package com.bustracker;

public class PassengerBusStatus {
    private String busId;
    private double latitude;
    private double longitude;
    private String nextStopName;
    private double distanceToNextStopKm;
    private long etaMinutes;

    public PassengerBusStatus(String busId, double latitude, double longitude, 
                              String nextStopName, double distanceToNextStopKm, long etaMinutes) {
        this.busId = busId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.nextStopName = nextStopName;
        this.distanceToNextStopKm = distanceToNextStopKm;
        this.etaMinutes = etaMinutes;
    }

    public String getBusId() { return busId; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public String getNextStopName() { return nextStopName; }
    public double getDistanceToNextStopKm() { return distanceToNextStopKm; }
    public long getEtaMinutes() { return etaMinutes; }
}