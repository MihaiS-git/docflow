"use client";

import { ReactNode } from "react";
import Dialog from "@/components/ui/Dialog";
import Button from "@/components/ui/Button";
import Input from "@/components/ui/Input";
import FormField from "@/components/ui/FormField";

type Props = {
  open: boolean;
  title: string;
  description?: ReactNode;
  confirmLabel?: string;
  cancelLabel?: string;
  requireComment?: boolean;
  comment?: string;
  onCommentChange?: (v: string) => void;
  loading?: boolean;
  onConfirm: () => void;
  onClose: () => void;
  children?: React.ReactNode;
  confirmDisabled?: boolean;
};

export default function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel = "Confirm",
  cancelLabel = "Cancel",
  requireComment = false,
  comment,
  onCommentChange,
  loading = false,
  onConfirm,
  onClose,
  children,
  confirmDisabled = false,
}: Props) {
  return (
    <Dialog open={open} onClose={onClose} title={title}>
      <div className="space-y-4">
        {description && (
          <p className="text-sm text-(--color-text-secondary)">
            {description}
          </p>
        )}

        {children}

        {requireComment && (
          <FormField label="Comment">
            <Input
              value={comment}
              onChange={(e) => onCommentChange?.(e.target.value)}
              placeholder="Required comment"
            />
          </FormField>
        )}

        <div className="flex justify-end gap-2 pt-2">
          <Button variant="ghost" onClick={onClose}>
            {cancelLabel}
          </Button>

          <Button
            variant="danger"
            loading={loading}
            disabled={
              confirmDisabled || (requireComment && !comment?.trim())
            }
            onClick={onConfirm}
          >
            {confirmLabel}
          </Button>
        </div>
      </div>
    </Dialog>
  );
}