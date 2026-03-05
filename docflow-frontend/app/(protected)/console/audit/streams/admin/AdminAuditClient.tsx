"use client";

import { createAuditStreamClient } from "@/components/audit/createAuditStreamClient";
import { adminAuditStream } from "@/lib/audit/streams/adminAuditStream";

export const AdminAuditClient =
  createAuditStreamClient(adminAuditStream);