package com.luban.backend.service;

import com.luban.backend.entity.Form;
import com.luban.backend.entity.Lead;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DefaultLeadNotifyService 单测：mock RestClient 链式调用，验证 Webhook 投递语义。
 * RestClient 调用链：post() → RequestBodyUriSpec.uri() → RequestBodySpec.contentType() →
 * RequestBodySpec.body() → RequestBodySpec.retrieve()。
 */
class DefaultLeadNotifyServiceTest {

    private RestClient restClient;
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;
    private RestClient.RequestBodySpec requestBodySpec;
    private RestClient.ResponseSpec responseSpec;

    @BeforeEach
    void setUp() {
        restClient = mock(RestClient.class);
        requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        requestBodySpec = mock(RestClient.RequestBodySpec.class);
        responseSpec = mock(RestClient.ResponseSpec.class);
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(String.class))).thenReturn(requestBodyUriSpec);
        // contentType()/body() 返回 RequestBodySpec（父接口 RequestHeadersSpec<RequestBodySpec> 的自类型）
        doReturn(requestBodySpec).when(requestBodyUriSpec).contentType(any(MediaType.class));
        doReturn(requestBodySpec).when(requestBodySpec).body(any(Object.class));
        doReturn(responseSpec).when(requestBodySpec).retrieve();
    }

    private Lead sampleLead() {
        Lead lead = new Lead();
        lead.setId("lead-1");
        lead.setSiteId("site-1");
        lead.setFormId("form-1");
        lead.setStatus("new");
        lead.setContactJson("cipher-data");
        return lead;
    }

    private Form sampleForm() {
        Form form = new Form();
        form.setId("form-1");
        return form;
    }

    @Test
    void blankUrlSkipsWebhook() {
        // 单构造器：webhookUrl + RestClient（mock）
        DefaultLeadNotifyService svc = new DefaultLeadNotifyService("", restClient);
        svc.notifyNewLead(sampleLead(), sampleForm());
        // 未配置 URL：不应触碰 RestClient
        verify(restClient, never()).post();
    }

    @Test
    void postsPayloadToWebhookUrl() {
        DefaultLeadNotifyService svc = new DefaultLeadNotifyService("https://hook.example/lead", restClient);
        svc.notifyNewLead(sampleLead(), sampleForm());
        // 验证 URI 与 contentType 设置
        verify(requestBodyUriSpec, times(1)).uri(eq("https://hook.example/lead"));
        verify(requestBodyUriSpec, times(1)).contentType(eq(MediaType.APPLICATION_JSON));
        verify(requestBodySpec, times(1)).retrieve();
    }

    @Test
    void webhookFailureDoesNotThrow() {
        // retrieve() 抛异常模拟投递失败：notifyNewLead 应吞掉异常不抛穿
        doThrow(new RuntimeException("connection refused")).when(requestBodySpec).retrieve();
        DefaultLeadNotifyService svc = new DefaultLeadNotifyService("https://hook.example/lead", restClient);
        // 不应抛异常（降级为日志）
        svc.notifyNewLead(sampleLead(), sampleForm());
        verify(requestBodyUriSpec, times(1)).uri(eq("https://hook.example/lead"));
    }
}
