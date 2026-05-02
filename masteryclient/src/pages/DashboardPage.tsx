import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { formatDocumentStatus } from "../entities/document/statusLabel";
import type { UploadedDocument } from "../entities/document/types";
import { API_BASE_URL } from "../shared/apiBase";

export function DashboardPage() {
  const [files, setFiles] = useState<File[]>([]);
  const [documents, setDocuments] = useState<UploadedDocument[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const navigate = useNavigate();

  useEffect(() => {
    fetchDocuments();
  }, []);

  async function fetchDocuments() {
    try {
      const res = await fetch(`${API_BASE_URL}/documents/fetch`);

      if (!res.ok) {
        throw new Error("Failed to fetch documents");
      }

      const data = await res.json();
      setDocuments(data);
    } catch (err) {
      console.error(err);
      setError("Failed to load documents");
    }
  }

  function sameFile(a: File, b: File) {
    return (
      a.name === b.name &&
      a.size === b.size &&
      a.lastModified === b.lastModified
    );
  }

  function handleFileChange(event: React.ChangeEvent<HTMLInputElement>) {
    const picked = Array.from(event.target.files ?? []);
    if (picked.length === 0) {
      return;
    }
    setFiles((prev) => {
      const next = [...prev];
      for (const file of picked) {
        if (!next.some((f) => sameFile(f, file))) {
          next.push(file);
        }
      }
      return next;
    });
    setError(null);
    event.target.value = "";
  }

  async function handleUpload() {
    if (files.length === 0) {
      setError("Please select at least one file.");
      return;
    }

    try {
      setLoading(true);
      setError(null);

      const formData = new FormData();
      files.forEach((file) => formData.append("files", file));

      const response = await fetch(`${API_BASE_URL}/documents/upload`, {
        method: "POST",
        body: formData,
      });

      if (!response.ok) {
        throw new Error("Upload failed");
      }

      const uploadedDocuments: UploadedDocument[] = await response.json();

      setDocuments((prev) => [...uploadedDocuments, ...prev]);
      setFiles([]);
      if (fileInputRef.current) {
        fileInputRef.current.value = "";
      }
    } catch {
      setError("Upload failed. Please check backend or file format.");
    } finally {
      setLoading(false);
    }
  }

  function issueSummary(doc: UploadedDocument) {
    const issues = doc.validationIssues ?? [];
    const errors = issues.filter((i) => i.severity === "ERROR").length;
    const warnings = issues.filter((i) => i.severity === "WARNING").length;
    return { count: issues.length, errors, warnings };
  }

  function totalsGroupedByCurrency(docs: UploadedDocument[]): [string, number][] {
    const map = new Map<string, number>();
    for (const d of docs) {
      if (d.totalAmount == null || Number.isNaN(Number(d.totalAmount))) continue;
      const code =
        d.currencyCode && d.currencyCode.trim() !== ""
          ? d.currencyCode.trim().toUpperCase()
          : "—";
      const n = Number(d.totalAmount);
      map.set(code, (map.get(code) ?? 0) + n);
    }
    return Array.from(map.entries()).sort((a, b) => a[0].localeCompare(b[0]));
  }

  const currencyTotals = totalsGroupedByCurrency(documents);

  function getStatusBadgeClass(status: string) {
    switch (status) {
      case "VALIDATED":
        return "bg-success";
      case "NEEDS_REVIEW":
        return "bg-warning text-dark";
      case "REJECTED":
        return "bg-danger";
      default:
        return "bg-primary";
    }
  }

  return (
    <main className="container py-5">
      <div className="mb-4">
        <h1 className="fw-bold mb-2">Mastery Files Upload</h1>
        <p className="text-muted mb-0">
          Upload business documents, extract structured data, and review
          validation status.
        </p>
      </div>

      <div className="row g-3 mb-4">
        <div className="col-md-3">
          <div className="card shadow-sm border-0">
            <div className="card-body">
              <div className="text-muted small">Total documents</div>
              <h3 className="mb-0">{documents.length}</h3>
            </div>
          </div>
        </div>

        <div className="col-md-3">
          <div className="card shadow-sm border-0">
            <div className="card-body">
              <div className="text-muted small">Needs review</div>
              <h3 className="mb-0">
                {documents.filter((d) => d.status === "NEEDS_REVIEW").length}
              </h3>
            </div>
          </div>
        </div>

        <div className="col-md-3">
          <div className="card shadow-sm border-0">
            <div className="card-body">
              <div className="text-muted small">Validated</div>
              <h3 className="mb-0">
                {documents.filter((d) => d.status === "VALIDATED").length}
              </h3>
            </div>
          </div>
        </div>

        <div className="col-md-3">
          <div className="card shadow-sm border-0">
            <div className="card-body">
              <div className="text-muted small">Rejected</div>
              <h3 className="mb-0">
                {documents.filter((d) => d.status === "REJECTED").length}
              </h3>
            </div>
          </div>
        </div>
      </div>

      {currencyTotals.length > 0 && (
        <section className="card shadow-sm border-0 mb-4">
          <div className="card-body">
            <h5 className="card-title mb-2">Totals by currency</h5>
            <p className="text-muted small mb-3">
              Sum of extracted <code>totalAmount</code> per currency across all
              documents in the list.
            </p>
            <div className="d-flex flex-wrap gap-2">
              {currencyTotals.map(([code, sum]) => (
                <span
                  key={code}
                  className="badge rounded-pill text-bg-secondary px-3 py-2"
                >
                  <span className="fw-semibold">{code}</span>
                  <span className="mx-1">·</span>
                  {sum.toLocaleString(undefined, {
                    minimumFractionDigits: 2,
                    maximumFractionDigits: 2,
                  })}
                </span>
              ))}
            </div>
          </div>
        </section>
      )}

      <section className="card shadow-sm border-0 mb-4">
        <div className="card-body">
          <h5 className="card-title mb-1">Upload documents</h5>
          <p className="text-muted small mb-3">
            Supported formats: PDF, CSV, TXT, PNG, JPG. Open &ldquo;Choose
            files&rdquo; again to add more.
          </p>

          <div className="d-flex gap-2 flex-column flex-md-row">
            <input
              ref={fileInputRef}
              className="form-control"
              type="file"
              multiple
              accept=".pdf,.csv,.txt,.png,.jpg,.jpeg,.webp"
              onChange={handleFileChange}
            />

            <button
              className="btn btn-primary px-4"
              onClick={handleUpload}
              disabled={loading}
            >
              {loading ? "Uploading..." : "Upload"}
            </button>
          </div>

          {files.length > 0 && (
            <div className="mt-3">
              <div className="d-flex align-items-center gap-2 mb-2">
                <span className="text-muted small mb-0">Selected files:</span>
                <button
                  type="button"
                  className="btn btn-link btn-sm text-decoration-none p-0"
                  onClick={() => {
                    setFiles([]);
                    if (fileInputRef.current) {
                      fileInputRef.current.value = "";
                    }
                  }}
                >
                  Clear all
                </button>
              </div>
              <div className="d-flex flex-wrap gap-2">
                {files.map((file, i) => (
                  <span
                    key={`${file.name}-${file.size}-${i}`}
                    className="badge text-bg-light border"
                  >
                    {file.name}
                  </span>
                ))}
              </div>
            </div>
          )}

          {error && <div className="alert alert-danger mt-3 mb-0">{error}</div>}
        </div>
      </section>

      <section className="card shadow-sm border-0">
        <div className="card-body">
          <div className="d-flex justify-content-between align-items-center mb-3">
            <h5 className="card-title mb-0">Documents</h5>
            <span className="text-muted small">{documents.length} total</span>
          </div>

          <div className="table-responsive">
            <table className="table table-hover align-middle mb-0">
              <thead className="table-light">
                <tr>
                  <th>File name</th>
                  <th>File type</th>
                  <th>Status</th>
                  <th>Issues</th>
                  <th>Actions</th>
                </tr>
              </thead>

              <tbody>
                {documents.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="text-center text-muted py-4">
                      No documents uploaded yet.
                    </td>
                  </tr>
                ) : (
                  documents.map((doc) => (
                    <tr key={doc.id}>
                      <td className="fw-semibold">{doc.originalFileName}</td>
                      <td>{doc.fileType}</td>
                      <td>
                        <span
                          className={`badge ${getStatusBadgeClass(doc.status)}`}
                        >
                          {formatDocumentStatus(doc.status)}
                        </span>
                      </td>
                      <td>
                        {(() => {
                          if (doc.status === "REJECTED") {
                            return (
                              <span
                                className="text-muted small"
                                title="Rejected — open Details for validation history"
                              >
                                —
                              </span>
                            );
                          }
                          const { count, errors, warnings } = issueSummary(doc);
                          if (count === 0) {
                            return (
                              <span
                                className="text-muted small"
                                title="No validation issues"
                              >
                                —
                              </span>
                            );
                          }
                          return (
                            <span
                              className={`badge ${
                                errors > 0
                                  ? "text-bg-danger"
                                  : warnings > 0
                                  ? "text-bg-warning text-dark"
                                  : "text-bg-secondary"
                              }`}
                              title={`${errors} error(s), ${warnings} warning(s)`}
                            >
                              {count}
                            </span>
                          );
                        })()}
                      </td>
                      <td>
                        <button
                          className="btn btn-sm btn-outline-primary"
                          onClick={() => navigate(`/documents/${doc.id}`)}
                        >
                          Details
                        </button>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
      </section>
    </main>
  );
}
