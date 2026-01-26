package com.brutecx.docflow_backend.user;

import com.brutecx.docflow_backend.security.session.SessionRevocationService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final SessionRevocationService sessionRevocationService;

    public List<AdminUserResponseDTO> listUsers() {
        return userRepository.findAll().stream()
                .map(u -> new AdminUserResponseDTO(
                        u.getId(),
                        u.getEmail(),
                        u.getStatus()
                ))
                .toList();
    }

    @Transactional
    public void lockUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (user.getStatus() == UserStatus.LOCKED) {
            return; // idempotent
        }
        user.lock();
        sessionRevocationService.revokeSessionsBySubject(
                user.getExternalSubjectId()
        );
    }

    @Transactional
    public void disableUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (user.getStatus() == UserStatus.DISABLED) {
            return; // idempotent
        }
        user.disable();
        sessionRevocationService.revokeSessionsBySubject(
                user.getExternalSubjectId()
        );
    }

    @Transactional
    public void activateUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (user.getStatus() == UserStatus.ACTIVE) {
            return; // idempotent
        }
        user.activate();
    }
}
