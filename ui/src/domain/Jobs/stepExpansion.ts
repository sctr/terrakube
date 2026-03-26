import type { JobStep } from "../types";

const CLOSED_BY_DEFAULT_STEP_NAMES = new Set(["manual approval pending", "apply pending"]);
const CLOSED_BY_DEFAULT_STEP_STATUSES = new Set(["waitingApproval", "notExecuted", "pending"]);

export const shouldStepBeExpandedByDefault = (item: Pick<JobStep, "name" | "status">) => {
  const normalizedName = item.name.trim().toLowerCase();
  const isApprovalPendingStep = CLOSED_BY_DEFAULT_STEP_NAMES.has(normalizedName);

  if (!isApprovalPendingStep) {
    return true;
  }

  return !CLOSED_BY_DEFAULT_STEP_STATUSES.has(item.status);
};
