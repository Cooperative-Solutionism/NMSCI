package com.cooperativesolutionism.nmsci.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
    void mapsIllegalArgumentToInternalServerErrorWithoutLeakingDetails() throws Exception {
        mockMvc.perform(get("/failure/illegal-argument"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("服务器内部错误"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(content().string(not(containsString("secret-illegal-argument-detail"))));
    }

    @Test
    void mapsBadRequestExceptionToBadRequest() throws Exception {
        mockMvc.perform(get("/failure/bad-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请求参数非法"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void mapsNotFoundExceptionToNotFound() throws Exception {
        mockMvc.perform(get("/failure/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("资源不存在"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void mapsConflictExceptionToConflict() throws Exception {
        mockMvc.perform(get("/failure/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("资源冲突"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void mapsDataIntegrityViolationToConflictWithoutLeakingSql() throws Exception {
        mockMvc.perform(get("/failure/data-integrity"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value(containsString("唯一约束")))
                .andExpect(jsonPath("$.message").value(not(containsString("duplicate key value"))))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(content().string(not(containsString("duplicate key value"))));
    }

    @Test
    void mapsValidationExceptionsToBadRequestWithoutLeakingFrameworkDetails() throws Exception {
        mockMvc.perform(get("/failure/message-not-readable"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请求参数非法"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(content().string(not(containsString("secret-parser-detail"))));
    }

    @Test
    void mapsMissingRequiredRequestParamToBadRequest() throws Exception {
        // 缺失必填 @RequestParam 触发 MissingServletRequestParameterException（ServletRequestBindingException 子类）。
        // 修复前会被兜底的 Exception 处理器吞为 500，修复后须为 400。
        mockMvc.perform(get("/failure/requires-param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请求参数非法"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void mapsUnsupportedHttpMethodToMethodNotAllowed() throws Exception {
        // /failure/not-found 只声明了 GET，对其发 POST 触发 HttpRequestMethodNotSupportedException。
        // 修复前会被兜底的 Exception 处理器吞为 500，修复后须为 405。
        mockMvc.perform(post("/failure/not-found"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(405))
                .andExpect(jsonPath("$.message").value("请求方法不被支持"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void mapsUnsupportedMediaTypeToUnsupportedMediaType() throws Exception {
        // 向 consumes=application/json 的端点提交 text/plain，触发 HttpMediaTypeNotSupportedException。
        // 修复前会被兜底处理器吞为 500，修复后须为 415。
        mockMvc.perform(post("/failure/consumes-json")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("plain-text-body"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value(415))
                .andExpect(jsonPath("$.message").value("不支持的媒体类型"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void mapsUnknownExceptionToInternalServerErrorWithoutLeakingDetails() throws Exception {
        mockMvc.perform(get("/failure/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("服务器内部错误"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(content().string(not(containsString("secret-internal-detail"))));
    }

    @RestController
    public static class FailingController {

        @GetMapping("/failure/illegal-argument")
        void illegalArgument() {
            throw new IllegalArgumentException("secret-illegal-argument-detail");
        }

        @GetMapping("/failure/bad-request")
        void badRequest() {
            throw new BadRequestException("请求参数非法");
        }

        @GetMapping("/failure/not-found")
        void notFound() {
            throw new NotFoundException("资源不存在");
        }

        @GetMapping("/failure/conflict")
        void conflict() {
            throw new ConflictException("资源冲突");
        }

        @GetMapping("/failure/data-integrity")
        void dataIntegrity() {
            throw new DataIntegrityViolationException("duplicate key value violates unique constraint");
        }

        @GetMapping("/failure/message-not-readable")
        void messageNotReadable() {
            throw new HttpMessageNotReadableException("secret-parser-detail", new MockHttpInputMessage(new byte[0]));
        }

        @GetMapping("/failure/unexpected")
        void unexpected() {
            throw new IllegalStateException("secret-internal-detail");
        }

        @PostMapping(value = "/failure/consumes-json", consumes = MediaType.APPLICATION_JSON_VALUE)
        void consumesJson(@RequestBody String body) {
            // 仅用于触发 415：方法体不会被执行，因为媒体类型协商在进入方法前已失败。
        }

        @GetMapping("/failure/requires-param")
        void requiresParam(@RequestParam String requiredValue) {
            // 仅用于触发缺参的 MissingServletRequestParameterException：方法体不会被执行。
        }
    }
}
