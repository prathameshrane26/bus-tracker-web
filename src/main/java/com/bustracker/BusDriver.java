package com.bustracker;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "bus_drivers")
public class BusDriver {

    @Id
    private String busId;
    private String password;
    private String driverName;
    private String phoneNumber;
    private String busCategory; // KTCL or Private

    public BusDriver() {}

    public BusDriver(String busId, String password, String driverName) {
        this.busId = busId;
        this.password = password;
        this.driverName = driverName;
    }

    public BusDriver(String busId, String password, String driverName, String phoneNumber) {
        this(busId, password, driverName);
        this.phoneNumber = phoneNumber;
    }

    public BusDriver(String busId, String password, String driverName, String phoneNumber, String busCategory) {
        this(busId, password, driverName, phoneNumber);
        this.busCategory = busCategory;
    }

    public String getBusId() { return busId; }
    public void setBusId(String busId) { this.busId = busId; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getDriverName() { return driverName; }
    public void setDriverName(String driverName) { this.driverName = driverName; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getBusCategory() { return busCategory; }
    public void setBusCategory(String busCategory) { this.busCategory = busCategory; }
}