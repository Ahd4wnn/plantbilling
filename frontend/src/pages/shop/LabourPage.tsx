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
  clearLabourDue,
  listAttendance,
  markAttendance,
  type Attendance,
  type AttendanceStatus,
  type Gender,
  type Labourer,
  type LabourPayment,
} from "@/api/labour";
import { Plus, Pencil, Trash2, HardHat, Search, CalendarCheck } from "lucide-react";

function fmt(v: string | null | undefined): string {
  return formatINR(toPaise(v || "0"));
}
function todayISO(): string {
  return new Date().toLocaleDateString("en-CA", { timeZone: "Asia/Kolkata" });
}

type PayMode = "cash" | "upi" | "split" | "due";

export function LabourPage() {
  const user = useAuth((s) => s.user);
  const isManager = user?.role === "manager";

  const [labourers, setLabourers] = useState<Labourer[]>([]);
  const [payments, setPayments] = useState<LabourPayment[]>([]);
  const [query, setQuery] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [workerEdit, setWorkerEdit] = useState<Labourer | "new" | null>(null);
  const [payEdit, setPayEdit] = useState<LabourPayment | "new" | null>(null);
  const [detail, setDetail] = useState<Labourer | null>(null);
  const [clearFor, setClearFor] = useState<Labourer | null>(null);
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
        <Button variant="primary" size="action" className="flex-1 font-bold" onClick={() => { if (labourers.length === 0) { alert("Add a worker first."); return; } setPayEdit("new"); }}>Record a payment</Button>
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
                    {l.gender === "male" ? "Male" : "Female"} · Wage {fmt(l.default_wage)} · OT {fmt(l.overtime_rate)}/hr{l.phone ? ` · ${l.phone}` : ""}
                  </p>
                  {Number(l.outstanding_due) > 0 && <p className="text-sm font-semibold text-danger">Owes worker {fmt(l.outstanding_due)}</p>}
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
                  <p className="font-semibold text-ink truncate">{p.labourer_name}{p.kind === "due_clear" ? " · due cleared" : ""}</p>
                  <p className="text-sm text-ink-soft">
                    {formatDateTime(p.created_at)} · {p.payment_method}
                    {Number(p.overtime_amount) > 0 ? ` · OT ${p.overtime_hours}h ${fmt(p.overtime_amount)}` : ""}
                  </p>
                  {Number(p.due_amount) > 0 && <p className="text-sm font-semibold text-danger">Due {fmt(p.due_amount)}</p>}
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
      {detail && <DetailSheet labourer={detail} onClose={() => setDetail(null)} onClear={() => { setClearFor(detail); }} onChanged={load} />}
      {clearFor && <ClearDueSheet labourer={clearFor} onClose={() => setClearFor(null)} onSaved={() => { setClearFor(null); setDetail(null); load(); }} />}
      {attendanceOpen && <AttendanceSheet labourers={labourers} onClose={() => setAttendanceOpen(false)} />}

      <ConfirmDialog open={deleteWorker !== null} title="Remove worker?" body={`Remove ${deleteWorker?.name}? Past payments are kept.`} confirmLabel="Remove" cancelLabel="Cancel" destructive
        onConfirm={async () => { if (!deleteWorker) return; try { await deleteLabourer(deleteWorker.id); setDeleteWorker(null); await load(); } catch (e) { alert(friendlyError(e)); } }}
        onCancel={() => setDeleteWorker(null)} />
      <ConfirmDialog open={deletePay !== null} title="Delete payment?" body="This removes the record permanently." confirmLabel="Delete" cancelLabel="Cancel" destructive
        onConfirm={async () => { if (!deletePay) return; try { await deleteLabourPayment(deletePay.id); setDeletePay(null); await load(); } catch (e) { alert(friendlyError(e)); } }}
        onCancel={() => setDeletePay(null)} />
    </div>
  );
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

