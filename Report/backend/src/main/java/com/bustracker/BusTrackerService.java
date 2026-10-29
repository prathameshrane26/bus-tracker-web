package com.bustracker;

import org.springframework.stereotype.Service;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BusTrackerService {

    private final Set<String> activeBuses = ConcurrentHashMap.newKeySet();

    public void registerActiveBus(String busId) {
        if (busId != null) {
            activeBuses.add(busId);
        }
    }

    public void unregisterActiveBus(String busId) {
        if (busId != null) {
            activeBuses.remove(busId);
        }
    }

    public Set<String> getActiveBuses() {
        return activeBuses;
    }

    public double calculateDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        final int EARTH_RADIUS_KM = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    public long calculateEtaMinutes(double distanceKm) {
        double averageSpeed = 25.0; // Default average speed in km/h
        return Math.round((distanceKm / averageSpeed) * 60);
    }
}