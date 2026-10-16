package com.bustracker;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {
    private final UserAccountRepository userAccountRepository;
    private final BusDriverRepository busDriverRepository;

    public AuthController(UserAccountRepository userAccountRepository, BusDriverRepository busDriverRepository) {
        this.userAccountRepository = userAccountRepository;
        this.busDriverRepository = busDriverRepository;
    }

    @PostMapping("/signup")
    public ResponseEntity<Map<String, String>> signup(@RequestBody SignupRequest request) {
        String role = normalize(request.getRole());
        String username = trim(request.getUsername());
        String busId = trim(request.getBusId());
        String phoneNumber = trim(request.getPhoneNumber());
        if ("DRIVER".equals(role)) {
            username = busId != null ? busId : username;
        }
        if (!"DRIVER".equals(role) && !"PASSENGER".equals(role)) {
            return error(HttpStatus.BAD_REQUEST, "role must be DRIVER or PASSENGER");
        }
        if (username == null) {
            return error(HttpStatus.BAD_REQUEST, "username is required (busId for drivers)");
        }
        if ("DRIVER".equals(role) && phoneNumber == null) {
            return error(HttpStatus.BAD_REQUEST, "phoneNumber is required for driver signup");
        }
        if (!"DRIVER".equals(role) && (request.getPassword() == null || request.getPassword().isBlank())) {
            return error(HttpStatus.BAD_REQUEST, "password is required");
        }
        if (userAccountRepository.existsByUsername(username)
                || ("DRIVER".equals(role) && busDriverRepository.existsById(username))) {
            return error(HttpStatus.CONFLICT, "An account with that username already exists");
        }

        String displayName = trim(request.getDisplayName());
        if (displayName == null) displayName = "DRIVER".equals(role) ? "Driver" : "Passenger";
        String password = request.getPassword() == null ? "" : request.getPassword();
        userAccountRepository.save(new UserAccount(username, password, role, displayName, phoneNumber));
        if ("DRIVER".equals(role)) {
            busDriverRepository.save(new BusDriver(username, password, displayName, phoneNumber));
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "status", "SUCCESS", "message", "Account created successfully",
                "username", username, "role", role));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody Map<String, String> credentials,
                                                      HttpServletRequest request) {
        if (credentials == null) {
            return error(HttpStatus.BAD_REQUEST, "credentials are required");
        }
        String identifier = trim(credentials.get("username"));
        if (identifier == null) identifier = trim(credentials.get("busId"));
        if (identifier == null) identifier = trim(credentials.get("busNumber"));
        String phoneNumber = trim(credentials.get("phoneNumber"));
        if (phoneNumber == null) phoneNumber = trim(credentials.get("phone"));
        if (identifier == null) {
            return error(HttpStatus.BAD_REQUEST, "username/busId and password, or busId and phoneNumber, are required");
        }
        String requestedRole = normalize(credentials.get("role"));
        final String loginIdentifier = identifier;
        final String loginPhoneNumber = phoneNumber;
        final String loginPassword = credentials.get("password");

        // Temporary testing mode: any nonblank bus identifier and phone number
        // creates a driver session without verification or database lookup.
        if (loginPhoneNumber != null && !"PASSENGER".equals(requestedRole)) {
            return authenticated(loginIdentifier, "DRIVER", "Driver", request);
        }

        Optional<UserAccount> account = userAccountRepository.findByUsername(loginIdentifier)
                .filter(value -> loginPassword != null && loginPassword.equals(value.getPassword()));
        if (account.isPresent() && roleMatches(requestedRole, account.get().getRole())) {
            return authenticated(account.get().getUsername(), account.get().getRole(),
                    account.get().getDisplayName(), request);
        }

        // Admin-created driver accounts are stored in the legacy bus_drivers table.
        if (!"PASSENGER".equals(requestedRole)) {
            Optional<BusDriver> driver = busDriverRepository.findById(loginIdentifier)
                    .filter(value -> loginPassword != null && loginPassword.equals(value.getPassword()));
            if (driver.isPresent()) {
                return authenticated(loginIdentifier, "DRIVER", driver.get().getDriverName(), request);
            }
        }
        return error(HttpStatus.UNAUTHORIZED, "Invalid username/bus ID or password");
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
        if (request.getSession(false) != null) request.getSession(false).invalidate();
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Logged out successfully"));
    }

    private ResponseEntity<Map<String, String>> authenticated(String username, String role,
                                                               String displayName, HttpServletRequest request) {
        var session = request.getSession(true);
        session.setAttribute("authenticatedUser", username);
        session.setAttribute("role", role);
        Map<String, String> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("username", username);
        response.put("role", role);
        response.put("displayName", displayName == null ? role : displayName);
        if ("DRIVER".equals(role)) {
            response.put("busId", username);
            response.put("driverName", displayName == null ? "Driver" : displayName);
        }
        return ResponseEntity.ok(response);
    }

    private boolean roleMatches(String requestedRole, String actualRole) {
        return requestedRole == null || requestedRole.equals(actualRole);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("status", "ERROR", "message", message));
    }
}
