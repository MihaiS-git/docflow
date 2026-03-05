"use client";

import { createAuditStreamClient } from "@/components/audit/createAuditStreamClient";
import { identityProjectionStream } from "@/lib/audit/streams/identityProjectionStream";

export const IdentityProjectionAuditClient =
  createAuditStreamClient(identityProjectionStream);