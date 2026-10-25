package com.jobhub.service.customer;

import com.jobhub.controller.customer.CustomerBookingRequestController;
import com.jobhub.controller.provider.ProviderBookingOfferController;
import com.jobhub.dto.job.BookingRequestsApi.Transition;
import com.jobhub.security.AuthenticatedUser;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BookingCompletionControllerTest {
    BookingRequestService service=mock(BookingRequestService.class);
    MockMvc mvc;
    @BeforeEach void setup(){
        mvc=MockMvcBuilders.standaloneSetup(new ProviderBookingOfferController(service),new CustomerBookingRequestController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
        var user=new AuthenticatedUser(5L,"test",null,"ACTIVE",List.of("CUSTOMER"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,"unused",List.of()));
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    @Test void providerCanRequestCompletionWithoutSendingAnEndCode() throws Exception {
        when(service.requestCompletion(5L,70L)).thenReturn(new Transition(true,"AWAITING_COMPLETION_CONFIRMATION"));
        mvc.perform(post("/api/v1/providers/me/bookings/70/complete"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("AWAITING_COMPLETION_CONFIRMATION"));
        verify(service).requestCompletion(5L,70L);
    }
    @Test void customerCanConfirmCompletionWithoutAnyRequestBody() throws Exception {
        when(service.confirmCompletion(5L,70L)).thenReturn(new Transition(true,"COMPLETED"));
        mvc.perform(post("/api/v1/customers/me/bookings/70/confirm-completion"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        verify(service).confirmCompletion(5L,70L);
    }
    @Test void startingStillRequiresCustomerStartCode() throws Exception {
        mvc.perform(post("/api/v1/providers/me/bookings/70/start")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
