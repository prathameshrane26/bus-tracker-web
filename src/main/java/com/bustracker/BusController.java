package com.bustracker;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

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

    @Autowired(required = false)
    private JavaMailSender mailSender;

    private final Map<String, BusLocationUpdate> activeBusLocations = new ConcurrentHashMap<>();

    // ------------------------------------------------------------------------
    // DRIVER AUTHENTICATION
    // ------------------------------------------------------------------------

    @PostMapping("/auth/login")
    public ResponseEntity<Map<String, String>> loginDriver(@RequestBody Map<String, String> credentials) {
        try {
            if (credentials == null || !credentials.containsKey("busId") || !credentials.containsKey("password")) {
                return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", "busId and password are required."));
            }

            String busId = credentials.get("busId");
            String password = credentials.get("password");

            Optional<BusDriver> driverOpt = busDriverRepository.findById(busId);

            if (driverOpt.isPresent() && password.equals(driverOpt.get().getPassword())) {
                Map<String, String> response = new HashMap<>();
                response.put("status", "SUCCESS");
                response.put("busId", busId);
                response.put("driverName", driverOpt.get().getDriverName() != null ? driverOpt.get().getDriverName() : "Driver");
                return ResponseEntity.ok(response);
            }

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", "ERROR", "message", "Invalid Bus ID or Password"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "ERROR", "message", "Auth failure: " + e.getMessage()));
        }
    }

    // ------------------------------------------------------------------------
    // REAL-TIME WEBSOCKET GPS STREAMING
    // ------------------------------------------------------------------------

    @MessageMapping("/update-location")
    public void handleLocationUpdate(BusLocationUpdate update) {
        if (update == null || update.getBusId() == null || update.getBusId().trim().isEmpty()) {
            return;
        }

        activeBusLocations.put(update.getBusId(), update);

        messagingTemplate.convertAndSend("/topic/bus/" + update.getBusId(), update);
        messagingTemplate.convertAndSend("/topic/active-buses", getActiveBusIds());
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
        return activeBusLocations.values();
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
                    resultLocations.add(activeBusLocations.get(busId));
                } else {
                    BusLocationUpdate staticLoc = new BusLocationUpdate(
                        busId,
                        schedule.getStartLatitude(),
                        schedule.getStartLongitude(),
                        0.0,
                        schedule.getDestinationLocation()
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
            busDriverRepository.saveAndFlush(new BusDriver(schedule.getBusId(), generatedPassword, driverName));

            // 4. Send login credentials immediately to the manually entered driver email
            sendCredentialsEmail(schedule.getDriverEmail(), driverName, schedule.getBusId(), generatedPassword);

            // 5. Notify frontend clients via WebSockets
            BusLocationUpdate staticLoc = new BusLocationUpdate(
                schedule.getBusId(),
                schedule.getStartLatitude(),
                schedule.getStartLongitude(),
                0.0,
                schedule.getDestinationLocation()
            );

            messagingTemplate.convertAndSend("/topic/schedule-added", savedSchedule);
            messagingTemplate.convertAndSend("/topic/bus/" + schedule.getBusId(), staticLoc);

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

            BusSchedule saved = busScheduleRepository.saveAndFlush(existing);

            // Generate new login password on schedule update
            String newPassword = String.valueOf((int) ((Math.random() * 900000) + 100000));
            String driverName = saved.getDriverName() != null ? saved.getDriverName() : "Assigned Driver";

            busDriverRepository.saveAndFlush(new BusDriver(saved.getBusId(), newPassword, driverName));

            // Send updated portal credentials to driver
            sendCredentialsEmail(saved.getDriverEmail(), driverName, saved.getBusId(), newPassword);

            messagingTemplate.convertAndSend("/topic/schedule-added", saved);

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
            mail.setSubject("Goa Transit KTC Driver Portal Credentials - Bus ID: " + busId);
            mail.setText(
                "Mogall " + driverName + ",\n\n" +
                "You have been assigned a bus schedule by the administration. Below are your login credentials for the Driver Portal:\n\n" +
                "--------------------------------------------------\n" +
                "BUS ID (USERID)  : " + busId + "\n" +
                "PASSWORD         : " + password + "\n" +
                "DRIVER PORTAL    : http://localhost:8080/driver.html\n" +
                "--------------------------------------------------\n\n" +
                "INSTRUCTIONS:\n" +
                "1. Access the Driver Portal link above.\n" +
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