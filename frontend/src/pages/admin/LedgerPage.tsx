import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  Plus,
  Wallet,
  Smartphone,
  Clock,
  TrendingUp,
  TrendingDown,
  X,
  Trash2,
  Undo2,
  IndianRupee,
} from "lucide-react";
import {
  listSales,
  listExpenses,
  listTrash,
  createSale,
  createExpense,
  collectDue,
  deleteSale,
  deleteExpense,
  restoreSale,
  restoreExpense,
  getLedgerSummary,
  type AdminSale,
  type AdminExpense,
  type LedgerSummary,
  type PaymentMethod,
  type TrashedEntry,
} from "@/api/adminLedger";
import { friendlyError } from "@/api/client";
import { Button } from "@/components/Button";
import { ConfirmDialog } from "@/components/ConfirmDialog";
import { Spinner } from "@/components/Spinner";
import { Bars } from "@/components/MiniChart";
import { formatINR, toPaise, fromPaise } from "@/lib/money";
import { todayISO, shiftDay, formatDay, formatDateTime } from "@/lib/datetime";

type Period = "today" | "week" | "month" | "year" | "all";
type Tab = "sales" | "expenses" | "dues" | "trash";

/** How many rows each list pulls per request. The API caps this at 200. */
const PAGE_SIZE = 100;

const PERIOD_LABEL: Record<Period, string> = {
  today: "Today",
  week: "7 days",
  month: "30 days",
  year: "This year",
  all: "All time",
};

/** Prose for the KPI captions, so a tile never implies it's a lifetime total. */
const PERIOD_CAPTION: Record<Period, string> = {
  today: "today",
  week: "in the last 7 days",
  month: "in the last 30 days",
  year: "this year",
  all: "all time",
};

/**
 * The window each period covers. "all" returns no bounds at all: the params are
 * then omitted from the request and the API returns everything.
 *
 * There deliberately IS an all-time option. Without one, the page silently
 * capped every list and every total at 30 days, and entries older than that
 * looked as though they had been deleted.
 */
function rangeFor(period: Period): { from?: string; to?: string } {
  const to = todayISO();
  if (period === "all") return {};
  if (period === "today") return { from: to, to };
  if (period === "year") return { from: `${to.slice(0, 4)}-01-01`, to };
  return { from: shiftDay(to, period === "week" ? -6 : -29), to };
}

const PERIOD_KEY = "plantora.ledger.period";

function loadPeriod(): Period {
  const saved = localStorage.getItem(PERIOD_KEY);
  return saved && saved in PERIOD_LABEL ? (saved as Period) : "month";
}

const inr = (s: string) => formatINR(toPaise(s));

export type Range = { from?: string; to?: string };

