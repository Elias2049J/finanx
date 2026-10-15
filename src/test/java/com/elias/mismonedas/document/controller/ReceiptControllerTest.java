package com.elias.mismonedas.document.controller;

import com.elias.mismonedas.document.domain.enums.ReceiptScanStatus;
import com.elias.mismonedas.document.dto.ReceiptResponse;
import com.elias.mismonedas.document.service.ReceiptScanService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReceiptControllerTest {

    private final ReceiptScanService receiptScanService = mock(ReceiptScanService.class);
    private final ReceiptController controller = new ReceiptController(receiptScanService);

    @BeforeEach
    void bindRequest() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldReturn202WithLocationWhenScanIsEnqueued() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image", "recibo.jpg", "image/jpeg", "bytes".getBytes());
        ReceiptResponse response = new ReceiptResponse(
                7L, ReceiptScanStatus.PENDING, null, 3L,
                null, null, null, null, null, null, null, null, null, null);
        when(receiptScanService.startScan(eq(1L), any(), eq("recibo.jpg"), eq("image/jpeg")))
                .thenReturn(response);

        ResponseEntity<ReceiptResponse> result = controller.scan(1L, image);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(result.getHeaders().getLocation()).hasToString("http://localhost/receipts/7");
        assertThat(result.getBody()).isSameAs(response);
    }

    @Test
    void shouldRejectEmptyImage() {
        MockMultipartFile image = new MockMultipartFile("image", "recibo.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> controller.scan(1L, image))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vacía");
        verify(receiptScanService, never()).startScan(any(), any(), any(), any());
    }
}
