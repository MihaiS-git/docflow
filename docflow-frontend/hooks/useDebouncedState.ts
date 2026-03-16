"use client";

import { useEffect, useState } from "react";

/**
 * useDebouncedState
 *
 * Returns a normal state value and a debounced mirror of that value.
 * The debounced value updates only after the specified delay.
 *
 * Useful for search filters that should not trigger API calls on every keystroke.
 */
export function useDebouncedState<T>(
  initialValue: T,
  delay = 350
) {
  const [value, setValue] = useState<T>(initialValue);
  const [debounced, setDebounced] = useState<T>(initialValue);

  useEffect(() => {
    const timer = setTimeout(() => {
      setDebounced(value);
    }, delay);

    return () => clearTimeout(timer);
  }, [value, delay]);

  return [value, setValue, debounced] as const;
}