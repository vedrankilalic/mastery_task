export interface LineItemForm {
  id?: number;
  lineNo: number;
  description: string;
  quantity: number;
  unitPrice: number;
  lineTaxAmount: number;
  lineTotal: number;
}

export interface DocumentDetail extends UploadedDocument {
  supplierName?: string;
  documentNumber?: string;
  documentType?: DocumentType;
  currencyCode?: string;
  issueDate?: string;
  dueDate?: string;
  subtotal?: number;
  taxAmount?: number;
  totalAmount?: number;
  lineItems?: {
    id: number;
    description: string;
    quantity: number;
    unitPrice: number;
    lineTaxAmount: number;
    lineTotal: number;
  }[];
  validationIssues?: ValidationIssue[];
}


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
  issueType:
    | "MISSING_FIELD"
    | "TOTAL_MISMATCH"
    | "INVALID_DATE"
    | "LINE_CALC_ERROR"
    | "DUPLICATE_DOC_NUMBER"
    | "OTHER";
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
  currencyCode?: string;
  totalAmount?: number;
  validationIssues?: ValidationIssue[];
}

export interface UpdateDocumentPayload {
  supplierName?: string;
  documentNumber?: string;
  documentType?: DocumentType;
  issueDate?: string;
  dueDate?: string;
  currencyCode?: string;
  subtotal?: number;
  taxAmount?: number;
  totalAmount?: number;
  lineItems?: Array<{
    lineNo?: number;
    description?: string;
    quantity?: number;
    unitPrice?: number;
    lineTaxAmount?: number;
    lineTotal?: number;
  }>;
  confirmFinalize?: boolean;
  changeReason?: string;
  changedBy?: string;
  status?: DocumentStatus;
}