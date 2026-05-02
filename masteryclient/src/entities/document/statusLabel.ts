import type { DocumentStatus } from "./types";

export function formatDocumentStatus(status: DocumentStatus | string): string {
  if (status === "NEEDS_REVIEW") return "NEEDS REVIEW";
  return status;
}
