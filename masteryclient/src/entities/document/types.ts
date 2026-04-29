export type DocumentStatus =
  | "UPLOADED"
  | "NEEDS_REVIEW"
  | "VALIDATED"
  | "REJECTED";

export type DocumentType = "INVOICE" | "PURCHASE_ORDER";

export interface LineItem {
  id: number;
  description: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
}

export interface ValidationIssue {
  id: number;
  issueType: string;
  fieldName?: string;
  message: string;
  severity: "INFO" | "WARNING" | "ERROR";
  isResolved: boolean;
}

export interface DocumentSummary {
  id: number;
  supplierName?: string;
  documentNumber?: string;
  documentType?: DocumentType;
  currencyCode?: string;
  totalAmount?: number;
  status: DocumentStatus;
}

export type FileType = "PDF" | "IMAGE" | "CSV" | "TXT";

export interface UploadedDocument {
  id: number;
  originalFileName: string;
  fileType: FileType;
  status: DocumentStatus;
}