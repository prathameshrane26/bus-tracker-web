 package com.bustracker;

public class AuthRequest {
    private String busId;
    private String username;
    private String password;
    private String driverName;
    private String phoneNumber;
    private String busCategory;
    private String otp;
    private String role;

    public String getBusId() { return busId; }
    public void setBusId(String busId) { this.busId = busId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getDriverName() { return driverName; }
    public void setDriverName(String driverName) { this.driverName = driverName; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getBusCategory() { return busCategory; }
    public void setBusCategory(String busCategory) { this.busCategory = busCategory; }

    public String getOtp() { return otp; }
    public void setOtp(String otp) { this.otp = otp; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}