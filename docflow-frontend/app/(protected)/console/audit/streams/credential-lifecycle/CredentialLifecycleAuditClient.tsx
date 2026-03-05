"use client";

import { createAuditStreamClient } from "@/components/audit/createAuditStreamClient";
import { credentialLifecycleStream } from "@/lib/audit/streams/credentialLifecycleStream";

export const CredentialLifecycleAuditClient =
  createAuditStreamClient(credentialLifecycleStream);