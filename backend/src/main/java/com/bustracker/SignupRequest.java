package com.bustracker;

public class SignupRequest {
    private String username;
    private String password;
    private String role;
    private String busId;
    private String displayName;
    private String phoneNumber;
    private String busCategory;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getBusId() { return busId; }
    public void setBusId(String busId) { this.busId = busId; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public String getBusCategory() { return busCategory; }
    public void setBusCategory(String busCategory) { this.busCategory = busCategory; }
}