export function LedgerPage() {
  // Remembered across visits: an admin who switches to All time to find an old
  // entry shouldn't be dropped back into the 30-day window on every reload.
  const [period, setPeriodState] = useState<Period>(loadPeriod);
  const setPeriod = useCallback((p: Period) => {
    setPeriodState(p);
    localStorage.setItem(PERIOD_KEY, p);
  }, []);
  const range = useMemo(() => rangeFor(period), [period]);
  const [reloadKey, setReloadKey] = useState(0);
  const refresh = useCallback(() => setReloadKey((k) => k + 1), []);

  const [tab, setTab] = useState<Tab>("sales");
  const [addSaleOpen, setAddSaleOpen] = useState(false);
  const [addExpenseOpen, setAddExpenseOpen] = useState(false);

  // ── Summary ────────────────────────────────────────────────────────────
  const [summary, setSummary] = useState<LedgerSummary | null>(null);
  const [sumLoading, setSumLoading] = useState(true);
  const [sumError, setSumError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    setSumLoading(true);
    setSumError(null);
    getLedgerSummary(range.from, range.to, period === "all")
      .then((d) => alive && setSummary(d))
      .catch((e) => alive && setSumError(friendlyError(e, "Couldn't load the summary.")))
      .finally(() => alive && setSumLoading(false));
    return () => {
      alive = false;
    };
  }, [range.from, range.to, period, reloadKey]);

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-extrabold tracking-tight text-ink">Sales &amp; Expenses</h1>
          <p className="text-base font-semibold text-ink-soft">Your own books — money you earn and spend</p>
        </div>
        <div className="flex items-center gap-2">
          <div className="flex flex-wrap rounded-control border border-border bg-white p-1">
            {(["today", "week", "month", "year", "all"] as Period[]).map((p) => (
              <button
                key={p}
                type="button"
                onClick={() => setPeriod(p)}
                className={[
                  "rounded-control px-3 py-1.5 text-sm font-bold transition-colors",
                  period === p ? "bg-primary-600 text-white" : "text-ink-soft hover:bg-surface-muted",
                ].join(" ")}
              >
                {PERIOD_LABEL[p]}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Dashboard */}
      {sumLoading && (
        <div className="flex justify-center py-16">
          <Spinner className="h-8 w-8 text-primary-600" />
        </div>
      )}
      {sumError && !sumLoading && (
        <div className="py-12 text-center">
          <p className="text-base font-semibold text-danger">{sumError}</p>
          <Button variant="secondary" size="tap" className="mt-4" onClick={refresh}>
            Try again
          </Button>
        </div>
      )}
      {summary && !sumLoading && <Dashboard summary={summary} period={period} />}

      {/* Add actions */}
      <div className="flex flex-wrap gap-3">
        <Button variant="primary" size="tap" onClick={() => setAddSaleOpen(true)}>
          <Plus className="h-5 w-5" /> Log a sale
        </Button>
        <Button variant="secondary" size="tap" className="border-2 bg-white" onClick={() => setAddExpenseOpen(true)}>
          <Plus className="h-5 w-5" /> Log an expense
        </Button>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 border-b border-border">
        {(
          [
            ["sales", "Sales"],
            ["expenses", "Expenses"],
            ["dues", "Outstanding dues"],
            ["trash", "Recently deleted"],
          ] as [Tab, string][]
        ).map(([t, label]) => (
          <button
            key={t}
            type="button"
            onClick={() => setTab(t)}
            className={[
              "-mb-px border-b-2 px-4 py-2.5 text-base font-bold transition-colors",
              tab === t ? "border-primary-600 text-primary-700" : "border-transparent text-ink-soft hover:text-ink",
            ].join(" ")}
          >
            {label}
          </button>
        ))}
      </div>

      {tab === "sales" && <SalesList range={range} period={period} reloadKey={reloadKey} onChanged={refresh} />}
      {tab === "expenses" && <ExpensesList range={range} period={period} reloadKey={reloadKey} onChanged={refresh} />}
      {tab === "dues" && <DuesList reloadKey={reloadKey} onChanged={refresh} />}
      {tab === "trash" && <TrashListView reloadKey={reloadKey} onChanged={refresh} />}

      {addSaleOpen && (
        <AddSaleModal
          onClose={() => setAddSaleOpen(false)}
          onSaved={() => {
            setAddSaleOpen(false);
            refresh();
          }}
        />
      )}
      {addExpenseOpen && (
        <AddExpenseModal
          onClose={() => setAddExpenseOpen(false)}
          onSaved={() => {
            setAddExpenseOpen(false);
            refresh();
          }}
        />
      )}
    </div>
  );
}

// ── Dashboard ────────────────────────────────────────────────────────────
function Dashboard({ summary, period }: { summary: LedgerSummary; period: Period }) {
  const collected = toPaise(summary.cash_collected) + toPaise(summary.upi_collected);
  const netPositive = toPaise(summary.net_collected) >= 0;
  const when = PERIOD_CAPTION[period];
  // The tile shows every rupee still owed, ever — not just dues on sales that
  // happen to fall inside the current window. It used to show the windowed
  // figure, which disagreed with the Outstanding dues tab on this same page.
  const dueP = toPaise(summary.outstanding_due_all_time);

  const trend = summary.trend.map((t) => ({
    label: formatDay(`${t.date}T00:00:00`),
    value: Math.round(toPaise(t.sales) / 100),
  }));

  return (
    <div className="space-y-4">
      <section className="rounded-card border border-border bg-surface p-5 shadow-card">
        <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
          <Kpi
            icon={<IndianRupee className="h-4 w-4" />}
            label="Total sales"
            value={inr(summary.total_sales)}
            sub={`${summary.sales_count} sale${summary.sales_count === 1 ? "" : "s"} ${when}`}
            accent="text-ink"
          />
          <Kpi
            icon={<TrendingUp className="h-4 w-4" />}
            label="Collected"
            value={formatINR(collected)}
            sub={`cash + UPI ${when}`}
            accent="text-primary-700"
          />
          <Kpi
            icon={<Clock className="h-4 w-4" />}
            label="Outstanding due"
            value={inr(summary.outstanding_due_all_time)}
            sub={
              dueP > 0
                ? `all time · ${summary.dues_count_all_time} unpaid`
                : "all time · all settled"
            }
            accent={dueP > 0 ? "text-warning" : "text-ink-soft"}
          />
          <Kpi
            icon={<TrendingDown className="h-4 w-4" />}
            label="Expenses"
            value={inr(summary.total_expenses)}
            sub={`${summary.expenses_count} entr${summary.expenses_count === 1 ? "y" : "ies"} ${when}`}
            accent="text-ink"
          />
        </div>

        {/* Cash / UPI split + net */}
        <div className="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-3">
          <SplitTile icon={<Wallet className="h-4 w-4" />} label="Cash" value={inr(summary.cash_collected)} tone="cash" />
          <SplitTile icon={<Smartphone className="h-4 w-4" />} label="UPI" value={inr(summary.upi_collected)} tone="upi" />
          <div
            className={[
              "rounded-xl border px-4 py-3",
              netPositive ? "border-primary-100 bg-primary-50" : "border-danger-soft bg-danger-soft/40",
            ].join(" ")}
          >
            <div className="text-xs font-bold uppercase tracking-wider text-ink-soft">
              Net (collected − expenses) · {when}
            </div>
            <div className={["mt-0.5 text-2xl font-extrabold tracking-tight", netPositive ? "text-primary-700" : "text-danger"].join(" ")}>
              {inr(summary.net_collected)}
            </div>
          </div>
        </div>
      </section>

      {/* Trend */}
      <section className="rounded-card border border-border bg-surface p-5 shadow-card">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg font-bold text-ink">Sales trend</h2>
          <span className="text-sm font-semibold text-ink-soft">₹ sales per day</span>
        </div>
        {trend.length <= 1 ? (
          <p className="py-8 text-center text-ink-soft">Not enough days yet — log a few sales to see the trend.</p>
        ) : (
          <>
            <Bars data={trend} format={(v) => formatINR(v * 100)} />
            <div className="mt-2 flex justify-between text-xs font-medium text-ink-soft">
              <span>{trend[0].label}</span>
              <span>{trend[trend.length - 1].label}</span>
            </div>
          </>
        )}
      </section>
    </div>
  );
}

