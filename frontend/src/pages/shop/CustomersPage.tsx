import { useEffect, useState, useMemo } from "react";
import { fetchShopCustomers, downloadCustomersCSV, type ShopCustomer } from "@/api/customers";
import { friendlyError } from "@/api/client";
import { Spinner } from "@/components/Spinner";
import { Button } from "@/components/Button";
import { Download } from "lucide-react";
import { useAuth } from "@/store/auth";

function formatDate(iso: string): string {
  return new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", year: "numeric" }).format(new Date(iso));
}

export function CustomersPage() {
  const user = useAuth((s) => s.user);
  const isOwner = user?.role === "manager";
  const [customers, setCustomers] = useState<ShopCustomer[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [debouncedQ, setDebouncedQ] = useState("");
  const [exporting, setExporting] = useState(false);

  useEffect(() => {
    const t = setTimeout(() => setDebouncedQ(search.trim()), 300);
    return () => clearTimeout(t);
  }, [search]);

  const loadCustomers = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await fetchShopCustomers(debouncedQ || undefined);
      setCustomers(data);
    } catch (e) {
      setError(friendlyError(e, "Couldn't load customers."));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadCustomers();
  }, [debouncedQ]);

  const handleExport = async () => {
    setExporting(true);
    try {
      await downloadCustomersCSV(debouncedQ || undefined);
    } catch (e) {
      alert("Failed to export customer list.");
    } finally {
      setExporting(false);
    }
  };

  const countLabel = useMemo(() => {
    const total = customers.length;
    const n = total.toLocaleString("en-IN");
    return `${n} ${total === 1 ? "customer" : "customers"}${debouncedQ ? " matching search" : ""}`;
  }, [customers, debouncedQ]);

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between gap-3">
        <h1 className="text-2xl font-extrabold text-ink">Customers</h1>
        {isOwner && (
          <Button
            variant="secondary"
            size="tap"
            className="border-2 bg-white flex items-center gap-1.5 font-bold"
            onClick={handleExport}
            disabled={loading || exporting || customers.length === 0}
          >
            <Download className="h-4.5 w-4.5" />
            {exporting ? "Exporting..." : "Export Excel"}
          </Button>
        )}
      </div>

      <div className="flex flex-col gap-3 rounded-2xl border border-border bg-white p-4 shadow-sm">
        <div className="flex flex-col gap-1">
          <label className="text-sm font-semibold text-ink-soft">Search Customers</label>
          <input
            type="search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search name or phone number..."
            className="h-11 w-full rounded-control border-2 border-border bg-white px-3 text-base text-ink focus:border-primary-600 focus:outline-none focus:ring-4 focus:ring-primary-600/20"
          />
        </div>
        <p className="text-sm text-ink-soft">{loading ? "Loading..." : countLabel}</p>
      </div>

      {loading ? (
        <div className="flex justify-center py-16">
          <Spinner className="h-8 w-8 text-primary-600" />
        </div>
      ) : error ? (
        <div className="py-12 text-center">
          <p className="text-base font-semibold text-danger">{error}</p>
          <Button variant="secondary" size="tap" className="mt-4" onClick={loadCustomers}>
            Try again
          </Button>
        </div>
      ) : customers.length === 0 ? (
        <div className="rounded-2xl border border-border bg-white py-16 text-center shadow-sm">
          <p className="text-lg font-bold text-ink">
            {debouncedQ ? "No matching customers found." : "No customers yet."}
          </p>
          <p className="mt-1 text-sm text-ink-soft">
            {debouncedQ ? "Try searching for another name or number." : "Customers will appear here once they complete checkout."}
          </p>
        </div>
      ) : (
        <>
          {/* Desktop Table view */}
          <div className="hidden overflow-hidden rounded-card border border-border bg-surface shadow-card sm:block">
            <table className="w-full text-left text-base">
              <thead className="border-b border-border bg-surface-muted text-sm font-semibold text-ink-soft">
                <tr>
                  <th className="px-4 py-3">Name</th>
                  <th className="px-4 py-3">Phone</th>
                  <th className="px-4 py-3">Joined Date</th>
                </tr>
              </thead>
              <tbody>
                {customers.map((c) => (
                  <tr key={c.id} className="border-b border-border last:border-b-0 hover:bg-surface-muted/50">
                    <td className="px-4 py-3.5 font-bold text-ink">{c.name}</td>
                    <td className="px-4 py-3.5 text-ink-soft font-medium">{c.phone ?? "—"}</td>
                    <td className="px-4 py-3.5 text-ink-soft font-medium">{formatDate(c.created_at)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Mobile Cards view */}
          <div className="space-y-3 sm:hidden">
            {customers.map((c) => (
              <div key={c.id} className="rounded-card border border-border bg-surface p-4 shadow-card space-y-2">
                <div className="flex justify-between items-start">
                  <div className="text-lg font-bold text-ink">{c.name}</div>
                  <div className="text-xs text-ink-soft font-medium bg-surface-muted px-2 py-0.5 rounded-full">
                    {formatDate(c.created_at)}
                  </div>
                </div>
                <div className="text-base text-ink-soft font-semibold">{c.phone ?? "No Phone Number"}</div>
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  );
}
