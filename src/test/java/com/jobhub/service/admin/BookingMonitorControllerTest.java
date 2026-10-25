package com.jobhub.service.admin;

import com.jobhub.controller.admin.AdminBookingMonitorController;
import com.jobhub.dto.admin.BookingMonitorApi.*;
import com.jobhub.exception.*;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.Page;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BookingMonitorControllerTest {
    BookingMonitorService service=mock(BookingMonitorService.class);
    MockMvc mvc;
    @BeforeEach void setup(){mvc=MockMvcBuilders.standaloneSetup(new AdminBookingMonitorController(service))
        .setControllerAdvice(new GlobalExceptionHandler()).build();}
    @Test void bindsRequestFiltersAndPaginates() throws Exception {
        when(service.requests(any(),eq(2),eq(10))).thenReturn(Page.empty(org.springframework.data.domain.PageRequest.of(0,20)));
        mvc.perform(get("/api/v1/admin/booking-monitor/requests").param("marketId","1").param("customerUserId","5")
            .param("status","MATCHING").param("createdFrom","2026-10-01T00:00:00").param("page","2").param("size","10"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray());
        verify(service).requests(argThat(f -> f.marketId()==1L && f.customerUserId()==5L && f.status().equals("MATCHING")
            && f.createdFrom().getMonthValue()==10),eq(2),eq(10));
    }
    @Test void bindsOffsetReceiptTimesAndReturnsPage() throws Exception {
        when(service.payments(any(),eq(0),eq(20))).thenReturn(Page.empty(org.springframework.data.domain.PageRequest.of(0,20)));
        mvc.perform(get("/api/v1/admin/booking-monitor/payment-notices").param("requestId","501")
            .param("statusCode","2").param("receivedFrom","2026-10-25T08:00:00Z"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray());
        verify(service).payments(argThat(f -> f.requestId()==501L && f.receivedFrom()!=null && f.statusCode().equals("2")),eq(0),eq(20));
    }
    @Test void rejectsInvalidFilterIdentifiersAndDateBindings() throws Exception {
        mvc.perform(get("/api/v1/admin/booking-monitor/requests").param("marketId","-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/booking-monitor/events").param("createdFrom","yesterday")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void missingTransactionUsesExisting404Contract() throws Exception {
        when(service.transaction(9L)).thenThrow(new ResourceNotFoundException("Wallet transaction not found"));
        mvc.perform(get("/api/v1/admin/booking-monitor/transactions/9")).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("Wallet transaction not found"));
    }
}
