import { useParams } from "react-router-dom";

export function DocumentDetailPage() {
  const { id } = useParams();

  return (
    <main className="container py-5">
      <div className="mb-4">
        <h1 className="fw-bold mb-2">Document Details</h1>
        <p className="text-muted mb-0">
          Review extracted data and validation issues for document #{id}.
        </p>
      </div>

      <div className="row g-4">
        <div className="col-lg-8">
          <section className="card shadow-sm border-0 mb-4">
            <div className="card-body">
              <h5 className="card-title mb-3">Extracted Data</h5>

              <div className="row g-3">
                <div className="col-md-6">
                  <label className="form-label">Supplier name</label>
                  <input className="form-control" placeholder="Not extracted yet" disabled />
                </div>

                <div className="col-md-6">
                  <label className="form-label">Document number</label>
                  <input className="form-control" placeholder="Not extracted yet" disabled />
                </div>

                <div className="col-md-6">
                  <label className="form-label">Document type</label>
                  <input className="form-control" placeholder="Invoice / Purchase Order" disabled />
                </div>

                <div className="col-md-6">
                  <label className="form-label">Currency</label>
                  <input className="form-control" placeholder="BAM / EUR / USD" disabled />
                </div>

                <div className="col-md-4">
                  <label className="form-label">Subtotal</label>
                  <input className="form-control" placeholder="0.00" disabled />
                </div>

                <div className="col-md-4">
                  <label className="form-label">Tax</label>
                  <input className="form-control" placeholder="0.00" disabled />
                </div>

                <div className="col-md-4">
                  <label className="form-label">Total</label>
                  <input className="form-control" placeholder="0.00" disabled />
                </div>
              </div>
            </div>
          </section>

          <section className="card shadow-sm border-0">
            <div className="card-body">
              <h5 className="card-title mb-3">Line Items</h5>

              <table className="table table-hover align-middle mb-0">
                <thead className="table-light">
                  <tr>
                    <th>Description</th>
                    <th>Qty</th>
                    <th>Unit price</th>
                    <th>Total</th>
                  </tr>
                </thead>
                <tbody>
                  <tr>
                    <td colSpan={4} className="text-center text-muted py-4">
                      No line items extracted yet.
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
        </div>

        <div className="col-lg-4">
          <section className="card shadow-sm border-0 mb-4">
            <div className="card-body">
              <h5 className="card-title mb-3">Status</h5>
              <span className="badge bg-primary">UPLOADED</span>
            </div>
          </section>

          <section className="card shadow-sm border-0">
            <div className="card-body">
              <h5 className="card-title mb-3">Validation Issues</h5>

              <div className="alert alert-secondary mb-0">
                No validation issues available yet.
              </div>
            </div>
          </section>
        </div>
      </div>
    </main>
  );
}