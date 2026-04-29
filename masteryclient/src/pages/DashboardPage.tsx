import { useEffect, useState } from "react";
import type { UploadedDocument } from "../entities/document/types";

export function DashboardPage() {
  const [files, setFiles] = useState<File[]>([]);
  const [documents, setDocuments] = useState<UploadedDocument[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchDocuments();
  }, []);

  async function fetchDocuments() {
    try {
      const res = await fetch("http://localhost:8080/documents/fetch");

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

  function handleFileChange(event: React.ChangeEvent<HTMLInputElement>) {
    setFiles(Array.from(event.target.files ?? []));
    setError(null);
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

      const response = await fetch("http://localhost:8080/documents/upload", {
        method: "POST",
        body: formData,
      });

      if (!response.ok) {
        throw new Error("Upload failed");
      }

      const uploadedDocuments: UploadedDocument[] = await response.json();

      setDocuments((prev) => [...uploadedDocuments, ...prev]);
      setFiles([]);
    } catch {
      setError("Upload failed. Please check backend or file format.");
    } finally {
      setLoading(false);
    }
  }

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
              <div className="text-muted small">Uploaded</div>
              <h3 className="mb-0">
                {documents.filter((d) => d.status === "UPLOADED").length}
              </h3>
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
      </div>

      <section className="card shadow-sm border-0 mb-4">
        <div className="card-body">
          <h5 className="card-title mb-1">Upload documents</h5>
          <p className="text-muted small mb-3">
            Supported formats: PDF, CSV, TXT, PNG, JPG, JPEG, WEBP.
          </p>

          <div className="d-flex gap-2 flex-column flex-md-row">
            <input
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
              <div className="text-muted small mb-2">Selected files:</div>
              <div className="d-flex flex-wrap gap-2">
                {files.map((file) => (
                  <span key={file.name} className="badge text-bg-light border">
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
                  <th>Actions</th>
                </tr>
              </thead>

              <tbody>
                {documents.length === 0 ? (
                  <tr>
                    <td colSpan={4} className="text-center text-muted py-4">
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
                          {doc.status}
                        </span>
                      </td>
                      <td>
                        <button className="btn btn-sm btn-outline-primary">
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
