import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { formatDocumentStatus } from "../entities/document/statusLabel";
import type {
  DocumentDetail,
  DocumentType,
  LineItemForm,
  UpdateDocumentPayload,
} from "../entities/document/types";
import { API_BASE_URL } from "../shared/apiBase";

function mapLineItemsFromApi(
  items: DocumentDetail["lineItems"]
): LineItemForm[] {
  if (!items?.length) return [];
  return items.map((item, i) => ({
    id: item.id,
    lineNo: i + 1,
    description: item.description ?? "",
    quantity: Number(item.quantity) || 0,
    unitPrice: Number(item.unitPrice) || 0,
    lineTaxAmount: Number(item.lineTaxAmount) || 0,
    lineTotal: Number(item.lineTotal) || 0,
  }));
}

export function DocumentDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [document, setDocument] = useState<DocumentDetail | null>(null);
  const [form, setForm] = useState<UpdateDocumentPayload>({});
  const [lineItems, setLineItems] = useState<LineItemForm[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [editMode, setEditMode] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    async function fetchDocument() {
      try {
        setLoading(true);
        setError(null);
        const res = await fetch(`${API_BASE_URL}/documents/fetch/${id}`);
        if (!res.ok) {
          throw new Error("Failed to fetch document details");
        }
        const data = await res.json();
        setDocument(data);
        setForm({
          supplierName: data.supplierName ?? "",
          documentNumber: data.documentNumber ?? "",
          documentType: data.documentType,
          issueDate: data.issueDate ?? "",
          dueDate: data.dueDate ?? "",
          currencyCode: data.currencyCode ?? "",
          subtotal: data.subtotal ?? 0,
          taxAmount: data.taxAmount ?? 0,
          discountAmount: data.discountAmount ?? 0,
          totalAmount: data.totalAmount ?? 0,
        });
        setLineItems(mapLineItemsFromApi(data.lineItems));
      } catch {
        setError("Unable to load document details.");
      } finally {
        setLoading(false);
      }
    }

    fetchDocument();
  }, [id]);

  const status = document?.status ?? "UPLOADED";
  const statusBadgeClass =
    status === "VALIDATED"
      ? "bg-success"
      : status === "NEEDS_REVIEW"
      ? "bg-warning text-dark"
      : status === "REJECTED"
      ? "bg-danger"
      : "bg-primary";

  function getIssueClass(severity: "INFO" | "WARNING" | "ERROR") {
    if (severity === "ERROR") return "alert-danger";
    if (severity === "WARNING") return "alert-warning";
    return "alert-info";
  }

  async function saveCorrections(opts?: {
    status?: "REJECTED";
    confirmFinalize?: boolean;
  }) {
    if (!id) return;
    try {
      setSaving(true);
      setError(null);
      const payload: UpdateDocumentPayload = {
        ...form,
        lineItems: lineItems.map((row, idx) => ({
          lineNo: row.lineNo || idx + 1,
          description: row.description,
          quantity: row.quantity,
          unitPrice: row.unitPrice,
          lineTaxAmount: row.lineTaxAmount,
          lineTotal: row.lineTotal,
        })),
        confirmFinalize: opts?.confirmFinalize === true,
        changeReason: "Manual correction from review screen",
        changedBy: "reviewer",
        status: opts?.status,
      };

      const response = await fetch(`${API_BASE_URL}/documents/update/${id}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });

      if (!response.ok) {
        throw new Error("Failed to save corrections");
      }

      const updated = await response.json();
      setDocument(updated);
      setForm({
        supplierName: updated.supplierName ?? "",
        documentNumber: updated.documentNumber ?? "",
        documentType: updated.documentType,
        issueDate: updated.issueDate ?? "",
        dueDate: updated.dueDate ?? "",
        currencyCode: updated.currencyCode ?? "",
        subtotal: updated.subtotal ?? 0,
        taxAmount: updated.taxAmount ?? 0,
        discountAmount: updated.discountAmount ?? 0,
        totalAmount: updated.totalAmount ?? 0,
      });
      setLineItems(mapLineItemsFromApi(updated.lineItems));
      setEditMode(false);
    } catch {
      setError("Failed to save document corrections.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <main className="container py-5">
      <div className="mb-4">
        <h1 className="fw-bold mb-2">Document Details</h1>
        <p className="text-muted mb-0">
          Review extracted data, line items, and validation issues for document
          #{id}.
        </p>
      </div>

      <button
        className="btn btn-outline-secondary btn-sm"
        onClick={() => navigate("/")}
      >
        ← Back to Dashboard
      </button>
      <div className="d-inline-flex gap-2 ms-2">
        <button
          className="btn btn-sm btn-outline-primary"
          onClick={() => {
            if (editMode && document) {
              setForm({
                supplierName: document.supplierName ?? "",
                documentNumber: document.documentNumber ?? "",
                documentType: document.documentType,
                issueDate: document.issueDate ?? "",
                dueDate: document.dueDate ?? "",
                currencyCode: document.currencyCode ?? "",
                subtotal: document.subtotal ?? 0,
                taxAmount: document.taxAmount ?? 0,
                discountAmount: document.discountAmount ?? 0,
                totalAmount: document.totalAmount ?? 0,
              });
              setLineItems(mapLineItemsFromApi(document.lineItems));
            }
            setEditMode((prev) => !prev);
          }}
        >
          {editMode ? "Cancel edit" : "Edit fields"}
        </button>
        {editMode && (
          <>
            <button
              className="btn btn-sm btn-success"
              onClick={() => saveCorrections()}
              disabled={saving}
            >
              {saving ? "Saving..." : "Save corrections"}
            </button>
            <button
              className="btn btn-sm btn-primary"
              onClick={() => saveCorrections({ confirmFinalize: true })}
              disabled={saving}
              title="Saves only if there are no validation issues"
            >
              Confirm final version
            </button>
            <button
              className="btn btn-sm btn-danger"
              onClick={() => saveCorrections({ status: "REJECTED" })}
              disabled={saving}
            >
              Reject
            </button>
          </>
        )}
      </div>

      {loading && (
        <div className="alert alert-info mt-3">Loading document details...</div>
      )}
      {error && <div className="alert alert-danger mt-3">{error}</div>}

      <div className="row g-4">
        <div className="col-lg-8">
          <section className="card shadow-sm border-0 mb-4">
            <div className="card-body">
              <h5 className="card-title mb-3">Extracted Data</h5>

              <div className="row g-3">
                <div className="col-md-6">
                  <label className="form-label">Supplier / Company</label>
                  <input
                    className="form-control"
                    disabled={!editMode}
                    value={form.supplierName ?? ""}
                    onChange={(e) =>
                      setForm((prev) => ({ ...prev, supplierName: e.target.value }))
                    }
                    placeholder="Not extracted yet"
                  />
                </div>

                <div className="col-md-6">
                  <label className="form-label">Document number</label>
                  <input
                    className="form-control"
                    disabled={!editMode}
                    value={form.documentNumber ?? ""}
                    onChange={(e) =>
                      setForm((prev) => ({ ...prev, documentNumber: e.target.value }))
                    }
                    placeholder="Not extracted yet"
                  />
                </div>

                <div className="col-md-6">
                  <label className="form-label">Document type</label>
                  <select
                    className="form-select"
                    disabled={!editMode}
                    value={form.documentType ?? ""}
                    onChange={(e) =>
                      setForm((prev) => ({
                        ...prev,
                        documentType: (e.target.value || undefined) as DocumentType | undefined,
                      }))
                    }
                  >
                    <option value="">Select type</option>
                    <option value="INVOICE">INVOICE</option>
                    <option value="PURCHASE_ORDER">PURCHASE_ORDER</option>
                  </select>
                </div>

                <div className="col-md-6">
                  <label className="form-label">Currency</label>
                  <input
                    className="form-control"
                    disabled={!editMode}
                    value={form.currencyCode ?? ""}
                    onChange={(e) =>
                      setForm((prev) => ({ ...prev, currencyCode: e.target.value }))
                    }
                    placeholder="BAM / EUR / USD"
                  />
                </div>

                <div className="col-md-6">
                  <label className="form-label">Issue date</label>
                  <input
                    className="form-control"
                    type="date"
                    disabled={!editMode}
                    value={form.issueDate ?? ""}
                    onChange={(e) =>
                      setForm((prev) => ({ ...prev, issueDate: e.target.value }))
                    }
                    placeholder="Not extracted yet"
                  />
                </div>

                <div className="col-md-6">
                  <label className="form-label">Due date</label>
                  <input
                    className="form-control"
                    type="date"
                    disabled={!editMode}
                    value={form.dueDate ?? ""}
                    onChange={(e) =>
                      setForm((prev) => ({ ...prev, dueDate: e.target.value }))
                    }
                    placeholder="Not extracted yet"
                  />
                </div>

                <div className="col-md-3">
                  <label className="form-label">Subtotal</label>
                  <input
                    className="form-control"
                    type="number"
                    step="0.01"
                    disabled={!editMode}
                    value={form.subtotal ?? 0}
                    onChange={(e) =>
                      setForm((prev) => ({
                        ...prev,
                        subtotal: Number(e.target.value),
                      }))
                    }
                    placeholder="0.00"
                  />
                </div>

                <div className="col-md-3">
                  <label className="form-label">Tax</label>
                  <input
                    className="form-control"
                    type="number"
                    step="0.01"
                    disabled={!editMode}
                    value={form.taxAmount ?? 0}
                    onChange={(e) =>
                      setForm((prev) => ({
                        ...prev,
                        taxAmount: Number(e.target.value),
                      }))
                    }
                    placeholder="0.00"
                  />
                </div>

                <div className="col-md-3">
                  <label className="form-label">Discount</label>
                  <input
                    className="form-control"
                    type="number"
                    step="0.01"
                    disabled={!editMode}
                    value={form.discountAmount ?? 0}
                    onChange={(e) =>
                      setForm((prev) => ({
                        ...prev,
                        discountAmount: Number(e.target.value),
                      }))
                    }
                    placeholder="0.00"
                  />
                </div>

                <div className="col-md-3">
                  <label className="form-label">Total</label>
                  <input
                    className="form-control"
                    type="number"
                    step="0.01"
                    disabled={!editMode}
                    value={form.totalAmount ?? 0}
                    onChange={(e) =>
                      setForm((prev) => ({
                        ...prev,
                        totalAmount: Number(e.target.value),
                      }))
                    }
                    placeholder="0.00"
                  />
                </div>
              </div>
            </div>
          </section>

          <section className="card shadow-sm border-0">
            <div className="card-body">
              <div className="d-flex justify-content-between align-items-center mb-3">
                <h5 className="card-title mb-0">Line Items</h5>
                {editMode && (
                  <button
                    type="button"
                    className="btn btn-sm btn-outline-secondary"
                    onClick={() =>
                      setLineItems((prev) => [
                        ...prev,
                        {
                          lineNo: prev.length + 1,
                          description: "",
                          quantity: 1,
                          unitPrice: 0,
                          lineTaxAmount: 0,
                          lineTotal: 0,
                        },
                      ])
                    }
                  >
                    Add row
                  </button>
                )}
              </div>

              <div className="table-responsive">
                <table className="table table-hover align-middle mb-0">
                  <thead className="table-light">
                    <tr>
                      <th>Description</th>
                      <th>Quantity</th>
                      <th>Unit price</th>
                      <th>Tax</th>
                      <th>Total</th>
                      {editMode && <th style={{ width: "1%" }} />}
                    </tr>
                  </thead>

                  <tbody>
                    {lineItems.length === 0 ? (
                      <tr>
                        <td
                          colSpan={editMode ? 6 : 5}
                          className="text-center text-muted py-4"
                        >
                          No line items extracted yet.
                        </td>
                      </tr>
                    ) : (
                      lineItems.map((row, index) => (
                        <tr key={row.id ?? `new-${index}`}>
                          <td>
                            {editMode ? (
                              <input
                                className="form-control form-control-sm"
                                value={row.description}
                                onChange={(e) =>
                                  setLineItems((prev) =>
                                    prev.map((r, i) =>
                                      i === index
                                        ? { ...r, description: e.target.value }
                                        : r
                                    )
                                  )
                                }
                              />
                            ) : (
                              row.description
                            )}
                          </td>
                          <td>
                            {editMode ? (
                              <input
                                type="number"
                                className="form-control form-control-sm"
                                step="0.01"
                                value={row.quantity}
                                onChange={(e) =>
                                  setLineItems((prev) =>
                                    prev.map((r, i) =>
                                      i === index
                                        ? {
                                            ...r,
                                            quantity: Number(e.target.value),
                                          }
                                        : r
                                    )
                                  )
                                }
                              />
                            ) : (
                              row.quantity
                            )}
                          </td>
                          <td>
                            {editMode ? (
                              <input
                                type="number"
                                className="form-control form-control-sm"
                                step="0.01"
                                value={row.unitPrice}
                                onChange={(e) =>
                                  setLineItems((prev) =>
                                    prev.map((r, i) =>
                                      i === index
                                        ? {
                                            ...r,
                                            unitPrice: Number(e.target.value),
                                          }
                                        : r
                                    )
                                  )
                                }
                              />
                            ) : (
                              row.unitPrice
                            )}
                          </td>
                          <td>
                            {editMode ? (
                              <input
                                type="number"
                                className="form-control form-control-sm"
                                step="0.01"
                                value={row.lineTaxAmount}
                                onChange={(e) =>
                                  setLineItems((prev) =>
                                    prev.map((r, i) =>
                                      i === index
                                        ? {
                                            ...r,
                                            lineTaxAmount: Number(
                                              e.target.value
                                            ),
                                          }
                                        : r
                                    )
                                  )
                                }
                              />
                            ) : (
                              row.lineTaxAmount
                            )}
                          </td>
                          <td>
                            {editMode ? (
                              <input
                                type="number"
                                className="form-control form-control-sm"
                                step="0.01"
                                value={row.lineTotal}
                                onChange={(e) =>
                                  setLineItems((prev) =>
                                    prev.map((r, i) =>
                                      i === index
                                        ? {
                                            ...r,
                                            lineTotal: Number(e.target.value),
                                          }
                                        : r
                                    )
                                  )
                                }
                              />
                            ) : (
                              row.lineTotal
                            )}
                          </td>
                          {editMode && (
                            <td>
                              <button
                                type="button"
                                className="btn btn-sm btn-outline-danger"
                                onClick={() =>
                                  setLineItems((prev) =>
                                    prev.filter((_, i) => i !== index)
                                  )
                                }
                              >
                                ×
                              </button>
                            </td>
                          )}
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          </section>
        </div>

        <div className="col-lg-4">
          <section className="card shadow-sm border-0 mb-4">
            <div className="card-body">
              <h5 className="card-title mb-3">Document Status</h5>
              <span className={`badge ${statusBadgeClass}`}>
                {formatDocumentStatus(status)}
              </span>
            </div>
          </section>

          <section className="card shadow-sm border-0 mb-4">
            <div className="card-body">
              <h5 className="card-title mb-3">File Info</h5>

              <div className="mb-2">
                <div className="text-muted small">File name</div>
                <div className="fw-semibold">
                  {document?.originalFileName ?? "Not loaded yet"}
                </div>
              </div>

              <div>
                <div className="text-muted small">File type</div>
                <div className="fw-semibold">
                  {document?.fileType ?? "Not loaded yet"}
                </div>
              </div>
            </div>
          </section>

          <section className="card shadow-sm border-0">
            <div className="card-body">
              <h5 className="card-title mb-3">Validation Issues</h5>
              {!document?.validationIssues ||
              document.validationIssues.length === 0 ? (
                <div className="alert alert-secondary mb-0">
                  No validation issues available yet.
                </div>
              ) : (
                <div className="d-grid gap-2">
                  {document.validationIssues.map((issue) => (
                    <div
                      key={issue.id}
                      className={`alert ${getIssueClass(issue.severity)} mb-0 py-2`}
                    >
                      <div className="fw-semibold">{issue.issueType}</div>
                      <div className="small">{issue.message}</div>
                      {issue.fieldName && (
                        <div className="small text-muted">
                          Field: {issue.fieldName}
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
          </section>
        </div>
      </div>
    </main>
  );
}
