package com.cooperativesolutionism.nmsci.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void mapsIllegalArgumentToBadRequest() throws Exception {
        mockMvc.perform(get("/failure/illegal-argument"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Bad Request"))
                .andExpect(jsonPath("$.data").value("参数错误"));
    }

    @Test
    void mapsDataIntegrityViolationToConflictWithoutLeakingSql() throws Exception {
        mockMvc.perform(get("/failure/data-integrity"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("Conflict"))
                .andExpect(jsonPath("$.data").value(containsString("唯一约束")))
                .andExpect(jsonPath("$.data").value(not(containsString("duplicate key value"))));
    }

    @Test
    void mapsUnknownExceptionToInternalServerErrorWithoutLeakingDetails() throws Exception {
        mockMvc.perform(get("/failure/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("Internal Server Error"))
                .andExpect(jsonPath("$.data").value("服务器内部错误"))
                .andExpect(jsonPath("$.data").value(not(containsString("secret-internal-detail"))));
    }

    @RestController
    public static class FailingController {

        @GetMapping("/failure/illegal-argument")
        void illegalArgument() {
            throw new IllegalArgumentException("参数错误");
        }

        @GetMapping("/failure/data-integrity")
        void dataIntegrity() {
            throw new DataIntegrityViolationException("duplicate key value violates unique constraint");
        }

        @GetMapping("/failure/unexpected")
        void unexpected() {
            throw new IllegalStateException("secret-internal-detail");
        }
    }
}
