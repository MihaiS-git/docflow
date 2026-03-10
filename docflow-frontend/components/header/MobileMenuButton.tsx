import { Dispatch, SetStateAction } from "react";

type MobileProps = {
  mobileOpen: boolean;
  setMobileOpen: Dispatch<SetStateAction<boolean>>;
};

export default function MobileMenuButton({
  mobileOpen,
  setMobileOpen,
}: MobileProps) {
  return (
    <button
      onClick={() => setMobileOpen((prev) => !prev)}
      className="sm:hidden inline-flex items-center justify-center rounded-md p-2 text-(--color-text-secondary) hover:bg-(--color-surface-alt) hover:text-(--color-text-primary)"
      aria-label={mobileOpen ? "Close menu" : "Open menu"}
      aria-expanded={mobileOpen}
      aria-controls="mobile-menu"
      type="button"
    >
      {mobileOpen ? (
        <svg
          viewBox="0 0 24 24"
          stroke="currentColor"
          strokeWidth="1.5"
          className="h-6 w-6"
          fill="none"
        >
          <path
            d="M6 18L18 6M6 6l12 12"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      ) : (
        <svg
          viewBox="0 0 24 24"
          stroke="currentColor"
          strokeWidth="1.5"
          className="h-6 w-6"
          fill="none"
        >
          <path
            d="M3.75 6.75h16.5M3.75 12h16.5m-16.5 5.25h16.5"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      )}
    </button>
  );
}