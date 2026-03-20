import PageContainer from "@/components/layout/PageContainer";
import AuditKeysClient from "./AuditKeysClient";
import PageHeader from "@/components/layout/PageHeader";

export default function Page() {
  return (
    <PageContainer>
      <PageHeader
        title="Security — Audit Signing Keys"
        description="Review public signing keys used to verify exported audit JSONL files."
      />

      <AuditKeysClient />
    </PageContainer>
  );
}