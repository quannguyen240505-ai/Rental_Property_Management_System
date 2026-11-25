package com.rental.rental.controller;

import com.rental.rental.config.SecurityConfig;
import com.rental.rental.dto.RentalContractResponse;
import com.rental.rental.entity.RentalContract;
import com.rental.rental.security.AuthIntrospectionClient;
import com.rental.rental.security.BearerTokenFilter;
import com.rental.rental.service.ContractActivationService;
import com.rental.rental.service.CheckoutSettlementService;
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

    @MockitoBean
    private ContractActivationService activationService;

    @MockitoBean
    private CheckoutSettlementService checkoutService;

    private final UUID contractId = UUID.randomUUID();

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

    @Test
    void activate_withoutToken_returns401() throws Exception {

        mvc.perform(
                        post("/api/contracts/{id}/activate", contractId)
                )
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(activationService);
    }

    @Test
    void activate_staff_returns403() throws Exception {

        mockRole("test-token", "STAFF");

        mvc.perform(
                        post("/api/contracts/{id}/activate", contractId)
                                .header("Authorization", "Bearer test-token")
                )
                .andExpect(status().isForbidden());

        verifyNoInteractions(activationService);
    }

    @Test
    void activate_owner_returns200() throws Exception {

        mockRole("test-token", "OWNER");

        RentalContract contract = mock(RentalContract.class);
        when(contract.getId()).thenReturn(contractId);

        RentalContractResponse response =
                new RentalContractResponse(
                        contractId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "ACTIVE",
                        java.time.LocalDate.of(2026, 12, 1),
                        java.time.LocalDate.of(2027, 1, 1),
                        5_000_000L,
                        1_000_000L
                );

        when(activationService.activate(contractId))
                .thenReturn(contract);

        when(contractService.getById(contractId))
                .thenReturn(response);

        mvc.perform(
                        post("/api/contracts/{id}/activate", contractId)
                                .header("Authorization", "Bearer test-token")
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(activationService).activate(contractId);
    }
    @Test
    void settlement_staff_returns403() throws Exception {
        mockRole("staff-token", "STAFF");

        mvc.perform(
                        get("/api/contracts/{id}/settlement", contractId)
                                .header("Authorization", "Bearer staff-token")
                )
                .andExpect(status().isForbidden());

        verifyNoInteractions(checkoutService);
    }

    @Test
    void settlement_withoutToken_returns401() throws Exception {
        mvc.perform(
                        get("/api/contracts/{id}/settlement", contractId)
                )
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(checkoutService);
    }
    @Test
    void staff_getContractDetail_returns403() throws Exception {
        mockRole("staff-token", "STAFF");

        mvc.perform(
                        get("/api/contracts/{id}", contractId)
                                .header("Authorization", "Bearer staff-token")
                )
                .andExpect(status().isForbidden());

        verifyNoInteractions(contractService);
    }

    @Test
    void staff_createContract_returns403() throws Exception {
        mockRole("staff-token", "STAFF");

        mvc.perform(
                        post("/api/contracts")
                                .header("Authorization", "Bearer staff-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                        {
                          "roomId": "00000000-0000-0000-0000-000000000001",
                          "tenantProfileId": "00000000-0000-0000-0000-000000000002",
                          "startDate": "2026-12-01",
                          "endDate": "2027-12-01",
                          "rentAmount": 3000000,
                          "depositRequired": 2000000
                        }
                        """)
                )
                .andExpect(status().isForbidden());

        verifyNoInteractions(contractService);
    }
}
