"use client";

import { useEffect, useRef, useState } from "react";

/**
 * Debounces a string value and prevents further requests when a prefix
 * previously returned zero results.
 *
 * Usage pattern:
 *  if (filter.shouldBlock()) return emptyResult;
 *  const res = await fetch(...filter.debounced...);
 *  filter.registerResult(res.content.length);
 */
export function useDebouncedPrefixFilter(
  value: string,
  delay: number = 350,
) {
  const [debounced, setDebounced] = useState(value);

  const lastEmptyRef = useRef<string | null>(null);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebounced(value);
    }, delay);

    return () => window.clearTimeout(timer);
  }, [value, delay]);

  function shouldBlock(): boolean {
    return (
      lastEmptyRef.current !== null &&
      debounced !== "" &&
      debounced.startsWith(lastEmptyRef.current)
    );
  }

  function registerResult(resultCount: number) {
    if (!debounced) {
      lastEmptyRef.current = null;
      return;
    }

    lastEmptyRef.current = resultCount === 0 ? debounced : null;
  }

  function reset() {
    lastEmptyRef.current = null;
  }

  return {
    debounced,
    shouldBlock,
    registerResult,
    reset,
  };
}