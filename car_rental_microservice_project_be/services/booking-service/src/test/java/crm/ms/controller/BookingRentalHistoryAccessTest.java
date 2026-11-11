package crm.ms.controller;

import crm.ms.config.SecurityConfig;
import crm.ms.service.BookingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookingCarController.class)
@ContextConfiguration(classes = {BookingCarController.class, SecurityConfig.class})
class BookingRentalHistoryAccessTest {
    private static final String CALLER = "4f1ddc1b-3b0e-4ad9-8ad9-236376d0ed23";
    private static final String OTHER_CUSTOMER = "8d24b1a7-ab77-4c84-adb4-08bec7c51b5e";
    private static final String HISTORY = "/api/v1/bookings/by-user/";

    @Autowired MockMvc mvc;
    @MockBean BookingService bookings;
    @MockBean JwtDecoder jwtDecoder;

    @Test
    void anonymousRequestsCannotReadRentalHistory() throws Exception {
        mvc.perform(get(HISTORY + CALLER)).andExpect(status().isUnauthorized());
        verifyNoInteractions(bookings);
    }

    @Test
    void customersCanReadTheirOwnRentalHistory() throws Exception {
        when(bookings.getMyBookings(CALLER)).thenReturn(List.of());
        mvc.perform(get(HISTORY + CALLER).with(jwt().jwt(token -> token.subject(CALLER))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        verify(bookings).getMyBookings(CALLER);
    }

    @Test
    void customersCannotReadAnotherCustomersRentalHistory() throws Exception {
        mvc.perform(get(HISTORY + OTHER_CUSTOMER).with(jwt().jwt(token -> token.subject(CALLER))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(bookings);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "STAFF"})
    void staffCanReadTheRequestedCustomersRentalHistory(String role) throws Exception {
        when(bookings.getMyBookings(OTHER_CUSTOMER)).thenReturn(List.of());
        mvc.perform(get(HISTORY + OTHER_CUSTOMER).with(jwt().jwt(token -> token.subject(CALLER))
                        .authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        verify(bookings).getMyBookings(OTHER_CUSTOMER);
    }
}
