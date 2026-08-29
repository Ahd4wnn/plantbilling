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
  listAttendance,
  markAttendance,
  type Attendance,
  type AttendanceStatus,
  type Gender,
  type Labourer,
  type LabourPayment,
  type WageType,
} from "@/api/labour";
import { Plus, Pencil, Trash2, HardHat, Search, CalendarCheck, Wallet } from "lucide-react";

function fmt(v: string | null | undefined): string {
  return formatINR(toPaise(v || "0"));
}
function todayISO(): string {
  return new Date().toLocaleDateString("en-CA", { timeZone: "Asia/Kolkata" });
}
/** "₹500/day" or "₹15,000/month" — whichever this worker is actually paid. */
function wageLine(l: Labourer): string {
  return l.wage_type === "monthly" ? `${fmt(l.monthly_wage)}/month` : `${fmt(l.default_wage)}/day`;
}

/**
 * What to pre-fill as a wage payment's amount: one day's wage for a daily worker,
 * or the outstanding balance for a monthly one (a salary isn't paid in days, and
 * their default_wage is 0). Blank for an advance — that amount is always typed.
 */
function prefillAmount(l: Labourer | null, isAdvance: boolean): string {
  if (!l || isAdvance) return "";
  if (l.wage_type === "monthly") {
    const owed = parseFloat(l.balance_to_pay) || 0;
    return owed > 0 ? owed.toFixed(2) : "";
  }
  return parseFloat(l.default_wage).toString();
}

type PayMode = "cash" | "upi" | "split";

