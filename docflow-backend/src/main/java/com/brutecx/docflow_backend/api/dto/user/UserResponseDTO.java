package com.brutecx.docflow_backend.api.dto.user;

import com.brutecx.docflow_backend.domain.tenant.Tenant;
import com.brutecx.docflow_backend.domain.user.UserStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class UserResponseDTO {

    private UUID id;
    private String externalSubjectId;
    private Tenant tenant;
    private String email;
    private String firstName;
    private String lastName;
    private String displayName;
    private String jobTitle;
    private String department;
    private String businessPhone;
    private UserStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastLoginAt;
    private String lastLoginIp;
    private String lastLoginUserAgent;

}
