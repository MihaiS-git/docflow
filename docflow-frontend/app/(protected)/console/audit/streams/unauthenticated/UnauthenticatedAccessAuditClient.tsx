"use client";

import { createAuditStreamClient } from "@/components/audit/createAuditStreamClient";
import { unauthenticatedAccessStream } from "@/lib/audit/streams/unauthenticatedAccessStream";

export const UnauthenticatedAccessAuditClient =
  createAuditStreamClient(unauthenticatedAccessStream);