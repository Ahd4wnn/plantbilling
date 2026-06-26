import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getOwnerOverview, type OwnerOverview } from "@/api/owner";
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
  const d = new Date(now.getFullYear(), now.getMonth(), 1);
  return { from: ymd(d), to };
}

export function OwnerDashboard() {
  const navigate = useNavigate();
  const [period, setPeriod] = useState<Period>("today");
  const [data, setData] = useState<OwnerOverview | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const range = useMemo(() => rangeFor(period), [period]);

  useEffect(() => {
    let alive = true;
    setLoading(true);
    setError(null);
    getOwnerOverview(range.from, range.to)
      .then((d) => alive && setData(d))
      .catch((e) => alive && setError(friendlyError(e, "Couldn't load your dashboard.")))
      .finally(() => alive && setLoading(false));
    return () => {
      alive = false;
    };
  }, [range.from, range.to]);

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-bold text-ink">Business overview</h1>
        <div className="flex rounded-control border border-border bg-white p-1">
          {(["today", "week", "month"] as Period[]).map((p) => (
            <button
              key={p}
              type="button"
              onClick={() => setPeriod(p)}
              className={[
                "rounded-control px-3 py-1.5 text-sm font-semibold capitalize transition-colors",
                period === p ? "bg-primary-600 text-white" : "text-ink-soft hover:bg-surface-muted",
              ].join(" ")}
            >
              {p === "week" ? "Last 7 days" : p === "month" ? "This month" : "Today"}
            </button>
          ))}
        </div>
      </div>

      {loading && (
        <div className="flex justify-center py-16 text-primary-600">
          <Spinner className="h-8 w-8" label="Loading" />
        </div>
      )}
      {error && <div className="rounded-card border border-danger/30 bg-danger/5 p-4 text-danger">{error}</div>}

      {data && !loading && (
        <>
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
            <Kpi label="Total sales" value={inr(data.total_sales)} accent="text-primary-700" />
            <Kpi label="Expenses" value={"− " + inr(data.total_expenses)} accent="text-danger" />
            <Kpi label="Net income" value={inr(data.net_sales)} accent={Number(data.net_sales) < 0 ? "text-danger" : "text-primary-700"} />
            <Kpi label="Bills" value={String(data.bill_count)} accent="text-ink" />
          </div>

          <div className="grid grid-cols-3 gap-3">
            <Kpi label="Cash" value={inr(data.cash_total)} accent="text-emerald-700" small />
            <Kpi label="UPI" value={inr(data.upi_total)} accent="text-sky-700" small />
            <Kpi label="Due" value={inr(data.due_total)} accent="text-amber-700" small />
          </div>

          <section>
            <h2 className="mb-2 text-lg font-semibold text-ink">
              Shops <span className="text-ink-soft">({data.shop_count})</span>
            </h2>
            {data.shops.length === 0 ? (
              <p className="rounded-card border border-border bg-white p-4 text-ink-soft">
                No shops are linked to your account yet. Ask the admin to assign your shops.
              </p>
            ) : (
              <div className="grid gap-3 sm:grid-cols-2">
                {data.shops.map((s) => (
                  <button
                    key={s.shop_id}
                    type="button"
                    onClick={() => navigate(`/owner/shops/${s.shop_id}`)}
                    className="rounded-card border border-border bg-white p-4 text-left transition-shadow hover:shadow-sm"
                  >
                    <div className="flex items-center justify-between">
                      <span className="font-semibold text-ink">{s.shop_name}</span>
                      <span className="text-lg font-bold text-primary-700">{inr(s.total_sales)}</span>
                    </div>
                    <div className="mt-1 text-sm text-ink-soft">
                      {s.bill_count} bills · Net {inr(s.net_sales)} · Due {inr(s.due_total)}
                    </div>
                  </button>
                ))}
              </div>
            )}
          </section>

          <section>
            <h2 className="mb-2 text-lg font-semibold text-ink">Top sellers</h2>
            {data.staff.length === 0 ? (
              <p className="rounded-card border border-border bg-white p-4 text-ink-soft">No sales in this period.</p>
            ) : (
              <div className="overflow-hidden rounded-card border border-border bg-white">
                {data.staff.map((st, i) => (
                  <div
                    key={`${st.user_id}-${st.shop_id}`}
                    className="flex items-center justify-between border-b border-border px-4 py-3 last:border-0"
                  >
                    <div className="min-w-0">
                      <div className="truncate font-medium text-ink">
                        {i + 1}. {st.email ?? "—"}
                      </div>
                      <div className="text-sm text-ink-soft">
                        {st.shop_name} · {st.role} · {st.bill_count} bills
                      </div>
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

function Kpi({ label, value, accent, small }: { label: string; value: string; accent: string; small?: boolean }) {
  return (
    <div className="rounded-card border border-border bg-white p-4">
      <div className="text-xs font-medium uppercase tracking-wide text-ink-soft">{label}</div>
      <div className={[small ? "text-lg" : "text-xl", "font-bold", accent].join(" ")}>{value}</div>
    </div>
  );
}
