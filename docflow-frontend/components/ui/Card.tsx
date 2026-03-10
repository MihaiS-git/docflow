import { ReactNode } from "react";

type Props = {
  children: ReactNode;
  className?: string;
};

export default function Card({ children, className = "" }: Props) {
  return (
    <div
      className={`
        bg-(--color-surface)
        border border-(--color-border)
        rounded-lg
        p-6
        ${className}
      `}
    >
      {children}
    </div>
  );
}