function WorkerSheet({ worker, onClose, onSaved }: { worker: Labourer | "new" | null; onClose: () => void; onSaved: () => void }) {
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
    setName(editing?.name ?? ""); setPhone(editing?.phone ?? ""); setGender(editing?.gender ?? "male");
    setWage(editing ? parseFloat(editing.default_wage).toString() : ""); setOtRate(editing ? parseFloat(editing.overtime_rate).toString() : ""); setErr(null);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [worker]);

  const submit = async () => {
    if (!name.trim()) return;
    setSaving(true); setErr(null);
    try {
      const payload = { name: name.trim(), phone: phone.trim() || null, gender, default_wage: wage.trim() || "0", overtime_rate: otRate.trim() || "0" };
      if (editing) await updateLabourer(editing.id, payload); else await createLabourer(payload);
      onSaved(); onClose();
    } catch (e) { setErr(friendlyError(e, "Couldn't save worker.")); } finally { setSaving(false); }
  };

  return (
    <BottomSheet open={worker !== null} onClose={onClose} title={editing ? "Edit worker" : "Add worker"}
      footer={<Button variant="primary" size="action" className="w-full font-bold" disabled={!name.trim() || saving} loading={saving} onClick={submit}>{editing ? "Save changes" : "Add worker"}</Button>}>
      <div className="space-y-4">
        {err && <p className="rounded-control bg-danger-soft px-4 py-3 text-base font-semibold text-danger">{err}</p>}
        <TextInput label="Name" value={name} onChange={(e) => setName(e.target.value)} required />
        <TextInput label="Phone (optional)" value={phone} onChange={(e) => setPhone(e.target.value)} inputMode="tel" />
        <GenderPicker value={gender} onChange={setGender} />
        <TextInput label="Default wage (₹)" value={wage} onChange={(e) => setWage(e.target.value.replace(/[^0-9.]/g, ""))} inputMode="decimal" placeholder="0" />
        <TextInput label="Overtime rate per hour (₹)" value={otRate} onChange={(e) => setOtRate(e.target.value.replace(/[^0-9.]/g, ""))} inputMode="decimal" placeholder="0" />
      </div>
    </BottomSheet>
  );
}

function PaymentSheet({ edit, labourers, onClose, onSaved }: { edit: LabourPayment | "new" | null; labourers: Labourer[]; onClose: () => void; onSaved: () => void }) {
  const editing = edit && edit !== "new" ? edit : null;
  const [labourerId, setLabourerId] = useState<string | null>(null);
  const [wage, setWage] = useState("");
  const [otHours, setOtHours] = useState("");
  const [otRate, setOtRate] = useState("0");
  const [mode, setMode] = useState<PayMode>("cash");
  const [splitCash, setSplitCash] = useState("");
  const [note, setNote] = useState("");
  const [saving, setSaving] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    if (!edit) return;
    if (editing) {
      setLabourerId(editing.labourer_id); setWage(parseFloat(editing.wage_amount).toString()); setOtHours(editing.overtime_hours);
      setOtRate(editing.overtime_rate); setMode(editing.payment_method); setSplitCash(parseFloat(editing.cash_amount).toString()); setNote(editing.note ?? "");
    } else { setLabourerId(null); setWage(""); setOtHours(""); setOtRate("0"); setMode("cash"); setSplitCash(""); setNote(""); }
    setErr(null);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [edit]);

  const selectWorker = (l: Labourer) => { setLabourerId(l.id); setWage(parseFloat(l.default_wage).toString()); setOtRate(l.overtime_rate); };

  const totalNum = (parseFloat(wage || "0") || 0) + (parseFloat(otRate || "0") || 0) * (parseFloat(otHours || "0") || 0);
  const totalStr = totalNum.toFixed(2);
  const split = useMemo(() => {
    const cashN = mode === "cash" ? totalNum : mode === "split" ? (parseFloat(splitCash || "0") || 0) : 0;
    const upiN = mode === "upi" ? totalNum : mode === "split" ? Math.max(0, totalNum - cashN) : 0;
    const dueN = mode === "due" ? totalNum : 0;
    return { cash: cashN.toFixed(2), upi: upiN.toFixed(2), due: dueN.toFixed(2), upiN };
  }, [mode, totalNum, splitCash]);

  const submit = async () => {
    if (!labourerId || !wage.trim()) return;
    setSaving(true); setErr(null);
    try {
      const body = { wage_amount: wage.trim() || "0", overtime_hours: otHours.trim() || "0", cash_amount: split.cash, upi_amount: split.upi, due_amount: split.due, note: note.trim() || null };
      if (editing) await updateLabourPayment(editing.id, body);
      else await createLabourPayment({ labourer_id: labourerId, ...body });
      onSaved(); onClose();
    } catch (e) { setErr(friendlyError(e, "Couldn't record the payment.")); } finally { setSaving(false); }
  };

  const modes: PayMode[] = ["cash", "upi", "split", "due"];
  return (
    <BottomSheet open={edit !== null} onClose={onClose} title={editing ? "Edit payment" : "Record payment"}
      footer={<Button variant="primary" size="action" className="w-full font-bold" disabled={!labourerId || !wage.trim() || saving} loading={saving} onClick={submit}>{editing ? "Save changes" : "Record payment"}</Button>}>
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
        <TextInput label="Wage (₹)" value={wage} onChange={(e) => setWage(e.target.value.replace(/[^0-9.]/g, ""))} inputMode="decimal" />
        <TextInput label="Overtime hours" value={otHours} onChange={(e) => setOtHours(e.target.value.replace(/[^0-9.]/g, ""))} inputMode="decimal" placeholder="0" />
        {Number(otRate) > 0 && <p className="text-sm text-ink-soft -mt-2">Overtime: {fmt(((parseFloat(otRate) || 0) * (parseFloat(otHours || "0") || 0)).toFixed(2))} (at {fmt(otRate)}/hr)</p>}

        <div>
          <label className="field-label">Payment method</label>
          <div className="grid grid-cols-4 gap-2">
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
        {mode === "due" && <p className="text-sm text-ink-soft">This whole amount will be recorded as owed to the worker.</p>}

        <TextInput label="Note (optional)" value={note} onChange={(e) => setNote(e.target.value)} />
        <div className="flex items-center justify-between border-t border-border pt-3">
          <span className="text-base font-bold text-ink">Total</span>
          <span className="text-xl font-bold text-ink">{fmt(totalStr)}</span>
        </div>
      </div>
    </BottomSheet>
  );
}

