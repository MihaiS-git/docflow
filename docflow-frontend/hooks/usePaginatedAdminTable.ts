"use client";

import {
  useCallback,
  useEffect,
  useRef,
  useState,
  useTransition,
} from "react";

type Loader<T> = () => Promise<T>;

type Options = {
  enabled: boolean;
  queryKey: string;
  resetKeys: unknown[];
};

export function usePaginatedAdminTable<T>(
  loader: Loader<T>,
  { enabled, queryKey, resetKeys }: Options,
) {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(false);

  const [isPending, startTransition] = useTransition();

  const requestIdRef = useRef(0);
  const lastRequestedQueryKeyRef = useRef<string | null>(null);
  const loadRef = useRef<((force?: boolean) => Promise<void>) | null>(null);

  const resetKey = JSON.stringify(resetKeys);

  const load = useCallback(
    async (force = false) => {
      if (!enabled) return;

      if (!force && lastRequestedQueryKeyRef.current === queryKey) {
        return;
      }

      lastRequestedQueryKeyRef.current = queryKey;

      const requestId = ++requestIdRef.current;

      setLoading(true);

      try {
        const result = await loader();

        if (requestId !== requestIdRef.current) return;

        startTransition(() => {
          setData(result);
        });

        setLoading(false);
      } catch (err) {
        setLoading(false);
        throw err;
      } 
    },
    [enabled, loader, queryKey],
  );

  useEffect(() => {
    loadRef.current = load;
  }, [load]);

  useEffect(() => {
  let mounted = true;

  const run = async () => {
    if (!mounted) return;
    await load();
  };

  void run();

  return () => {
    mounted = false;
  };
}, [load]);

  useEffect(() => {
    lastRequestedQueryKeyRef.current = null;
  }, [resetKey]);

  const reload = useCallback(() => {
    void loadRef.current?.(true);
  }, []);

  return {
    data,
    setData,
    loading,
    isPending,
    reload,
  };
}