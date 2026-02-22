import { buildRangeQueryParams } from "@/lib/audit/auditRange";
import { downloadAuditFile } from "@/lib/audit/auditDownload";

type ExtraParamsBuilder = (qs: URLSearchParams) => void;

export function createJsonlExportHandler(
  endpoint: string,
  filename: string,
  from: string,
  to: string,
  extraParams?: ExtraParamsBuilder,
  setDownloading?: (value: boolean) => void,
  setError?: (value: string | null) => void,
) {
  return async function handleExportJsonl() {
    setDownloading?.(true);
    setError?.(null);

    try {
      const qs = buildRangeQueryParams({ from, to });

      if (extraParams) {
        extraParams(qs);
      }

      await downloadAuditFile({
        path: `${endpoint}/export?${qs.toString()}`,
        filename,
      });
    } catch (e) {
      setError?.(e instanceof Error ? e.message : "Export failed");
    } finally {
      setDownloading?.(false);
    }
  };
}