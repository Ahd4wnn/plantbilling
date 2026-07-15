import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { AlertTriangle, Store, Activity, Users, UserCog, Sprout } from "lucide-react";
import { getAdminOverview, type AdminOverview } from "@/api/adminAnalytics";
import { friendlyError } from "@/api/client";
import { Spinner } from "@/components/Spinner";
import { Button } from "@/components/Button";
import { Bars } from "@/components/MiniChart";

function ymd(d: Date): string {
  return d.toISOString().slice(0, 10);
}
function shortDate(iso: string): string {
  return new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short" }).format(new Date(iso + "T00:00:00"));
}
function fromNow(iso: string | null): string {
  if (!iso) return "Never billed";
  const days = Math.floor((Date.now() - new Date(iso).getTime()) / 86_400_000);
  if (days <= 0) return "Active today";
  if (days === 1) return "1 day ago";
  if (days < 30) return `${days} days ago`;
  return new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short" }).format(new Date(iso));
}
function health(lastBillAt: string | null): string {
  if (!lastBillAt) return "bg-slate-300";
  const days = Math.floor((Date.now() - new Date(lastBillAt).getTime()) / 86_400_000);
  if (days <= 0) return "bg-success";
  if (days <= 6) return "bg-amber-400";
  return "bg-danger";
}

type Period = "today" | "week" | "month";
function rangeFor(period: Period): { from: string; to: string } {
  const now = new Date();
  const to = ymd(now);
  if (period === "today") return { from: to, to };
  const d = new Date(now);
  d.setDate(d.getDate() - (period === "week" ? 6 : 29));
  return { from: ymd(d), to };
}

const KIND_STYLE: Record<string, string> = {
  inactive: "bg-slate-100 text-ink-soft",
  no_owner: "bg-warning-soft text-warning",
  silent: "bg-danger-soft text-danger",
};
const KIND_LABEL: Record<string, string> = { inactive: "Inactive", no_owner: "No owner", silent: "Quiet" };

