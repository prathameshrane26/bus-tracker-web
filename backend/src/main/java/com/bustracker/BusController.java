package com.bustracker;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class BusController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private BusDriverRepository busDriverRepository;

    @Autowired
    private BusScheduleRepository busScheduleRepository;

    @Autowired
    private BusTrackerService busTrackerService;

    @Autowired
    private LocationSseService locationSseService;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    private static class BusLocationWithTimestamp {
        private final BusLocationUpdate location;
        private final long lastUpdated;

        public BusLocationWithTimestamp(BusLocationUpdate location) {
            this.location = location;
            this.lastUpdated = System.currentTimeMillis();
        }

        public BusLocationUpdate getLocation() { return location; }
        public long getLastUpdated() { return lastUpdated; }
    }

    private final Map<String, BusLocationWithTimestamp> activeBusLocations = new ConcurrentHashMap<>();

    @Scheduled(fixedRate = 60000)
    public void cleanupInactiveBuses() {
        long now = System.currentTimeMillis();
        long threshold = 15 * 60 * 1000; // 15 minutes

        activeBusLocations.entrySet().removeIf(entry -> {
            boolean timedOut = (now - entry.getValue().getLastUpdated()) > threshold;
            if (timedOut) {
                String busId = entry.getKey();
                System.out.println("[BusController] Timeout: Removing bus " + busId + " due to inactivity.");
                
                // Notify clients
                Map<String, String> payload = Map.of("busId", busId, "status", "STOPPED");
                messagingTemplate.convertAndSend("/topic/bus-stopped", payload);
                messagingTemplate.convertAndSend("/topic/active-buses", getActiveBusIds());
                
                locationSseService.broadcastBusStopped(busId);
                
                // Optionally reset to start position
                Optional<BusSchedule> scheduleOpt = busScheduleRepository.findAll().stream()
                    .filter(s -> busId.equals(s.getBusId()))
                    .findFirst();
                if (scheduleOpt.isPresent()) {
                    BusSchedule s = scheduleOpt.get();
                    BusLocationUpdate staticLoc = new BusLocationUpdate(
                        busId, s.getBusNumber(), s.getStartLatitude(), s.getStartLongitude(),
                        s.getStartLocation(), s.getDestinationLocation(), s.getRouteName(), s.getBusCategory()
                    );
                    locationSseService.broadcastLocation(staticLoc);
                    messagingTemplate.convertAndSend("/topic/bus-locations", staticLoc);
                    messagingTemplate.convertAndSend("/topic/bus/" + busId, staticLoc);
                }
            }
            return timedOut;
        });
    }

    // ------------------------------------------------------------------------
    // REAL-TIME WEBSOCKET GPS STREAMING
    // ------------------------------------------------------------------------

    @MessageMapping("/update-location")
    public void handleLocationUpdate(BusLocationUpdate update) {
        if (update == null || update.getBusId() == null || update.getBusId().trim().isEmpty()) {
            return;
        }

        activeBusLocations.put(update.getBusId(), new BusLocationWithTimestamp(update));
        if (busTrackerService != null) {
            busTrackerService.registerActiveBus(update.getBusId());
        }

        locationSseService.broadcastLocation(update);
        messagingTemplate.convertAndSend("/topic/bus/" + update.getBusId(), update);
        messagingTemplate.convertAndSend("/topic/bus-locations", update);
        messagingTemplate.convertAndSend("/topic/active-buses", getActiveBusIds());
    }

    @PostMapping("/buses/stop-sharing")
    public ResponseEntity<Map<String, String>> stopLocationSharing(@RequestBody Map<String, String> request) {
        String busId = request.get("busId");
        if (busId == null || busId.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", "Bus ID is required."));
        }

        if (activeBusLocations.containsKey(busId)) {
            activeBusLocations.remove(busId);
            
            // Notify clients that the bus is no longer active
            Map<String, String> payload = Map.of("busId", busId, "status", "STOPPED");
            messagingTemplate.convertAndSend("/topic/bus-stopped", payload);
            messagingTemplate.convertAndSend("/topic/active-buses", getActiveBusIds());
            
            // Broadcast removal via SSE
            locationSseService.broadcastBusStopped(busId);
            
            // If the bus is scheduled, broadcast its static location to reset the map icon
            Optional<BusSchedule> scheduleOpt = busScheduleRepository.findAll().stream()
                .filter(s -> busId.equals(s.getBusId()))
                .findFirst();
            
            if (scheduleOpt.isPresent()) {
                BusSchedule schedule = scheduleOpt.get();
                BusLocationUpdate staticLoc = new BusLocationUpdate(
                    busId,
                    schedule.getBusNumber(),
                    schedule.getStartLatitude(),
                    schedule.getStartLongitude(),
                    schedule.getStartLocation(),
                    schedule.getDestinationLocation(),
                    schedule.getRouteName(),
                    schedule.getBusCategory()
                );
                // We broadcast this as a location-update so the client sees the bus at its start position (scheduled)
                locationSseService.broadcastLocation(staticLoc);
                messagingTemplate.convertAndSend("/topic/bus-locations", staticLoc);
                messagingTemplate.convertAndSend("/topic/bus/" + busId, staticLoc);
            }
            
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Location sharing stopped for bus: " + busId));
        }

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("status", "ERROR", "message", "Bus ID not currently sharing location."));
    }

    // ------------------------------------------------------------------------
    // PASSENGER / PUBLIC ENDPOINTS
    // ------------------------------------------------------------------------

    @GetMapping("/buses/active")
    public Set<String> getActiveBusIds() {
        return activeBusLocations.keySet();
    }

    @GetMapping("/buses/locations")
    public Collection<BusLocationUpdate> getAllActiveLocations() {
        return activeBusLocations.values().stream()
                .map(BusLocationWithTimestamp::getLocation)
                .toList();
    }

    @GetMapping(value = "/buses/locations/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamActiveLocations(@RequestParam String clientId) {
        return locationSseService.subscribe(clientId);
    }

    @PostMapping("/buses/track")
    public ResponseEntity<Map<String, String>> trackBus(@RequestParam String clientId, @RequestParam String busId) {
        if (clientId == null || clientId.trim().isEmpty() || busId == null || busId.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", "clientId and busId are required."));
        }
        
        BusLocationWithTimestamp locationWithTimestamp = activeBusLocations.get(busId);
        BusLocationUpdate currentLocation = locationWithTimestamp != null ? locationWithTimestamp.getLocation() : null;
        
        // If not active, maybe it's scheduled but not moving yet?
        if (currentLocation == null) {
            Optional<BusSchedule> scheduleOpt = busScheduleRepository.findAll().stream()
                .filter(s -> busId.equals(s.getBusId()))
                .findFirst();
            if (scheduleOpt.isPresent()) {
                BusSchedule s = scheduleOpt.get();
                currentLocation = new BusLocationUpdate(
                    busId, s.getBusNumber(), s.getStartLatitude(), s.getStartLongitude(),
                    s.getStartLocation(), s.getDestinationLocation(), s.getRouteName(), s.getBusCategory()
                );
            }
        }
        
        locationSseService.trackBus(clientId, busId, currentLocation);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Tracking bus: " + busId));
    }

    @PostMapping("/buses/untrack")
    public ResponseEntity<Map<String, String>> untrackBus(@RequestParam String clientId, @RequestParam String busId) {
        if (clientId == null || clientId.trim().isEmpty() || busId == null || busId.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", "clientId and busId are required."));
        }
        locationSseService.untrackBus(clientId, busId);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Stopped tracking bus: " + busId));
    }

    @GetMapping("/buses/all-scheduled-locations")
    public ResponseEntity<List<BusLocationUpdate>> getAllScheduledLocations() {
        try {
            List<BusSchedule> schedules = busScheduleRepository.findAll();
            List<BusLocationUpdate> resultLocations = new ArrayList<>();
            Set<String> processedBuses = new HashSet<>();

            for (BusSchedule schedule : schedules) {
                if (schedule.getBusId() == null || processedBuses.contains(schedule.getBusId())) {
                    continue;
                }

                String busId = schedule.getBusId();
                processedBuses.add(busId);

                if (activeBusLocations.containsKey(busId)) {
                    resultLocations.add(activeBusLocations.get(busId).getLocation());
                } else {
                    BusLocationUpdate staticLoc = new BusLocationUpdate(
                        busId,
                        schedule.getBusNumber(),
                        schedule.getStartLatitude(),
                        schedule.getStartLongitude(),
                        schedule.getStartLocation(),
                        schedule.getDestinationLocation(),
                        schedule.getRouteName(),
                        schedule.getBusCategory()
                    );
                    resultLocations.add(staticLoc);
                }
            }
            return ResponseEntity.ok(resultLocations);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ------------------------------------------------------------------------
    // ADMIN SCHEDULE MANAGEMENT (WITH MANUAL EMAIL ENTRY & DISPATCH)
    // ------------------------------------------------------------------------

    @GetMapping("/admin/schedules")
    public ResponseEntity<List<BusSchedule>> getAllSchedules() {
        try {
            List<BusSchedule> schedules = busScheduleRepository.findAll();
            return ResponseEntity.ok(schedules);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/admin/schedules")
    public ResponseEntity<Map<String, Object>> addSchedule(@RequestBody BusSchedule schedule) {
        try {
            if (schedule == null || schedule.getBusId() == null || schedule.getBusId().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", "Bus ID is required."));
            }

            if (schedule.getDriverEmail() == null || schedule.getDriverEmail().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", "Driver Email is required by Admin."));
            }

            if (schedule.getScheduleId() == null || schedule.getScheduleId().trim().isEmpty()) {
                schedule.setScheduleId("SCH-" + UUID.randomUUID().toString().substring(0, 8));
            }

            // 1. Generate 6-digit random driver password
            String generatedPassword = String.valueOf((int) ((Math.random() * 900000) + 100000));

            // 2. Commit schedule to DB
            BusSchedule savedSchedule = busScheduleRepository.saveAndFlush(schedule);

            // 3. Save or update driver account details for authentication
            String driverName = schedule.getDriverName() != null ? schedule.getDriverName() : "Assigned Driver";
            busDriverRepository.saveAndFlush(new BusDriver(schedule.getBusId(), generatedPassword, driverName, null, schedule.getBusCategory()));

            // 4. Send login credentials immediately to the manually entered driver email
            sendCredentialsEmail(schedule.getDriverEmail(), driverName, schedule.getBusId(), generatedPassword);

            // 5. Notify frontend clients via WebSockets
            BusLocationUpdate staticLoc = new BusLocationUpdate(
                schedule.getBusId(),
                schedule.getBusNumber(),
                schedule.getStartLatitude(),
                schedule.getStartLongitude(),
                schedule.getStartLocation(),
                schedule.getDestinationLocation(),
                schedule.getRouteName(),
                schedule.getBusCategory()
            );

            messagingTemplate.convertAndSend("/topic/schedule-added", savedSchedule);
            messagingTemplate.convertAndSend("/topic/bus/" + schedule.getBusId(), staticLoc);
            messagingTemplate.convertAndSend("/topic/bus-locations", staticLoc);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "SUCCESS");
            response.put("message", "Schedule created and login credentials emailed to driver!");
            response.put("schedule", savedSchedule);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "ERROR", "message", "Schedule Creation Failed: " + e.getMessage()));
        }
    }

    @PutMapping("/admin/schedules/{scheduleId}")
    public ResponseEntity<Map<String, Object>> updateSchedule(@PathVariable String scheduleId, @RequestBody BusSchedule updatedSchedule) {
        try {
            Optional<BusSchedule> existingOpt = busScheduleRepository.findById(scheduleId);
            if (existingOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("status", "ERROR", "message", "Schedule ID not found in database."));
            }

            if (updatedSchedule.getDriverEmail() == null || updatedSchedule.getDriverEmail().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", "Driver Email is required by Admin."));
            }

            BusSchedule existing = existingOpt.get();
            existing.setBusId(updatedSchedule.getBusId());
            existing.setBusNumber(updatedSchedule.getBusNumber());
            existing.setDriverName(updatedSchedule.getDriverName());
            existing.setDriverEmail(updatedSchedule.getDriverEmail());
            existing.setRouteName(updatedSchedule.getRouteName());
            existing.setStartLocation(updatedSchedule.getStartLocation());
            existing.setDestinationLocation(updatedSchedule.getDestinationLocation());
            existing.setStartLatitude(updatedSchedule.getStartLatitude());
            existing.setStartLongitude(updatedSchedule.getStartLongitude());
            existing.setDepartureTime(updatedSchedule.getDepartureTime());
            existing.setArrivalTime(updatedSchedule.getArrivalTime());
            existing.setOperatingDays(updatedSchedule.getOperatingDays());
            existing.setBusType(updatedSchedule.getBusType());
            existing.setBusCategory(updatedSchedule.getBusCategory());

            BusSchedule saved = busScheduleRepository.saveAndFlush(existing);

            // Generate new login password on schedule update
            String newPassword = String.valueOf((int) ((Math.random() * 900000) + 100000));
            String driverName = saved.getDriverName() != null ? saved.getDriverName() : "Assigned Driver";

            busDriverRepository.saveAndFlush(new BusDriver(saved.getBusId(), newPassword, driverName, null, saved.getBusCategory()));

            // Send updated portal credentials to driver
            sendCredentialsEmail(saved.getDriverEmail(), driverName, saved.getBusId(), newPassword);

            messagingTemplate.convertAndSend("/topic/schedule-added", saved);
            
            BusLocationUpdate staticLoc = new BusLocationUpdate(
                saved.getBusId(),
                saved.getBusNumber(),
                saved.getStartLatitude(),
                saved.getStartLongitude(),
                saved.getStartLocation(),
                saved.getDestinationLocation(),
                saved.getRouteName(),
                saved.getBusCategory()
            );
            messagingTemplate.convertAndSend("/topic/bus-locations", staticLoc);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "SUCCESS");
            response.put("message", "Schedule updated and new credentials emailed to driver!");
            response.put("schedule", saved);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "ERROR", "message", "Schedule Update Failed: " + e.getMessage()));
        }
    }

    @DeleteMapping("/admin/schedules/{scheduleId}")
    public ResponseEntity<Map<String, String>> deleteSchedule(@PathVariable String scheduleId) {
        try {
            Optional<BusSchedule> schedOpt = busScheduleRepository.findById(scheduleId);
            if (schedOpt.isPresent()) {
                String busId = schedOpt.get().getBusId();

                if (busId != null) {
                    activeBusLocations.remove(busId);
                }

                busScheduleRepository.deleteById(scheduleId);

                Map<String, String> payload = Map.of("scheduleId", scheduleId, "busId", busId != null ? busId : "");
                messagingTemplate.convertAndSend("/topic/schedule-deleted", payload);
                messagingTemplate.convertAndSend("/topic/bus-deleted", payload);

                return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Schedule deleted successfully!"));
            }

            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("status", "ERROR", "message", "Schedule ID not found in database."));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "ERROR", "message", "Delete Failed: " + e.getMessage()));
        }
    }

    // ------------------------------------------------------------------------
    // HELPER EMAIL DISPATCH
    // ------------------------------------------------------------------------

    private void sendCredentialsEmail(String recipientEmail, String driverName, String busId, String password) {
        if (mailSender == null) {
            System.err.println("⚠️ JavaMailSender is not configured. Skipping email dispatch.");
            return;
        }

        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setTo(recipientEmail);
            mail.setSubject("Goa Transit KTC Driver App Credentials - Bus ID: " + busId);
            mail.setText(
                "Mogall " + driverName + ",\n\n" +
                "You have been assigned a bus schedule by the administration. Below are your login credentials for the Driver App:\n\n" +
                "--------------------------------------------------\n" +
                "BUS ID (USERID)  : " + busId + "\n" +
                "PASSWORD         : " + password + "\n" +
                "--------------------------------------------------\n\n" +
                "INSTRUCTIONS:\n" +
                "1. Open the Goa Transit Driver Android App.\n" +
                "2. Login using your BUS ID as the User ID and the generated password.\n" +
                "3. Click 'Start Trip' to begin live location streaming.\n\n" +
                "Dev Borem Korum,\n" +
                "Kadamba Transport Corporation"
            );
            mailSender.send(mail);
            System.out.println("✅ Credentials successfully sent to " + recipientEmail);
        } catch (Exception e) {
            System.err.println("❌ Failed to send credentials email to " + recipientEmail + ": " + e.getMessage());
        }
    }
}