export function LabourPage() {
  const user = useAuth((s) => s.user);
  const isManager = user?.role === "manager";

  const [labourers, setLabourers] = useState<Labourer[]>([]);
  const [payments, setPayments] = useState<LabourPayment[]>([]);
  const [query, setQuery] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [workerEdit, setWorkerEdit] = useState<Labourer | "new" | null>(null);
  // Payment editor: existing payment, or a {worker, advance} to start a new one.
  const [payEdit, setPayEdit] = useState<LabourPayment | { worker: Labourer | null; advance: boolean } | null>(null);
  const [detail, setDetail] = useState<Labourer | null>(null);
  const [attendanceOpen, setAttendanceOpen] = useState(false);
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

  useEffect(() => { void load(); }, [load]);

  // Keep the open detail sheet in sync with refreshed data.
  const detailLive = detail ? labourers.find((l) => l.id === detail.id) ?? detail : null;

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return labourers;
    return labourers.filter((l) => l.name.toLowerCase().includes(q) || (l.phone || "").includes(q));
  }, [labourers, query]);

  if (loading) return <div className="flex justify-center py-16"><Spinner className="h-8 w-8 text-primary-600" /></div>;
  if (error) return (
    <div className="py-10 text-center text-danger font-semibold space-y-2">
      <p>{error}</p>
      <Button variant="secondary" size="tap" onClick={load}>Try again</Button>
    </div>
  );

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-primary-50 text-primary-700"><HardHat className="h-6 w-6" /></div>
        <div>
          <h1 className="text-xl font-bold text-ink">Labour</h1>
          <p className="text-sm text-ink-soft mt-0.5">Manage workers, record payments, and mark attendance.</p>
        </div>
      </div>

      <div className="flex gap-3">
        <Button variant="primary" size="action" className="flex-1 font-bold" onClick={() => { if (labourers.length === 0) { alert("Add a worker first."); return; } setPayEdit({ worker: null, advance: false }); }}>Record a payment</Button>
        <Button variant="secondary" size="action" className="flex-1 font-bold border-2 flex items-center justify-center gap-2" onClick={() => setAttendanceOpen(true)}><CalendarCheck className="h-5 w-5" /> Attendance</Button>
      </div>

      {/* Workers */}
      <div className="rounded-2xl border border-border bg-white p-5 shadow-sm space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-bold text-ink">Workers</h2>
          <Button variant="secondary" size="tap" className="flex items-center gap-1.5 font-bold" onClick={() => setWorkerEdit("new")}><Plus className="h-4 w-4" /> Add</Button>
        </div>
        <div className="relative">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-soft" />
          <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search workers" className="h-11 w-full rounded-control border border-border pl-9 pr-3 text-ink focus:border-primary-600 focus:outline-none" />
        </div>
        {filtered.length === 0 ? (
          <p className="text-sm text-ink-soft">{labourers.length === 0 ? "No workers yet. Tap Add to set up your first worker." : "No workers match your search."}</p>
        ) : (
          <div className="divide-y divide-border border-t border-border">
            {filtered.map((l) => (
              <div key={l.id} className="py-3 flex items-center justify-between gap-3">
                <button type="button" onClick={() => setDetail(l)} className="min-w-0 text-left">
                  <p className="font-semibold text-ink truncate">{l.name}</p>
                  <p className="text-sm text-ink-soft">
                    {l.gender === "male" ? "Male" : "Female"} · {wageLine(l)}{l.phone ? ` · ${l.phone}` : ""}
                  </p>
                  <BalanceLine labourer={l} />
                </button>
                {isManager && (
                  <div className="flex items-center gap-2 shrink-0">
                    <button type="button" onClick={() => setWorkerEdit(l)} className="flex h-10 w-10 items-center justify-center rounded-xl border border-border bg-white text-ink hover:bg-surface-muted"><Pencil className="h-4 w-4" /></button>
                    <button type="button" onClick={() => setDeleteWorker(l)} className="flex h-10 w-10 items-center justify-center rounded-xl border border-red-200 bg-red-50 text-red-500 hover:bg-red-100"><Trash2 className="h-4 w-4" /></button>
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
                  <p className="font-semibold text-ink truncate">{p.labourer_name}{p.kind === "advance" ? " · advance" : p.kind === "due_clear" ? " · due cleared" : ""}</p>
                  <p className="text-sm text-ink-soft">
                    {formatDateTime(p.created_at)} · {p.payment_method}{p.kind === "wage" && p.days ? ` · ${p.days} day(s)` : ""}
                  </p>
                </div>
                <div className="flex items-center gap-2 shrink-0">
                  <span className="font-bold text-ink">{fmt(p.total_amount)}</span>
                  {isManager && (
                    <>
                      <button type="button" onClick={() => setPayEdit(p)} className="flex h-9 w-9 items-center justify-center rounded-xl border border-border bg-white text-ink hover:bg-surface-muted"><Pencil className="h-4 w-4" /></button>
                      <button type="button" onClick={() => setDeletePay(p)} className="flex h-9 w-9 items-center justify-center rounded-xl border border-red-200 bg-red-50 text-red-500 hover:bg-red-100"><Trash2 className="h-4 w-4" /></button>
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
      {detailLive && <DetailSheet labourer={detailLive} onClose={() => setDetail(null)} onRecord={(advance) => { setDetail(null); setPayEdit({ worker: detailLive, advance }); }} />}
      {attendanceOpen && <AttendanceSheet labourers={labourers} onClose={() => setAttendanceOpen(false)} onChanged={load} />}

      {/* Deleting a worker cascade-deletes their attendance, which is what every
          wage figure is calculated from — so say that, rather than only mentioning
          the payments that survive. */}
      <ConfirmDialog open={deleteWorker !== null} title="Remove worker?"
        body={`Remove ${deleteWorker?.name}? Their attendance record will be deleted and cannot be brought back, so their wage can no longer be worked out. Past payments are kept.`}
        confirmLabel="Remove" cancelLabel="Cancel" destructive
        onConfirm={async () => { if (!deleteWorker) return; try { await deleteLabourer(deleteWorker.id); setDeleteWorker(null); await load(); } catch (e) { alert(friendlyError(e)); } }}
        onCancel={() => setDeleteWorker(null)} />
      <ConfirmDialog open={deletePay !== null} title="Delete payment?" body="This removes the record permanently." confirmLabel="Delete" cancelLabel="Cancel" destructive
        onConfirm={async () => { if (!deletePay) return; try { await deleteLabourPayment(deletePay.id); setDeletePay(null); await load(); } catch (e) { alert(friendlyError(e)); } }}
        onCancel={() => setDeletePay(null)} />
    </div>
  );
}

/** "Balance to pay" (owed) or "Paid ahead" (advance), based on attendance. */
function BalanceLine({ labourer: l }: { labourer: Labourer }) {
  const bal = Number(l.balance_to_pay);
  if (bal > 0) return <p className="text-sm font-semibold text-danger">Balance to pay {fmt(l.balance_to_pay)}</p>;
  if (bal < 0) return <p className="text-sm font-semibold text-primary-700">Paid ahead {fmt(String(-bal))}</p>;
  return null;
}

function GenderPicker({ value, onChange }: { value: Gender; onChange: (g: Gender) => void }) {
  return (
    <div>
      <label className="field-label">Gender</label>
      <div className="grid grid-cols-2 gap-2">
        {(["male", "female"] as Gender[]).map((g) => (
          <button key={g} type="button" onClick={() => onChange(g)} className={`rounded-control border px-4 py-3 text-base font-bold ${value === g ? "bg-primary-50 text-primary-700 border-primary-300 ring-2 ring-primary-600/10" : "bg-white text-ink-soft border-border"}`}>{g === "male" ? "Male" : "Female"}</button>
        ))}
      </div>
    </div>
  );
}

function WageTypePicker({ value, onChange }: { value: WageType; onChange: (t: WageType) => void }) {
  return (
    <div>
      <label className="field-label">How is this worker paid?</label>
      <div className="grid grid-cols-2 gap-2">
        {(["daily", "monthly"] as WageType[]).map((t) => (
          <button key={t} type="button" onClick={() => onChange(t)} className={`rounded-control border px-4 py-3 text-base font-bold ${value === t ? "bg-primary-50 text-primary-700 border-primary-300 ring-2 ring-primary-600/10" : "bg-white text-ink-soft border-border"}`}>{t === "daily" ? "Per day" : "Per month"}</button>
        ))}
      </div>
    </div>
  );
}

function WorkerSheet({ worker, onClose, onSaved }: { worker: Labourer | "new" | null; onClose: () => void; onSaved: () => void }) {
  const editing = worker && worker !== "new" ? worker : null;
  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [aadhaar, setAadhaar] = useState("");
  const [gender, setGender] = useState<Gender>("male");
  const [wageType, setWageType] = useState<WageType>("daily");
  const [wage, setWage] = useState("");
  const [monthlyWage, setMonthlyWage] = useState("");
  const [paidLeaves, setPaidLeaves] = useState("");
  const [joinedOn, setJoinedOn] = useState("");
  const [saving, setSaving] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    if (!worker) return;
    setName(editing?.name ?? ""); setPhone(editing?.phone ?? ""); setAadhaar(editing?.aadhaar ?? ""); setGender(editing?.gender ?? "male");
    setWageType(editing?.wage_type ?? "daily");
    setWage(editing ? parseFloat(editing.default_wage).toString() : "");
    setMonthlyWage(editing && editing.monthly_wage ? parseFloat(editing.monthly_wage).toString() : "");
    setPaidLeaves(editing ? String(editing.paid_leaves_per_month ?? 0) : "");
    // New workers default to today; the field is editable because people are
    // routinely entered into the app days or weeks after they actually started.
    setJoinedOn(editing?.joined_on ?? todayISO());
    setErr(null);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [worker]);

  const monthly = wageType === "monthly";
  // The wage that matters for the selected mode. Blocking here rather than
  // letting the server reject it keeps the message next to the empty field.
  const wageFilled = monthly ? Number(monthlyWage) > 0 : Number(wage) > 0;

  const submit = async () => {
    if (!name.trim() || !wageFilled) return;
    setSaving(true); setErr(null);
    try {
      const payload = {
        name: name.trim(), phone: phone.trim() || null, aadhaar: aadhaar.trim() || null, gender,
        wage_type: wageType,
        // Both wages are always sent so switching a worker between modes doesn't
        // leave a stale figure behind in the mode they're no longer paid in.
        default_wage: monthly ? "0" : wage.trim() || "0",
        monthly_wage: monthly ? monthlyWage.trim() || "0" : "0",
        paid_leaves_per_month: monthly ? Number(paidLeaves || 0) : 0,
        joined_on: joinedOn || null,
      };
      if (editing) await updateLabourer(editing.id, payload); else await createLabourer(payload);
      onSaved(); onClose();
    } catch (e) { setErr(friendlyError(e, "Couldn't save worker.")); } finally { setSaving(false); }
  };

  return (
    <BottomSheet open={worker !== null} onClose={onClose} title={editing ? "Edit worker" : "Add worker"}
      footer={<Button variant="primary" size="action" className="w-full font-bold" disabled={!name.trim() || !wageFilled || saving} loading={saving} onClick={submit}>{editing ? "Save changes" : "Add worker"}</Button>}>
      <div className="space-y-4">
        {err && <p className="rounded-control bg-danger-soft px-4 py-3 text-base font-semibold text-danger">{err}</p>}
        <TextInput label="Name" value={name} onChange={(e) => setName(e.target.value)} required />
        <TextInput label="Phone number" value={phone} onChange={(e) => setPhone(e.target.value)} inputMode="tel" />
        <TextInput label="Aadhaar number (optional)" value={aadhaar} onChange={(e) => setAadhaar(e.target.value)} inputMode="numeric" />
        <GenderPicker value={gender} onChange={setGender} />
        <TextInput label="Joining date" type="date" value={joinedOn} max={todayISO()} onChange={(e) => setJoinedOn(e.target.value)} />
        <WageTypePicker value={wageType} onChange={setWageType} />
        {monthly ? (
          <>
            <TextInput label="Monthly wage (₹)" value={monthlyWage} onChange={(e) => setMonthlyWage(e.target.value.replace(/[^0-9.]/g, ""))} inputMode="decimal" placeholder="0" required />
            <TextInput label="Paid leaves per month" value={paidLeaves} onChange={(e) => setPaidLeaves(e.target.value.replace(/[^0-9]/g, ""))} inputMode="numeric" placeholder="0" />
            <p className="text-sm text-ink-soft">
              Leaves beyond this many in a month are deducted from the wage. Days you
              haven't marked in Attendance are not counted as leave.
            </p>
          </>
        ) : (
          <TextInput label="Wage per day (₹)" value={wage} onChange={(e) => setWage(e.target.value.replace(/[^0-9.]/g, ""))} inputMode="decimal" placeholder="0" required />
        )}
      </div>
    </BottomSheet>
  );
}

type PayStart = LabourPayment | { worker: Labourer | null; advance: boolean };

function PaymentSheet({ edit, labourers, onClose, onSaved }: { edit: PayStart | null; labourers: Labourer[]; onClose: () => void; onSaved: () => void }) {
  const editing = edit && "id" in edit ? edit : null;
  const startAdvance = edit && !("id" in edit) ? edit.advance : false;

  const [labourerId, setLabourerId] = useState<string | null>(null);
  const [isAdvance, setIsAdvance] = useState(false);
  const [wagePerDay, setWagePerDay] = useState("0");
  const [days, setDays] = useState("1");
  const [amount, setAmount] = useState("");
  const [mode, setMode] = useState<PayMode>("cash");
  const [splitCash, setSplitCash] = useState("");
  const [note, setNote] = useState("");
  const [saving, setSaving] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    if (!edit) return;
    if (editing) {
      setLabourerId(editing.labourer_id);
      setIsAdvance(editing.kind === "advance");
      const w = labourers.find((l) => l.id === editing.labourer_id);
      setWagePerDay(w ? parseFloat(w.default_wage).toString() : "0");
      setDays(editing.days ?? "1");
      setAmount(parseFloat(editing.wage_amount).toString());
      setMode(editing.payment_method === "due" ? "cash" : (editing.payment_method as PayMode));
      setSplitCash(parseFloat(editing.cash_amount).toString());
      setNote(editing.note ?? "");
    } else {
      const w = edit && !("id" in edit) ? edit.worker : null;
      setLabourerId(w?.id ?? null);
      setIsAdvance(startAdvance);
      setWagePerDay(w ? parseFloat(w.default_wage).toString() : "0");
      setDays("1");
      // A monthly worker's default_wage is 0, so one day's wage would pre-fill ₹0.
      // What's normally being handed over is the outstanding balance.
      setAmount(prefillAmount(w, startAdvance));
      setMode("cash"); setSplitCash(""); setNote("");
    }
    setErr(null);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [edit]);

  // Which worker this payment is for, so the form can ask the right question.
  // Null while editing a payment whose worker has since been removed.
  const selectedWorker = labourers.find((l) => l.id === labourerId) ?? null;
  const monthlyWorker = selectedWorker?.wage_type === "monthly";

  const selectWorker = (l: Labourer) => {
    setLabourerId(l.id);
    setWagePerDay(parseFloat(l.default_wage).toString());
    if (isAdvance) return;
    setAmount(
      l.wage_type === "monthly"
        ? prefillAmount(l, false)
        : (parseFloat(l.default_wage) * (parseFloat(days || "0") || 0)).toString(),
    );
  };

  const setDaysAndAmount = (d: string) => {
    setDays(d);
    if (!isAdvance) setAmount(((parseFloat(wagePerDay || "0") || 0) * (parseFloat(d || "0") || 0)).toString());
  };

  const totalNum = parseFloat(amount || "0") || 0;
  const totalStr = totalNum.toFixed(2);
  const split = useMemo(() => {
    const cashN = mode === "cash" ? totalNum : mode === "split" ? (parseFloat(splitCash || "0") || 0) : 0;
    const upiN = mode === "upi" ? totalNum : mode === "split" ? Math.max(0, totalNum - cashN) : 0;
    return { cash: cashN.toFixed(2), upi: upiN.toFixed(2) };
  }, [mode, totalNum, splitCash]);

  const submit = async () => {
    if (!labourerId || !amount.trim()) return;
    setSaving(true); setErr(null);
    try {
      const body = { wage_amount: amount.trim() || "0", cash_amount: split.cash, upi_amount: split.upi, due_amount: "0", note: note.trim() || null };
      // "Days covered" is only meaningful for a daily wage; a salary payment isn't
      // measured in days, and the column is nullable for exactly this reason.
      const daysCovered = isAdvance || monthlyWorker ? null : days.trim() || "0";
      if (editing) await updateLabourPayment(editing.id, { ...body, days: daysCovered });
      else await createLabourPayment({ labourer_id: labourerId, kind: isAdvance ? "advance" : "wage", days: daysCovered, ...body });
      onSaved(); onClose();
    } catch (e) { setErr(friendlyError(e, "Couldn't record the payment.")); } finally { setSaving(false); }
  };

  const modes: PayMode[] = ["cash", "upi", "split"];
  return (
    <BottomSheet open={edit !== null} onClose={onClose} title={editing ? "Edit payment" : isAdvance ? "Give advance" : "Record payment"}
      footer={<Button variant="primary" size="action" className="w-full font-bold" disabled={!labourerId || !amount.trim() || saving} loading={saving} onClick={submit}>{editing ? "Save changes" : isAdvance ? "Give advance" : "Record payment"}</Button>}>
      <div className="space-y-4">
        {err && <p className="rounded-control bg-danger-soft px-4 py-3 text-base font-semibold text-danger">{err}</p>}
        {editing ? (
          <p className="text-base font-semibold text-ink">{editing.labourer_name}</p>
        ) : (
          <div>
            <label className="field-label">Worker</label>
            <div className="flex flex-wrap gap-2">
              {labourers.map((l) => (
                <button key={l.id} type="button" onClick={() => selectWorker(l)} className={`px-3 py-1.5 rounded-full text-sm font-semibold border ${labourerId === l.id ? "bg-primary-50 text-primary-700 border-primary-300 ring-2 ring-primary-600/10" : "bg-white text-ink-soft border-border"}`}>{l.name}</button>
              ))}
            </div>
          </div>
        )}

        {/* Wage payment vs advance */}
        {!editing && (
          <div className="grid grid-cols-2 gap-2">
            {[{ v: false, label: "Wage payment" }, { v: true, label: "Advance" }].map((o) => (
              <button key={o.label} type="button" onClick={() => {
                setIsAdvance(o.v);
                if (o.v) { setAmount(""); return; }
                setAmount(
                  monthlyWorker
                    ? prefillAmount(selectedWorker, false)
                    : ((parseFloat(wagePerDay || "0") || 0) * (parseFloat(days || "0") || 0)).toString(),
                );
              }}
                className={`rounded-control border px-4 py-3 text-base font-bold ${isAdvance === o.v ? "bg-primary-50 text-primary-700 border-primary-300 ring-2 ring-primary-600/10" : "bg-white text-ink-soft border-border"}`}>{o.label}</button>
            ))}
          </div>
        )}

        {!isAdvance && !monthlyWorker && (
          <>
            <p className="text-sm text-ink-soft">Wage per day: <span className="font-semibold text-ink">{fmt(wagePerDay)}</span></p>
            <TextInput label="Number of days" value={days} onChange={(e) => setDaysAndAmount(e.target.value.replace(/[^0-9.]/g, ""))} inputMode="decimal" />
          </>
        )}
        {!isAdvance && monthlyWorker && selectedWorker && (
          <p className="text-sm text-ink-soft">
            Monthly wage: <span className="font-semibold text-ink">{fmt(selectedWorker.monthly_wage)}</span>
            {" · "}Balance to pay: <span className="font-semibold text-ink">{fmt(selectedWorker.balance_to_pay)}</span>
          </p>
        )}
        <TextInput label={isAdvance ? "Advance amount (₹)" : "Amount (₹)"} value={amount} onChange={(e) => setAmount(e.target.value.replace(/[^0-9.]/g, ""))} inputMode="decimal" />

        <div>
          <label className="field-label">Payment method</label>
          <div className="grid grid-cols-3 gap-2">
            {modes.map((m) => (
              <button key={m} type="button" onClick={() => { setMode(m); if (m === "split" && !splitCash) setSplitCash(totalStr); }} className={`rounded-control border px-2 py-2.5 text-sm font-bold capitalize ${mode === m ? "bg-primary-50 text-primary-700 border-primary-300 ring-2 ring-primary-600/10" : "bg-white text-ink-soft border-border"}`}>{m}</button>
            ))}
          </div>
        </div>
        {mode === "split" && (
          <>
            <TextInput label="Cash part (₹)" value={splitCash} onChange={(e) => setSplitCash(e.target.value.replace(/[^0-9.]/g, ""))} inputMode="decimal" />
            <p className="text-sm text-ink-soft -mt-2">UPI part: {fmt(split.upi)}</p>
          </>
        )}

        <TextInput label="Note (optional)" value={note} onChange={(e) => setNote(e.target.value)} />
        <div className="flex items-center justify-between border-t border-border pt-3">
          <span className="text-base font-bold text-ink">Total</span>
          <span className="text-xl font-bold text-ink">{fmt(totalStr)}</span>
        </div>
      </div>
    </BottomSheet>
  );
}

function DetailSheet({ labourer, onClose, onRecord }: { labourer: Labourer; onClose: () => void; onRecord: (advance: boolean) => void }) {
  const [history, setHistory] = useState<LabourPayment[] | null>(null);
  useEffect(() => {
    listLabourPayments(labourer.id).then(setHistory).catch(() => setHistory([]));
  }, [labourer.id]);

  const bal = Number(labourer.balance_to_pay);
  return (
    <BottomSheet open onClose={onClose} title={labourer.name}
      footer={
        <div className="flex gap-2">
          <Button variant="secondary" size="action" className="flex-1 font-bold" onClick={() => onRecord(true)}>Give advance</Button>
          <Button variant="primary" size="action" className="flex-1 font-bold" onClick={() => onRecord(false)}>Record payment</Button>
        </div>
      }>
      <div className="space-y-4">
        <p className="text-sm text-ink-soft">{labourer.gender === "male" ? "Male" : "Female"}{labourer.phone ? ` · ${labourer.phone}` : ""}{labourer.aadhaar ? ` · Aadhaar ${labourer.aadhaar}` : ""}</p>

        {/* Statement of pay. A monthly worker's earnings are a salary minus
            deductions, so show the leave position that produced them — otherwise a
            reduced figure looks like the app got the sum wrong. */}
        <div className="rounded-card border border-border divide-y divide-border">
          <StatementRow label={`Days worked`} value={`${labourer.days_worked} day(s)`} />
          {labourer.wage_type === "monthly" ? (
            <>
              <StatementRow label="Leaves this month" value={`${labourer.leaves_this_month} of ${labourer.paid_leaves_per_month} paid`} />
              {Number(labourer.unpaid_leaves_this_month) > 0 && (
                <StatementRow label="Unpaid leaves this month" value={`${labourer.unpaid_leaves_this_month} day(s) deducted`} />
              )}
              <StatementRow label={`Earned (${fmt(labourer.monthly_wage)}/month)`} value={fmt(labourer.earned)} />
            </>
          ) : (
            <StatementRow label={`Earned (${fmt(labourer.default_wage)}/day)`} value={fmt(labourer.earned)} />
          )}
          <StatementRow label="Total paid" value={fmt(labourer.total_paid)} />
          <div className="flex items-center justify-between px-4 py-3">
            <span className="flex items-center gap-2 font-bold text-ink"><Wallet className="h-4 w-4" /> {bal < 0 ? "Paid ahead" : "Balance to pay"}</span>
            <span className={`text-lg font-bold ${bal > 0 ? "text-danger" : bal < 0 ? "text-primary-700" : "text-ink"}`}>{bal < 0 ? fmt(String(-bal)) : fmt(labourer.balance_to_pay)}</span>
          </div>
        </div>

        <div>
          <h3 className="font-bold text-ink mb-2">Payment history</h3>
          {!history ? <div className="flex justify-center py-6"><Spinner className="h-5 w-5 text-primary-600" /></div>
            : history.length === 0 ? <p className="text-sm text-ink-soft">No payments yet.</p>
              : <div className="divide-y divide-border border-t border-border">
                {history.map((p) => (
                  <div key={p.id} className="py-2 flex items-center justify-between gap-2">
                    <div className="min-w-0">
                      <p className="text-sm text-ink">{formatDateTime(p.created_at)} · {p.payment_method}{p.kind === "advance" ? " · advance" : p.kind === "due_clear" ? " · due cleared" : p.days ? ` · ${p.days} day(s)` : ""}</p>
                    </div>
                    <span className="font-semibold text-ink">{fmt(p.total_amount)}</span>
                  </div>
                ))}
              </div>}
        </div>
      </div>
    </BottomSheet>
  );
}

function StatementRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between px-4 py-3">
      <span className="text-sm text-ink-soft">{label}</span>
      <span className="font-semibold text-ink">{value}</span>
    </div>
  );
}

function AttendanceSheet({ labourers, onClose, onChanged }: { labourers: Labourer[]; onClose: () => void; onChanged: () => void }) {
  const day = todayISO();
  const [records, setRecords] = useState<Record<string, Attendance>>({});
  const [busyId, setBusyId] = useState<string | null>(null);

  useEffect(() => {
    listAttendance(day).then((rows) => {
      const m: Record<string, Attendance> = {};
      rows.forEach((r) => { m[r.labourer_id] = r; });
      setRecords(m);
    }).catch(() => {});
  }, [day]);

  const mark = async (l: Labourer, status: AttendanceStatus) => {
    setBusyId(l.id);
    try {
      const rec = await markAttendance({ labourer_id: l.id, day, status });
      setRecords((m) => ({ ...m, [l.id]: rec }));
      onChanged();
    } catch (e) { alert(friendlyError(e)); } finally { setBusyId(null); }
  };

  return (
    <BottomSheet open onClose={onClose} title="Today's attendance"
      footer={<Button variant="secondary" size="action" className="w-full" onClick={onClose}>Done</Button>}>
      <div className="space-y-4">
        {labourers.length === 0 && <p className="text-sm text-ink-soft">Add a worker first.</p>}
        {labourers.map((l) => {
          const rec = records[l.id];
          return (
            <div key={l.id} className="border-b border-border pb-3 last:border-0">
              <div className="flex items-center justify-between">
                <p className="font-semibold text-ink">{l.name}</p>
                <p className="text-sm text-ink-soft">{l.days_worked} day(s) worked</p>
              </div>
              <div className="mt-1.5 grid grid-cols-3 gap-2">
                {(["present", "half_day", "absent"] as AttendanceStatus[]).map((s) => (
                  <button key={s} type="button" disabled={busyId === l.id} onClick={() => mark(l, s)}
                    className={`rounded-control border px-2 py-2 text-sm font-bold ${rec?.status === s ? "bg-primary-50 text-primary-700 border-primary-300 ring-2 ring-primary-600/10" : "bg-white text-ink-soft border-border"} disabled:opacity-50`}>
                    {s === "present" ? "Present" : s === "half_day" ? "Half-day" : "Absent"}
                  </button>
                ))}
              </div>
            </div>
          );
        })}
      </div>
    </BottomSheet>
  );
}
