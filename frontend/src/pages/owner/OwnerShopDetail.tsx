import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  createShopStaff,
  deleteShopStaff,
  getShopReport,
  listOwnerShops,
  listShopStaff,
  resetStaffPassword,
  setStaffActive,
  updateOwnerShop,
  type OwnerReport,
  type OwnerShop,
  type OwnerStaff,
} from "@/api/owner";
import { friendlyError } from "@/api/client";
import { Spinner } from "@/components/Spinner";

function inr(v: string | number): string {
  const n = typeof v === "string" ? Number(v) : v;
  return "₹" + (isFinite(n) ? n : 0).toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}
function ymd(d: Date): string {
  return d.toISOString().slice(0, 10);
}
type Period = "today" | "week" | "month";
function rangeFor(period: Period): { from: string; to: string } {
  const now = new Date();
  const to = ymd(now);
  if (period === "today") return { from: to, to };
  if (period === "week") {
    const d = new Date(now);
    d.setDate(d.getDate() - 6);
    return { from: ymd(d), to };
  }
  return { from: ymd(new Date(now.getFullYear(), now.getMonth(), 1)), to };
}

export function OwnerShopDetail() {
  const { shopId = "" } = useParams();
  const navigate = useNavigate();
  const [shop, setShop] = useState<OwnerShop | null>(null);
  const [staff, setStaff] = useState<OwnerStaff[]>([]);
  const [report, setReport] = useState<OwnerReport | null>(null);
  const [period, setPeriod] = useState<Period>("today");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [msg, setMsg] = useState<string | null>(null);

  const refreshStaff = () => listShopStaff(shopId).then(setStaff).catch(() => {});

  useEffect(() => {
    let alive = true;
    setLoading(true);
    Promise.all([listOwnerShops(), listShopStaff(shopId)])
      .then(([shops, st]) => {
        if (!alive) return;
        setShop(shops.find((s) => s.id === shopId) ?? null);
        setStaff(st);
      })
      .catch((e) => alive && setError(friendlyError(e, "Couldn't load this shop.")))
      .finally(() => alive && setLoading(false));
    return () => {
      alive = false;
    };
  }, [shopId]);

  useEffect(() => {
    const r = rangeFor(period);
    getShopReport(shopId, r.from, r.to).then(setReport).catch(() => {});
  }, [shopId, period]);

  if (loading) {
    return (
      <div className="flex justify-center py-16 text-primary-600">
        <Spinner className="h-8 w-8" label="Loading" />
      </div>
    );
  }
  if (error) return <div className="rounded-card border border-danger/30 bg-danger/5 p-4 text-danger">{error}</div>;

  return (
    <div className="space-y-6">
      <button type="button" onClick={() => navigate("/owner")} className="text-sm font-semibold text-primary-700">
        ← Back to overview
      </button>
      <h1 className="text-2xl font-bold text-ink">{shop?.name ?? "Shop"}</h1>
      {msg && <div className="rounded-card border border-primary-200 bg-primary-50 p-3 text-primary-800">{msg}</div>}

      {/* ── Report ── */}
      <section className="space-y-3">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-semibold text-ink">Sales</h2>
          <div className="flex rounded-control border border-border bg-white p-1">
            {(["today", "week", "month"] as Period[]).map((p) => (
              <button
                key={p}
                type="button"
                onClick={() => setPeriod(p)}
                className={[
                  "rounded-control px-3 py-1.5 text-sm font-semibold transition-colors",
                  period === p ? "bg-primary-600 text-white" : "text-ink-soft hover:bg-surface-muted",
                ].join(" ")}
              >
                {p === "week" ? "7 days" : p === "month" ? "Month" : "Today"}
              </button>
            ))}
          </div>
        </div>
        {report && (
          <>
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
              <Kpi label="Sales" value={inr(report.total_sales)} accent="text-primary-700" />
              <Kpi label="Expenses" value={"− " + inr(report.total_expenses)} accent="text-danger" />
              <Kpi label="Net" value={inr(report.net_sales)} accent={Number(report.net_sales) < 0 ? "text-danger" : "text-primary-700"} />
              <Kpi label="Bills" value={String(report.bill_count)} accent="text-ink" />
            </div>
            <div className="grid grid-cols-3 gap-3">
              <Kpi label="Cash" value={inr(report.cash_total)} accent="text-emerald-700" small />
              <Kpi label="UPI" value={inr(report.upi_total)} accent="text-sky-700" small />
              <Kpi label="Due" value={inr(report.due_total)} accent="text-amber-700" small />
            </div>
            {report.top_products.length > 0 && (
              <div className="overflow-hidden rounded-card border border-border bg-white">
                <div className="border-b border-border px-4 py-2 text-sm font-semibold text-ink-soft">Top products</div>
                {report.top_products.slice(0, 8).map((p) => (
                  <div key={p.product_name} className="flex items-center justify-between border-b border-border px-4 py-2 last:border-0">
                    <span className="text-ink">{p.product_name} <span className="text-ink-soft">×{p.quantity}</span></span>
                    <span className="font-semibold text-ink">{inr(p.total_sales)}</span>
                  </div>
                ))}
              </div>
            )}
          </>
        )}
      </section>

      {/* ── Business details ── */}
      {shop && <BusinessDetails shop={shop} onSaved={(s) => { setShop(s); setMsg("Business details saved."); }} />}

      {/* ── Staff ── */}
      <StaffManager shopId={shopId} staff={staff} onChange={refreshStaff} onMessage={setMsg} />
    </div>
  );
}

