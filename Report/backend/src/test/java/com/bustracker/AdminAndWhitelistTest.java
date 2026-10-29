package com.bustracker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class AdminAndWhitelistTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DriverWhitelistRepository driverWhitelistRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private BusDriverRepository busDriverRepository;

    @Value("${admin.username:admin}")
    private String adminUsername;

    @Value("${admin.password:admin123}")
    private String adminPassword;

    @BeforeEach
    void setup() {
        driverWhitelistRepository.deleteAll();
        userAccountRepository.deleteAll();
        busDriverRepository.deleteAll();
    }

    @Test
    void testAdminLoginSuccessAndFailure() throws Exception {
        // Invalid credentials
        mockMvc.perform(post("/api/admin/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"wrongpassword\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("ERROR"));

        // Valid credentials
        mockMvc.perform(post("/api/admin/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + adminUsername + "\",\"password\":\"" + adminPassword + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void testWhitelistEndpointsRequireAdmin() throws Exception {
        // Access without login
        mockMvc.perform(get("/api/admin/whitelist"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/admin/whitelist")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phoneNumber\":\"1234567890\"}"))
                .andExpect(status().isUnauthorized());

        // Login as admin
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/admin/login")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + adminUsername + "\",\"password\":\"" + adminPassword + "\"}"))
                .andExpect(status().isOk());

        // Add phone number to whitelist
        mockMvc.perform(post("/api/admin/whitelist")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phoneNumber\":\"+19876543210\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.phoneNumber").value("+19876543210"));

        // List whitelist
        mockMvc.perform(get("/api/admin/whitelist")
                .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].phoneNumber").value("+19876543210"));

        // Delete from whitelist
        mockMvc.perform(delete("/api/admin/whitelist/+19876543210")
                .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        // Verify empty
        mockMvc.perform(get("/api/admin/whitelist")
                .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void testDriverRequestOtp() throws Exception {
        String driverPhone = "9988776655";
        String nonWhitelistedPhone = "1122334455";

        // Whitelist driverPhone
        driverWhitelistRepository.save(new DriverWhitelist(driverPhone));

        // 1. Request OTP with missing phone number -> 400 Bad Request
        mockMvc.perform(post("/api/auth/request-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"));

        // 2. Request OTP with non-whitelisted phone -> 403 Forbidden
        mockMvc.perform(post("/api/auth/request-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phoneNumber\":\"" + nonWhitelistedPhone + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("ERROR"));

        // 3. Request OTP with whitelisted phone -> 200 OK
        mockMvc.perform(post("/api/auth/request-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phoneNumber\":\"" + driverPhone + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.phoneNumber").value(driverPhone));
    }

    @Test
    void testDriverLoginEnforcesWhitelistAndOtp() throws Exception {
        String driverPhone = "9988776655";
        String nonWhitelistedPhone = "1122334455";

        // Whitelist driverPhone
        driverWhitelistRepository.save(new DriverWhitelist(driverPhone));

        // 1. Driver login with non-whitelisted number -> 403 Forbidden
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phoneNumber\":\"" + nonWhitelistedPhone + "\",\"busCategory\":\"KTCL\",\"otp\":\"1234\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("ERROR"));

        // 2. Driver login with whitelisted number and missing OTP -> 400 Bad Request
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phoneNumber\":\"" + driverPhone + "\",\"busCategory\":\"KTCL\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"));

        // 3. Driver login with whitelisted number and incorrect OTP -> 401 Unauthorized
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phoneNumber\":\"" + driverPhone + "\",\"busCategory\":\"KTCL\",\"otp\":\"9999\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.message").value("Invalid OTP"));

        // 4. Driver login with whitelisted number and valid OTP 1234 -> 200 OK
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phoneNumber\":\"" + driverPhone + "\",\"busCategory\":\"KTCL\",\"otp\":\"1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.role").value("DRIVER"))
                .andExpect(jsonPath("$.busCategory").value("KTCL"))
                .andExpect(jsonPath("$.phoneNumber").value(driverPhone));

        // 5. Driver signup with non-whitelisted number should be 403 Forbidden
        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"BUS-202\",\"password\":\"pass123\",\"role\":\"DRIVER\",\"phoneNumber\":\"" + nonWhitelistedPhone + "\"}"))
                .andExpect(status().isForbidden());

        // 6. Driver signup with whitelisted number should succeed
        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"BUS-202\",\"password\":\"pass123\",\"role\":\"DRIVER\",\"phoneNumber\":\"" + driverPhone + "\",\"busCategory\":\"Private\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        // 7. Driver login using existing account phone + OTP 1234 should pick up details
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phoneNumber\":\"" + driverPhone + "\",\"otp\":\"1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.role").value("DRIVER"))
                .andExpect(jsonPath("$.busId").value("BUS-202"))
                .andExpect(jsonPath("$.busCategory").value("Private"));

        // Now remove phone from whitelist and try logging in again
        driverWhitelistRepository.deleteById(driverPhone);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phoneNumber\":\"" + driverPhone + "\",\"otp\":\"1234\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testAdminPageAccessible() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/admin/index.html"));
    }
}
