import { useCallback, useEffect, useRef, useState } from "react";
import { useAuth } from "@/store/auth";
import {
  fetchBills,
  fetchTodaySummary,
  type BillListItem,
  type BillSummary,
} from "@/api/sales";
import { listSalespeople, type Salesperson } from "@/api/shop_users";
import { friendlyError } from "@/api/client";
import { todayISO } from "@/lib/datetime";
import { Spinner } from "@/components/Spinner";
import { Button } from "@/components/Button";
import { SummaryHero } from "./sales/SummaryHero";
import { HistoryFilters, type HistoryFilterValue } from "./sales/HistoryFilters";
import { BillRow } from "./sales/BillRow";
import { BillDetailView } from "./sales/BillDetailView";
import { DetailedReportSheet } from "./sales/DetailedReportSheet";

const PAGE_SIZE = 20;

export function SalesPage() {
  const user = useAuth((s) => s.user);
  const isOwner = user?.role === "manager";

  // Staff listing & filtering state (Owner only)
  const [staffList, setStaffList] = useState<Salesperson[]>([]);
  const [selectedStaffId, setSelectedStaffId] = useState("");

  useEffect(() => {
    if (isOwner) {
      listSalespeople()
        .then(setStaffList)
        .catch(() => {});
    }
  }, [isOwner]);

  // ── Summary (its own date + staff filter) ──────────────────────────────────
  const [date, setDate] = useState(todayISO());
  const [summary, setSummary] = useState<BillSummary | null>(null);
  const [sumLoading, setSumLoading] = useState(true);
  const [sumError, setSumError] = useState<string | null>(null);

  const loadSummary = useCallback(async (d: string, staffId: string) => {
    setSumLoading(true);
    setSumError(null);
    try {
      setSummary(await fetchTodaySummary(d, staffId || undefined));
    } catch (e) {
      setSumError(friendlyError(e, "Couldn't load the summary."));
    } finally {
      setSumLoading(false);
    }
  }, []);

  useEffect(() => {
    loadSummary(date, selectedStaffId);
  }, [date, selectedStaffId, loadSummary]);

  // ── History (filters + pagination + staff filter) ──────────────────────────
  const [filters, setFilters] = useState<HistoryFilterValue>({ from: "", to: "" });
  const [bills, setBills] = useState<BillListItem[]>([]);
  const [histLoading, setHistLoading] = useState(true);
  const [histError, setHistError] = useState<string | null>(null);
  const [hasMore, setHasMore] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);

  const filterParams = useCallback(
    (offset: number) => ({
      date_from: filters.from || undefined,
      date_to: filters.to || undefined,
      created_by: selectedStaffId || undefined,
      is_edited: filters.is_edited,
      limit: PAGE_SIZE,
      offset,
    }),
    [filters, selectedStaffId],
  );

  const loadFirstPage = useCallback(async () => {
    setHistLoading(true);
    setHistError(null);
    try {
      const res = await fetchBills(filterParams(0));
      setBills(res.items);
      setHasMore(res.has_more);
    } catch (e) {
      setHistError(friendlyError(e, "Couldn't load bills."));
    } finally {
      setHistLoading(false);
    }
  }, [filterParams]);

  useEffect(() => {
    loadFirstPage();
  }, [loadFirstPage]);

  const loadMore = async () => {
    setLoadingMore(true);
    try {
      const res = await fetchBills(filterParams(bills.length));
      setBills((prev) => [...prev, ...res.items]);
      setHasMore(res.has_more);
    } catch {
      // Keep what we have; the button stays so they can retry.
    } finally {
      setLoadingMore(false);
    }
  };

  // ── Refresh when the tab regains focus (a bill may have been made) ───────
  const refresh = useCallback(() => {
    loadSummary(date, selectedStaffId);
    loadFirstPage();
  }, [date, selectedStaffId, loadSummary, loadFirstPage]);

  const refreshRef = useRef(refresh);
  refreshRef.current = refresh;
  useEffect(() => {
    const onFocus = () => {
      if (document.visibilityState === "visible") refreshRef.current();
    };
    document.addEventListener("visibilitychange", onFocus);
    window.addEventListener("focus", onFocus);
    return () => {
      document.removeEventListener("visibilitychange", onFocus);
      window.removeEventListener("focus", onFocus);
    };
  }, []);

  // ── Detail ──────────────────────────────────────────────────────────────
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [reportOpen, setReportOpen] = useState(false);

  return (
    <section className="space-y-5">
      <div className="flex items-center justify-between gap-3">
        <h1 className="text-2xl font-extrabold text-ink">Sales</h1>
        <Button
          variant="secondary"
          size="tap"
          className="border-2 bg-white"
          onClick={() => setReportOpen(true)}
        >
          Detailed Reports
        </Button>
      </div>

      {/* Staff filter dropdown (Owner only) */}
      {isOwner && (
        <div className="flex flex-wrap items-center gap-3 rounded-2xl border border-border bg-white p-4 shadow-sm justify-between">
          <span className="text-base font-semibold text-ink-soft">View sales by staff:</span>
          <select
            value={selectedStaffId}
            onChange={(e) => setSelectedStaffId(e.target.value)}
            className="h-10 rounded-xl border-2 border-border bg-white px-3 text-base font-semibold text-ink focus:border-primary-600 focus:outline-none focus:ring-4 focus:ring-primary-600/20 max-w-full sm:max-w-xs"
          >
            <option value="">All Staff</option>
            {user && <option value={user.id}>{user.email} (Owner)</option>}
            {staffList.map((sp) => (
              <option key={sp.id} value={sp.id}>
                {sp.email}
              </option>
            ))}
          </select>
        </div>
      )}

      <SummaryHero
        date={date}
        onDateChange={setDate}
        summary={summary}
        loading={sumLoading}
        error={sumError}
        onRetry={() => loadSummary(date, selectedStaffId)}
        onExpenseAdded={refresh}
      />

      <div className="space-y-3">
        <h2 className="text-lg font-bold text-ink">Recent bills</h2>
        <HistoryFilters value={filters} onChange={setFilters} />

        {histLoading ? (
          <div className="flex justify-center py-12">
            <Spinner className="h-8 w-8 text-primary-600" />
          </div>
        ) : histError ? (
          <div className="py-10 text-center">
            <p className="text-base font-semibold text-danger">{histError}</p>
            <Button variant="secondary" size="action" className="mt-4 max-w-xs" onClick={loadFirstPage}>
              Try again
            </Button>
          </div>
        ) : bills.length === 0 ? (
          <p className="py-12 text-center text-base text-ink-soft">
            No bills for this filter. Try a wider date range.
          </p>
        ) : (
          <>
            <div className="space-y-3">
              {bills.map((b) => (
                <BillRow key={b.id} bill={b} onOpen={setSelectedId} />
              ))}
            </div>
            {hasMore && (
              <Button
                variant="secondary"
                size="action"
                className="mt-2"
                loading={loadingMore}
                loadingLabel="Loading…"
                onClick={loadMore}
              >
                Load more
              </Button>
            )}
          </>
        )}
      </div>

      <BillDetailView billId={selectedId} onClose={() => setSelectedId(null)} onUpdated={refresh} />
      
      <DetailedReportSheet
        open={reportOpen}
        onClose={() => setReportOpen(false)}
        staffList={staffList}
        currentUserEmail={user?.email}
        currentUserId={user?.id}
      />
    </section>
  );
}
