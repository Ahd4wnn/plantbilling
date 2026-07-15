import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { AlertTriangle, TrendingUp, Store, IndianRupee, ReceiptText } from "lucide-react";
import { getAdminOverview, type AdminOverview } from "@/api/adminAnalytics";
import { friendlyError } from "@/api/client";
import { Spinner } from "@/components/Spinner";
import { Button } from "@/components/Button";
import { Bars } from "@/components/MiniChart";

function inr(v: string | number): string {
  const n = typeof v === "string" ? Number(v) : v;
  return "₹" + (isFinite(n) ? n : 0).toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}
function inrCompact(n: number): string {
  if (n >= 1e7) return "₹" + (n / 1e7).toFixed(1) + "Cr";
  if (n >= 1e5) return "₹" + (n / 1e5).toFixed(1) + "L";
  if (n >= 1e3) return "₹" + (n / 1e3).toFixed(1) + "k";
  return "₹" + Math.round(n);
}
function ymd(d: Date): string {
  return d.toISOString().slice(0, 10);
}
function shortDate(iso: string): string {
  return new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short" }).format(new Date(iso + "T00:00:00"));
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
  inactive: "bg-surface-muted text-ink-soft",
  no_owner: "bg-warning-soft text-warning",
  silent: "bg-danger-soft text-danger",
};
const KIND_LABEL: Record<string, string> = { inactive: "Inactive", no_owner: "No owner", silent: "Quiet" };