function Kpi({ icon, label, value, sub, accent }: { icon?: React.ReactNode; label: string; value: string; sub?: string; accent: string }) {
  return (
    <div className="rounded-xl border border-slate-100 bg-slate-50 p-4">
      <div className="flex items-center gap-1.5 text-xs font-bold uppercase tracking-wider text-ink-soft">
        {icon && <span className="text-ink-soft/70">{icon}</span>}
        {label}
      </div>
      <div className={["mt-1.5 block text-2xl font-extrabold tracking-tight", accent].join(" ")}>{value}</div>
      {sub && <div className="mt-1 block text-xs font-medium text-ink-soft">{sub}</div>}
    </div>
  );
}

function SplitTile({ icon, label, value, tone }: { icon: React.ReactNode; label: string; value: string; tone: "cash" | "upi" }) {
  const color = tone === "cash" ? "text-cash" : "text-upi";
  return (
    <div className="flex items-center justify-between rounded-xl border border-border bg-white px-4 py-3">
      <span className="flex items-center gap-2 text-sm font-bold text-ink-soft">
        <span className={color}>{icon}</span>
        {label}
      </span>
      <span className={["text-lg font-extrabold tracking-tight", color].join(" ")}>{value}</span>
    </div>
  );
}

// ── Paging ─────────────────────────────────────────────────────────────────
/**
 * A list that loads one page at a time and can fetch the rest.
 *
 * The lists here used to ask for 100 rows, throw away the `has_more` flag the
 * API returns, and render whatever came back — so beyond 100 entries the rest
 * simply weren't on screen, with nothing to say so. A list showing a subset has
 * to offer the remainder.
 */
