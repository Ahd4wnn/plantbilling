import { useEffect, useMemo, useState } from "react";
import { getAdminStaff, type AdminStaffRow } from "@/api/adminAnalytics";
import { friendlyError } from "@/api/client";
import { Spinner } from "@/components/Spinner";
import { Button } from "@/components/Button";

function fromNow(iso: string | null): string {
  if (!iso) return "Never";
  const days = Math.floor((Date.now() - new Date(iso).getTime()) / 86_400_000);
  if (days <= 0) return "Today";
  if (days === 1) return "Yesterday";
  if (days < 30) return `${days} days ago`;
  return new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", year: "numeric" }).format(new Date(iso));
}

type RoleFilter = "all" | "manager" | "salesperson";

export function StaffPage() {
  const [rows, setRows] = useState<AdminStaffRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [role, setRole] = useState<RoleFilter>("all");

  const load = () => {
    setLoading(true);
    setError(null);
    getAdminStaff()
      .then(setRows)
      .catch((e) => setError(friendlyError(e, "Couldn't load staff.")))
      .finally(() => setLoading(false));
  };
  useEffect(load, []);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return rows.filter((r) => {
      if (role !== "all" && r.role !== role) return false;
      if (!q) return true;
      return r.email.toLowerCase().includes(q) || (r.shop_name ?? "").toLowerCase().includes(q);
    });
  }, [rows, query, role]);

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-extrabold text-ink">Staff</h1>
          {!loading && !error && <p className="text-sm text-ink-soft">{rows.length} across all shops · managers &amp; salespeople</p>}
        </div>
      </div>

      <div className="mt-4 flex flex-wrap items-center gap-2">
        <input
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search email or shop…"
          className="h-11 min-w-[14rem] flex-1 rounded-control border-2 border-border bg-white px-3 text-base text-ink focus:border-primary-600 focus:outline-none focus:ring-4 focus:ring-primary-600/20"
        />
        <div className="flex gap-1 rounded-control bg-surface-muted p-1">
          {(["all", "manager", "salesperson"] as RoleFilter[]).map((r) => (
            <button
              key={r}
              type="button"
              onClick={() => setRole(r)}
              aria-pressed={role === r}
              className={["h-9 rounded-control px-3 text-sm font-semibold capitalize transition-colors", role === r ? "bg-primary-600 text-white" : "text-ink-soft"].join(" ")}
            >
              {r}
            </button>
          ))}
        </div>
      </div>

      {loading ? (
        <div className="flex justify-center py-16"><Spinner className="h-8 w-8 text-primary-600" /></div>
      ) : error ? (
        <div className="py-12 text-center">
          <p className="text-base font-semibold text-danger">{error}</p>
          <Button variant="secondary" size="tap" className="mt-4" onClick={load}>Try again</Button>
        </div>
      ) : filtered.length === 0 ? (
        <p className="py-12 text-center text-base text-ink-soft">{rows.length === 0 ? "No staff yet." : "No staff match your search."}</p>
      ) : (
        <>
          {/* Desktop table */}
          <div className="mt-4 hidden overflow-hidden rounded-card border border-border bg-surface shadow-card md:block">
            <table className="w-full text-left text-base">
              <thead className="border-b border-border bg-surface-muted text-sm text-ink-soft">
                <tr>
                  <Th>Person</Th><Th>Role</Th><Th>Shop</Th><Th>Status</Th><Th>Bills</Th><Th>Last active</Th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((r) => (
                  <tr key={r.user_id} className="border-b border-border last:border-b-0">
                    <td className="px-4 py-3 font-semibold text-ink">{r.email}</td>
                    <td className="px-4 py-3"><RoleBadge role={r.role} /></td>
                    <td className="px-4 py-3 text-ink-soft">{r.shop_name ?? "—"}</td>
                    <td className="px-4 py-3">
                      <span className={["inline-flex rounded-full px-2 py-0.5 text-sm font-bold", r.is_active ? "bg-success-soft text-success" : "bg-surface-muted text-ink-soft"].join(" ")}>
                        {r.is_active ? "Active" : "Disabled"}
                      </span>
                    </td>
                    <td className="px-4 py-3 font-semibold text-ink">{r.bill_count.toLocaleString("en-IN")}</td>
                    <td className="px-4 py-3 text-ink-soft">{fromNow(r.last_bill_at)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Mobile cards */}
          <div className="mt-4 space-y-3 md:hidden">
            {filtered.map((r) => (
              <div key={r.user_id} className="rounded-card border border-border bg-surface p-4 shadow-card">
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <div className="truncate text-lg font-bold text-ink">{r.email}</div>
                    <div className="mt-0.5"><RoleBadge role={r.role} /></div>
                  </div>
                  <span className={["shrink-0 rounded-full px-2 py-0.5 text-sm font-bold", r.is_active ? "bg-success-soft text-success" : "bg-surface-muted text-ink-soft"].join(" ")}>
                    {r.is_active ? "Active" : "Disabled"}
                  </span>
                </div>
                <div className="mt-2 text-base text-ink-soft">{r.shop_name ?? "—"}</div>
                <div className="mt-1 text-base text-ink-soft">
                  {r.bill_count} {r.bill_count === 1 ? "bill" : "bills"} · {fromNow(r.last_bill_at)}
                </div>
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  );
}

function Th({ children }: { children: React.ReactNode }) {
  return <th className="px-4 py-2 font-semibold">{children}</th>;
}
function RoleBadge({ role }: { role: string }) {
  const isManager = role === "manager";
  return (
    <span className={["inline-flex rounded-full px-2 py-0.5 text-sm font-bold capitalize", isManager ? "bg-primary-50 text-primary-700" : "bg-sky-50 text-sky-700"].join(" ")}>
      {role}
    </span>
  );
}
