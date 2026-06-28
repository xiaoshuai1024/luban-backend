package com.luban.backend.operatorside.controller;
import com.luban.backend.operatorside.controller.LeadController;

import com.luban.backend.operatorside.service.LeadService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * LeadController 集成测试（T-be-9）：
 * 验证 keyword 参数绑定 + 解密查看端点路由 + 参数透传到 service。
 * 纯 MockMvc standalone（不加载 Spring 上下文），避免 AuthFilter/MyBatis 依赖。
 */
class LeadControllerIT {

    private final LeadService leadService = mock(LeadService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new LeadController(leadService)).build();

    @Test
    void listAcceptsKeywordParamAndForwards() throws Exception {
        when(leadService.list(eq("site-1"), any(), any(), any(), eq("张三"), eq(1), eq(20)))
                .thenReturn(Map.of("list", List.of(), "total", 0, "page", 1, "pageSize", 20));

        mockMvc.perform(get("/leads")
                        .param("siteId", "site-1")
                        .param("keyword", "张三"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
        verify(leadService).list("site-1", null, null, null, "张三", 1, 20);
    }

    @Test
    void getContactEndpointReturnsDecrypted() throws Exception {
        when(leadService.getContact(eq("site-1"), eq("lead-1"), any()))
                .thenReturn(Map.of("phone", "13812345678", "name", "张三"));

        mockMvc.perform(get("/leads/lead-1/contact")
                        .param("siteId", "site-1")
                        .header("X-User-ID", "user-9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("13812345678"));
        verify(leadService).getContact("site-1", "lead-1", "user-9");
    }

    @Test
    void listDefaultsPageAndSize() throws Exception {
        when(leadService.list(anyString(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(Map.of("list", List.of(), "total", 0, "page", 1, "pageSize", 20));

        mockMvc.perform(get("/leads").param("siteId", "site-1"))
                .andExpect(status().isOk());
        // 默认 page=1, size=20
        verify(leadService).list("site-1", null, null, null, null, 1, 20);
    }
}
