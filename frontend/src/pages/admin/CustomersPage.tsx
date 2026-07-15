import { useCallback, useEffect, useMemo, useState } from "react";
import {
  listAdminCustomers,
  listShops,
  downloadAdminCustomersCSV,
  type AdminCustomerRow,
  type ShopRow,
} from "@/api/admin";
import { getExportStatus, type ExportStatus } from "@/api/adminAnalytics";
import { friendlyError } from "@/api/client";
import { Spinner } from "@/components/Spinner";
import { Button } from "@/components/Button";
import { Download } from "lucide-react";

const PAGE_SIZE = 50;

function formatDate(iso: string): string {
  return new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", year: "numeric" }).format(new Date(iso));
}
function formatDateTime(iso: string): string {
  return new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", year: "numeric", hour: "numeric", minute: "2-digit" }).format(new Date(iso));
}

export function CustomersPage() {
  const [shops, setShops] = useState<ShopRow[]>([]);
  const [rows, setRows] = useState<AdminCustomerRow[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [loadingMore, setLoadingMore] = useState(false);

  const [search, setSearch] = useState("");
  const [debouncedQ, setDebouncedQ] = useState("");
  const [shopId, setShopId] = useState<string>("");
  const [exporting, setExporting] = useState<null | "since_last" | "all" | "range">(null);
  const [exportStatus, setExportStatus] = useState<ExportStatus | null>(null);
  const [showRange, setShowRange] = useState(false);
  const [rangeFrom, setRangeFrom] = useState("");
  const [rangeTo, setRangeTo] = useState("");

  const refreshStatus = useCallback(() => {
    getExportStatus().then(setExportStatus).catch(() => {});
  }, []);

  const handleExport = async (mode: "since_last" | "all" | "range") => {
    if (mode === "range" && !rangeFrom && !rangeTo) {
      alert("Pick at least one date for the range export.");
      return;
    }
    setExporting(mode);
    try {
      await downloadAdminCustomersCSV({
        q: debouncedQ || undefined,
        shop_id: shopId || undefined,
        mode,
        created_from: mode === "range" ? rangeFrom || undefined : undefined,
        created_to: mode === "range" ? rangeTo || undefined : undefined,
      });
      // "since_last" advanced the server watermark — reflect that in the UI.
      if (mode === "since_last") refreshStatus();
    } catch (e) {
      alert("Failed to export customer list.");
    } finally {
      setExporting(null);
    }
  };

  useEffect(() => {
    listShops().then(setShops).catch(() => setShops([]));
    refreshStatus();
  }, [refreshStatus]);

  useEffect(() => {
    const t = setTimeout(() => setDebouncedQ(search.trim()), 300);
    return () => clearTimeout(t);
  }, [search]);

  const params = useCallback(
    (offset: number) => ({ q: debouncedQ || undefined, shop_id: shopId || undefined, limit: PAGE_SIZE, offset }),
    [debouncedQ, shopId],
  );

  const loadFirst = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await listAdminCustomers(params(0));
      setRows(res.items);
      setTotal(res.total);
    } catch (e) {
      setError(friendlyError(e, "Couldn't load customers."));
    } finally {
      setLoading(false);
    }
  }, [params]);

  useEffect(() => { loadFirst(); }, [loadFirst]);

  const loadMore = async () => {
    setLoadingMore(true);
    try {
      const res = await listAdminCustomers(params(rows.length));
      setRows((prev) => [...prev, ...res.items]);
      setTotal(res.total);
    } catch {
      // keep current rows; button remains for retry
    } finally {
      setLoadingMore(false);
    }
  };

  const hasMore = rows.length < total;
  const countLabel = useMemo(() => {
    const n = total.toLocaleString("en-IN");
    return `${n} ${total === 1 ? "customer" : "customers"}${shopId || debouncedQ ? " (filtered)" : " across all shops"}`;
  }, [total, shopId, debouncedQ]);

  return (
    <div>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-extrabold text-ink">Customers</h1>
          <p className="text-sm text-ink-soft">{loading ? "Loading…" : countLabel}</p>
        </div>
      </div>

      {/* Export panel: incremental "since last download" + all + date range. */}
      <div className="mt-3 rounded-card border border-border bg-white p-4 shadow-card">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="text-sm text-ink-soft">
            {exportStatus ? (
              exportStatus.last_exported_at ? (
                <>
                  Last download: <span className="font-semibold text-ink">{formatDateTime(exportStatus.last_exported_at)}</span>
                  {" · "}
                  <span className="font-semibold text-primary-700">{exportStatus.new_since_last.toLocaleString("en-IN")} new</span> since then
                </>
              ) : (
                <>Never downloaded · <span className="font-semibold text-ink">{exportStatus.total_customers.toLocaleString("en-IN")}</span> total</>
              )
            ) : (
              "Loading export status…"
            )}
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <Button
              variant="primary"
              size="tap"
              className="flex items-center gap-1.5 font-bold"
              onClick={() => handleExport("since_last")}
              disabled={exporting !== null || (exportStatus?.new_since_last === 0)}
            >
              <Download className="h-4.5 w-4.5" />
              {exporting === "since_last" ? "Exporting…" : `Export new${exportStatus ? ` (${exportStatus.new_since_last.toLocaleString("en-IN")})` : ""}`}
            </Button>
            <Button
              variant="secondary"
              size="tap"
              className="border-2 bg-white font-bold"
              onClick={() => handleExport("all")}
              disabled={exporting !== null || rows.length === 0}
            >
              {exporting === "all" ? "Exporting…" : "Export all"}
            </Button>
            <Button
              variant="ghost"
              size="tap"
              className="font-semibold"
              onClick={() => setShowRange((v) => !v)}
              disabled={exporting !== null}
            >
              Date range
            </Button>
          </div>
        </div>
        {showRange && (
          <div className="mt-3 flex flex-wrap items-end gap-2 border-t border-border pt-3">
            <label className="text-sm text-ink-soft">
              From
              <input type="date" value={rangeFrom} onChange={(e) => setRangeFrom(e.target.value)} className="mt-1 block h-11 rounded-control border-2 border-border bg-white px-3 text-base text-ink focus:border-primary-600 focus:outline-none" />
            </label>
            <label className="text-sm text-ink-soft">
              To
              <input type="date" value={rangeTo} onChange={(e) => setRangeTo(e.target.value)} className="mt-1 block h-11 rounded-control border-2 border-border bg-white px-3 text-base text-ink focus:border-primary-600 focus:outline-none" />
            </label>
            <Button variant="secondary" size="tap" className="border-2 bg-white font-bold" onClick={() => handleExport("range")} disabled={exporting !== null}>
              {exporting === "range" ? "Exporting…" : "Download range"}
            </Button>
          </div>
        )}
        <p className="mt-2 text-xs text-ink-soft/80">Exports are de-duplicated by phone number. “Export new” only includes customers added since your last download.</p>
      </div>

      {/* Privacy note */}
      <div className="mt-3 rounded-control border border-warning/40 bg-warning-soft px-4 py-2.5 text-sm font-semibold text-warning">
        Sensitive data: end-customers' personal contact details across all shops. Admin-only — handle per privacy
        (DPDP) obligations.
      </div>

      {/* Filters */}
      <div className="mt-4 flex flex-wrap items-center gap-2">
        <input
          type="search"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search name or phone…"
          className="h-11 min-w-[14rem] flex-1 rounded-control border-2 border-border bg-white px-3 text-base text-ink focus:border-primary-600 focus:outline-none focus:ring-4 focus:ring-primary-600/20"
        />
        <select
          value={shopId}
          onChange={(e) => setShopId(e.target.value)}
          className="h-11 rounded-control border-2 border-border bg-white px-3 text-base text-ink focus:border-primary-600 focus:outline-none"
          aria-label="Filter by shop"
        >
          <option value="">All shops</option>
          {shops.map((s) => (
            <option key={s.id} value={s.id}>{s.name}</option>
          ))}
        </select>
      </div>

      {/* Content */}
      {loading ? (
        <div className="flex justify-center py-16"><Spinner className="h-8 w-8 text-primary-600" /></div>
      ) : error ? (
        <div className="py-12 text-center">
          <p className="text-base font-semibold text-danger">{error}</p>
          <Button variant="secondary" size="tap" className="mt-4" onClick={loadFirst}>Try again</Button>
        </div>
      ) : rows.length === 0 ? (
        <p className="py-12 text-center text-base text-ink-soft">
          {debouncedQ || shopId ? "No customers match your filters." : "No customers yet."}
        </p>
      ) : (
        <>
          {/* Desktop table */}
          <div className="mt-4 hidden overflow-hidden rounded-card border border-border bg-surface shadow-card md:block">
            <table className="w-full text-left text-base">
              <thead className="border-b border-border bg-surface-muted text-sm text-ink-soft">
                <tr>
                  <th className="px-4 py-2 font-semibold">Name</th>
                  <th className="px-4 py-2 font-semibold">Phone</th>
                  <th className="px-4 py-2 font-semibold">Shop</th>
                  <th className="px-4 py-2 font-semibold">Added</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((c) => (
                  <tr key={c.id} className="border-b border-border last:border-b-0">
                    <td className="px-4 py-3 font-semibold text-ink">{c.name}</td>
                    <td className="px-4 py-3 text-ink-soft">{c.phone ?? "—"}</td>
                    <td className="px-4 py-3 text-ink-soft">{c.shop_name}</td>
                    <td className="px-4 py-3 text-ink-soft">{formatDate(c.created_at)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Mobile cards */}
          <div className="mt-4 space-y-3 md:hidden">
            {rows.map((c) => (
              <div key={c.id} className="rounded-card border border-border bg-surface p-4 shadow-card">
                <div className="text-lg font-bold text-ink">{c.name}</div>
                <div className="text-base text-ink-soft">{c.phone ?? "—"}</div>
                <div className="mt-1 text-base text-ink-soft">{c.shop_name} · {formatDate(c.created_at)}</div>
              </div>
            ))}
          </div>

          {hasMore && (
            <Button
              variant="secondary"
              size="action"
              className="mt-4"
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
  );
}
