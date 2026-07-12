import { useCallback, useEffect, useMemo, useState } from "react";
import { useAuth } from "@/store/auth";
import { Button } from "@/components/Button";
import { Spinner } from "@/components/Spinner";
import { TextInput } from "@/components/TextInput";
import { BottomSheet } from "@/components/BottomSheet";
import { ConfirmDialog } from "@/components/ConfirmDialog";
import { friendlyError } from "@/api/client";
import { formatINR, toPaise } from "@/lib/money";
import { formatDateTime } from "@/lib/datetime";
import {
  listLabourers,
  listLabourPayments,
  createLabourer,
  updateLabourer,
  deleteLabourer,
  createLabourPayment,
  updateLabourPayment,
  deleteLabourPayment,
  type Gender,
  type Labourer,
  type LabourPayment,
} from "@/api/labour";
import { Plus, Pencil, Trash2, HardHat } from "lucide-react";

function fmt(v: string | null | undefined): string {
  return formatINR(toPaise(v || "0"));
}

export function LabourPage() {
  const user = useAuth((s) => s.user);
  const isManager = user?.role === "manager";

  const [labourers, setLabourers] = useState<Labourer[]>([]);
  const [payments, setPayments] = useState<LabourPayment[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [workerEdit, setWorkerEdit] = useState<Labourer | "new" | null>(null);
  const [payEdit, setPayEdit] = useState<LabourPayment | "new" | null>(null);
  const [deleteWorker, setDeleteWorker] = useState<Labourer | null>(null);
  const [deletePay, setDeletePay] = useState<LabourPayment | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [ls, ps] = await Promise.all([listLabourers(), listLabourPayments()]);
      setLabourers(ls);
      setPayments(ps);
    } catch (e) {
      setError(friendlyError(e, "Couldn't load labour data."));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  if (loading) {
    return (
      <div className="flex justify-center py-16">
        <Spinner className="h-8 w-8 text-primary-600" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="py-10 text-center text-danger font-semibold space-y-2">
        <p>{error}</p>
        <Button variant="secondary" size="tap" onClick={load}>Try again</Button>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-primary-50 text-primary-700">
          <HardHat className="h-6 w-6" />
        </div>
        <div>
          <h1 className="text-xl font-bold text-ink">Labour</h1>
          <p className="text-sm text-ink-soft mt-0.5">
            {isManager ? "Manage workers and record their payments." : "Record payments to workers."}
          </p>
        </div>
      </div>

      <Button
        variant="primary"
        size="action"
        className="w-full font-bold"
        onClick={() => {
          if (labourers.length === 0) {
            alert("Add a worker first before recording a payment.");
            return;
          }
          setPayEdit("new");
        }}
      >
        Record a payment
      </Button>

      {/* Workers */}
      <div className="rounded-2xl border border-border bg-white p-5 shadow-sm space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-bold text-ink">Workers</h2>
          {isManager && (
            <Button variant="secondary" size="tap" className="flex items-center gap-1.5 font-bold" onClick={() => setWorkerEdit("new")}>
              <Plus className="h-4 w-4" /> Add
            </Button>
          )}
        </div>
        {labourers.length === 0 ? (
          <p className="text-sm text-ink-soft">
            {isManager ? "No workers yet. Tap Add to set up your first worker." : "No workers set up yet. Ask your manager to add them."}
          </p>
        ) : (
          <div className="divide-y divide-border border-t border-border">
            {labourers.map((l) => (
              <div key={l.id} className="py-3 flex items-center justify-between gap-3">
                <div className="min-w-0">
                  <p className="font-semibold text-ink truncate">{l.name}</p>
                  <p className="text-sm text-ink-soft">
                    {l.gender === "male" ? "Male" : "Female"} · Wage {fmt(l.default_wage)} · OT {fmt(l.overtime_rate)}/hr
                    {l.phone ? ` · ${l.phone}` : ""}
                  </p>
                </div>
                {isManager && (
                  <div className="flex items-center gap-2 shrink-0">
                    <button
                      type="button"
                      onClick={() => setWorkerEdit(l)}
                      className="flex h-10 w-10 items-center justify-center rounded-xl border border-border bg-white text-ink hover:bg-surface-muted active:scale-95"
                      title="Edit worker"
                    >
                      <Pencil className="h-4 w-4" />
                    </button>
                    <button
                      type="button"
                      onClick={() => setDeleteWorker(l)}
                      className="flex h-10 w-10 items-center justify-center rounded-xl border border-red-200 bg-red-50 text-red-500 hover:bg-red-100 active:scale-95"
                      title="Remove worker"
                    >
                      <Trash2 className="h-4 w-4" />
                    </button>
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Payments */}
      <div className="rounded-2xl border border-border bg-white p-5 shadow-sm space-y-4">
        <h2 className="text-lg font-bold text-ink">Recent payments</h2>
        {payments.length === 0 ? (
          <p className="text-sm text-ink-soft">No payments recorded yet.</p>
        ) : (
          <div className="divide-y divide-border border-t border-border">
            {payments.map((p) => (
              <div key={p.id} className="py-3 flex items-center justify-between gap-3">
                <div className="min-w-0">
                  <p className="font-semibold text-ink truncate">{p.labourer_name}</p>
                  <p className="text-sm text-ink-soft">
                    {formatDateTime(p.created_at)} · wage {fmt(p.wage_amount)}
                    {toPaise(p.overtime_amount) > 0 ? ` · OT ${p.overtime_hours}h ${fmt(p.overtime_amount)}` : ""}
                  </p>
                  {p.note && <p className="text-sm text-ink-soft">{p.note}</p>}
                </div>
                <div className="flex items-center gap-2 shrink-0">
                  <span className="font-bold text-ink">{fmt(p.total_amount)}</span>
                  {isManager && (
                    <>
                      <button
                        type="button"
                        onClick={() => setPayEdit(p)}
                        className="flex h-9 w-9 items-center justify-center rounded-xl border border-border bg-white text-ink hover:bg-surface-muted active:scale-95"
                        title="Edit payment"
                      >
                        <Pencil className="h-4 w-4" />
                      </button>
                      <button
                        type="button"
                        onClick={() => setDeletePay(p)}
                        className="flex h-9 w-9 items-center justify-center rounded-xl border border-red-200 bg-red-50 text-red-500 hover:bg-red-100 active:scale-95"
                        title="Delete payment"
                      >
                        <Trash2 className="h-4 w-4" />
                      </button>
                    </>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      <WorkerSheet worker={workerEdit} onClose={() => setWorkerEdit(null)} onSaved={load} />
      <PaymentSheet edit={payEdit} labourers={labourers} onClose={() => setPayEdit(null)} onSaved={load} />

      <ConfirmDialog
        open={deleteWorker !== null}
        title="Remove worker?"
        body={`Remove ${deleteWorker?.name}? Past payments are kept in your records.`}
        confirmLabel="Remove"
        cancelLabel="Cancel"
        destructive
        onConfirm={async () => {
          if (!deleteWorker) return;
          try {
            await deleteLabourer(deleteWorker.id);
            setDeleteWorker(null);
            await load();
          } catch (e) {
            alert(friendlyError(e, "Couldn't remove worker."));
          }
        }}
        onCancel={() => setDeleteWorker(null)}
      />

      <ConfirmDialog
        open={deletePay !== null}
        title="Delete payment?"
        body="This removes the payment record permanently."
        confirmLabel="Delete"
        cancelLabel="Cancel"
        destructive
        onConfirm={async () => {
          if (!deletePay) return;
          try {
            await deleteLabourPayment(deletePay.id);
            setDeletePay(null);
            await load();
          } catch (e) {
            alert(friendlyError(e, "Couldn't delete payment."));
          }
        }}
        onCancel={() => setDeletePay(null)}
      />
    </div>
  );
}

// ── Worker add/edit ──────────────────────────────────────────────────────────
function WorkerSheet({
  worker,
  onClose,
  onSaved,
}: {
  worker: Labourer | "new" | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const editing = worker && worker !== "new" ? worker : null;
  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [gender, setGender] = useState<Gender>("male");
  const [wage, setWage] = useState("");
  const [otRate, setOtRate] = useState("");
  const [saving, setSaving] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    if (!worker) return;
    setName(editing?.name ?? "");
    setPhone(editing?.phone ?? "");
    setGender(editing?.gender ?? "male");
    setWage(editing ? parseFloat(editing.default_wage).toString() : "");
    setOtRate(editing ? parseFloat(editing.overtime_rate).toString() : "");
    setErr(null);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [worker]);

  const submit = async () => {
    if (!name.trim()) return;
    setSaving(true);
    setErr(null);
    try {
      const payload = {
        name: name.trim(),
        phone: phone.trim() || null,
        gender,
        default_wage: wage.trim() || "0",
        overtime_rate: otRate.trim() || "0",
      };
      if (editing) await updateLabourer(editing.id, payload);
      else await createLabourer(payload);
      onSaved();
      onClose();
    } catch (e) {
      setErr(friendlyError(e, "Couldn't save worker."));
    } finally {
      setSaving(false);
    }
  };

  return (
    <BottomSheet
      open={worker !== null}
      onClose={onClose}
      title={editing ? "Edit worker" : "Add worker"}
      footer={
        <Button variant="primary" size="action" className="w-full font-bold" disabled={!name.trim() || saving} loading={saving} onClick={submit}>
          {editing ? "Save changes" : "Add worker"}
        </Button>
      }
    >
      <div className="space-y-4">
        {err && <p className="rounded-control bg-danger-soft px-4 py-3 text-base font-semibold text-danger">{err}</p>}
        <TextInput label="Name" value={name} onChange={(e) => setName(e.target.value)} required />
        <TextInput label="Phone (optional)" value={phone} onChange={(e) => setPhone(e.target.value)} inputMode="tel" />
        <div>
          <label className="field-label">Gender</label>
          <div className="grid grid-cols-2 gap-2">
            {(["male", "female"] as Gender[]).map((g) => (
              <button
                key={g}
                type="button"
                onClick={() => setGender(g)}
                className={`rounded-control border px-4 py-3 text-base font-bold transition-all ${
                  gender === g ? "bg-primary-50 text-primary-700 border-primary-300 ring-2 ring-primary-600/10" : "bg-white text-ink-soft border-border hover:bg-slate-50"
                }`}
              >
                {g === "male" ? "Male" : "Female"}
              </button>
            ))}
          </div>
        </div>
        <TextInput label="Default wage (₹)" value={wage} onChange={(e) => setWage(e.target.value.replace(/[^0-9.]/g, ""))} inputMode="decimal" placeholder="0" />
        <TextInput label="Overtime rate per hour (₹)" value={otRate} onChange={(e) => setOtRate(e.target.value.replace(/[^0-9.]/g, ""))} inputMode="decimal" placeholder="0" />
      </div>
    </BottomSheet>
  );
}

// ── Record / edit payment ────────────────────────────────────────────────────
function PaymentSheet({
  edit,
  labourers,
  onClose,
  onSaved,
}: {
  edit: LabourPayment | "new" | null;
  labourers: Labourer[];
  onClose: () => void;
  onSaved: () => void;
}) {
  const editing = edit && edit !== "new" ? edit : null;
  const [labourerId, setLabourerId] = useState<string | null>(null);
  const [wage, setWage] = useState("");
  const [otHours, setOtHours] = useState("");
  const [otRate, setOtRate] = useState("0"); // rupees, for the live total
  const [note, setNote] = useState("");
  const [saving, setSaving] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    if (!edit) return;
    if (editing) {
      setLabourerId(editing.labourer_id);
      setWage(parseFloat(editing.wage_amount).toString());
      setOtHours(editing.overtime_hours);
      setOtRate(editing.overtime_rate);
      setNote(editing.note ?? "");
    } else {
      setLabourerId(null);
      setWage("");
      setOtHours("");
      setOtRate("0");
      setNote("");
    }
    setErr(null);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [edit]);

  const selectWorker = (l: Labourer) => {
    setLabourerId(l.id);
    setWage(parseFloat(l.default_wage).toString());
    setOtRate(l.overtime_rate);
  };

  const totalPaise = useMemo(() => {
    const wageP = toPaise(wage || "0");
    const otP = Math.round(toPaise(otRate || "0") * (parseFloat(otHours || "0") || 0));
    return wageP + otP;
  }, [wage, otRate, otHours]);
  const otPaise = Math.round(toPaise(otRate || "0") * (parseFloat(otHours || "0") || 0));

  const submit = async () => {
    if (!labourerId || !wage.trim()) return;
    setSaving(true);
    setErr(null);
    try {
      if (editing) {
        await updateLabourPayment(editing.id, { wage_amount: wage.trim() || "0", overtime_hours: otHours.trim() || "0", note: note.trim() || null });
      } else {
        await createLabourPayment({ labourer_id: labourerId, wage_amount: wage.trim() || "0", overtime_hours: otHours.trim() || "0", note: note.trim() || null });
      }
      onSaved();
      onClose();
    } catch (e) {
      setErr(friendlyError(e, "Couldn't record the payment."));
    } finally {
      setSaving(false);
    }
  };

  return (
    <BottomSheet
      open={edit !== null}
      onClose={onClose}
      title={editing ? "Edit payment" : "Record payment"}
      footer={
        <Button variant="primary" size="action" className="w-full font-bold" disabled={!labourerId || !wage.trim() || saving} loading={saving} onClick={submit}>
          {editing ? "Save changes" : "Record payment"}
        </Button>
      }
    >
      <div className="space-y-4">
        {err && <p className="rounded-control bg-danger-soft px-4 py-3 text-base font-semibold text-danger">{err}</p>}

        {editing ? (
          <p className="text-base font-semibold text-ink">{editing.labourer_name}</p>
        ) : (
          <div>
            <label className="field-label">Worker</label>
            <div className="flex flex-wrap gap-2">
              {labourers.map((l) => (
                <button
                  key={l.id}
                  type="button"
                  onClick={() => selectWorker(l)}
                  className={`px-3 py-1.5 rounded-full text-sm font-semibold border transition-all ${
                    labourerId === l.id ? "bg-primary-50 text-primary-700 border-primary-300 ring-2 ring-primary-600/10" : "bg-white text-ink-soft border-border hover:bg-slate-50"
                  }`}
                >
                  {l.name}
                </button>
              ))}
            </div>
          </div>
        )}

        <TextInput label="Wage paid (₹)" value={wage} onChange={(e) => setWage(e.target.value.replace(/[^0-9.]/g, ""))} inputMode="decimal" />
        <TextInput label="Overtime hours" value={otHours} onChange={(e) => setOtHours(e.target.value.replace(/[^0-9.]/g, ""))} inputMode="decimal" placeholder="0" />
        {toPaise(otRate) > 0 && (
          <p className="text-sm text-ink-soft -mt-2">Overtime: {formatINR(otPaise)} (at {fmt(otRate)}/hr)</p>
        )}
        <TextInput label="Note (optional)" value={note} onChange={(e) => setNote(e.target.value)} />

        <div className="flex items-center justify-between border-t border-border pt-3">
          <span className="text-base font-bold text-ink">Total to pay</span>
          <span className="text-xl font-bold text-ink">{formatINR(totalPaise)}</span>
        </div>
      </div>
    </BottomSheet>
  );
}
