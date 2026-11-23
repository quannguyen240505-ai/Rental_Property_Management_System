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

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RentalContractController.class)
@Import({SecurityConfig.class, BearerTokenFilter.class})
class RentalContractControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RentalContractService contractService;

    @MockitoBean
    private ContractDepositService depositService;

    @MockitoBean
    private ContractActivationService activationService;

    @MockitoBean
    private AuthIntrospectionClient authClient;

    @MockitoBean
    private CheckoutSettlementService checkoutService;

    private final UUID contractId = UUID.randomUUID();

    @Test
    void activate_withoutToken_returns401() throws Exception {

        mockMvc.perform(
                        post("/api/contracts/{id}/activate", contractId)
                )
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(activationService);
    }

    @Test
    void activate_staff_returns403() throws Exception {

        mockUser("STAFF");

        mockMvc.perform(
                        post("/api/contracts/{id}/activate", contractId)
                                .header("Authorization", "Bearer test-token")
                )
                .andExpect(status().isForbidden());

        verifyNoInteractions(activationService);
    }

    @Test
    void activate_owner_returns200() throws Exception {

        mockUser("OWNER");

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

        mockMvc.perform(
                        post("/api/contracts/{id}/activate", contractId)
                                .header("Authorization", "Bearer test-token")
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(activationService).activate(contractId);
    }

    private void mockUser(String role) {

        when(authClient.introspect(anyString()))
                .thenReturn(
                        new AuthIntrospectionClient.AuthUser(
                                true,
                                UUID.randomUUID().toString(),
                                "test-user",
                                role,
                                null
                        )
                );
    }
}
