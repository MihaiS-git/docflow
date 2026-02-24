"use client";

import { AuditResult } from "@/types/api/AuditResult";

type Props = {
  result: AuditResult;
};

function getClass(result: AuditResult): string {
  switch (result) {
    case "SUCCESS":
      return "px-2 py-0.5 rounded text-xs font-semibold bg-green-100 text-green-700";
    case "FAILED":
      return "px-2 py-0.5 rounded text-xs font-semibold bg-red-100 text-red-700";
    case "DENIED":
      return "px-2 py-0.5 rounded text-xs font-semibold bg-orange-100 text-orange-700";
    default:
      return "px-2 py-0.5 rounded text-xs font-semibold bg-gray-100 text-gray-700";
  }
}

export function ResultBadge({ result }: Props) {
  return <span className={getClass(result)}>{result}</span>;
}