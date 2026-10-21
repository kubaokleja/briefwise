package com.briefwise.shared.infrastructure.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.briefwise.shared.domain.exception.UsageLimitExceededException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** No endpoint spends LLM budget yet (the admin job endpoint comes in M1 slice 6), so a stub throws. */
class ApiExceptionHandlerTest {

    @RestController
    static class BudgetedController {

        @GetMapping("/budgeted")
        String budgeted() {
            throw new UsageLimitExceededException("news-classification", new BigDecimal("0.10"),
                    new BigDecimal("0.10"));
        }
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new BudgetedController())
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    @Test
    void used_up_budget_is_429_problem_detail_naming_the_feature() throws Exception {
        mvc.perform(get("/budgeted"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.feature").value("news-classification"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("0.10 USD")));
    }
}
