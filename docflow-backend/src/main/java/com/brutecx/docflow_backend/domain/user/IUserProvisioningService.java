package com.brutecx.docflow_backend.domain.user;

import com.brutecx.docflow_backend.domain.tenant.Tenant;

import java.util.UUID;


public interface IUserProvisioningService {
    User provisionInvitedUser(
            Tenant tenant,
            String email,
            String firstName,
            String lastName,
            String jobTitle,
            String department
    );

}
