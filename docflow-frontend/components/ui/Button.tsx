import {
  ButtonHTMLAttributes,
  ReactNode,
  forwardRef,
} from "react";

type Variant =
  | "primary"
  | "secondary"
  | "danger"
  | "outline"
  | "ghost"
  | "link";

type Size = "sm" | "md" | "lg" | "icon";

type Props = {
  children?: ReactNode;
  variant?: Variant;
  size?: Size;
  loading?: boolean;
} & ButtonHTMLAttributes<HTMLButtonElement>;

const Button = forwardRef<HTMLButtonElement, Props>(
  (
    {
      children,
      variant = "primary",
      size = "md",
      loading = false,
      className = "",
      disabled,
      ...props
    },
    ref,
  ) => {
    const base =
      "inline-flex items-center justify-center gap-2 rounded-md font-medium select-none transition-colors duration-150 ease-out focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-(--color-focus-ring) focus-visible:ring-offset-2 focus-visible:ring-offset-(--color-focus-ring-offset) disabled:opacity-60 disabled:pointer-events-none disabled:cursor-not-allowed active:translate-y-[1px] cursor-pointer";

    const variants: Record<Variant, string> = {
      primary:
        "bg-(--color-primary) text-(--color-text-inverse) hover:bg-(--color-primary-hover)",

      secondary:
        "bg-(--color-surface-alt) text-(--color-text-primary) hover:bg-(--color-border)",

      danger:
        "bg-(--color-error) text-(--color-text-inverse) hover:bg-(--color-error-hover)",

      outline:
        "border border-(--color-border) text-(--color-text-primary) hover:bg-(--color-surface-alt)",

      ghost:
        "text-(--color-text-primary) hover:bg-(--color-surface-alt) hover:ring-1 hover:ring-(--color-border)",

      link:
        "text-(--color-primary) hover:underline px-0 py-0 h-auto",
    };

    const sizes: Record<Size, string> = {
      sm: "h-8 px-3 text-sm",
      md: "h-9 px-4 text-sm",
      lg: "h-10 px-6 text-base",
      icon: "h-9 w-9 p-0",
    };

    return (
      <button
        ref={ref}
        className={`${base} ${variants[variant]} ${sizes[size]} ${className}`}
        disabled={disabled || loading}
        {...props}
      >
        {loading && (
          <span className="h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent" />
        )}
        {children}
      </button>
    );
  },
);

Button.displayName = "Button";

export default Button;