import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ChevronLeft, Check, X, Phone } from "lucide-react";
import {
  listPendingSettlements,
  approveSettlement,
  rejectSettlement,
  type PendingSettlement,
} from "@/api/settlements";
import { friendlyError } from "@/api/client";
import { formatINR, toPaise } from "@/lib/money";
import { formatDateTime } from "@/lib/datetime";
import { Button } from "@/components/Button";
import { Spinner } from "@/components/Spinner";

function fmt(v: string | null | undefined): string {
  return formatINR(toPaise(v || "0"));
}

function methodLabel(cash: string, upi: string): string {
  const c = toPaise(cash) > 0;
  const u = toPaise(upi) > 0;
  if (c && u) return "Split";
  if (u) return "UPI";
  return "Cash";
}

export function ApprovalsPage() {
  const navigate = useNavigate();
  const [rows, setRows] = useState<PendingSettlement[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setRows(await listPendingSettlements());
    } catch (e) {
      setError(friendlyError(e, "Couldn't load the approval queue."));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const flash = (m: string) => {
    setToast(m);
    setTimeout(() => setToast(null), 3000);
  };

  const act = async (row: PendingSettlement, kind: "approve" | "reject") => {
    setBusyId(row.id);
    try {
      if (kind === "approve") await approveSettlement(row.id);
      else await rejectSettlement(row.id);
      setRows((prev) => prev.filter((r) => r.id !== row.id));
      flash(kind === "approve" ? "Collection approved." : "Collection rejected — the due stays owed.");
    } catch (e) {
      flash(friendlyError(e, "Couldn't update the request."));
    } finally {
      setBusyId(null);
    }
  };

  return (
    <section className="space-y-5">
      <div className="flex items-center gap-2">
        <button
          type="button"
          onClick={() => navigate(-1)}
          className="flex h-tap items-center gap-1 rounded-control pr-3 text-base font-semibold text-primary-700 hover:bg-surface-muted"
        >
          <ChevronLeft className="h-5 w-5" /> Back
        </button>
        <h1 className="text-2xl font-extrabold text-ink">Approvals</h1>
      </div>
      <p className="text-base text-ink-soft">Due collections your salespeople recorded, waiting for you to confirm the money arrived.</p>

      {loading ? (
        <div className="flex justify-center py-16"><Spinner className="h-8 w-8 text-primary-600" /></div>
      ) : error ? (
        <div className="py-10 text-center">
          <p className="text-base font-semibold text-danger">{error}</p>
          <Button variant="secondary" size="tap" className="mt-4" onClick={load}>Try again</Button>
        </div>
      ) : rows.length === 0 ? (
        <p className="py-12 text-center text-base text-ink-soft">Nothing waiting for approval.</p>
      ) : (
        <div className="space-y-3">
          {rows.map((r) => (
            <div key={r.id} className="rounded-2xl border border-border bg-white p-4 shadow-sm">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <div className="truncate text-lg font-bold text-ink">{r.customer_name?.trim() || "Walk-in customer"}</div>
                  {r.customer_phone && (
                    <a href={`tel:${r.customer_phone}`} className="mt-0.5 inline-flex items-center gap-1 text-sm text-ink-soft">
                      <Phone className="h-3.5 w-3.5" /> {r.customer_phone}
                    </a>
                  )}
                  <p className="mt-1 text-xs text-ink-soft">
                    {formatDateTime(r.created_at)} · by {r.requested_by_email?.split("@")[0] ?? "staff"} · {methodLabel(r.cash_amount, r.upi_amount)}
                  </p>
                </div>
                <div className="shrink-0 text-right">
                  <div className="text-xl font-extrabold tracking-tight text-primary-700">{formatINR(toPaise(r.cash_amount) + toPaise(r.upi_amount))}</div>
                  <div className="text-xs text-ink-soft">of {fmt(r.bill_total)} bill</div>
                </div>
              </div>
              <div className="mt-3 flex gap-2 border-t border-border pt-3">
                <Button
                  variant="secondary"
                  size="tap"
                  className="flex-1 font-bold !text-danger border-2 border-danger/20"
                  disabled={busyId === r.id}
                  onClick={() => act(r, "reject")}
                >
                  <X className="h-5 w-5" /> Reject
                </Button>
                <Button
                  variant="primary"
                  size="tap"
                  className="flex-1 font-bold"
                  loading={busyId === r.id}
                  onClick={() => act(r, "approve")}
                >
                  <Check className="h-5 w-5" /> Approve
                </Button>
              </div>
            </div>
          ))}
        </div>
      )}

      {toast && (
        <div className="fixed inset-x-0 top-4 z-[70] flex justify-center px-4">
          <div className="rounded-control bg-ink px-5 py-3 text-base font-semibold text-white shadow-card-lg">{toast}</div>
        </div>
      )}
    </section>
  );
}
