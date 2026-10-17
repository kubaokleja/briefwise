package com.briefwise.shared.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    private record Result(String headerInResponse, String mdcDuringRequest) {}

    private Result call(String incomingHeader) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (incomingHeader != null) {
            request.addHeader(CorrelationIdFilter.HEADER, incomingHeader);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seenInChain = new AtomicReference<>();
        filter.doFilter(request, response,
                new MockFilterChain() {
                    @Override
                    public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                        seenInChain.set(MDC.get(CorrelationIdFilter.MDC_KEY));
                    }
                });
        return new Result(response.getHeader(CorrelationIdFilter.HEADER), seenInChain.get());
    }

    @Test
    void reuses_a_well_formed_incoming_id_and_exposes_it_in_mdc_and_response() throws Exception {
        Result result = call("req-123.abc_DEF");

        assertThat(result.headerInResponse()).isEqualTo("req-123.abc_DEF");
        assertThat(result.mdcDuringRequest()).isEqualTo("req-123.abc_DEF");
    }

    @Test
    void generates_an_id_when_the_header_is_absent() throws Exception {
        Result result = call(null);

        assertThat(result.headerInResponse()).isNotBlank().isEqualTo(result.mdcDuringRequest());
        assertThat(result.headerInResponse()).hasSize(36);
    }

    @Test
    void replaces_a_malformed_id_so_callers_cannot_inject_into_logs() throws Exception {
        Result result = call("evil value\nINFO forged log line");

        assertThat(result.headerInResponse()).doesNotContain("evil").hasSize(36);
        assertThat(result.mdcDuringRequest()).isEqualTo(result.headerInResponse());
    }

    @Test
    void clears_the_mdc_after_the_request() throws Exception {
        call("req-1");

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
