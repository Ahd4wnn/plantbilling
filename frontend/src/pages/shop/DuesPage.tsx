import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ChevronLeft, Search, Phone, Clock } from "lucide-react";
import { useAuth } from "@/store/auth";
import { fetchBills, type BillListItem } from "@/api/sales";
import { collectDue } from "@/api/settlements";
import { friendlyError } from "@/api/client";
import { formatINR, toPaise, fromPaise } from "@/lib/money";
import { formatDateTime } from "@/lib/datetime";
import { Button } from "@/components/Button";
import { Spinner } from "@/components/Spinner";
import { TextInput } from "@/components/TextInput";
import { BottomSheet } from "@/components/BottomSheet";

function fmt(v: string | null | undefined): string {
  return formatINR(toPaise(v || "0"));
}

type Mode = "cash" | "upi" | "split";

/** Resolve total + mode + cash-part into API cash/upi strings that sum to total. */
function resolveSplit(totalStr: string, mode: Mode, splitCash: string): { cash: string; upi: string } {
  const total = toPaise(totalStr);
  if (mode === "cash") return { cash: fromPaise(total), upi: "0.00" };
  if (mode === "upi") return { cash: "0.00", upi: fromPaise(total) };
  const cash = Math.min(toPaise(splitCash), total);
  return { cash: fromPaise(cash), upi: fromPaise(total - cash) };
}

