package com.rental.rental.controller;

import com.rental.rental.config.SecurityConfig;
import com.rental.rental.security.AuthIntrospectionClient;
import com.rental.rental.security.BearerTokenFilter;
import com.rental.rental.service.ContractDepositService;
import com.rental.rental.service.RentalContractService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RentalContractController.class)
@Import({SecurityConfig.class, BearerTokenFilter.class})
class RentalContractControllerSecurityTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthIntrospectionClient authClient;

    @MockitoBean
    private RentalContractService contractService;

    @MockitoBean
    private ContractDepositService depositService;

    private void mockRole(String token, String role) {
        when(authClient.introspect(token))
                .thenReturn(new AuthIntrospectionClient.AuthUser(
                        true, UUID.randomUUID().toString(),
                        "test-user", role, null
                ));
    }

    @Test
    void missingToken_returns401() throws Exception {
        mvc.perform(get("/api/contracts"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(contractService);
    }

    @Test
    void owner_getAll_returns200() throws Exception {
        mockRole("owner-token", "OWNER");

        when(contractService.getAll()).thenReturn(List.of());

        mvc.perform(get("/api/contracts")
                        .header("Authorization", "Bearer owner-token"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void staff_getAll_returns403() throws Exception {
        mockRole("staff-token", "STAFF");

        mvc.perform(get("/api/contracts")
                        .header("Authorization", "Bearer staff-token"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(contractService);
    }

    @Test
    void invalidToken_returns401() throws Exception {
        when(authClient.introspect("invalid"))
                .thenReturn(new AuthIntrospectionClient.AuthUser(
                        false, null, null, null, null
                ));

        mvc.perform(get("/api/contracts")
                        .header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authUnavailable_returns503() throws Exception {
        when(authClient.introspect("token"))
                .thenThrow(
                        new AuthIntrospectionClient
                                .AuthServiceUnavailableException()
                );

        mvc.perform(get("/api/contracts")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void owner_negativeRent_returns400() throws Exception {
        mockRole("owner-token", "OWNER");

        String json = """
                {
                  "roomId": "00000000-0000-0000-0000-000000000001",
                  "tenantProfileId": "00000000-0000-0000-0000-000000000002",
                  "startDate": "2026-12-01",
                  "endDate": "2027-12-01",
                  "rentAmount": -1000,
                  "depositRequired": 2000000
                }
                """;

        mvc.perform(post("/api/contracts")
                        .header("Authorization", "Bearer owner-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());

        verify(contractService, never()).createDraft(any());
    }
}
