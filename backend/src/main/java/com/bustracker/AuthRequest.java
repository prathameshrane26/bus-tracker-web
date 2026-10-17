 package com.bustracker;

public class AuthRequest {
    private String busId;
    private String password;
    private String driverName;

    public String getBusId() { return busId; }
    public void setBusId(String busId) { this.busId = busId; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getDriverName() { return driverName; }
    public void setDriverName(String driverName) { this.driverName = driverName; }
}