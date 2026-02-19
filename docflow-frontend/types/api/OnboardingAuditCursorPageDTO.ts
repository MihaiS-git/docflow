import { OnboardingAuditRow } from "./OnboardingAuditRow";

export type OnboardingAuditCursorPageDTO = {
  items: OnboardingAuditRow[];
  hasMore: boolean;
  nextCursorTimestamp: string | null;
  nextCursorId: string | null;
};
