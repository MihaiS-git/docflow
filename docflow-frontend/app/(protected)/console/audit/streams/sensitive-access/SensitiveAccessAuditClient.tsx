"use client";

import { createAuditStreamClient } from "@/components/audit/createAuditStreamClient";
import { sensitiveAccessStream } from "@/lib/audit/streams/sensitiveAccessStream";

export const SensitiveAccessAuditClient =
  createAuditStreamClient(sensitiveAccessStream);