"use client";

import { createAuditStreamClient } from "@/components/audit/createAuditStreamClient";
import { authenticationStream } from "@/lib/audit/streams/authenticationStream";

export const AuthenticationAuditClient =
  createAuditStreamClient(authenticationStream);