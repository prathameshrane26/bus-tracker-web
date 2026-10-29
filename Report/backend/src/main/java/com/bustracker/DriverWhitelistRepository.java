package com.bustracker;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DriverWhitelistRepository extends JpaRepository<DriverWhitelist, String> {
    boolean existsByPhoneNumber(String phoneNumber);
}
