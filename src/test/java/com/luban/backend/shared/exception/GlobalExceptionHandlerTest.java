package com.luban.backend.shared.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * GlobalExceptionHandler 单测（backend-ddd-refactor T17）。
 *
 * <p>覆盖 BusinessException 映射、参数校验、JSON 不可读、500 脱敏（敏感关键词替换/null 消息）。
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleBusiness_mapsStatusAndCode() {
        BusinessException ex = new BusinessException(409, "LEAD_DUPLICATE", "重复提交");

        var resp = handler.handleBusiness(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().code()).isEqualTo("LEAD_DUPLICATE");
        assertThat(resp.getBody().message()).isEqualTo("重复提交");
    }

    @Test
    void handleValidation_returnsFirstFieldError() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        org.springframework.validation.BindingResult br = mock(org.springframework.validation.BindingResult.class);
        when(ex.getBindingResult()).thenReturn(br);
        FieldError fe = new FieldError("obj", "phone", "不能为空");
        when(br.getFieldErrors()).thenReturn(java.util.List.of(fe));

        var resp = handler.handleValidation(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().code()).isEqualTo("INVALID_ARGUMENT");
        assertThat(resp.getBody().message()).isEqualTo("phone: 不能为空");
    }

    @Test
    void handleValidation_noFieldErrors_returnsGenericMessage() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        org.springframework.validation.BindingResult br = mock(org.springframework.validation.BindingResult.class);
        when(ex.getBindingResult()).thenReturn(br);
        when(br.getFieldErrors()).thenReturn(java.util.List.of());

        var resp = handler.handleValidation(ex);

        assertThat(resp.getBody().message()).isEqualTo("请求参数非法");
    }

    @Test
    void handleUnreadable_requiredBody_returnsBodyRequiredMessage() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "Required request body is missing", mock(org.springframework.http.HttpInputMessage.class));

        var resp = handler.handleUnreadable(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().code()).isEqualTo("INVALID_ARGUMENT");
        assertThat(resp.getBody().message()).isEqualTo("request body is required");
    }

    @Test
    void handleUnreadable_malformedJson_returnsMalformedMessage() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "JSON parse error", mock(org.springframework.http.HttpInputMessage.class));

        var resp = handler.handleUnreadable(ex);

        assertThat(resp.getBody().message()).isEqualTo("malformed JSON body");
    }

    @Test
    void handleOther_sanitizesMessageContainingSensitiveKeyword() {
        Exception ex = new RuntimeException("failed to decrypt phone contact data");

        var resp = handler.handleOther(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody().code()).isEqualTo("INTERNAL");
        // 含 "decrypt"/"phone"/"contact" → 脱敏
        assertThat(resp.getBody().message()).isEqualTo("服务器内部错误（详细信息已脱敏）");
    }

    @Test
    void handleOther_safeMessageReturnedAsIs() {
        Exception ex = new RuntimeException("null pointer somewhere");

        var resp = handler.handleOther(ex);

        assertThat(resp.getBody().code()).isEqualTo("INTERNAL");
        assertThat(resp.getBody().message()).isEqualTo("null pointer somewhere");
    }

    @Test
    void handleOther_nullMessage_returnsGenericError() {
        Exception ex = new RuntimeException();
        // 构造无 message 的异常
        var resp = handler.handleOther(ex);

        assertThat(resp.getBody().message()).isEqualTo("internal error");
    }

    @Test
    void handleOther_chineseSensitiveKeywordSanitized() {
        Exception ex = new RuntimeException("解密手机号失败");

        var resp = handler.handleOther(ex);

        assertThat(resp.getBody().message()).isEqualTo("服务器内部错误（详细信息已脱敏）");
    }
}
