package com.bustracker;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@CrossOrigin(origins = "*")
public class AdminController {

    private final DriverWhitelistRepository driverWhitelistRepository;
    private final String adminUsername;
    private final String adminPassword;

    public AdminController(DriverWhitelistRepository driverWhitelistRepository,
                           @Value("${admin.username:admin}") String adminUsername,
                           @Value("${admin.password:admin123}") String adminPassword) {
        this.driverWhitelistRepository = driverWhitelistRepository;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @GetMapping("/admin")
    public String adminPage() {
        return "forward:/admin/index.html";
    }

    @PostMapping("/api/admin/login")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> credentials,
                                                     HttpServletRequest request) {
        if (credentials == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "ERROR", "message", "Username and password required"));
        }
        String username = trim(credentials.get("username"));
        String password = credentials.get("password");

        if (adminUsername.equals(username) && adminPassword.equals(password)) {
            HttpSession session = request.getSession(true);
            session.setAttribute("adminUser", username);
            session.setAttribute("isAdmin", true);
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Admin logged in successfully"));
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("status", "ERROR", "message", "Invalid admin credentials"));
    }

    @PostMapping("/api/admin/logout")
    @ResponseBody
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute("adminUser");
            session.removeAttribute("isAdmin");
            session.invalidate();
        }
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Admin logged out successfully"));
    }

    @GetMapping("/api/admin/status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> status(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        boolean isAdmin = session != null && Boolean.TRUE.equals(session.getAttribute("isAdmin"));
        Map<String, Object> resp = new HashMap<>();
        resp.put("authenticated", isAdmin);
        if (isAdmin) {
            resp.put("username", session.getAttribute("adminUser"));
        }
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/api/admin/whitelist")
    @ResponseBody
    public ResponseEntity<?> getWhitelist(HttpServletRequest request) {
        if (!isAdmin(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", "ERROR", "message", "Unauthorized"));
        }
        List<DriverWhitelist> whitelist = driverWhitelistRepository.findAll();
        return ResponseEntity.ok(whitelist);
    }

    @PostMapping("/api/admin/whitelist")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> addToWhitelist(@RequestBody Map<String, String> body,
                                                              HttpServletRequest request) {
        if (!isAdmin(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", "ERROR", "message", "Unauthorized"));
        }
        String phoneNumber = trim(body.get("phoneNumber"));
        if (phoneNumber == null) {
            phoneNumber = trim(body.get("phone"));
        }
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "ERROR", "message", "Phone number is required"));
        }

        if (driverWhitelistRepository.existsById(phoneNumber)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("status", "ERROR", "message", "Phone number is already whitelisted", "phoneNumber", phoneNumber));
        }

        DriverWhitelist entry = driverWhitelistRepository.save(new DriverWhitelist(phoneNumber));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("status", "SUCCESS", "message", "Phone number whitelisted successfully", "phoneNumber", entry.getPhoneNumber()));
    }

    @DeleteMapping("/api/admin/whitelist/{phoneNumber}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> removeFromWhitelist(@PathVariable String phoneNumber,
                                                                   HttpServletRequest request) {
        if (!isAdmin(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", "ERROR", "message", "Unauthorized"));
        }
        String phone = trim(phoneNumber);
        if (phone != null && driverWhitelistRepository.existsById(phone)) {
            driverWhitelistRepository.deleteById(phone);
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Phone number removed from whitelist"));
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("status", "ERROR", "message", "Phone number not found in whitelist"));
    }

    private boolean isAdmin(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && Boolean.TRUE.equals(session.getAttribute("isAdmin"));
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