function Kpi({ label, value, accent, small }: { label: string; value: string; accent: string; small?: boolean }) {
  return (
    <div className="rounded-card border border-border bg-white p-4">
      <div className="text-xs font-medium uppercase tracking-wide text-ink-soft">{label}</div>
      <div className={[small ? "text-lg" : "text-xl", "font-bold", accent].join(" ")}>{value}</div>
    </div>
  );
}

function field(label: string, value: string, onChange: (v: string) => void, placeholder = "") {
  return (
    <label className="block">
      <span className="text-sm font-medium text-ink-soft">{label}</span>
      <input
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        className="mt-1 w-full rounded-control border border-border px-3 py-2 text-ink focus:border-primary-500 focus:outline-none"
      />
    </label>
  );
}

function BusinessDetails({ shop, onSaved }: { shop: OwnerShop; onSaved: (s: OwnerShop) => void }) {
  const [form, setForm] = useState({
    business_name: shop.business_name ?? "",
    business_address: shop.business_address ?? "",
    business_phone: shop.business_phone ?? "",
    business_email: shop.business_email ?? "",
    business_upi: shop.business_upi ?? "",
  });
  const [saving, setSaving] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const set = (k: keyof typeof form) => (v: string) => setForm((f) => ({ ...f, [k]: v }));

  const save = async () => {
    setSaving(true);
    setErr(null);
    try {
      const updated = await updateOwnerShop(shop.id, form);
      onSaved(updated);
    } catch (e) {
      setErr(friendlyError(e, "Couldn't save."));
    } finally {
      setSaving(false);
    }
  };

  return (
    <section className="space-y-3 rounded-card border border-border bg-white p-4">
      <h2 className="text-lg font-semibold text-ink">Business details</h2>
      <div className="grid gap-3 sm:grid-cols-2">
        {field("Business name", form.business_name, set("business_name"))}
        {field("Phone", form.business_phone, set("business_phone"))}
        {field("Email", form.business_email, set("business_email"))}
        {field("UPI ID", form.business_upi, set("business_upi"), "name@bank")}
        <div className="sm:col-span-2">{field("Address", form.business_address, set("business_address"))}</div>
      </div>
      {err && <p className="text-sm text-danger">{err}</p>}
      <button
        type="button"
        onClick={save}
        disabled={saving}
        className="rounded-control bg-primary-600 px-4 py-2 font-semibold text-white disabled:opacity-60"
      >
        {saving ? "Saving…" : "Save details"}
      </button>
    </section>
  );
}

