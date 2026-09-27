package com.bustracker;

public class BusLocationUpdate {
    private String busId;
    private double latitude;
    private double longitude;
    private double speed;
    private String destination;

    // Default No-Arg Constructor (Required by Spring/Jackson for JSON deserialization)
    public BusLocationUpdate() {}

    // Full Constructor
    public BusLocationUpdate(String busId, double latitude, double longitude, double speed, String destination) {
        this.busId = busId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.speed = speed;
        this.destination = destination;
    }

    // Getters and Setters
    public String getBusId() { return busId; }
    public void setBusId(String busId) { this.busId = busId; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public double getSpeed() { return speed; }
    public void setSpeed(double speed) { this.speed = speed; }

    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
}