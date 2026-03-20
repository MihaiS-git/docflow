"use client";

import PageContainer from "@/components/layout/PageContainer";
import { OnboardingAuditClient } from "./OnboardingAuditClient";
import PageHeader from "@/components/layout/PageHeader";

export default function OnboardingAuditPage() {
  return (
    <PageContainer>
      <PageHeader
        title="Audit — Onboarding"
        description="Trace user and tenant onboarding flows, including provisioning and initial access setup."
      />

      <OnboardingAuditClient />
    </PageContainer>
  );
}