export function DashboardPage() {
  const navigate = useNavigate();
  const [period, setPeriod] = useState<Period>("today");
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

  const topShops = useMemo(
    () => (data ? [...data.shops].sort((a, b) => Number(b.total_sales) - Number(a.total_sales)).slice(0, 6) : []),
    [data],
  );

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-extrabold text-ink">Platform overview</h1>
          <p className="text-sm text-ink-soft">Across every shop · {period === "today" ? "Today" : period === "week" ? "Last 7 days" : "Last 30 days"}</p>
        </div>
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
          {/* Hero KPIs */}
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <Kpi icon={<IndianRupee className="h-4.5 w-4.5" />} label="Total sales" value={inr(data.total_sales)} accent="text-primary-700" />
            <Kpi icon={<TrendingUp className="h-4.5 w-4.5" />} label="Net income" value={inr(data.net_sales)} accent={Number(data.net_sales) < 0 ? "text-danger" : "text-primary-700"} />
            <Kpi icon={<ReceiptText className="h-4.5 w-4.5" />} label="Bills" value={data.bill_count.toLocaleString("en-IN")} accent="text-ink" />
            <Kpi icon={<Store className="h-4.5 w-4.5" />} label="Active shops" value={`${data.active_shops}/${data.total_shops}`} accent="text-ink" />
          </div>
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <Kpi label="Cash" value={inr(data.cash_total)} accent="text-emerald-700" small />
            <Kpi label="UPI" value={inr(data.upi_total)} accent="text-sky-700" small />
            <Kpi label="Due" value={inr(data.due_total)} accent="text-amber-700" small />
            <Kpi label="Expenses" value={"− " + inr(data.total_expenses)} accent="text-danger" small />
          </div>

          {/* Trend */}
          <section className="rounded-card border border-border bg-white p-4 shadow-card">
            <div className="mb-3 flex items-center justify-between">
              <h2 className="text-lg font-bold text-ink">Sales trend</h2>
              <span className="text-sm text-ink-soft">{data.trend.length} day{data.trend.length === 1 ? "" : "s"}</span>
            </div>
            {data.trend.length <= 1 ? (
              <p className="py-8 text-center text-ink-soft">Pick a longer range to see the trend.</p>
            ) : (
              <>
                <Bars data={data.trend.map((t) => ({ label: shortDate(t.date), value: Number(t.sales) }))} format={inrCompact} />
                <div className="mt-2 flex justify-between text-xs text-ink-soft">
                  <span>{shortDate(data.trend[0].date)}</span>
                  <span>{shortDate(data.trend[data.trend.length - 1].date)}</span>
                </div>
              </>
            )}
          </section>

          {/* Attention + Top shops */}
          <div className="grid gap-4 lg:grid-cols-2">
            <section className="rounded-card border border-border bg-white p-4 shadow-card">
              <h2 className="mb-3 flex items-center gap-2 text-lg font-bold text-ink">
                <AlertTriangle className="h-5 w-5 text-warning" /> Needs attention
              </h2>
              {data.attention.length === 0 ? (
                <p className="py-6 text-center text-ink-soft">All shops are active, owned, and billing. 🎉</p>
              ) : (
                <div className="space-y-2">
                  {data.attention.slice(0, 8).map((a) => (
                    <button
                      key={`${a.kind}-${a.shop_id}`}
                      type="button"
                      onClick={() => navigate(`/admin/shops?focus=${a.shop_id}`)}
                      className="flex w-full items-center justify-between gap-3 rounded-control border border-border px-3 py-2.5 text-left hover:bg-surface-muted"
                    >
                      <span className="min-w-0">
                        <span className="block truncate font-semibold text-ink">{a.shop_name}</span>
                        <span className="block truncate text-sm text-ink-soft">{a.detail}</span>
                      </span>
                      <span className={["shrink-0 rounded-full px-2 py-0.5 text-xs font-bold", KIND_STYLE[a.kind] ?? "bg-surface-muted text-ink-soft"].join(" ")}>
                        {KIND_LABEL[a.kind] ?? a.kind}
                      </span>
                    </button>
                  ))}
                </div>
              )}
            </section>

            <section className="rounded-card border border-border bg-white p-4 shadow-card">
              <h2 className="mb-3 text-lg font-bold text-ink">Top shops</h2>
              {topShops.length === 0 || topShops.every((s) => Number(s.total_sales) === 0) ? (
                <p className="py-6 text-center text-ink-soft">No sales in this period.</p>
              ) : (
                <div className="space-y-2">
                  {topShops.map((s, i) => (
                    <button
                      key={s.shop_id}
                      type="button"
                      onClick={() => navigate(`/admin/shops?focus=${s.shop_id}`)}
                      className="flex w-full items-center justify-between gap-3 rounded-control px-3 py-2 text-left hover:bg-surface-muted"
                    >
                      <span className="min-w-0">
                        <span className="block truncate font-semibold text-ink">{i + 1}. {s.shop_name}</span>
                        <span className="block text-sm text-ink-soft">{s.bill_count} bills · Net {inr(s.net_sales)}</span>
                      </span>
                      <span className="shrink-0 font-bold text-primary-700">{inr(s.total_sales)}</span>
                    </button>
                  ))}
                </div>
              )}
            </section>
          </div>

          {/* Top sellers */}
          <section>
            <h2 className="mb-2 text-lg font-bold text-ink">Top sellers</h2>
            {data.staff.length === 0 ? (
              <p className="rounded-card border border-border bg-white p-4 text-ink-soft">No sales in this period.</p>
            ) : (
              <div className="overflow-hidden rounded-card border border-border bg-white shadow-card">
                {data.staff.map((st, i) => (
                  <div key={`${st.user_id}-${st.shop_id}`} className="flex items-center justify-between border-b border-border px-4 py-3 last:border-0">
                    <div className="min-w-0">
                      <div className="truncate font-medium text-ink">{i + 1}. {st.email ?? "—"}</div>
                      <div className="text-sm text-ink-soft">{st.shop_name} · {st.role} · {st.bill_count} bills</div>
                    </div>
                    <span className="font-semibold text-ink">{inr(st.total_sales)}</span>
                  </div>
                ))}
              </div>
            )}
          </section>
        </>
      )}
    </div>
  );
}

function Kpi({ icon, label, value, accent, small }: { icon?: React.ReactNode; label: string; value: string; accent: string; small?: boolean }) {
  return (
    <div className="rounded-card border border-border bg-white p-4 shadow-card">
      <div className="flex items-center gap-1.5 text-xs font-medium uppercase tracking-wide text-ink-soft">
        {icon && <span className="text-ink-soft/70">{icon}</span>}
        {label}
      </div>
      <div className={[small ? "text-lg" : "text-2xl", "mt-1 font-extrabold", accent].join(" ")}>{value}</div>
    </div>
  );
}
