"use client";

import { useEffect, useState } from "react";
import Dialog from "@/components/ui/Dialog";
import Input from "@/components/ui/Input";
import Button from "@/components/ui/Button";
import FormField from "@/components/ui/FormField";
import type { AdminTenant } from "@/types/admin/Tenant";
import { updateTenant } from "@/lib/admin/adminTenants";

type Props = {
  tenant: AdminTenant;
  open: boolean;
  onClose: () => void;
  onSaved: () => void;
};

export default function EditTenantDialog({
  tenant,
  open,
  onClose,
  onSaved,
}: Props) {
  const [name, setName] = useState("");
  const [dataRegion, setDataRegion] = useState("");
  const [retentionDays, setRetentionDays] = useState<string>("");

  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!open) return;

    setName(tenant.name);
    setDataRegion(tenant.dataRegion ?? "");
    setRetentionDays(
      tenant.retentionDays != null ? String(tenant.retentionDays) : ""
    );
  }, [tenant, open]);

  async function handleSave() {
    setLoading(true);

    try {
      await updateTenant(tenant.id, {
        name: name.trim(),
        dataRegion: dataRegion || undefined,
        retentionDays:
          retentionDays === "" ? undefined : Number(retentionDays),
      });

      onSaved();
      onClose();
    } finally {
      setLoading(false);
    }
  }

  return (
    <Dialog open={open} onClose={onClose} title="Edit tenant">
      <div className="space-y-4">
        <FormField label="Tenant name">
          <Input
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
        </FormField>

        <FormField label="Data region">
          <Input
            value={dataRegion}
            onChange={(e) => setDataRegion(e.target.value)}
            placeholder="Optional region identifier"
          />
        </FormField>

        <FormField label="Retention days">
          <Input
            type="number"
            value={retentionDays}
            onChange={(e) => setRetentionDays(e.target.value)}
            placeholder="Optional"
          />
        </FormField>

        <div className="flex justify-end gap-2 pt-2">
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>

          <Button loading={loading} onClick={handleSave}>
            Save
          </Button>
        </div>
      </div>
    </Dialog>
  );
}