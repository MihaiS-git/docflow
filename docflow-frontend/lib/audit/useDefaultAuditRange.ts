"use client";

import { useMemo, useState } from "react";
import { toDateTimeLocalString } from "@/lib/date/dateTimeLocal";

export function useDefaultAuditRange() {
  const now = useMemo(() => new Date(), []);
  const oneHourAgo = useMemo(
    () => new Date(now.getTime() - 3600000),
    [now],
  );

  const [from, setFrom] = useState(
    toDateTimeLocalString(oneHourAgo),
  );

  const [to, setTo] = useState(
    toDateTimeLocalString(now),
  );

  const [size, setSize] = useState(20);

  return {
    from,
    to,
    size,
    setFrom,
    setTo,
    setSize,
  };
}