export function DashboardPage() {
  const navigate = useNavigate();
  const [period, setPeriod] = useState<Period>("week");
  const [data, setData] = useState<AdminOverview | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  const range = useMemo(() => rangeFor(period), [period]);

  useEffect(() => {
    let alive = true;
    setLoading(true);
    setError(null);
    getAdminOverview(range.from, range.to)
      .then((d) => alive && setData(d))
      .catch((e) => alive && setError(friendlyError(e, "Couldn't load the dashboard.")))
      .finally(() => alive && setLoading(false));
    return () => { alive = false; };
  }, [range.from, range.to, reloadKey]);

  const unowned = useMemo(() => data?.attention.filter((a) => a.kind === "no_owner").length ?? 0, [data]);
  const byActivity = useMemo(
    () => (data ? [...data.shops].sort((a, b) => b.bills_in_period - a.bills_in_period).slice(0, 6) : []),
    [data],
  );
  const recentShops = useMemo(
    () => (data ? [...data.shops].sort((a, b) => +new Date(b.created_at) - +new Date(a.created_at)).slice(0, 5) : []),
    [data],
  );

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-extrabold tracking-tight text-ink">Platform overview</h1>
          <p className="text-base font-semibold text-ink-soft">
            {period === "today" ? "Today" : period === "week" ? "Last 7 days" : "Last 30 days"} · across every shop
          </p>
        </div>
        <div className="flex rounded-control border border-border bg-white p-1">
          {(["today", "week", "month"] as Period[]).map((p) => (
            <button
              key={p}
              type="button"
              onClick={() => setPeriod(p)}
              className={[
                "rounded-control px-3 py-1.5 text-sm font-bold transition-colors",
                period === p ? "bg-primary-600 text-white" : "text-ink-soft hover:bg-surface-muted",
              ].join(" ")}
            >
              {p === "week" ? "7 days" : p === "month" ? "30 days" : "Today"}
            </button>
          ))}
        </div>
      </div>

      {loading && <div className="flex justify-center py-16"><Spinner className="h-8 w-8 text-primary-600" /></div>}
      {error && !loading && (
        <div className="py-12 text-center">
          <p className="text-base font-semibold text-danger">{error}</p>
          <Button variant="secondary" size="tap" className="mt-4" onClick={() => setReloadKey((k) => k + 1)}>Try again</Button>
        </div>
      )}

      {data && !loading && (
        <>
          {/* Hero KPIs — platform health, no shop finances */}
          <section className="rounded-card border border-border bg-surface p-5 shadow-card">
            <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
              <Kpi icon={<Store className="h-4 w-4" />} label="Total shops" value={data.total_shops} sub={data.new_shops > 0 ? `+${data.new_shops} new this period` : "No new shops"} accent="text-ink" />
              <Kpi icon={<Sprout className="h-4 w-4" />} label="Active shops" value={`${data.active_shops}/${data.total_shops}`} sub="billed in period" accent="text-primary-700" />
              <Kpi icon={<Activity className="h-4 w-4" />} label="Bills created" value={data.total_bills} sub="app activity" accent="text-ink" />
              <Kpi icon={<UserCog className="h-4 w-4" />} label="Staff" value={data.total_staff} sub={`${data.total_owners} owner${data.total_owners === 1 ? "" : "s"}`} accent="text-ink" />
            </div>
          </section>

          {/* Secondary counts */}
          <div className="grid grid-cols-3 gap-3">
            <MiniStat label="Inactive shops" value={data.inactive_shops} tone={data.inactive_shops > 0 ? "warn" : "ok"} />
            <MiniStat label="Without owner" value={unowned} tone={unowned > 0 ? "warn" : "ok"} />
            <MiniStat label="Needs attention" value={data.attention.length} tone={data.attention.length > 0 ? "danger" : "ok"} />
          </div>

          {/* Activity trend */}
          <section className="rounded-card border border-border bg-surface p-5 shadow-card">
            <div className="mb-4 flex items-center justify-between">
              <h2 className="text-lg font-bold text-ink">Daily activity</h2>
              <span className="text-sm font-semibold text-ink-soft">bills created per day</span>
            </div>
            {data.trend.length <= 1 ? (
              <p className="py-8 text-center text-ink-soft">Pick a longer range to see the trend.</p>
            ) : (
              <>
                <Bars data={data.trend.map((t) => ({ label: shortDate(t.date), value: t.bills }))} format={(v) => `${v} bills`} />
                <div className="mt-2 flex justify-between text-xs font-medium text-ink-soft">
                  <span>{shortDate(data.trend[0].date)}</span>
                  <span>{shortDate(data.trend[data.trend.length - 1].date)}</span>
                </div>
              </>
            )}
          </section>

          {/* Attention + Most active */}
          <div className="grid gap-4 lg:grid-cols-2">
            <section className="rounded-card border border-border bg-surface p-5 shadow-card">
              <h2 className="mb-4 flex items-center gap-2 text-lg font-bold text-ink">
                <AlertTriangle className="h-5 w-5 text-warning" /> Needs attention
              </h2>
              {data.attention.length === 0 ? (
                <p className="py-6 text-center text-ink-soft">Every shop is active, owned, and billing. 🎉</p>
              ) : (
                <div className="space-y-2">
                  {data.attention.slice(0, 8).map((a) => (
                    <button
                      key={`${a.kind}-${a.shop_id}`}
                      type="button"
                      onClick={() => navigate(`/admin/shops?focus=${a.shop_id}`)}
                      className="flex w-full items-center justify-between gap-3 rounded-xl border border-slate-100 bg-slate-50 px-3 py-2.5 text-left transition-colors hover:border-border hover:bg-white"
                    >
                      <span className="min-w-0">
                        <span className="block truncate font-bold text-ink">{a.shop_name}</span>
                        <span className="block truncate text-sm text-ink-soft">{a.detail}</span>
                      </span>
                      <span className={["shrink-0 rounded-full px-2.5 py-0.5 text-xs font-bold", KIND_STYLE[a.kind] ?? "bg-slate-100 text-ink-soft"].join(" ")}>
                        {KIND_LABEL[a.kind] ?? a.kind}
                      </span>
                    </button>
                  ))}
                </div>
              )}
            </section>

            <section className="rounded-card border border-border bg-surface p-5 shadow-card">
              <h2 className="mb-4 text-lg font-bold text-ink">Most active shops</h2>
              {byActivity.length === 0 || byActivity.every((s) => s.bills_in_period === 0) ? (
                <p className="py-6 text-center text-ink-soft">No activity in this period.</p>
              ) : (
                <div className="space-y-1">
                  {byActivity.map((s, i) => (
                    <button
                      key={s.shop_id}
                      type="button"
                      onClick={() => navigate(`/admin/shops?focus=${s.shop_id}`)}
                      className="flex w-full items-center justify-between gap-3 rounded-xl px-3 py-2.5 text-left hover:bg-slate-50"
                    >
                      <span className="flex min-w-0 items-center gap-2">
                        <span className={["h-2.5 w-2.5 shrink-0 rounded-full", health(s.last_bill_at)].join(" ")} />
                        <span className="min-w-0">
                          <span className="block truncate font-bold text-ink">{i + 1}. {s.shop_name}</span>
                          <span className="block text-sm text-ink-soft">{fromNow(s.last_bill_at)}</span>
                        </span>
                      </span>
                      <span className="shrink-0 text-right">
                        <span className="block font-extrabold tracking-tight text-primary-700">{s.bills_in_period}</span>
                        <span className="block text-xs text-ink-soft">bills</span>
                      </span>
                    </button>
                  ))}
                </div>
              )}
            </section>
          </div>

          {/* Recently onboarded */}
          <section className="rounded-card border border-border bg-surface p-5 shadow-card">
            <h2 className="mb-4 flex items-center gap-2 text-lg font-bold text-ink">
              <Users className="h-5 w-5 text-primary-600" /> Recently onboarded
            </h2>
            {recentShops.length === 0 ? (
              <p className="py-6 text-center text-ink-soft">No shops yet.</p>
            ) : (
              <div className="divide-y divide-border">
                {recentShops.map((s) => (
                  <button
                    key={s.shop_id}
                    type="button"
                    onClick={() => navigate(`/admin/shops?focus=${s.shop_id}`)}
                    className="flex w-full items-center justify-between gap-3 py-3 text-left first:pt-0 last:pb-0 hover:opacity-80"
                  >
                    <span className="min-w-0">
                      <span className="block truncate font-bold text-ink">{s.shop_name}</span>
                      <span className="block truncate text-sm text-ink-soft">{s.owner_email ?? "No manager"} · {s.staff_count} staff</span>
                    </span>
                    <span className="shrink-0 text-sm font-semibold text-ink-soft">
                      {new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", year: "numeric" }).format(new Date(s.created_at))}
                    </span>
                  </button>
                ))}
              </div>
            )}
          </section>
        </>
      )}
    </div>
  );
}

