package com.bustracker;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "driver_whitelist")
public class DriverWhitelist {

    @Id
    private String phoneNumber;

    private LocalDateTime createdAt;

    public DriverWhitelist() {
        this.createdAt = LocalDateTime.now();
    }

    public DriverWhitelist(String phoneNumber) {
        this.phoneNumber = phoneNumber;
        this.createdAt = LocalDateTime.now();
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