function usePagedList<T>(
  fetchPage: (offset: number) => Promise<{ items: T[]; has_more: boolean }>,
  errorMessage: string,
  deps: React.DependencyList,
) {
  const [rows, setRows] = useState<T[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [hasMore, setHasMore] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Held in a ref so callers can pass an inline closure without the effect
  // refiring on every render; `deps` stays the single source of when to reload.
  const fetchRef = useRef(fetchPage);
  fetchRef.current = fetchPage;

  useEffect(() => {
    let alive = true;
    setLoading(true);
    setError(null);
    fetchRef.current(0)
      .then((r) => {
        if (!alive) return;
        setRows(r.items);
        setHasMore(r.has_more);
      })
      .catch((e) => alive && setError(friendlyError(e, errorMessage)))
      .finally(() => alive && setLoading(false));
    return () => {
      alive = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  const loadMore = useCallback(async () => {
    setLoadingMore(true);
    try {
      const r = await fetchRef.current(rows.length);
      setRows((prev) => [...prev, ...r.items]);
      setHasMore(r.has_more);
    } catch (e) {
      setError(friendlyError(e, errorMessage));
    } finally {
      setLoadingMore(false);
    }
  }, [rows.length, errorMessage]);

  return { rows, loading, loadingMore, hasMore, error, loadMore };
}

function LoadMore({
  shown,
  loading,
  onClick,
}: {
  shown: number;
  loading: boolean;
  onClick: () => void;
}) {
  return (
    <div className="pt-2 text-center">
      <Button variant="secondary" size="tap" onClick={onClick} loading={loading} loadingLabel="Loading…">
        Load more
      </Button>
      <p className="mt-2 text-sm font-medium text-ink-soft">
        Showing the first {shown} — there are more.
      </p>
    </div>
  );
}

// ── Sales list ─────────────────────────────────────────────────────────────
function SalesList({
  range,
  period,
  reloadKey,
  onChanged,
}: {
  range: Range;
  period: Period;
  reloadKey: number;
  onChanged: () => void;
}) {
  const { rows, loading, loadingMore, hasMore, error, loadMore } = usePagedList<AdminSale>(
    (offset) =>
      listSales({ date_from: range.from, date_to: range.to, limit: PAGE_SIZE, offset }),
    "Couldn't load sales.",
    [range.from, range.to, reloadKey],
  );
  const [pendingDelete, setPendingDelete] = useState<AdminSale | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const confirmDelete = async () => {
    if (!pendingDelete) return;
    try {
      await deleteSale(pendingDelete.id);
      setPendingDelete(null);
      setDeleteError(null);
      onChanged();
    } catch (e) {
      setDeleteError(friendlyError(e, "Couldn't delete the sale."));
    }
  };

  if (loading) return <ListSpinner />;
  if (error) return <ListError message={error} />;
  if (rows.length === 0)
    return (
      <Empty
        message={
          period === "all"
            ? "No sales recorded yet. Log your first sale above."
            : `No sales ${PERIOD_CAPTION[period]}. Try “All time” to see older entries.`
        }
      />
    );

  return (
    <div className="space-y-2">
      {rows.map((s) => (
        <div key={s.id} className="flex items-center justify-between gap-3 rounded-card border border-border bg-white px-4 py-3 shadow-card">
          <div className="min-w-0">
            <div className="truncate font-bold text-ink">{s.title}</div>
            <div className="truncate text-sm text-ink-soft">
              {formatDay(`${s.occurred_on}T00:00:00`)}
              {s.customer_name ? ` · ${s.customer_name}` : ""}
              {" · "}
              <PaymentBadges sale={s} />
            </div>
          </div>
          <div className="flex shrink-0 items-center gap-3">
            <span className="text-lg font-extrabold tracking-tight text-ink">{inr(s.amount)}</span>
            <button
              type="button"
              onClick={() => setPendingDelete(s)}
              className="rounded-lg p-2 text-ink-soft hover:bg-danger-soft hover:text-danger"
              aria-label="Delete sale"
            >
              <Trash2 className="h-4 w-4" />
            </button>
          </div>
        </div>
      ))}
      {hasMore && <LoadMore shown={rows.length} loading={loadingMore} onClick={loadMore} />}

      <ConfirmDialog
        open={pendingDelete !== null}
        title="Delete this sale?"
        body={
          <>
            <strong className="text-ink">{pendingDelete?.title}</strong>
            {pendingDelete ? ` — ${inr(pendingDelete.amount)}` : ""}
            <br />
            It moves to Recently deleted, where you can restore it.
            {deleteError && <span className="mt-2 block font-semibold text-danger">{deleteError}</span>}
          </>
        }
        confirmLabel="Delete"
        cancelLabel="Keep it"
        destructive
        onConfirm={confirmDelete}
        onCancel={() => {
          setPendingDelete(null);
          setDeleteError(null);
        }}
      />
    </div>
  );
}

function PaymentBadges({ sale }: { sale: AdminSale }) {
  const parts: string[] = [];
  if (toPaise(sale.cash_amount) > 0) parts.push(`Cash ${inr(sale.cash_amount)}`);
  if (toPaise(sale.upi_amount) > 0) parts.push(`UPI ${inr(sale.upi_amount)}`);
  if (toPaise(sale.due_amount) > 0) parts.push(`Due ${inr(sale.due_amount)}`);
  return <span>{parts.join(" · ") || "—"}</span>;
}

// ── Expenses list ────────────────────────────────────────────────────────
function ExpensesList({
  range,
  period,
  reloadKey,
  onChanged,
}: {
  range: Range;
  period: Period;
  reloadKey: number;
  onChanged: () => void;
}) {
  const { rows, loading, loadingMore, hasMore, error, loadMore } = usePagedList<AdminExpense>(
    (offset) =>
      listExpenses({ date_from: range.from, date_to: range.to, limit: PAGE_SIZE, offset }),
    "Couldn't load expenses.",
    [range.from, range.to, reloadKey],
  );
  const [pendingDelete, setPendingDelete] = useState<AdminExpense | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const confirmDelete = async () => {
    if (!pendingDelete) return;
    try {
      await deleteExpense(pendingDelete.id);
      setPendingDelete(null);
      setDeleteError(null);
      onChanged();
    } catch (e) {
      setDeleteError(friendlyError(e, "Couldn't delete the expense."));
    }
  };

  if (loading) return <ListSpinner />;
  if (error) return <ListError message={error} />;
  if (rows.length === 0)
    return (
      <Empty
        message={
          period === "all"
            ? "No expenses recorded yet."
            : `No expenses ${PERIOD_CAPTION[period]}. Try “All time” to see older entries.`
        }
      />
    );

  return (
    <div className="space-y-2">
      {rows.map((x) => (
        <div key={x.id} className="flex items-center justify-between gap-3 rounded-card border border-border bg-white px-4 py-3 shadow-card">
          <div className="min-w-0">
            <div className="truncate font-bold text-ink">{x.reason}</div>
            <div className="truncate text-sm text-ink-soft">
              {formatDay(`${x.occurred_on}T00:00:00`)} · Paid by {x.payment_method === "upi" ? "UPI" : "Cash"}
              {x.note ? ` · ${x.note}` : ""}
            </div>
          </div>
          <div className="flex shrink-0 items-center gap-3">
            <span className="text-lg font-extrabold tracking-tight text-danger">− {inr(x.amount)}</span>
            <button
              type="button"
              onClick={() => setPendingDelete(x)}
              className="rounded-lg p-2 text-ink-soft hover:bg-danger-soft hover:text-danger"
              aria-label="Delete expense"
            >
              <Trash2 className="h-4 w-4" />
            </button>
          </div>
        </div>
      ))}
      {hasMore && <LoadMore shown={rows.length} loading={loadingMore} onClick={loadMore} />}

      <ConfirmDialog
        open={pendingDelete !== null}
        title="Delete this expense?"
        body={
          <>
            <strong className="text-ink">{pendingDelete?.reason}</strong>
            {pendingDelete ? ` — ${inr(pendingDelete.amount)}` : ""}
            <br />
            It moves to Recently deleted, where you can restore it.
            {deleteError && <span className="mt-2 block font-semibold text-danger">{deleteError}</span>}
          </>
        }
        confirmLabel="Delete"
        cancelLabel="Keep it"
        destructive
        onConfirm={confirmDelete}
        onCancel={() => {
          setPendingDelete(null);
          setDeleteError(null);
        }}
      />
    </div>
  );
}

// ── Outstanding dues ───────────────────────────────────────────────────────
function DuesList({ reloadKey, onChanged }: { reloadKey: number; onChanged: () => void }) {
  // Deliberately unfiltered by period: a debt doesn't stop existing because the
  // dashboard is showing the last 7 days.
  const { rows, loading, loadingMore, hasMore, error, loadMore } = usePagedList<AdminSale>(
    (offset) => listSales({ due_only: true, limit: PAGE_SIZE, offset }),
    "Couldn't load dues.",
    [reloadKey],
  );
  const [collecting, setCollecting] = useState<AdminSale | null>(null);

  const total = rows.reduce((sum, r) => sum + toPaise(r.due_amount), 0);

  if (loading) return <ListSpinner />;
  if (error) return <ListError message={error} />;
  if (rows.length === 0) return <Empty message="No outstanding dues. Everyone's paid up. 🎉" />;

  return (
    <div className="space-y-3">
      <div className="rounded-card border border-warning-soft bg-warning-soft/40 px-4 py-3">
        <span className="text-sm font-bold uppercase tracking-wider text-warning">
          Total outstanding{hasMore ? " (so far)" : ""}
        </span>
        <span className="ml-2 text-xl font-extrabold tracking-tight text-warning">{formatINR(total)}</span>
        {hasMore && (
          <p className="mt-1 text-sm font-medium text-ink-soft">
            More dues haven't loaded yet — the tile above shows the true all-time total.
          </p>
        )}
      </div>
      {rows.map((s) => (
        <div key={s.id} className="flex items-center justify-between gap-3 rounded-card border border-border bg-white px-4 py-3 shadow-card">
          <div className="min-w-0">
            <div className="truncate font-bold text-ink">{s.title}</div>
            <div className="truncate text-sm text-ink-soft">
              {formatDay(`${s.occurred_on}T00:00:00`)}
              {s.customer_name ? ` · ${s.customer_name}` : ""}
              {s.customer_phone ? ` · ${s.customer_phone}` : ""}
            </div>
          </div>
          <div className="flex shrink-0 items-center gap-3">
            <div className="text-right">
              <div className="text-lg font-extrabold tracking-tight text-warning">{inr(s.due_amount)}</div>
              <div className="text-xs text-ink-soft">of {inr(s.amount)}</div>
            </div>
            <Button variant="primary" size="sm" onClick={() => setCollecting(s)}>
              Collect
            </Button>
          </div>
        </div>
      ))}

      {hasMore && <LoadMore shown={rows.length} loading={loadingMore} onClick={loadMore} />}

      {collecting && (
        <CollectModal
          sale={collecting}
          onClose={() => setCollecting(null)}
          onCollected={() => {
            setCollecting(null);
            onChanged();
          }}
        />
      )}
    </div>
  );
}

// ── Recently deleted ───────────────────────────────────────────────────────
/**
 * Everything an admin has removed, with who removed it and a way to put it
 * back. Deleting a money record used to be permanent and unlogged, so a mis-tap
 * on the trash icon was unrecoverable and left no trace.
 */
function TrashListView({ reloadKey, onChanged }: { reloadKey: number; onChanged: () => void }) {
  const { rows, loading, loadingMore, hasMore, error, loadMore } = usePagedList<TrashedEntry>(
    (offset) => listTrash({ limit: PAGE_SIZE, offset }),
    "Couldn't load deleted entries.",
    [reloadKey],
  );
  const [busyId, setBusyId] = useState<string | null>(null);
  const [restoreError, setRestoreError] = useState<string | null>(null);

  const onRestore = async (entry: TrashedEntry) => {
    setBusyId(entry.id);
    setRestoreError(null);
    try {
      if (entry.kind === "sale") await restoreSale(entry.id);
      else await restoreExpense(entry.id);
      onChanged();
    } catch (e) {
      setRestoreError(friendlyError(e, "Couldn't restore that entry."));
    } finally {
      setBusyId(null);
    }
  };

  if (loading) return <ListSpinner />;
  if (error) return <ListError message={error} />;
  if (rows.length === 0)
    return <Empty message="Nothing has been deleted. Anything you remove will show up here." />;

  return (
    <div className="space-y-2">
      {restoreError && <ListError message={restoreError} />}
      {rows.map((t) => (
        <div
          key={`${t.kind}-${t.id}`}
          className="flex items-center justify-between gap-3 rounded-card border border-border bg-slate-50 px-4 py-3 shadow-card"
        >
          <div className="min-w-0">
            <div className="truncate font-bold text-ink">
              <span className="mr-2 rounded-md bg-white px-1.5 py-0.5 text-xs font-bold uppercase tracking-wider text-ink-soft">
                {t.kind}
              </span>
              {t.label}
            </div>
            <div className="truncate text-sm text-ink-soft">
              {formatDay(`${t.occurred_on}T00:00:00`)} · deleted {formatDateTime(t.deleted_at)}
              {t.deleted_by_email ? ` by ${t.deleted_by_email}` : ""}
            </div>
          </div>
          <div className="flex shrink-0 items-center gap-3">
            <span className="text-lg font-extrabold tracking-tight text-ink-soft line-through">
              {inr(t.amount)}
            </span>
            <Button
              variant="secondary"
              size="sm"
              onClick={() => onRestore(t)}
              loading={busyId === t.id}
              loadingLabel="Restoring…"
            >
              <Undo2 className="h-4 w-4" /> Restore
            </Button>
          </div>
        </div>
      ))}
      {hasMore && <LoadMore shown={rows.length} loading={loadingMore} onClick={loadMore} />}
    </div>
  );
}

// ── Shared small bits ──────────────────────────────────────────────────────
function ListSpinner() {
  return (
    <div className="flex justify-center py-12">
      <Spinner className="h-8 w-8 text-primary-600" />
    </div>
  );
}
function ListError({ message }: { message: string }) {
  return <p className="py-10 text-center text-base font-semibold text-danger">{message}</p>;
}
function Empty({ message }: { message: string }) {
  return (
    <div className="rounded-card border border-border bg-slate-50 py-14 text-center shadow-card">
      <p className="text-base text-ink-soft">{message}</p>
    </div>
  );
}

// ── Modal shell ────────────────────────────────────────────────────────────
function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: React.ReactNode }) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);
  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/40 p-0 sm:items-center sm:p-4" onClick={onClose}>
      <div
        className="max-h-[92dvh] w-full max-w-lg overflow-y-auto rounded-t-2xl bg-white p-5 shadow-card-lg sm:rounded-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-xl font-extrabold text-ink">{title}</h2>
          <button type="button" onClick={onClose} className="rounded-lg p-2 text-ink-soft hover:bg-surface-muted" aria-label="Close">
            <X className="h-5 w-5" />
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}

