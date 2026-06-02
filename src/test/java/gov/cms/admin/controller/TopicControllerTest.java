package gov.cms.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.cms.admin.config.SecurityConfig;
import gov.cms.admin.dto.TopicContentItemsRequest;
import gov.cms.admin.dto.TopicRequest;
import gov.cms.admin.entity.Topic;
import gov.cms.admin.security.JwtAuthenticationFilter;
import gov.cms.admin.service.CustomUserDetailsService;
import gov.cms.admin.service.TopicService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TopicController.class)
@Import(SecurityConfig.class)
class TopicControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private TopicService topicService;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> {
            FilterChain filterChain = invocation.getArgument(2);
            filterChain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test
    @WithMockUser(authorities = "topic:manage:view")
    void getTopicsReturnsOk() throws Exception {
        Topic topic = new Topic();
        topic.setId(1L);
        topic.setName("专题一");
        topic.setCode("topic-1");
        when(topicService.getTopics(1L, null, null)).thenReturn(List.of(topic));

        mockMvc.perform(get("/api/topics").param("siteId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("topic-1"));
    }

    @Test
    @WithMockUser(authorities = "topic:manage:create")
    void createTopicReturnsCreated() throws Exception {
        TopicRequest request = new TopicRequest();
        request.setSiteId(1L);
        request.setName("专题一");
        request.setCode("topic-1");
        request.setSlug("topic-1");
        request.setAggregationMode("manual");

        Topic topic = new Topic();
        topic.setId(1L);
        topic.setCode("topic-1");
        when(topicService.createTopic(any())).thenReturn(topic);

        mockMvc.perform(post("/api/topics")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    @WithMockUser(authorities = "topic:manage:update")
    void replaceTopicContentItemsReturnsOk() throws Exception {
        TopicContentItemsRequest request = new TopicContentItemsRequest();
        request.setSiteId(1L);
        request.setArticleIds(List.of(1L, 2L));

        mockMvc.perform(post("/api/topics/{id}/content-items", 1L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "topic:manage:delete")
    void deleteReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/topics/{id}", 1L).param("siteId", "1"))
                .andExpect(status().isNoContent());
    }
}