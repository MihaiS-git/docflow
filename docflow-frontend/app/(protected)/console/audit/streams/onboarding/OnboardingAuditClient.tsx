"use client";

import { createAuditStreamClient } from "@/components/audit/createAuditStreamClient";
import { onboardingStream } from "@/lib/audit/streams/onboardingStream";

export const OnboardingAuditClient =
  createAuditStreamClient(onboardingStream);