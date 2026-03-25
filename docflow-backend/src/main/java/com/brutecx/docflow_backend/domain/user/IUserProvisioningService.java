package com.brutecx.docflow_backend.domain.user;

public interface IUserProvisioningService {

    User provisionInvitedUser(
            String externalSubjectId,
            String email,
            String firstName,
            String lastName,
            String jobTitle,
            String department
    );
}