function StaffManager({
  shopId,
  staff,
  onChange,
  onMessage,
}: {
  shopId: string;
  staff: OwnerStaff[];
  onChange: () => void;
  onMessage: (m: string) => void;
}) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [role, setRole] = useState<"manager" | "salesperson">("salesperson");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const add = async () => {
    setBusy(true);
    setErr(null);
    try {
      await createShopStaff(shopId, { email, password, role });
      setEmail("");
      setPassword("");
      onChange();
      onMessage("Staff member added.");
    } catch (e) {
      setErr(friendlyError(e, "Couldn't add staff."));
    } finally {
      setBusy(false);
    }
  };

  const toggle = async (s: OwnerStaff) => {
    await setStaffActive(shopId, s.id, !s.is_active).catch(() => {});
    onChange();
  };
  const reset = async (s: OwnerStaff) => {
    const pw = prompt(`New password for ${s.email} (min 8 chars):`);
    if (!pw || pw.length < 8) return;
    await resetStaffPassword(shopId, s.id, pw).catch(() => {});
    onMessage("Password reset.");
  };
  const remove = async (s: OwnerStaff) => {
    if (!confirm(`Remove ${s.email}? Their past bills are kept.`)) return;
    await deleteShopStaff(shopId, s.id).catch(() => {});
    onChange();
  };

  return (
    <section className="space-y-3 rounded-card border border-border bg-white p-4">
      <h2 className="text-lg font-semibold text-ink">Staff</h2>
      <div className="divide-y divide-border">
        {staff.length === 0 && <p className="text-ink-soft">No staff yet.</p>}
        {staff.map((s) => (
          <div key={s.id} className="flex flex-wrap items-center justify-between gap-2 py-3">
            <div>
              <div className="font-medium text-ink">{s.email}</div>
              <div className="text-sm text-ink-soft">
                {s.role} · {s.is_active ? "Active" : "Inactive"}
              </div>
            </div>
            <div className="flex gap-2 text-sm">
              <button type="button" onClick={() => toggle(s)} className="rounded-control border border-border px-2.5 py-1 font-semibold text-ink">
                {s.is_active ? "Deactivate" : "Activate"}
              </button>
              <button type="button" onClick={() => reset(s)} className="rounded-control border border-border px-2.5 py-1 font-semibold text-ink">
                Reset
              </button>
              <button type="button" onClick={() => remove(s)} className="rounded-control border border-danger/40 px-2.5 py-1 font-semibold text-danger">
                Remove
              </button>
            </div>
          </div>
        ))}
      </div>

      <div className="rounded-control border border-border p-3">
        <div className="mb-2 text-sm font-semibold text-ink-soft">Add staff</div>
        <div className="grid gap-2 sm:grid-cols-2">
          <input value={email} onChange={(e) => setEmail(e.target.value)} placeholder="Login email" className="rounded-control border border-border px-3 py-2" />
          <input value={password} onChange={(e) => setPassword(e.target.value)} placeholder="Password (min 8)" className="rounded-control border border-border px-3 py-2" />
          <select value={role} onChange={(e) => setRole(e.target.value as "manager" | "salesperson")} className="rounded-control border border-border px-3 py-2">
            <option value="salesperson">Salesperson</option>
            <option value="manager">Manager</option>
          </select>
          <button type="button" onClick={add} disabled={busy || !email || password.length < 8} className="rounded-control bg-primary-600 px-4 py-2 font-semibold text-white disabled:opacity-60">
            {busy ? "Adding…" : "Add"}
          </button>
        </div>
        {err && <p className="mt-2 text-sm text-danger">{err}</p>}
      </div>
    </section>
  );
}
