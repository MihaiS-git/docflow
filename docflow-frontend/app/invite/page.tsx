import { Suspense } from "react";
import InviteClient from "./InviteClient";

export const dynamic = "force-dynamic";

export default function InvitePage() {
  return (
    <Suspense fallback={<div>Loading…</div>}>
      <InviteClient />
    </Suspense>
  );
}
