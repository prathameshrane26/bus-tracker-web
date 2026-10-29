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
    private final DriverWhitelistRepository driverWhitelistRepository;

    public AuthController(UserAccountRepository userAccountRepository,
                          BusDriverRepository busDriverRepository,
                          DriverWhitelistRepository driverWhitelistRepository) {
        this.userAccountRepository = userAccountRepository;
        this.busDriverRepository = busDriverRepository;
        this.driverWhitelistRepository = driverWhitelistRepository;
    }

    public void generateOtp(String phoneNumber) {
        // TODO: implement OTP generation and sending logic later
    }

    @PostMapping({"/request-otp", "/send-otp", "/driver/request-otp"})
    public ResponseEntity<Map<String, String>> requestOtp(@RequestBody(required = false) Map<String, String> request) {
        if (request == null) {
            return error(HttpStatus.BAD_REQUEST, "Phone number is required");
        }
        String phoneNumber = trim(request.get("phoneNumber"));
        if (phoneNumber == null) {
            phoneNumber = trim(request.get("phone"));
        }
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return error(HttpStatus.BAD_REQUEST, "Phone number is required");
        }
        if (!driverWhitelistRepository.existsById(phoneNumber)) {
            return error(HttpStatus.FORBIDDEN, "Driver phone number is not whitelisted");
        }

        generateOtp(phoneNumber);

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "OTP sent successfully",
                "phoneNumber", phoneNumber
        ));
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
        if ("DRIVER".equals(role)) {
            if (phoneNumber == null) {
                return error(HttpStatus.BAD_REQUEST, "phoneNumber is required for driver signup");
            }
            if (!driverWhitelistRepository.existsById(phoneNumber)) {
                return error(HttpStatus.FORBIDDEN, "Driver phone number is not whitelisted");
            }
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
        String busCategory = trim(request.getBusCategory());
        userAccountRepository.save(new UserAccount(username, password, role, displayName, phoneNumber, busCategory));
        if ("DRIVER".equals(role)) {
            busDriverRepository.save(new BusDriver(username, password, displayName, phoneNumber, busCategory));
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "status", "SUCCESS", "message", "Account created successfully",
                "username", username, "role", role));
    }

    @PostMapping({"/login", "/driver/login", "/verify-otp"})
    public ResponseEntity<Map<String, String>> login(@RequestBody Map<String, String> credentials,
                                                      HttpServletRequest request) {
        if (credentials == null) {
            return error(HttpStatus.BAD_REQUEST, "credentials are required");
        }

        String phoneNumber = trim(credentials.get("phoneNumber"));
        if (phoneNumber == null) phoneNumber = trim(credentials.get("phone"));

        String otp = trim(credentials.get("otp"));
        if (otp == null) otp = trim(credentials.get("code"));

        String busCategory = trim(credentials.get("busCategory"));
        if (busCategory == null) busCategory = trim(credentials.get("bus_category"));
        if (busCategory == null) busCategory = trim(credentials.get("category"));

        String requestedRole = normalize(credentials.get("role"));
        String username = trim(credentials.get("username"));
        if (username == null) username = trim(credentials.get("busId"));
        if (username == null) username = trim(credentials.get("busNumber"));
        String password = credentials.get("password");

        // Driver login uses busCategory, phoneNumber, and otp
        boolean isDriverLogin = phoneNumber != null || otp != null || "DRIVER".equals(requestedRole)
                || (busCategory != null && password == null);

        if (isDriverLogin) {
            if (phoneNumber == null || phoneNumber.isEmpty()) {
                return error(HttpStatus.BAD_REQUEST, "Phone number is required");
            }
            if (!driverWhitelistRepository.existsById(phoneNumber)) {
                return error(HttpStatus.FORBIDDEN, "Driver phone number is not whitelisted");
            }
            if (otp == null || otp.isEmpty()) {
                return error(HttpStatus.BAD_REQUEST, "OTP is required");
            }
            if (!"1234".equals(otp)) {
                return error(HttpStatus.UNAUTHORIZED, "Invalid OTP");
            }

            return authenticatedDriver(phoneNumber, busCategory, request);
        }

        // Passenger login with username & password
        if (username == null || password == null || password.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "username and password are required");
        }

        Optional<UserAccount> account = userAccountRepository.findByUsername(username)
                .filter(value -> password.equals(value.getPassword()));
        if (account.isPresent() && roleMatches(requestedRole, account.get().getRole())) {
            return authenticated(account.get().getUsername(), account.get().getRole(),
                    account.get().getDisplayName(), request);
        }

        return error(HttpStatus.UNAUTHORIZED, "Invalid username or password");
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
        if (request.getSession(false) != null) request.getSession(false).invalidate();
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Logged out successfully"));
    }

    private ResponseEntity<Map<String, String>> authenticatedDriver(String phoneNumber,
                                                                    String requestedBusCategory,
                                                                    HttpServletRequest request) {
        String busId = phoneNumber;
        String driverName = "Driver";
        String resolvedBusCategory = requestedBusCategory;

        // Check if there is an existing account or bus driver record for this phone number
        Optional<UserAccount> existingAccount = userAccountRepository.findAll().stream()
                .filter(u -> phoneNumber.equals(u.getPhoneNumber()))
                .findFirst();

        if (existingAccount.isPresent()) {
            UserAccount acc = existingAccount.get();
            if (acc.getUsername() != null && !acc.getUsername().isBlank()) {
                busId = acc.getUsername();
            }
            if (acc.getDisplayName() != null && !acc.getDisplayName().isBlank()) {
                driverName = acc.getDisplayName();
            }
            if (resolvedBusCategory == null && acc.getBusCategory() != null) {
                resolvedBusCategory = acc.getBusCategory();
            }
        } else {
            Optional<BusDriver> existingDriver = busDriverRepository.findAll().stream()
                    .filter(d -> phoneNumber.equals(d.getPhoneNumber()))
                    .findFirst();
            if (existingDriver.isPresent()) {
                BusDriver d = existingDriver.get();
                if (d.getBusId() != null && !d.getBusId().isBlank()) {
                    busId = d.getBusId();
                }
                if (d.getDriverName() != null && !d.getDriverName().isBlank()) {
                    driverName = d.getDriverName();
                }
                if (resolvedBusCategory == null && d.getBusCategory() != null) {
                    resolvedBusCategory = d.getBusCategory();
                }
            }
        }

        var session = request.getSession(true);
        session.setAttribute("authenticatedUser", busId);
        session.setAttribute("role", "DRIVER");
        session.setAttribute("phoneNumber", phoneNumber);
        if (resolvedBusCategory != null) {
            session.setAttribute("busCategory", resolvedBusCategory);
        }

        Map<String, String> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("username", busId);
        response.put("role", "DRIVER");
        response.put("displayName", driverName);
        response.put("busId", busId);
        response.put("driverName", driverName);
        response.put("phoneNumber", phoneNumber);
        if (resolvedBusCategory != null) {
            response.put("busCategory", resolvedBusCategory);
        }

        return ResponseEntity.ok(response);
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
