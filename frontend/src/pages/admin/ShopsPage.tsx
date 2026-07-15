import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { AnimatePresence, motion } from "motion/react";
import { listShops, updateShop, deleteShop, type ShopRow } from "@/api/admin";
import { getAdminOverview } from "@/api/adminAnalytics";
import { friendlyError } from "@/api/client";
import { Spinner } from "@/components/Spinner";
import { Button } from "@/components/Button";
import { ConfirmDialog } from "@/components/ConfirmDialog";
import { CreateShopModal } from "./CreateShopModal";
import { ResetPasswordModal } from "./ResetPasswordModal";
import { BusinessDetailsModal } from "./BusinessDetailsModal";
import { ShopDetailDrawer } from "./ShopDetailDrawer";
import { VoiceSearchButton } from "@/components/VoiceSearchButton";

type StatusFilter = "all" | "active" | "inactive";

interface ShopStat {
  total_sales: string;
  bill_count: number;
  last_bill_at: string | null;
}

function inr(v: string): string {
  const n = Number(v);
  return "₹" + (isFinite(n) ? n : 0).toLocaleString("en-IN", { minimumFractionDigits: 0, maximumFractionDigits: 0 });
}

/** Health signal from the most recent bill: green today, amber this week, red silent. */
function health(lastBillAt: string | null): { dot: string; label: string } {
  if (!lastBillAt) return { dot: "bg-slate-300", label: "Never billed" };
  const days = Math.floor((Date.now() - new Date(lastBillAt).getTime()) / 86_400_000);
  if (days <= 0) return { dot: "bg-success", label: "Billed today" };
  if (days <= 6) return { dot: "bg-amber-400", label: days === 1 ? "Billed yesterday" : `${days} days ago` };
  return { dot: "bg-danger", label: `Quiet ${days} days` };
}