const fieldClass =
  "h-11 w-full rounded-xl border-2 border-border bg-white px-3 text-base font-semibold text-ink focus:border-primary-600 focus:outline-none focus:ring-4 focus:ring-primary-600/20";
const labelClass = "mb-1 block text-sm font-bold text-ink-soft";

// ── Add sale ───────────────────────────────────────────────────────────────
function AddSaleModal({ onClose, onSaved }: { onClose: () => void; onSaved: () => void }) {
  const [title, setTitle] = useState("");
  const [amount, setAmount] = useState("");
  const [cash, setCash] = useState("");
  const [upi, setUpi] = useState("");
  const [due, setDue] = useState("");
  const [customerName, setCustomerName] = useState("");
  const [customerPhone, setCustomerPhone] = useState("");
  const [date, setDate] = useState(todayISO());
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const amountP = toPaise(amount);
  const splitP = toPaise(cash) + toPaise(upi) + toPaise(due);
  const remainingP = amountP - splitP;
  const balanced = amountP > 0 && remainingP === 0;

  // Quick-fills
  const allCash = () => {
    setCash(amount || "");
    setUpi("");
    setDue("");
  };
  const allUpi = () => {
    setUpi(amount || "");
    setCash("");
    setDue("");
  };
  const balanceToDue = () => {
    const rem = amountP - (toPaise(cash) + toPaise(upi));
    setDue(rem > 0 ? fromPaise(rem) : "0");
  };

  const submit = async () => {
    setError(null);
    if (!title.trim()) return setError("Give the sale a short title.");
    if (amountP <= 0) return setError("Enter the total amount.");
    if (!balanced) return setError("Cash + UPI + Due must add up to the total.");
    setSaving(true);
    try {
      await createSale({
        title: title.trim(),
        amount: fromPaise(amountP),
        cash_amount: fromPaise(toPaise(cash)),
        upi_amount: fromPaise(toPaise(upi)),
        due_amount: fromPaise(toPaise(due)),
        customer_name: customerName.trim() || null,
        customer_phone: customerPhone.trim() || null,
        occurred_on: date,
      });
      onSaved();
    } catch (e) {
      setError(friendlyError(e, "Couldn't save the sale."));
      setSaving(false);
    }
  };

  return (
    <Modal title="Log a sale" onClose={onClose}>
      <div className="space-y-4">
        <div>
          <label className={labelClass}>What was sold</label>
          <input className={fieldClass} value={title} onChange={(e) => setTitle(e.target.value)} placeholder="e.g. Thermal printer — Green Leaf Nursery" autoFocus />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className={labelClass}>Total amount (₹)</label>
            <input
              className={fieldClass}
              inputMode="decimal"
              value={amount}
              onChange={(e) => {
                setAmount(e.target.value);
                // convenience: default the whole amount to cash if nothing split yet
                if (!cash && !upi && !due) setCash(e.target.value);
              }}
              placeholder="0.00"
            />
          </div>
          <div>
            <label className={labelClass}>Date</label>
            <input type="date" className={fieldClass} value={date} max={todayISO()} onChange={(e) => setDate(e.target.value)} />
          </div>
        </div>

        {/* Payment split */}
        <div className="rounded-xl border border-border bg-surface-muted p-3">
          <div className="mb-2 flex items-center justify-between">
            <span className="text-sm font-bold text-ink-soft">Payment split</span>
            <div className="flex gap-1">
              <button type="button" onClick={allCash} className="rounded-lg border border-border bg-white px-2 py-1 text-xs font-bold text-ink hover:border-primary-600">All cash</button>
              <button type="button" onClick={allUpi} className="rounded-lg border border-border bg-white px-2 py-1 text-xs font-bold text-ink hover:border-primary-600">All UPI</button>
              <button type="button" onClick={balanceToDue} className="rounded-lg border border-border bg-white px-2 py-1 text-xs font-bold text-ink hover:border-primary-600">Rest → Due</button>
            </div>
          </div>
          <div className="grid grid-cols-3 gap-2">
            <div>
              <label className="mb-1 block text-xs font-bold text-cash">Cash</label>
              <input className={fieldClass} inputMode="decimal" value={cash} onChange={(e) => setCash(e.target.value)} placeholder="0" />
            </div>
            <div>
              <label className="mb-1 block text-xs font-bold text-upi">UPI</label>
              <input className={fieldClass} inputMode="decimal" value={upi} onChange={(e) => setUpi(e.target.value)} placeholder="0" />
            </div>
            <div>
              <label className="mb-1 block text-xs font-bold text-warning">Due (later)</label>
              <input className={fieldClass} inputMode="decimal" value={due} onChange={(e) => setDue(e.target.value)} placeholder="0" />
            </div>
          </div>
          <div className="mt-2 text-sm font-semibold">
            {amountP <= 0 ? (
              <span className="text-ink-soft">Enter a total to split it.</span>
            ) : balanced ? (
              <span className="text-primary-700">Balanced ✓</span>
            ) : remainingP > 0 ? (
              <span className="text-warning">{formatINR(remainingP)} still unassigned</span>
            ) : (
              <span className="text-danger">{formatINR(-remainingP)} over the total</span>
            )}
          </div>
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className={labelClass}>Customer name (optional)</label>
            <input className={fieldClass} value={customerName} onChange={(e) => setCustomerName(e.target.value)} placeholder="Name" />
          </div>
          <div>
            <label className={labelClass}>Phone (optional)</label>
            <input className={fieldClass} inputMode="tel" value={customerPhone} onChange={(e) => setCustomerPhone(e.target.value)} placeholder="Phone" />
          </div>
        </div>

        {error && <p className="text-sm font-semibold text-danger">{error}</p>}

        <div className="flex gap-3 pt-1">
          <Button variant="secondary" size="action" onClick={onClose} className="flex-1" type="button">
            Cancel
          </Button>
          <Button variant="primary" size="action" onClick={submit} className="flex-1" loading={saving} loadingLabel="Saving…" disabled={!balanced || !title.trim()}>
            Save sale
          </Button>
        </div>
      </div>
    </Modal>
  );
}

