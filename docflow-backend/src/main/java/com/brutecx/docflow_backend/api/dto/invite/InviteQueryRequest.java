package com.brutecx.docflow_backend.api.dto.invite;

import com.brutecx.docflow_backend.domain.invite.InviteStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

public record InviteQueryRequest(

        String email,
        InviteStatus status,

        SortField sortBy,
        SortDirection sortDir,

        @Min(0)
        Integer page,

        @Positive
        Integer size

) {

    public int resolvedPage() {
        return page != null ? page : 0;
    }

    public int resolvedSize() {
        return size != null ? size : 20;
    }

    public SortField resolvedSortField() {
        return sortBy != null ? sortBy : SortField.CREATED_AT;
    }

    public SortDirection resolvedSortDir() {
        return sortDir != null ? sortDir : SortDirection.DESC;
    }

    public enum SortField {
        CREATED_AT,
        EXPIRES_AT,
        EMAIL,
        STATUS
    }

    public enum SortDirection {
        ASC,
        DESC
    }
}