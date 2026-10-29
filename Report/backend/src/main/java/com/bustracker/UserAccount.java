package com.bustracker;

import jakarta.persistence.*;

@Entity
@Table(name = "user_accounts", uniqueConstraints = @UniqueConstraint(columnNames = "username"))
public class UserAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String username;
    @Column(nullable = false)
    private String password;
    @Column(nullable = false)
    private String role;
    private String displayName;
    private String phoneNumber;
    private String busCategory; // KTCL or Private

    public UserAccount() {}

    public UserAccount(String username, String password, String role, String displayName) {
        this.username = username;
        this.password = password;
        this.role = role;
        this.displayName = displayName;
    }

    public UserAccount(String username, String password, String role, String displayName, String phoneNumber) {
        this(username, password, role, displayName);
        this.phoneNumber = phoneNumber;
    }

    public UserAccount(String username, String password, String role, String displayName, String phoneNumber, String busCategory) {
        this(username, password, role, displayName, phoneNumber);
        this.busCategory = busCategory;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getRole() { return role; }
    public String getDisplayName() { return displayName; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getBusCategory() { return busCategory; }
}