function Kpi({ icon, label, value, sub, accent }: { icon?: React.ReactNode; label: string; value: string | number; sub?: string; accent: string }) {
  return (
    <div className="rounded-xl border border-slate-100 bg-slate-50 p-4">
      <div className="flex items-center gap-1.5 text-xs font-bold uppercase tracking-wider text-ink-soft">
        {icon && <span className="text-ink-soft/70">{icon}</span>}
        {label}
      </div>
      <div className={["mt-1.5 block text-3xl font-extrabold tracking-tight", accent].join(" ")}>{value}</div>
      {sub && <div className="mt-1 block text-xs font-medium text-ink-soft">{sub}</div>}
    </div>
  );
}

function MiniStat({ label, value, tone }: { label: string; value: number; tone: "ok" | "warn" | "danger" }) {
  const valueColor = value === 0 ? "text-ink-soft" : tone === "danger" ? "text-danger" : tone === "warn" ? "text-warning" : "text-ink";
  return (
    <div className="rounded-card border border-border bg-surface px-4 py-3 shadow-card">
      <div className="text-xs font-bold uppercase tracking-wider text-ink-soft">{label}</div>
      <div className={["mt-0.5 text-2xl font-extrabold tracking-tight", valueColor].join(" ")}>{value}</div>
    </div>
  );
}
