"use client";

import PageContainer from "@/components/layout/PageContainer";
import { AuthenticationAuditClient } from "./AuthenticationAuditClient";
import PageHeader from "@/components/layout/PageHeader";

export default function AuthenticationAuditPage() {
  return (
    <PageContainer>
      <PageHeader
        title="Audit — Authentication"
        description="Track authentication events including login, logout, and failed attempts across the system."
      />

      <AuthenticationAuditClient />
    </PageContainer>
  );
}