"use client";

import { createAuditStreamClient } from "@/components/audit/createAuditStreamClient";
import { lifecycleDeniedStream } from "@/lib/audit/streams/lifecycleDeniedStream";

export const LifecycleDeniedAuditClient =
  createAuditStreamClient(lifecycleDeniedStream);