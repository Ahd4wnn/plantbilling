import { useCallback, useEffect, useMemo, useState } from "react";
import { Button } from "@/components/Button";
import { Spinner } from "@/components/Spinner";
import { TextInput } from "@/components/TextInput";
import { BottomSheet } from "@/components/BottomSheet";
import { ConfirmDialog } from "@/components/ConfirmDialog";
import { friendlyError } from "@/api/client";
import { formatINR, toPaise, fromPaise } from "@/lib/money";
import { formatDateTime } from "@/lib/datetime";
import {
  listBorrowings,
  createBorrowing,
  payBorrowing,
  deleteBorrowing,
  type Borrowing,
  type BorrowingStatus,
} from "@/api/borrowings";
import { HandCoins, Plus, Trash2, Check, Phone } from "lucide-react";

function fmt(v: string | null | undefined): string {
  return formatINR(toPaise(v || "0"));
}

type PayMode = "cash" | "upi" | "split";
const MODES: PayMode[] = ["cash", "upi", "split"];

/** Resolve a total + mode + cash-part into API cash/upi strings that sum to total. */
function resolveSplit(totalStr: string, mode: PayMode, splitCash: string): { cash: string; upi: string } {
  const total = toPaise(totalStr);
  if (mode === "cash") return { cash: fromPaise(total), upi: "0.00" };
  if (mode === "upi") return { cash: "0.00", upi: fromPaise(total) };
  const cash = Math.min(toPaise(splitCash), total);
  return { cash: fromPaise(cash), upi: fromPaise(total - cash) };
}

