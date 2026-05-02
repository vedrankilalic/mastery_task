# Smart Document Processing System

A full-stack document processing application for ingesting invoices and purchase orders, extracting structured data, validating extracted values, and providing a review interface for manual correction and final approval.

The system is designed around a realistic document-processing workflow: extracted data is not blindly trusted. Every document goes through validation, detected issues are surfaced to the user, and uncertain or incomplete documents are moved into review instead of being automatically accepted.

---

## Overview

This project implements a document ingestion and review pipeline for business documents such as invoices and purchase orders.

Supported input formats:

- PDF
- Images: PNG, JPG/JPEG, WebP
- CSV
- TXT

The application extracts key business fields, validates them, stores the processed result, and allows users to review and correct the extracted data before final validation.

Some input documents are intentionally incomplete or inconsistent. The application handles this by extracting what it can, reporting validation issues, and keeping those documents in `NEEDS_REVIEW` until corrected.

---

## Features Implemented

| Area | Implementation |
|---|---|
| Document ingestion | Multipart upload for PDF, image, CSV, and TXT files |
| Text extraction | PDF extraction with Apache PDFBox, OCR extraction for images using Tesseract, custom parsers for CSV/TXT |
| Structured parsing | Rule-based parsing for document type, supplier, document number, dates, currency, totals, and line items |
| Validation engine | Missing required fields, invalid dates, subtotal/tax/total mismatches, line item calculation checks, duplicate document numbers |
| Review interface | Dashboard and detail view for reviewing extracted data, editing fields, editing line items, and saving corrections |
| Status workflow | `UPLOADED`, `NEEDS_REVIEW`, `VALIDATED`, `REJECTED` |
| Persistence | MySQL database with JPA entities for documents, line items, validation issues, and document revisions |
| API documentation | Swagger UI powered by springdoc-openapi |
| Revision history | Stores previous document snapshots when corrections are saved |
| Manual finalization | Prevents final validation when blocking validation errors still exist |

---

## Tech Stack

### Backend

- Java 17
- Spring Boot
- Spring Data JPA
- MySQL
- Lombok
- Apache PDFBox
- Tesseract OCR
- springdoc-openapi
- dotenv-java for optional local environment configuration

### Frontend

- React
- TypeScript
- Vite
- Bootstrap 5
- React Router

### OCR

Image OCR is handled through Tesseract. Tesseract must be installed on the host machine and available through `PATH`, or configured through the application property:

```properties
ocr.tesseract.path=C:\\Program Files\\Tesseract-OCR\\tesseract.exe