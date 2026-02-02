//package com.brutecx.docflow_backend.api.controller;
//
//import com.brutecx.docflow_backend.application.invite.InviteApplicationService;
//import jakarta.servlet.http.HttpServletRequest;
//import jakarta.servlet.http.HttpServletResponse;
//import lombok.RequiredArgsConstructor;
//import org.springframework.context.annotation.Profile;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RequestParam;
//import org.springframework.web.bind.annotation.RestController;
//
//import java.io.IOException;
//
//@RestController
//@RequiredArgsConstructor
//@Profile({"dev", "prod"})
//@RequestMapping("/api/invites")
//public class InvitePublicController {
//
//    private final InviteApplicationService inviteService;
//
//    @GetMapping("/accept")
//    public void acceptInvite(
//            @RequestParam("token") String token,
//            HttpServletRequest request,
//            HttpServletResponse response
//    ) throws IOException {
//        inviteService.validateAndStoreInviteToken(token, request.getSession(true));
//        response.sendRedirect("/oauth2/authorization/keycloak");
//    }
//}