export function BorrowingsPage() {
  const [items, setItems] = useState<Borrowing[]>([]);
  const [outstanding, setOutstanding] = useState("0");
  const [status, setStatus] = useState<BorrowingStatus>("all");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [addOpen, setAddOpen] = useState(false);
  const [payTarget, setPayTarget] = useState<Borrowing | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<Borrowing | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await listBorrowings(status);
      setItems(data.items);
      setOutstanding(data.total_outstanding);
    } catch (e) {
      setError(friendlyError(e, "Couldn't load borrowings."));
    } finally {
      setLoading(false);
    }
  }, [status]);

  useEffect(() => {
    load();
  }, [load]);

  const confirmDelete = async () => {
    if (!deleteTarget) return;
    setBusyId(deleteTarget.id);
    try {
      await deleteBorrowing(deleteTarget.id);
      setDeleteTarget(null);
      await load();
    } catch (e) {
      setError(friendlyError(e, "Couldn't delete."));
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center gap-3">
        <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-primary-50 text-primary-700">
          <HandCoins className="h-6 w-6" />
        </div>
        <div className="flex-1">
          <h1 className="text-xl font-bold text-ink">Money borrowed</h1>
          <p className="text-sm text-ink-soft">Track money you borrowed from people and mark it paid.</p>
        </div>
      </div>

      {/* Outstanding total */}
      <div className="rounded-2xl border border-border bg-white p-5 shadow-sm">
        <p className="text-sm font-semibold text-ink-soft">Still to pay back</p>
        <p className="mt-1 text-3xl font-extrabold tracking-tight text-danger">{fmt(outstanding)}</p>
      </div>

      <div className="flex items-center gap-2">
        <div className="flex flex-1 gap-1 rounded-control bg-surface-muted p-1">
          {(["all", "open", "paid"] as BorrowingStatus[]).map((s) => (
            <button
              key={s}
              type="button"
              onClick={() => setStatus(s)}
              aria-pressed={status === s}
              className={`h-9 flex-1 rounded-control text-sm font-bold capitalize transition-colors ${status === s ? "bg-primary-600 text-white" : "text-ink-soft"}`}
            >
              {s === "open" ? "To pay" : s}
            </button>
          ))}
        </div>
        <Button variant="primary" size="tap" className="font-bold" onClick={() => setAddOpen(true)}>
          <Plus className="h-5 w-5" /> Add
        </Button>
      </div>

      {loading ? (
        <div className="flex justify-center py-16"><Spinner className="h-8 w-8 text-primary-600" /></div>
      ) : error ? (
        <div className="py-10 text-center text-danger font-semibold space-y-2">
          <p>{error}</p>
          <Button variant="secondary" size="tap" onClick={load}>Try again</Button>
        </div>
      ) : items.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-border bg-white p-10 text-center text-ink-soft">
          {status === "paid" ? "No paid-off borrowings yet." : "Nothing borrowed. Tap Add to record a borrowing."}
        </div>
      ) : (
        <div className="space-y-3">
          {items.map((b) => (
            <div key={b.id} className="rounded-2xl border border-border bg-white p-4 shadow-sm">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="truncate text-lg font-bold text-ink">{b.lender_name}</span>
                    {b.is_paid ? (
                      <span className="shrink-0 rounded-full bg-success-soft px-2 py-0.5 text-xs font-bold text-success">Paid</span>
                    ) : toPaise(b.outstanding) < toPaise(b.amount) ? (
                      <span className="shrink-0 rounded-full bg-amber-100 px-2 py-0.5 text-xs font-bold text-amber-700">Partly paid</span>
                    ) : null}
                  </div>
                  {b.lender_phone && (
                    <a href={`tel:${b.lender_phone}`} className="mt-0.5 inline-flex items-center gap-1 text-sm text-ink-soft">
                      <Phone className="h-3.5 w-3.5" /> {b.lender_phone}
                    </a>
                  )}
                  <p className="mt-1 text-xs text-ink-soft">
                    {formatDateTime(b.created_at)} · received by {b.method}
                    {b.is_paid && b.paid_at ? ` · paid by ${b.paid_method}` : ""}
                  </p>
                  {b.remarks && <p className="mt-1 text-sm text-ink">{b.remarks}</p>}
                </div>
                <div className="shrink-0 text-right">
                  <div className={`text-xl font-extrabold tracking-tight ${b.is_paid ? "text-ink-soft line-through" : "text-ink"}`}>{fmt(b.amount)}</div>
                  {!b.is_paid && toPaise(b.outstanding) < toPaise(b.amount) && (
                    <div className="text-xs font-bold text-danger">{fmt(b.outstanding)} left</div>
                  )}
                </div>
              </div>

              <div className="mt-3 flex items-center justify-end gap-2 border-t border-border pt-3">
                {!b.is_paid && (
                  <Button variant="primary" size="tap" className="font-bold" onClick={() => setPayTarget(b)}>
                    <Check className="h-5 w-5" /> Pay back
                  </Button>
                )}
                <button
                  type="button"
                  onClick={() => setDeleteTarget(b)}
                  disabled={busyId === b.id}
                  aria-label="Delete borrowing"
                  className="flex h-10 w-10 items-center justify-center rounded-control border border-border text-ink-soft hover:bg-surface-muted disabled:opacity-50"
                >
                  <Trash2 className="h-5 w-5" />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {addOpen && <AddSheet onClose={() => setAddOpen(false)} onSaved={() => { setAddOpen(false); load(); }} />}
      {payTarget && <PaySheet borrowing={payTarget} onClose={() => setPayTarget(null)} onSaved={() => { setPayTarget(null); load(); }} />}

      <ConfirmDialog
        open={deleteTarget !== null}
        title="Delete this borrowing?"
        body={deleteTarget ? `Remove the ${fmt(deleteTarget.amount)} borrowed from ${deleteTarget.lender_name}? This can't be undone.` : ""}
        confirmLabel="Delete"
        destructive
        onConfirm={confirmDelete}
        onCancel={() => setDeleteTarget(null)}
      />
    </div>
  );
}

/** Cash / UPI / Split picker with a cash-part field for split. */
function MethodPicker({ total, mode, setMode, splitCash, setSplitCash }: {
  total: string; mode: PayMode; setMode: (m: PayMode) => void; splitCash: string; setSplitCash: (v: string) => void;
}) {
  const upiPart = useMemo(() => resolveSplit(total, "split", splitCash).upi, [total, splitCash]);
  return (
    <div className="space-y-4">
      <div>
        <label className="field-label">Method</label>
        <div className="grid grid-cols-3 gap-2">
          {MODES.map((m) => (
            <button
              key={m}
              type="button"
              onClick={() => { setMode(m); if (m === "split" && !splitCash) setSplitCash(total); }}
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
    </div>
  );
}

function AddSheet({ onClose, onSaved }: { onClose: () => void; onSaved: () => void }) {
  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [amount, setAmount] = useState("");
  const [mode, setMode] = useState<PayMode>("cash");
  const [splitCash, setSplitCash] = useState("");
  const [remarks, setRemarks] = useState("");
  const [saving, setSaving] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const canSave = name.trim().length > 0 && toPaise(amount) > 0 && !saving;

  const submit = async () => {
    setSaving(true);
    setErr(null);
    try {
      const { cash, upi } = resolveSplit(amount, mode, splitCash);
      await createBorrowing({
        lender_name: name.trim(),
        lender_phone: phone.trim() || null,
        amount: fromPaise(toPaise(amount)),
        cash_amount: cash,
        upi_amount: upi,
        remarks: remarks.trim() || null,
      });
      onSaved();
    } catch (e) {
      setErr(friendlyError(e, "Couldn't save."));
      setSaving(false);
    }
  };

  return (
    <BottomSheet
      open
      onClose={onClose}
      title="Add borrowing"
      footer={<Button variant="primary" size="action" className="w-full font-bold" disabled={!canSave} loading={saving} onClick={submit}>Save borrowing</Button>}
    >
      <div className="space-y-4">
        <TextInput label="Borrowed from (name)" value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Ramesh" />
        <TextInput label="Phone number (optional)" value={phone} onChange={(e) => setPhone(e.target.value.replace(/[^0-9+ ]/g, ""))} inputMode="tel" />
        <TextInput label="Amount (₹)" value={amount} onChange={(e) => setAmount(e.target.value.replace(/[^0-9.]/g, ""))} inputMode="decimal" />
        <MethodPicker total={amount} mode={mode} setMode={setMode} splitCash={splitCash} setSplitCash={setSplitCash} />
        <TextInput label="Remarks (optional)" value={remarks} onChange={(e) => setRemarks(e.target.value)} />
        {err && <p className="text-sm font-semibold text-danger">{err}</p>}
      </div>
    </BottomSheet>
  );
}

function PaySheet({ borrowing, onClose, onSaved }: { borrowing: Borrowing; onClose: () => void; onSaved: () => void }) {
  // Repay the whole outstanding by default, but allow paying only part of it.
  const [amount, setAmount] = useState(fromPaise(toPaise(borrowing.outstanding)));
  const [mode, setMode] = useState<PayMode>("cash");
  const [splitCash, setSplitCash] = useState("");
  const [saving, setSaving] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const owed = toPaise(borrowing.outstanding);
  const pay = toPaise(amount);
  const tooMuch = pay > owed;
  const canSave = pay > 0 && !tooMuch && !saving;

  const submit = async () => {
    setSaving(true);
    setErr(null);
    try {
      const { cash, upi } = resolveSplit(amount, mode, splitCash);
      await payBorrowing(borrowing.id, { paid_cash_amount: cash, paid_upi_amount: upi });
      onSaved();
    } catch (e) {
      setErr(friendlyError(e, "Couldn't record the repayment."));
      setSaving(false);
    }
  };

  return (
    <BottomSheet
      open
      onClose={onClose}
      title={`Pay back — ${borrowing.lender_name}`}
      footer={<Button variant="primary" size="action" className="w-full font-bold" disabled={!canSave} loading={saving} onClick={submit}>Pay back {fmt(amount)}</Button>}
    >
      <div className="space-y-4">
        <div className="flex items-center justify-between rounded-control border border-border bg-slate-50 px-4 py-3">
          <span className="text-base font-semibold text-ink-soft">Still owed</span>
          <span className="text-xl font-bold text-ink">{fmt(borrowing.outstanding)}</span>
        </div>
        <TextInput
          label="Paying back now (₹)"
          value={amount}
          onChange={(e) => setAmount(e.target.value.replace(/[^0-9.]/g, ""))}
          inputMode="decimal"
        />
        {tooMuch && <p className="-mt-2 text-sm font-semibold text-danger">Can't be more than the {fmt(borrowing.outstanding)} owed.</p>}
        <p className="text-sm text-ink-soft">How did you pay it back?</p>
        <MethodPicker total={amount} mode={mode} setMode={setMode} splitCash={splitCash} setSplitCash={setSplitCash} />
        {err && <p className="text-sm font-semibold text-danger">{err}</p>}
      </div>
    </BottomSheet>
  );
}
