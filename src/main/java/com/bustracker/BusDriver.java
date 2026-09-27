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

    public BusDriver() {}

    public BusDriver(String busId, String password, String driverName) {
        this.busId = busId;
        this.password = password;
        this.driverName = driverName;
    }

    public String getBusId() { return busId; }
    public void setBusId(String busId) { this.busId = busId; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getDriverName() { return driverName; }
    public void setDriverName(String driverName) { this.driverName = driverName; }
}