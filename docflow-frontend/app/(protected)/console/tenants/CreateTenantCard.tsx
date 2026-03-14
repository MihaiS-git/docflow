"use client";

import { memo } from "react";
import Card from "@/components/ui/Card";
import Input from "@/components/ui/Input";
import Button from "@/components/ui/Button";

type Props = {
  name: string;
  loading: boolean;
  error: string | null;
  setName: (v: string) => void;
  onSubmit: (e: React.FormEvent<HTMLFormElement>) => void;
};

function CreateTenantCard({
  name,
  loading,
  error,
  setName,
  onSubmit,
}: Props) {
  return (
    <Card title="Create tenant">
      <form
        onSubmit={onSubmit}
        className="flex flex-col gap-4 sm:flex-row sm:items-end"
      >
        <div className="flex-1">
          <Input
            label="Tenant name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            disabled={loading}
          />
        </div>

        <Button type="submit" loading={loading}>
          Create
        </Button>
      </form>

      {error && (
        <p className="mt-3 text-sm text-(--color-error)">
          {error}
        </p>
      )}
    </Card>
  );
}

export default memo(CreateTenantCard);