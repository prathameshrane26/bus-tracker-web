package com.bustracker;

public class PassengerBusStatus {
    private String busId;
    private String busNumber;
    private double latitude;
    private double longitude;
    private String startPoint;
    private String destination;
    private String routeTaken;
    private String nextStopName;
    private double distanceToNextStopKm;
    private long etaMinutes;

    public PassengerBusStatus(String busId, String busNumber, double latitude, double longitude, 
                              String startPoint, String destination, String routeTaken,
                              String nextStopName, double distanceToNextStopKm, long etaMinutes) {
        this.busId = busId;
        this.busNumber = busNumber;
        this.latitude = latitude;
        this.longitude = longitude;
        this.startPoint = startPoint;
        this.destination = destination;
        this.routeTaken = routeTaken;
        this.nextStopName = nextStopName;
        this.distanceToNextStopKm = distanceToNextStopKm;
        this.etaMinutes = etaMinutes;
    }

    public String getBusId() { return busId; }
    public String getBusNumber() { return busNumber; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public String getStartPoint() { return startPoint; }
    public String getDestination() { return destination; }
    public String getRouteTaken() { return routeTaken; }
    public String getNextStopName() { return nextStopName; }
    public double getDistanceToNextStopKm() { return distanceToNextStopKm; }
    public long getEtaMinutes() { return etaMinutes; }
}