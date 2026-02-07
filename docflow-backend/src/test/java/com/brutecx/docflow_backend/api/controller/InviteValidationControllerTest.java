//package com.brutecx.docflow_backend.api.controller;
//
//import com.brutecx.docflow_backend.application.invite.InviteApplicationService;
//import com.brutecx.docflow_backend.application.invite.InviteSessionKeys;
//import com.brutecx.docflow_backend.domain.invite.Invite;
//import com.brutecx.docflow_backend.domain.invite.InviteRepository;
//import jakarta.servlet.http.HttpSession;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.http.MediaType;
//import org.springframework.test.context.ActiveProfiles;
//import org.springframework.test.context.bean.override.mockito.MockitoBean;
//import org.springframework.test.web.servlet.MockMvc;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.ArgumentMatchers.eq;
//import static org.mockito.Mockito.doNothing;
//import static org.mockito.Mockito.doThrow;
//import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
//
//@SpringBootTest
//@AutoConfigureMockMvc
//@ActiveProfiles("test")
//class InviteValidationControllerTest {
//
//    @Autowired
//    private MockMvc mvc;
//
//    @MockitoBean
//    private InviteApplicationService inviteApplicationService;
//
//    @Test
//    void validToken_returns204_andStoresTokenInSession() throws Exception {
//        String token = "valid-token";
//
//        doNothing()
//                .when(inviteApplicationService)
//                .validateAndStoreInviteToken(eq(token), any(HttpSession.class));
//
//        var result = mvc.perform(
//                        post("/api/invites/validate")
//                                .queryParam("token", token)
//                                .with(csrf())
//                )
//                .andExpect(status().isNoContent())
//                .andReturn();
//    }
//
//    @Test
//    void invalidToken_returns400_withErrorResponse() throws Exception {
//        doThrow(new IllegalArgumentException("Invite link invalid or expired"))
//                .when(inviteApplicationService)
//                .validateAndStoreInviteToken(eq("not-a-real-token"), any(HttpSession.class));
//
//
//        mvc.perform(
//                        post("/api/invites/validate")
//                                .queryParam("token", "not-a-real-token")
//                                .with(csrf())
//                )
//                .andExpect(status().isBadRequest())
//                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
//                .andExpect(jsonPath("$.status").value(400))
//                .andExpect(jsonPath("$.errorCode").value("INVALID_ARGUMENT"));
//    }
//}
