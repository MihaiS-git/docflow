import { ReactNode } from "react";
import Card from "@/components/ui/Card";

type Props = {
  title: string;
  description?: string;
  children: ReactNode;
};

export default function InviteCard({
  title,
  description,
  children,
}: Props) {
  return (
    <Card className="space-y-4 max-w-md">
      <div className="space-y-1">
        <h2 className="text-lg font-semibold text-(--color-text-primary)">
          {title}
        </h2>

        {description && (
          <p className="text-sm text-(--color-text-secondary)">
            {description}
          </p>
        )}
      </div>

      {children}
    </Card>
  );
}