export function ShopsPage() {
  const [shops, setShops] = useState<ShopRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [query, setQuery] = useState("");
  const [status, setStatus] = useState<StatusFilter>("all");

  const [createOpen, setCreateOpen] = useState(false);
  const [resetShop, setResetShop] = useState<ShopRow | null>(null);
  const [editDetailsShop, setEditDetailsShop] = useState<ShopRow | null>(null);
  const [deactivateShop, setDeactivateShop] = useState<ShopRow | null>(null);
  const [deleteShopTarget, setDeleteShopTarget] = useState<ShopRow | null>(null);
  const [drawerShop, setDrawerShop] = useState<ShopRow | null>(null);
  const [stats, setStats] = useState<Record<string, ShopStat>>({});
  const [busyId, setBusyId] = useState<string | null>(null);

  const [searchParams, setSearchParams] = useSearchParams();

  const [toast, setToast] = useState<string | null>(null);
  const toastTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const candidates = useMemo(() => {
    const names = shops.map((s) => s.name);
    const owners = shops.map((s) => s.owner_name).filter(Boolean) as string[];
    return [...names, ...owners];
  }, [shops]);
  const showToast = (m: string) => {
    setToast(m);
    if (toastTimer.current) clearTimeout(toastTimer.current);
    toastTimer.current = setTimeout(() => setToast(null), 3500);
  };
  useEffect(() => () => { if (toastTimer.current) clearTimeout(toastTimer.current); }, []);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setShops(await listShops());
    } catch (e) {
      setError(friendlyError(e, "Couldn't load shops."));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  // Live activity (last 7 days) merged in from the analytics overview — best-effort,
  // so a stats failure never blocks the shops list.
  useEffect(() => {
    const now = new Date();
    const from = new Date(now);
    from.setDate(from.getDate() - 6);
    const ymd = (d: Date) => d.toISOString().slice(0, 10);
    getAdminOverview(ymd(from), ymd(now))
      .then((o) => {
        const map: Record<string, ShopStat> = {};
        for (const s of o.shops) {
          map[s.shop_id] = { total_sales: s.total_sales, bill_count: s.bill_count, last_bill_at: s.last_bill_at };
        }
        setStats(map);
      })
      .catch(() => {});
  }, []);

  // Deep link: /admin/shops?focus=<shopId> opens that shop's drawer once loaded.
  useEffect(() => {
    const focus = searchParams.get("focus");
    if (focus && shops.length > 0) {
      const target = shops.find((s) => s.id === focus);
      if (target) setDrawerShop(target);
      searchParams.delete("focus");
      setSearchParams(searchParams, { replace: true });
    }
  }, [shops, searchParams, setSearchParams]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return shops.filter((s) => {
      if (status === "active" && !s.is_active) return false;
      if (status === "inactive" && s.is_active) return false;
      if (!q) return true;
      return (
        s.name.toLowerCase().includes(q) ||
        (s.owner_name ?? "").toLowerCase().includes(q) ||
        (s.owner_email ?? "").toLowerCase().includes(q) ||
        (s.owner_phone ?? "").toLowerCase().includes(q)
      );
    });
  }, [shops, query, status]);

  async function applyActive(shop: ShopRow, isActive: boolean) {
    setBusyId(shop.id);
    try {
      await updateShop(shop.id, { is_active: isActive });
      showToast(isActive ? `${shop.name} reactivated.` : `${shop.name} deactivated.`);
      await load();
    } catch (e) {
      showToast(friendlyError(e, "Action failed."));
    } finally {
      setBusyId(null);
      setDeactivateShop(null);
    }
  }

  async function handleToggleWhatsapp(shop: ShopRow, autoSend: boolean) {
    setBusyId(shop.id);
    try {
      await updateShop(shop.id, { whatsapp_auto_send: autoSend });
      showToast(autoSend ? `WhatsApp auto-send enabled for ${shop.name}.` : `WhatsApp auto-send disabled for ${shop.name}.`);
      await load();
    } catch (e) {
      showToast(friendlyError(e, "Failed to update WhatsApp setting."));
    } finally {
      setBusyId(null);
    }
  }

  async function applyDelete(shop: ShopRow) {
    setBusyId(shop.id);
    try {
      await deleteShop(shop.id);
      showToast(`${shop.name} deleted successfully.`);
      await load();
    } catch (e) {
      showToast(friendlyError(e, "Delete failed."));
    } finally {
      setBusyId(null);
      setDeleteShopTarget(null);
    }
  }

  const toggleActive = (shop: ShopRow) => {
    if (shop.is_active) setDeactivateShop(shop); // confirm before deactivating
    else applyActive(shop, true);
  };

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-extrabold text-ink">Shops</h1>
          {!loading && !error && (
            <p className="text-sm text-ink-soft">{shops.length} {shops.length === 1 ? "shop" : "shops"}</p>
          )}
        </div>
        <Button variant="primary" size="tap" onClick={() => setCreateOpen(true)}>
          + Add Shop
        </Button>
      </div>

      {/* Filters */}
      <div className="mt-4 flex flex-wrap items-center gap-2">
        <div className="relative flex-1 min-w-[14rem]">
          <input
            type="search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search shop, owner, email, phone…"
            className="h-11 w-full rounded-control border-2 border-border bg-white pl-3 pr-12 text-base text-ink focus:border-primary-600 focus:outline-none focus:ring-4 focus:ring-primary-600/20"
          />
          <div className="absolute inset-y-0 right-0 flex items-center pr-2">
            <VoiceSearchButton candidates={candidates} onTranscript={(text) => setQuery(text)} />
          </div>
        </div>
        <div className="flex gap-1 rounded-control bg-surface-muted p-1">
          {(["all", "active", "inactive"] as StatusFilter[]).map((s) => (
            <button
              key={s}
              type="button"
              onClick={() => setStatus(s)}
              aria-pressed={status === s}
              className={[
                "h-9 rounded-control px-3 text-sm font-semibold capitalize transition-colors",
                status === s ? "bg-primary-600 text-white" : "text-ink-soft",
              ].join(" ")}
            >
              {s}
            </button>
          ))}
        </div>
      </div>

      {/* Content */}
      {loading ? (
        <div className="flex justify-center py-16"><Spinner className="h-8 w-8 text-primary-600" /></div>
      ) : error ? (
        <div className="py-12 text-center">
          <p className="text-base font-semibold text-danger">{error}</p>
          <Button variant="secondary" size="tap" className="mt-4" onClick={load}>Try again</Button>
        </div>
      ) : shops.length === 0 ? (
        <div className="rounded-card border border-border bg-surface py-16 text-center shadow-card">
          <p className="text-lg font-bold text-ink">No shops yet</p>
          <p className="mt-1 text-base text-ink-soft">Add your first shop to onboard an owner.</p>
          <Button variant="primary" size="tap" className="mt-5" onClick={() => setCreateOpen(true)}>+ Add Shop</Button>
        </div>
      ) : filtered.length === 0 ? (
        <p className="py-12 text-center text-base text-ink-soft">No shops match your search.</p>
      ) : (
        <>
          {/* Desktop table */}
          <div className="mt-4 hidden overflow-hidden rounded-card border border-border bg-surface shadow-card md:block">
            <table className="w-full text-left text-base">
              <thead className="border-b border-border bg-surface-muted text-sm text-ink-soft">
                <tr>
                  <Th>Shop</Th><Th>Owner Email</Th><Th>7-day activity</Th><Th>Status</Th><Th>Auto WhatsApp</Th><Th>Actions</Th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((s) => {
                  const st = stats[s.id];
                  const h = health(st?.last_bill_at ?? null);
                  return (
                  <tr key={s.id} className="border-b border-border last:border-b-0">
                    <td className="px-4 py-3">
                      <button
                        type="button"
                        onClick={() => setDrawerShop(s)}
                        className="flex items-center gap-2 font-semibold text-ink hover:text-primary-700"
                        title={h.label}
                      >
                        <span className={["h-2.5 w-2.5 shrink-0 rounded-full", h.dot].join(" ")} />
                        <span className="underline-offset-2 hover:underline">{s.name}</span>
                      </button>
                    </td>
                    <td className="px-4 py-3 text-ink-soft">{s.owner_email ?? "—"}</td>
                    <td className="px-4 py-3 text-ink-soft">
                      {st ? (
                        <span>{inr(st.total_sales)} · {st.bill_count} bills</span>
                      ) : (
                        <span className="text-ink-soft/60">—</span>
                      )}
                      <span className="block text-xs text-ink-soft/70">{h.label}</span>
                    </td>
                    <td className="px-4 py-3"><StatusBadge active={s.is_active} /></td>
                    <td className="px-4 py-3">
                      <label className="relative inline-flex items-center cursor-pointer">
                        <input
                          type="checkbox"
                          checked={s.whatsapp_auto_send}
                          disabled={busyId === s.id}
                          onChange={(e) => handleToggleWhatsapp(s, e.target.checked)}
                          className="sr-only peer"
                        />
                        <div className="w-11 h-6 bg-slate-200 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-slate-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-primary-600"></div>
                      </label>
                    </td>
                    <td className="px-4 py-3">
                      <RowActions shop={s} busy={busyId === s.id} onToggle={toggleActive} onReset={setResetShop} onDelete={setDeleteShopTarget} onEditDetails={setEditDetailsShop} />
                    </td>
                  </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          {/* Mobile cards */}
          <div className="mt-4 space-y-3 md:hidden">
            {filtered.map((s) => {
              const st = stats[s.id];
              const h = health(st?.last_bill_at ?? null);
              return (
              <div key={s.id} className="rounded-card border border-border bg-surface p-4 shadow-card">
                <div className="flex items-start justify-between gap-2">
                  <button type="button" onClick={() => setDrawerShop(s)} className="flex min-w-0 items-center gap-2 text-left">
                    <span className={["h-2.5 w-2.5 shrink-0 rounded-full", h.dot].join(" ")} />
                    <span className="truncate text-lg font-bold text-ink underline-offset-2">{s.name}</span>
                  </button>
                  <StatusBadge active={s.is_active} />
                </div>
                <div className="mt-2 space-y-0.5 text-base text-ink-soft">
                  <div className="truncate">{s.owner_email ?? "—"}</div>
                  <div>{st ? `${inr(st.total_sales)} · ${st.bill_count} bills · ` : ""}{h.label}</div>
                  <div className="flex items-center justify-between pt-1">
                    <span>Auto WhatsApp:</span>
                    <label className="relative inline-flex items-center cursor-pointer">
                      <input
                        type="checkbox"
                        checked={s.whatsapp_auto_send}
                        disabled={busyId === s.id}
                        onChange={(e) => handleToggleWhatsapp(s, e.target.checked)}
                        className="sr-only peer"
                      />
                      <div className="w-11 h-6 bg-slate-200 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-slate-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-primary-600"></div>
                    </label>
                  </div>
                </div>
                <div className="mt-3">
                  <RowActions shop={s} busy={busyId === s.id} onToggle={toggleActive} onReset={setResetShop} onDelete={setDeleteShopTarget} onEditDetails={setEditDetailsShop} />
                </div>
              </div>
              );
            })}
          </div>
        </>
      )}

      <ShopDetailDrawer
        shop={drawerShop}
        onClose={() => setDrawerShop(null)}
        onEditDetails={(s) => { setDrawerShop(null); setEditDetailsShop(s); }}
        onReset={(s) => { setDrawerShop(null); setResetShop(s); }}
        onToggle={(s) => { setDrawerShop(null); toggleActive(s); }}
        onDelete={(s) => { setDrawerShop(null); setDeleteShopTarget(s); }}
      />

      <CreateShopModal open={createOpen} onClose={() => setCreateOpen(false)} onCreated={load} />
      <ResetPasswordModal shop={resetShop} onClose={() => setResetShop(null)} />
      <BusinessDetailsModal shop={editDetailsShop} onClose={() => setEditDetailsShop(null)} onSaved={load} />
      <ConfirmDialog
        open={deactivateShop !== null}
        title={`Deactivate ${deactivateShop?.name ?? "shop"}?`}
        body="The owner won't be able to log in or use the app until you reactivate it. No data is deleted."
        confirmLabel="Deactivate"
        cancelLabel="Cancel"
        destructive
        onConfirm={() => deactivateShop && applyActive(deactivateShop, false)}
        onCancel={() => setDeactivateShop(null)}
      />
      <ConfirmDialog
        open={deleteShopTarget !== null}
        title={`Delete ${deleteShopTarget?.name ?? "shop"}?`}
        body="This will permanently delete the shop and all its data, including salespeople, products, customers, and bill history. This action cannot be undone."
        confirmLabel="Delete permanently"
        cancelLabel="Cancel"
        destructive
        onConfirm={() => deleteShopTarget && applyDelete(deleteShopTarget)}
        onCancel={() => setDeleteShopTarget(null)}
      />

      <AnimatePresence>
        {toast && (
          <motion.div
            className="fixed inset-x-0 top-4 z-[70] flex justify-center px-4"
            initial={{ opacity: 0, y: -12 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -12 }}
          >
            <div className="rounded-control bg-ink px-5 py-3 text-base font-semibold text-white shadow-card-lg">{toast}</div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

function Th({ children }: { children: React.ReactNode }) {
  return <th className="px-4 py-2 font-semibold">{children}</th>;
}

function StatusBadge({ active }: { active: boolean }) {
  return (
    <span
      className={[
        "inline-flex items-center rounded-full px-2 py-0.5 text-sm font-bold",
        active ? "bg-success-soft text-success" : "bg-surface-muted text-ink-soft",
      ].join(" ")}
    >
      {active ? "Active" : "Inactive"}
    </span>
  );
}

function RowActions({
  shop, busy, onToggle, onReset, onDelete, onEditDetails,
}: {
  shop: ShopRow;
  busy: boolean;
  onToggle: (s: ShopRow) => void;
  onReset: (s: ShopRow) => void;
  onDelete: (s: ShopRow) => void;
  onEditDetails: (s: ShopRow) => void;
}) {
  return (
    <div className="flex flex-wrap items-center gap-2">
      <button
        type="button"
        disabled={busy}
        onClick={() => onEditDetails(shop)}
        className="rounded-control border border-border-strong px-3 py-1.5 text-sm font-semibold text-ink hover:bg-surface-muted disabled:opacity-50"
      >
        Business details
      </button>
      <button
        type="button"
        disabled={busy}
        onClick={() => onToggle(shop)}
        className={[
          "rounded-control px-3 py-1.5 text-sm font-semibold disabled:opacity-50",
          shop.is_active
            ? "border border-border-strong text-danger hover:bg-danger-soft"
            : "border border-border-strong text-success hover:bg-success-soft",
        ].join(" ")}
      >
        {shop.is_active ? "Deactivate" : "Activate"}
      </button>
      <button
        type="button"
        disabled={busy}
        onClick={() => onReset(shop)}
        className="rounded-control border border-border-strong px-3 py-1.5 text-sm font-semibold text-ink hover:bg-surface-muted disabled:opacity-50"
      >
        Reset password
      </button>
      <button
        type="button"
        disabled={busy}
        onClick={() => onDelete(shop)}
        className="rounded-control border border-danger-soft px-3 py-1.5 text-sm font-semibold text-danger hover:bg-danger-soft disabled:opacity-50"
      >
        Delete
      </button>
    </div>
  );
}
