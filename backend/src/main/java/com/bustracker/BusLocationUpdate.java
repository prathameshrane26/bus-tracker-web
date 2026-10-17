package com.bustracker;

public class BusLocationUpdate {
    private String busId;
    private String busNumber;
    private double latitude;
    private double longitude;
    private String startPoint;
    private String destination;
    private String routeTaken;
    private String busCategory; // KTCL or Private

    // Default No-Arg Constructor (Required by Spring/Jackson for JSON deserialization)
    public BusLocationUpdate() {}

    // Full Constructor
    public BusLocationUpdate(String busId, String busNumber, double latitude, double longitude, String startPoint, String destination, String routeTaken) {
        this.busId = busId;
        this.busNumber = busNumber;
        this.latitude = latitude;
        this.longitude = longitude;
        this.startPoint = startPoint;
        this.destination = destination;
        this.routeTaken = routeTaken;
    }

    public BusLocationUpdate(String busId, String busNumber, double latitude, double longitude, String startPoint, String destination, String routeTaken, String busCategory) {
        this(busId, busNumber, latitude, longitude, startPoint, destination, routeTaken);
        this.busCategory = busCategory;
    }

    // Getters and Setters
    public String getBusId() { return busId; }
    public void setBusId(String busId) { this.busId = busId; }

    public String getBusNumber() { return busNumber; }
    public void setBusNumber(String busNumber) { this.busNumber = busNumber; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public String getStartPoint() { return startPoint; }
    public void setStartPoint(String startPoint) { this.startPoint = startPoint; }

    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }

    public String getRouteTaken() { return routeTaken; }
    public void setRouteTaken(String routeTaken) { this.routeTaken = routeTaken; }

    public String getBusCategory() { return busCategory; }
    public void setBusCategory(String busCategory) { this.busCategory = busCategory; }
}