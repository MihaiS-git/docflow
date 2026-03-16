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

  /**
   * Tracks the last prefix that produced zero results
   */
  const lastEmptyRef = useRef<string | null>(null);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebounced(value);
    }, delay);

    return () => window.clearTimeout(timer);
  }, [value, delay]);

  /**
   * Prevent API calls when extending a prefix that already returned empty
   */
  function shouldBlock(): boolean {
    return (
      lastEmptyRef.current !== null &&
      debounced !== "" &&
      debounced.startsWith(lastEmptyRef.current)
    );
  }

  /**
   * Register result size so the hook can update prefix guard state
   */
  function registerResult(resultCount: number) {
    if (!debounced) {
      lastEmptyRef.current = null;
      return;
    }

    lastEmptyRef.current = resultCount === 0 ? debounced : null;
  }

  /**
   * Reset prefix guard (e.g. when filters reset)
   */
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