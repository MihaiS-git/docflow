"use client";

import { createAuditStreamClient } from "@/components/audit/createAuditStreamClient";
import { rbacDeniedStream } from "@/lib/audit/streams/rbacDeniedStream";

export const RbacDeniedAuditClient =
  createAuditStreamClient(rbacDeniedStream);