export function DuesPage() {
  const navigate = useNavigate();
  const isManager = useAuth((s) => s.user?.role) === "manager";
  const [dues, setDues] = useState<BillListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [target, setTarget] = useState<BillListItem | null>(null);
  const [toast, setToast] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await fetchBills({ has_due: true, limit: 100 });
      setDues(res.items);
    } catch (e) {
      setError(friendlyError(e, "Couldn't load dues."));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return dues;
    return dues.filter(
      (d) => (d.customer_name ?? "").toLowerCase().includes(q) || (d.customer_phone ?? "").includes(q),
    );
  }, [dues, query]);

  const totalOwed = useMemo(() => dues.reduce((s, d) => s + toPaise(d.due_amount), 0), [dues]);

  const flash = (m: string) => {
    setToast(m);
    setTimeout(() => setToast(null), 3000);
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
        <h1 className="text-2xl font-extrabold text-ink">Dues</h1>
      </div>

      <div className="rounded-2xl border border-border bg-white p-5 shadow-sm">
        <p className="text-sm font-semibold text-ink-soft">Total still owed to the shop</p>
        <p className="mt-1 text-3xl font-extrabold tracking-tight text-amber-700">{formatINR(totalOwed)}</p>
      </div>

      <div className="relative">
        <Search className="pointer-events-none absolute left-3 top-1/2 h-5 w-5 -translate-y-1/2 text-ink-soft" />
        <input
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search by name or phone…"
          className="h-12 w-full rounded-control border-2 border-border bg-white pl-10 pr-3 text-base text-ink focus:border-primary-600 focus:outline-none focus:ring-4 focus:ring-primary-600/20"
        />
      </div>

      {loading ? (
        <div className="flex justify-center py-16"><Spinner className="h-8 w-8 text-primary-600" /></div>
      ) : error ? (
        <div className="py-10 text-center">
          <p className="text-base font-semibold text-danger">{error}</p>
          <Button variant="secondary" size="tap" className="mt-4" onClick={load}>Try again</Button>
        </div>
      ) : filtered.length === 0 ? (
        <p className="py-12 text-center text-base text-ink-soft">
          {dues.length === 0 ? "No outstanding dues. Everyone's paid up!" : "No dues match your search."}
        </p>
      ) : (
        <div className="space-y-3">
          {filtered.map((d) => (
            <div key={d.id} className="rounded-2xl border border-border bg-white p-4 shadow-sm">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <div className="truncate text-lg font-bold text-ink">{d.customer_name?.trim() || "Walk-in customer"}</div>
                  {d.customer_phone && (
                    <a href={`tel:${d.customer_phone}`} className="mt-0.5 inline-flex items-center gap-1 text-sm text-ink-soft">
                      <Phone className="h-3.5 w-3.5" /> {d.customer_phone}
                    </a>
                  )}
                  <p className="mt-1 text-xs text-ink-soft">{formatDateTime(d.created_at)}</p>
                  {d.pending_settlement && (
                    <span className="mt-1 inline-flex items-center gap-1 rounded-full bg-amber-100 px-2 py-0.5 text-xs font-bold text-amber-700">
                      <Clock className="h-3 w-3" /> Waiting for approval
                    </span>
                  )}
                </div>
                <div className="shrink-0 text-right">
                  <div className="text-xl font-extrabold tracking-tight text-amber-700">{fmt(d.due_amount)}</div>
                </div>
              </div>
              {!d.pending_settlement && (
                <div className="mt-3 flex justify-end border-t border-border pt-3">
                  <Button variant="primary" size="tap" className="font-bold" onClick={() => setTarget(d)}>
                    Collect
                  </Button>
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {target && (
        <CollectSheet
          due={target}
          isManager={isManager}
          onClose={() => setTarget(null)}
          onDone={(msg) => { setTarget(null); flash(msg); load(); }}
        />
      )}

      {toast && (
        <div className="fixed inset-x-0 top-4 z-[70] flex justify-center px-4">
          <div className="rounded-control bg-ink px-5 py-3 text-base font-semibold text-white shadow-card-lg">{toast}</div>
        </div>
      )}
    </section>
  );
}

function CollectSheet({ due, isManager, onClose, onDone }: {
  due: BillListItem; isManager: boolean; onClose: () => void; onDone: (msg: string) => void;
}) {
  const [amount, setAmount] = useState(fromPaise(toPaise(due.due_amount)));
  const [mode, setMode] = useState<Mode>("cash");
  const [splitCash, setSplitCash] = useState("");
  const [saving, setSaving] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const owed = toPaise(due.due_amount);
  const pay = toPaise(amount);
  const tooMuch = pay > owed;
  const canSave = pay > 0 && !tooMuch && !saving;
  const upiPart = resolveSplit(amount, "split", splitCash).upi;

  const submit = async () => {
    setSaving(true);
    setErr(null);
    try {
      const { cash, upi } = resolveSplit(amount, mode, splitCash);
      const res = await collectDue(due.id, cash, upi);
      const remaining = owed - pay;
      if (res.status === "pending") {
        onDone("Sent to the manager for approval.");
      } else if (remaining > 0) {
        onDone(`Collected ${formatINR(pay)} — ${formatINR(remaining)} still owed.`);
      } else {
        onDone(`Collected ${formatINR(pay)}.`);
      }
    } catch (e) {
      setErr(friendlyError(e, "Couldn't record the collection."));
      setSaving(false);
    }
  };

  const MODES: Mode[] = ["cash", "upi", "split"];

  return (
    <BottomSheet
      open
      onClose={onClose}
      title={`Collect due — ${due.customer_name?.trim() || "Walk-in"}`}
      footer={
        <Button variant="primary" size="action" className="w-full font-bold" disabled={!canSave} loading={saving} onClick={submit}>
          {isManager ? `Collect ${fmt(amount)}` : "Send for approval"}
        </Button>
      }
    >
      <div className="space-y-4">
        <div className="flex items-center justify-between rounded-control border border-border bg-slate-50 px-4 py-3">
          <span className="text-base font-semibold text-ink-soft">Amount owed</span>
          <span className="text-xl font-bold text-amber-700">{fmt(due.due_amount)}</span>
        </div>
        <TextInput
          label="Collecting now (₹)"
          value={amount}
          onChange={(e) => setAmount(e.target.value.replace(/[^0-9.]/g, ""))}
          inputMode="decimal"
        />
        {tooMuch && <p className="-mt-2 text-sm font-semibold text-danger">Can't be more than the {fmt(due.due_amount)} owed.</p>}
        <div>
          <label className="field-label">How was it paid?</label>
          <div className="grid grid-cols-3 gap-2">
            {MODES.map((m) => (
              <button
                key={m}
                type="button"
                onClick={() => { setMode(m); if (m === "split" && !splitCash) setSplitCash(amount); }}
                className={`rounded-control border px-2 py-2.5 text-sm font-bold capitalize ${mode === m ? "bg-primary-50 text-primary-700 border-primary-300 ring-2 ring-primary-600/10" : "bg-white text-ink-soft border-border"}`}
              >
                {m}
              </button>
            ))}
          </div>
        </div>
        {mode === "split" && (
          <>
            <TextInput label="Cash part (₹)" value={splitCash} onChange={(e) => setSplitCash(e.target.value.replace(/[^0-9.]/g, ""))} inputMode="decimal" />
            <p className="-mt-2 text-sm text-ink-soft">UPI part: {fmt(upiPart)}</p>
          </>
        )}
        {!isManager && (
          <p className="text-sm text-ink-soft">This will be sent to your manager to approve before the due is closed.</p>
        )}
        {err && <p className="text-sm font-semibold text-danger">{err}</p>}
      </div>
    </BottomSheet>
  );
}