// ── Add expense ────────────────────────────────────────────────────────────
function AddExpenseModal({ onClose, onSaved }: { onClose: () => void; onSaved: () => void }) {
  const [reason, setReason] = useState("");
  const [amount, setAmount] = useState("");
  const [method, setMethod] = useState<PaymentMethod>("cash");
  const [note, setNote] = useState("");
  const [date, setDate] = useState(todayISO());
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    setError(null);
    if (!reason.trim()) return setError("What was the money spent on?");
    if (toPaise(amount) <= 0) return setError("Enter the amount.");
    setSaving(true);
    try {
      await createExpense({
        reason: reason.trim(),
        amount: fromPaise(toPaise(amount)),
        payment_method: method,
        note: note.trim() || null,
        occurred_on: date,
      });
      onSaved();
    } catch (e) {
      setError(friendlyError(e, "Couldn't save the expense."));
      setSaving(false);
    }
  };

  return (
    <Modal title="Log an expense" onClose={onClose}>
      <div className="space-y-4">
        <div>
          <label className={labelClass}>What was it for</label>
          <input className={fieldClass} value={reason} onChange={(e) => setReason(e.target.value)} placeholder="e.g. Printer stock, office rent" autoFocus />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className={labelClass}>Amount (₹)</label>
            <input className={fieldClass} inputMode="decimal" value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="0.00" />
          </div>
          <div>
            <label className={labelClass}>Date</label>
            <input type="date" className={fieldClass} value={date} max={todayISO()} onChange={(e) => setDate(e.target.value)} />
          </div>
        </div>
        <div>
          <label className={labelClass}>Paid by</label>
          <div className="flex rounded-xl border-2 border-border p-1">
            {(["cash", "upi"] as PaymentMethod[]).map((m) => (
              <button
                key={m}
                type="button"
                onClick={() => setMethod(m)}
                className={[
                  "flex-1 rounded-lg py-2 text-base font-bold transition-colors",
                  method === m ? "bg-primary-600 text-white" : "text-ink-soft hover:bg-surface-muted",
                ].join(" ")}
              >
                {m === "cash" ? "Cash" : "UPI"}
              </button>
            ))}
          </div>
        </div>
        <div>
          <label className={labelClass}>Note (optional)</label>
          <input className={fieldClass} value={note} onChange={(e) => setNote(e.target.value)} placeholder="Any detail" />
        </div>

        {error && <p className="text-sm font-semibold text-danger">{error}</p>}

        <div className="flex gap-3 pt-1">
          <Button variant="secondary" size="action" onClick={onClose} className="flex-1" type="button">
            Cancel
          </Button>
          <Button variant="primary" size="action" onClick={submit} className="flex-1" loading={saving} loadingLabel="Saving…">
            Save expense
          </Button>
        </div>
      </div>
    </Modal>
  );
}

