package com.vibecode.antijob.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CorrelationIdFilterTest {

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void noRequestIdHeader_generatesOneAndSetsMdcDuringChainExecution() throws Exception {
        when(request.getHeader(CorrelationIdFilter.REQUEST_ID_HEADER)).thenReturn(null);
        doAnswer(invocation -> {
            assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNotBlank();
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader(org.mockito.ArgumentMatchers.eq(CorrelationIdFilter.REQUEST_ID_HEADER),
                org.mockito.ArgumentMatchers.anyString());
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void existingRequestIdHeader_isReused() throws Exception {
        when(request.getHeader(CorrelationIdFilter.REQUEST_ID_HEADER)).thenReturn("client-provided-id");

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader(CorrelationIdFilter.REQUEST_ID_HEADER, "client-provided-id");
    }

    @Test
    void mdcIsClearedAfterChainThrows() {
        when(request.getHeader(CorrelationIdFilter.REQUEST_ID_HEADER)).thenReturn(null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> {
            doAnswer(invocation -> {
                throw new RuntimeException("boom");
            }).when(filterChain).doFilter(request, response);
            filter.doFilterInternal(request, response, filterChain);
        }).isInstanceOf(RuntimeException.class);

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void order_isHighestPrecedence() {
        assertThat(filter.getOrder()).isEqualTo(org.springframework.core.Ordered.HIGHEST_PRECEDENCE);
    }
}