function DetailSheet({ labourer, onClose, onClear, onChanged }: { labourer: Labourer; onClose: () => void; onClear: () => void; onChanged: () => void }) {
  const [history, setHistory] = useState<LabourPayment[] | null>(null);
  useEffect(() => {
    listLabourPayments(labourer.id).then(setHistory).catch(() => setHistory([]));
  }, [labourer.id, onChanged]);

  return (
    <BottomSheet open onClose={onClose} title={labourer.name}
      footer={Number(labourer.outstanding_due) > 0 ? <Button variant="primary" size="action" className="w-full font-bold" onClick={onClear}>Clear due · {fmt(labourer.outstanding_due)}</Button> : undefined}>
      <div className="space-y-4">
        <div className="grid grid-cols-2 gap-3">
          <div className="rounded-card border border-border p-3"><div className="text-xs text-ink-soft">Total paid</div><div className="text-lg font-bold text-ink">{fmt(labourer.total_paid)}</div></div>
          <div className="rounded-card border border-border p-3"><div className="text-xs text-ink-soft">Outstanding</div><div className={`text-lg font-bold ${Number(labourer.outstanding_due) > 0 ? "text-danger" : "text-ink"}`}>{fmt(labourer.outstanding_due)}</div></div>
        </div>
        <div>
          <h3 className="font-bold text-ink mb-2">Payment history</h3>
          {!history ? <div className="flex justify-center py-6"><Spinner className="h-5 w-5 text-primary-600" /></div>
            : history.length === 0 ? <p className="text-sm text-ink-soft">No payments yet.</p>
              : <div className="divide-y divide-border border-t border-border">
                {history.map((p) => (
                  <div key={p.id} className="py-2 flex items-center justify-between gap-2">
                    <div className="min-w-0">
                      <p className="text-sm text-ink">{formatDateTime(p.created_at)} · {p.payment_method}{p.kind === "due_clear" ? " · due cleared" : ""}</p>
                      {Number(p.due_amount) > 0 && <p className="text-xs font-semibold text-danger">Due {fmt(p.due_amount)}</p>}
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

function ClearDueSheet({ labourer, onClose, onSaved }: { labourer: Labourer; onClose: () => void; onSaved: () => void }) {
  const [amount, setAmount] = useState(parseFloat(labourer.outstanding_due).toString());
  const [viaUpi, setViaUpi] = useState(false);
  const [saving, setSaving] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const submit = async () => {
    if (!amount.trim()) return;
    setSaving(true); setErr(null);
    try {
      await clearLabourDue({ labourer_id: labourer.id, cash_amount: viaUpi ? "0" : amount.trim(), upi_amount: viaUpi ? amount.trim() : "0", note: "Cleared due" });
      onSaved();
    } catch (e) { setErr(friendlyError(e, "Couldn't clear the due.")); } finally { setSaving(false); }
  };

  return (
    <BottomSheet open onClose={onClose} title={`Clear due · ${labourer.name}`}
      footer={<Button variant="primary" size="action" className="w-full font-bold" disabled={!amount.trim() || saving} loading={saving} onClick={submit}>Pay {fmt(amount || "0")}</Button>}>
      <div className="space-y-4">
        {err && <p className="rounded-control bg-danger-soft px-4 py-3 text-base font-semibold text-danger">{err}</p>}
        <p className="text-base font-semibold text-danger">Outstanding: {fmt(labourer.outstanding_due)}</p>
        <TextInput label="Amount to pay (₹)" value={amount} onChange={(e) => setAmount(e.target.value.replace(/[^0-9.]/g, ""))} inputMode="decimal" />
        <div className="grid grid-cols-2 gap-2">
          {(["cash", "upi"] as const).map((m) => (
            <button key={m} type="button" onClick={() => setViaUpi(m === "upi")} className={`rounded-control border px-4 py-3 text-base font-bold ${(m === "upi") === viaUpi ? "bg-primary-50 text-primary-700 border-primary-300 ring-2 ring-primary-600/10" : "bg-white text-ink-soft border-border"}`}>{m === "cash" ? "Cash" : "UPI"}</button>
          ))}
        </div>
      </div>
    </BottomSheet>
  );
}

function AttendanceSheet({ labourers, onClose }: { labourers: Labourer[]; onClose: () => void }) {
  const day = todayISO();
  const [records, setRecords] = useState<Record<string, Attendance>>({});
  const [busyId, setBusyId] = useState<string | null>(null);
  const [ot, setOt] = useState<Record<string, string>>({});

  useEffect(() => {
    listAttendance(day).then((rows) => {
      const m: Record<string, Attendance> = {};
      const o: Record<string, string> = {};
      rows.forEach((r) => { m[r.labourer_id] = r; o[r.labourer_id] = r.overtime_hours; });
      setRecords(m); setOt(o);
    }).catch(() => {});
  }, [day]);

  const mark = async (l: Labourer, status: AttendanceStatus) => {
    setBusyId(l.id);
    try {
      const rec = await markAttendance({ labourer_id: l.id, day, status, overtime_hours: ot[l.id] || "0" });
      setRecords((m) => ({ ...m, [l.id]: rec }));
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
              <p className="font-semibold text-ink">{l.name}</p>
              <div className="mt-1.5 grid grid-cols-3 gap-2">
                {(["present", "half_day", "absent"] as AttendanceStatus[]).map((s) => (
                  <button key={s} type="button" disabled={busyId === l.id} onClick={() => mark(l, s)}
                    className={`rounded-control border px-2 py-2 text-sm font-bold ${rec?.status === s ? "bg-primary-50 text-primary-700 border-primary-300 ring-2 ring-primary-600/10" : "bg-white text-ink-soft border-border"} disabled:opacity-50`}>
                    {s === "present" ? "Present" : s === "half_day" ? "Half-day" : "Absent"}
                  </button>
                ))}
              </div>
              <input value={ot[l.id] || ""} onChange={(e) => setOt((o) => ({ ...o, [l.id]: e.target.value.replace(/[^0-9.]/g, "") }))} placeholder="Overtime hours (optional)"
                className="mt-2 h-10 w-full rounded-control border border-border px-3 text-ink focus:border-primary-600 focus:outline-none" />
            </div>
          );
        })}
      </div>
    </BottomSheet>
  );
}
