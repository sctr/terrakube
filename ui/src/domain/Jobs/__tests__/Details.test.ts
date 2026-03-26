import { JobStatus } from "../../types";
import { shouldStepBeExpandedByDefault } from "../stepExpansion";

const buildStep = (name: string, status: JobStatus) => {
  return {
    name,
    status,
  };
};

describe("shouldStepBeExpandedByDefault", () => {
  it("keeps manual approval pending collapsed while approval is still pending", () => {
    expect(shouldStepBeExpandedByDefault(buildStep("manual approval pending", JobStatus.WaitingApproval))).toBe(false);
  });

  it("keeps apply pending collapsed until apply starts", () => {
    expect(shouldStepBeExpandedByDefault(buildStep("apply pending", JobStatus.Pending))).toBe(false);
    expect(shouldStepBeExpandedByDefault(buildStep("apply pending", JobStatus.NotExecuted))).toBe(false);
    expect(shouldStepBeExpandedByDefault(buildStep("apply pending", JobStatus.Running))).toBe(true);
  });

  it("expands once approval has been given", () => {
    expect(shouldStepBeExpandedByDefault(buildStep("manual approval pending", JobStatus.Approved))).toBe(true);
  });

  it("does not affect other step names", () => {
    expect(shouldStepBeExpandedByDefault(buildStep("terraform plan", JobStatus.Pending))).toBe(true);
  });
});
