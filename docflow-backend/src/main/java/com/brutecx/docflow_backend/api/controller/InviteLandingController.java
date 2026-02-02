//package com.brutecx.docflow_backend.api.controller;
//
//import jakarta.servlet.http.HttpServletResponse;
//import lombok.RequiredArgsConstructor;
//import org.springframework.context.annotation.Profile;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RestController;
//
//import java.io.IOException;
//
//@RestController
//@RequiredArgsConstructor
//@Profile({"dev", "prod"})
//public class InviteLandingController {
//
//    @GetMapping("/invite/complete")
//    public void inviteComplete(HttpServletResponse response) throws IOException {
//        response.sendRedirect("/oauth2/authorization/keycloak");
//    }
//}
