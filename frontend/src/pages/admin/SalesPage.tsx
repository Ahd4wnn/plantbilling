import { useCallback, useEffect, useRef, useState } from "react";
import { listShops, listShopUsers, type ShopRow, type OwnerInfo } from "@/api/admin";
import {
  fetchBills,
  fetchTodaySummary,
  type BillListItem,
  type BillSummary,
} from "@/api/sales";
import { friendlyError } from "@/api/client";
import { todayISO } from "@/lib/datetime";
import { Spinner } from "@/components/Spinner";
import { Button } from "@/components/Button";
import { SummaryHero } from "../shop/sales/SummaryHero";
import { HistoryFilters, type HistoryFilterValue } from "../shop/sales/HistoryFilters";
import { BillRow } from "../shop/sales/BillRow";
import { BillDetailView } from "../shop/sales/BillDetailView";
import { DetailedReportSheet } from "../shop/sales/DetailedReportSheet";


const PAGE_SIZE = 20;

export function SalesPage() {
  // Shops list
  const [shops, setShops] = useState<ShopRow[]>([]);
  const [loadingShops, setLoadingShops] = useState(true);
  const [selectedShopId, setSelectedShopId] = useState("");

  // Staff list for selected shop
  const [staffList, setStaffList] = useState<OwnerInfo[]>([]);
  const [selectedStaffId, setSelectedStaffId] = useState("");
  const [reportOpen, setReportOpen] = useState(false);

  // Load shops on mount
  useEffect(() => {
    listShops()
      .then((data) => {
        setShops(data.filter((s) => s.is_active));
      })
      .catch(() => {})
      .finally(() => setLoadingShops(false));
  }, []);

  // Fetch shop staff users when shop selection changes
  useEffect(() => {
    setSelectedStaffId("");
    if (selectedShopId) {
      listShopUsers(selectedShopId)
        .then(setStaffList)
        .catch(() => setStaffList([]));
    } else {
      setStaffList([]);
    }
  }, [selectedShopId]);

  // ── Summary (date + staff + shop) ──────────────────────────────────────────
  const [date, setDate] = useState(todayISO());
  const [summary, setSummary] = useState<BillSummary | null>(null);
  const [sumLoading, setSumLoading] = useState(false);
  const [sumError, setSumError] = useState<string | null>(null);

  const loadSummary = useCallback(async (d: string, shopId: string, staffId: string) => {
    if (!shopId) {
      setSummary(null);
      return;
    }
    setSumLoading(true);
    setSumError(null);
    try {
      setSummary(await fetchTodaySummary(d, staffId || undefined, shopId));
    } catch (e) {
      setSumError(friendlyError(e, "Couldn't load the summary."));
    } finally {
      setSumLoading(false);
    }
  }, []);

  useEffect(() => {
    loadSummary(date, selectedShopId, selectedStaffId);
  }, [date, selectedShopId, selectedStaffId, loadSummary]);

  // ── History (filters + pagination + staff + shop) ──────────────────────────
  const [filters, setFilters] = useState<HistoryFilterValue>({ from: "", to: "" });
  const [bills, setBills] = useState<BillListItem[]>([]);
  const [histLoading, setHistLoading] = useState(false);
  const [histError, setHistError] = useState<string | null>(null);
  const [hasMore, setHasMore] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);

  const filterParams = useCallback(
    (offset: number) => ({
      date_from: filters.from || undefined,
      date_to: filters.to || undefined,
      created_by: selectedStaffId || undefined,
      is_edited: filters.is_edited,
      shop_id: selectedShopId || undefined,
      limit: PAGE_SIZE,
      offset,
    }),
    [filters, selectedShopId, selectedStaffId],
  );

  const loadFirstPage = useCallback(async () => {
    if (!selectedShopId) {
      setBills([]);
      setHasMore(false);
      setHistLoading(false);
      return;
    }
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
  }, [selectedShopId, filterParams]);

  useEffect(() => {
    loadFirstPage();
  }, [loadFirstPage]);

  const loadMore = async () => {
    if (!selectedShopId) return;
    setLoadingMore(true);
    try {
      const res = await fetchBills(filterParams(bills.length));
      setBills((prev) => [...prev, ...res.items]);
      setHasMore(res.has_more);
    } catch {
      // Keep what we have
    } finally {
      setLoadingMore(false);
    }
  };

  // ── Refresh when window focus gains (polling-like refresh) ─────────────────
  const refresh = useCallback(() => {
    if (selectedShopId) {
      loadSummary(date, selectedShopId, selectedStaffId);
      loadFirstPage();
    }
  }, [date, selectedShopId, selectedStaffId, loadSummary, loadFirstPage]);

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

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between gap-3">
        <h1 className="text-2xl font-extrabold text-ink">Nursery Sales Analysis</h1>
        {selectedShopId && (
          <Button
            variant="secondary"
            size="tap"
            className="border-2 bg-white"
            onClick={() => setReportOpen(true)}
          >
            Detailed Reports
          </Button>
        )}
      </div>

      {/* Filter Options */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4 rounded-2xl border border-border bg-white p-5 shadow-sm">
        {/* Nursery selector */}
        <div className="space-y-1">
          <label className="text-base font-semibold text-ink-soft block">Nursery / Shop:</label>
          {loadingShops ? (
            <div className="h-10 flex items-center"><Spinner className="h-5 w-5 text-primary-600" /></div>
          ) : (
            <select
              value={selectedShopId}
              onChange={(e) => setSelectedShopId(e.target.value)}
              className="h-10 w-full rounded-xl border-2 border-border bg-white px-3 text-base font-semibold text-ink focus:border-primary-600 focus:outline-none focus:ring-4 focus:ring-primary-600/20"
            >
              <option value="">-- Select Nursery --</option>
              {shops.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </select>
          )}
        </div>

        {/* Staff selector */}
        <div className="space-y-1">
          <label className="text-base font-semibold text-ink-soft block">Salesperson / Staff:</label>
          <select
            value={selectedStaffId}
            onChange={(e) => setSelectedStaffId(e.target.value)}
            disabled={!selectedShopId}
            className="h-10 w-full rounded-xl border-2 border-border bg-white px-3 text-base font-semibold text-ink focus:border-primary-600 focus:outline-none focus:ring-4 focus:ring-primary-600/20 disabled:opacity-50"
          >
            <option value="">All Staff</option>
            {staffList.map((sp) => (
              <option key={sp.id} value={sp.id}>
                {sp.email} ({sp.role === "manager" ? "Owner" : "Staff"})
              </option>
            ))}
          </select>
        </div>
      </div>

      {selectedShopId ? (
        <section className="space-y-5">
          <SummaryHero
            date={date}
            onDateChange={setDate}
            summary={summary}
            loading={sumLoading}
            error={sumError}
            onRetry={() => loadSummary(date, selectedShopId, selectedStaffId)}
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
        </section>
      ) : (
        <div className="rounded-2xl border border-border bg-slate-50 py-16 text-center shadow-sm">
          <p className="text-lg font-bold text-ink">No Nursery Selected</p>
          <p className="mt-1 text-base text-ink-soft">Select a nursery above to analyze its sales logs and staff performances.</p>
        </div>
      )}

      <BillDetailView billId={selectedId} onClose={() => setSelectedId(null)} onUpdated={refresh} />

      <DetailedReportSheet
        open={reportOpen}
        onClose={() => setReportOpen(false)}
        staffList={staffList}
        currentUserEmail={undefined}
        currentUserId={undefined}
        shopId={selectedShopId}
      />
    </div>
  );
}