// ── Collect a due ──────────────────────────────────────────────────────────
function CollectModal({ sale, onClose, onCollected }: { sale: AdminSale; onClose: () => void; onCollected: () => void }) {
  const dueP = toPaise(sale.due_amount);
  const [amount, setAmount] = useState(sale.due_amount);
  const [method, setMethod] = useState<PaymentMethod>("cash");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const takeP = toPaise(amount);
  const valid = takeP > 0 && takeP <= dueP;

  const submit = async () => {
    setError(null);
    if (!valid) return setError(`Enter an amount up to ${inr(sale.due_amount)}.`);
    setSaving(true);
    try {
      await collectDue(sale.id, fromPaise(takeP), method);
      onCollected();
    } catch (e) {
      setError(friendlyError(e, "Couldn't record the collection."));
      setSaving(false);
    }
  };

  return (
    <Modal title="Collect due" onClose={onClose}>
      <div className="space-y-4">
        <div className="rounded-xl border border-border bg-surface-muted px-4 py-3">
          <div className="font-bold text-ink">{sale.title}</div>
          <div className="text-sm text-ink-soft">
            Owed: <span className="font-bold text-warning">{inr(sale.due_amount)}</span> of {inr(sale.amount)}
          </div>
        </div>
        <div>
          <label className={labelClass}>Amount collected (₹)</label>
          <input className={fieldClass} inputMode="decimal" value={amount} onChange={(e) => setAmount(e.target.value)} autoFocus />
          <button type="button" onClick={() => setAmount(sale.due_amount)} className="mt-1 text-sm font-bold text-primary-700 hover:underline">
            Collect all ({inr(sale.due_amount)})
          </button>
        </div>
        <div>
          <label className={labelClass}>Received as</label>
          <div className="flex rounded-xl border-2 border-border p-1">
            {(["cash", "upi"] as PaymentMethod[]).map((m) => (
              <button
                key={m}
                type="button"
                onClick={() => setMethod(m)}
                className={[
                  "flex-1 rounded-lg py-2 text-base font-bold transition-colors",
                  method === m ? "bg-primary-600 text-white" : "text-ink-soft hover:bg-surface-muted",
                ].join(" ")}
              >
                {m === "cash" ? "Cash" : "UPI"}
              </button>
            ))}
          </div>
        </div>

        {error && <p className="text-sm font-semibold text-danger">{error}</p>}

        <div className="flex gap-3 pt-1">
          <Button variant="secondary" size="action" onClick={onClose} className="flex-1" type="button">
            Cancel
          </Button>
          <Button variant="primary" size="action" onClick={submit} className="flex-1" loading={saving} loadingLabel="Saving…" disabled={!valid}>
            Record payment
          </Button>
        </div>
      </div>
    </Modal>
  );
}
