"use client";

import { ReactNode, useEffect, useState } from "react";
import { z } from "zod";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";

import Dialog from "@/components/ui/Dialog";
import Button from "@/components/ui/Button";
import Input from "@/components/ui/Input";
import FormField from "@/components/ui/FormField";
import { normalizeError } from "@/lib/api/normalizeError";

type Props = {
  open: boolean;
  title: string;
  description?: ReactNode;
  confirmLabel?: string;
  cancelLabel?: string;
  requireComment?: boolean;
  initialComment?: string;
  loading?: boolean;
  onConfirm: (comment: string) => void | Promise<void>;
  onClose: () => void;
  children?: React.ReactNode;
  confirmDisabled?: boolean;
};

const commentSchema = z.object({
  comment: z.string().trim().min(1, "Comment is required"),
});

type CommentFormValues = z.infer<typeof commentSchema>;

export default function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel = "Confirm",
  cancelLabel = "Cancel",
  requireComment = false,
  initialComment = "",
  loading = false,
  onConfirm,
  onClose,
  children,
  confirmDisabled = false,
}: Props) {
  const [serverError, setServerError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitted },
  } = useForm<CommentFormValues>({
    resolver: zodResolver(commentSchema),
    defaultValues: {
      comment: initialComment,
    },
    mode: "onSubmit",
    reValidateMode: "onChange",
  });

  useEffect(() => {
    if (open) {
      reset({ comment: initialComment });
    }
  }, [open, initialComment, reset]);

  const handleValidSubmit = async (values: CommentFormValues) => {
    try {
      setServerError(null);

      const comment = requireComment ? values.comment.trim() : "";
      await onConfirm(comment);
    } catch (err) {
      const apiError = normalizeError(err);
      setServerError(apiError.response?.message ?? apiError.message);
    }
  };

  const handleConfirmClick = () => {
    setServerError(null);

    if (!requireComment) {
      void handleValidSubmit({ comment: "" });
      return;
    }

    void handleSubmit(handleValidSubmit)();
  };

  const handleClose = () => {
    setServerError(null);
    onClose();
  };

  const showCommentError = requireComment && isSubmitted && !!errors.comment;

  return (
    <Dialog open={open} onClose={handleClose} title={title}>
      <div className="space-y-4">
        {description && (
          <p className="text-sm text-(--color-text-secondary)">{description}</p>
        )}

        {children}

        {requireComment && (
          <FormField label="Comment (required)">
            <Input
              {...register("comment")}
              placeholder="Required comment"
              aria-invalid={showCommentError}
              aria-describedby="comment-help comment-error"
            />

            <p
              id="comment-help"
              className="mt-1 text-xs text-(--color-text-muted)"
            >
              Required for audit purposes.
            </p>

            {showCommentError && (
              <p
                id="comment-error"
                className="mt-1 text-xs text-(--color-error)"
              >
                {errors.comment?.message}
              </p>
            )}
          </FormField>
        )}

        {serverError && (
          <div className="rounded-md border border-(--color-error) bg-(--color-error)/10 px-3 py-2 text-sm text-(--color-error)">
            {serverError}
          </div>
        )}

        <div className="flex justify-end gap-2 pt-2">
          <Button variant="ghost" onClick={handleClose}>
            {cancelLabel}
          </Button>

          <Button
            variant="danger"
            loading={loading}
            disabled={confirmDisabled}
            onClick={handleConfirmClick}
          >
            {confirmLabel}
          </Button>
        </div>
      </div>
    </Dialog>
  );
}
