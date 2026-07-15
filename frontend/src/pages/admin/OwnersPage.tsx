import { useCallback, useEffect, useMemo, useState } from "react";
import {
  addShopOwner,
  removeShopOwner,
  createOwner,
  listOwners,
  listShops,
  type OwnerAccount,
  type ShopRow,
} from "@/api/admin";
import { getAdminOverview } from "@/api/adminAnalytics";
import { X, Store } from "lucide-react";
import { friendlyError } from "@/api/client";
import { Spinner } from "@/components/Spinner";
import { Button } from "@/components/Button";

function inr(n: number): string {
  return "₹" + (isFinite(n) ? n : 0).toLocaleString("en-IN", { minimumFractionDigits: 0, maximumFractionDigits: 0 });
}
function ymd(d: Date): string {
  return d.toISOString().slice(0, 10);
}

function genPassword(): string {
  const chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
  let out = "";
  for (let i = 0; i < 12; i++) out += chars[Math.floor(Math.random() * chars.length)];
  return out;
}

export function OwnersPage() {
  const [owners, setOwners] = useState<OwnerAccount[]>([]);
  const [shops, setShops] = useState<ShopRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState(genPassword());
  const [creating, setCreating] = useState(false);
  const [createErr, setCreateErr] = useState<string | null>(null);
  const [created, setCreated] = useState<{ email: string; password: string } | null>(null);
  const [toast, setToast] = useState<string | null>(null);
  const [salesByShop, setSalesByShop] = useState<Record<string, number>>({});

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [o, s] = await Promise.all([listOwners(), listShops()]);
      setOwners(o);
      setShops(s);
    } catch (e) {
      setError(friendlyError(e, "Couldn't load owners."));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  // Combined 30-day GMV per shop — best-effort, so a stats failure never blocks the page.
  useEffect(() => {
    const now = new Date();
    const from = new Date(now);
    from.setDate(from.getDate() - 29);
    getAdminOverview(ymd(from), ymd(now))
      .then((o) => {
        const map: Record<string, number> = {};
        for (const s of o.shops) map[s.shop_id] = Number(s.total_sales);
        setSalesByShop(map);
      })
      .catch(() => {});
  }, []);

  // Per-owner portfolio: their shops + combined 30-day GMV.
  const portfolio = useMemo(() => {
    const map: Record<string, { shops: ShopRow[]; gmv: number }> = {};
    for (const o of owners) map[o.id] = { shops: [], gmv: 0 };
    for (const s of shops) {
      for (const link of s.owners) {
        if (!map[link.id]) map[link.id] = { shops: [], gmv: 0 };
        map[link.id].shops.push(s);
        map[link.id].gmv += salesByShop[s.id] ?? 0;
      }
    }
    return map;
  }, [owners, shops, salesByShop]);

  const create = async () => {
    setCreating(true);
    setCreateErr(null);
    try {
      const acct = await createOwner(email.trim(), password);
      setCreated({ email: acct.email, password });
      setEmail("");
      setPassword(genPassword());
      await load();
    } catch (e) {
      setCreateErr(friendlyError(e, "Couldn't create owner."));
    } finally {
      setCreating(false);
    }
  };

  const flash = (msg: string, ms = 3000) => {
    setToast(msg);
    setTimeout(() => setToast(null), ms);
  };

  const addOwner = async (shop: ShopRow, ownerId: string) => {
    if (!ownerId) return;
    try {
      const owners = await addShopOwner(shop.id, ownerId);
      // Update just this shop's owners in place; refresh counts in the background.
      setShops((prev) => prev.map((s) => (s.id === shop.id ? { ...s, owners } : s)));
      flash(`Added an owner to ${shop.name}.`);
      void load();
    } catch (e) {
      flash(friendlyError(e, "Couldn't add owner."), 3500);
    }
  };

  const dropOwner = async (shop: ShopRow, ownerId: string) => {
    try {
      const owners = await removeShopOwner(shop.id, ownerId);
      setShops((prev) => prev.map((s) => (s.id === shop.id ? { ...s, owners } : s)));
      flash(`Removed an owner from ${shop.name}.`);
      void load();
    } catch (e) {
      flash(friendlyError(e, "Couldn't remove owner."), 3500);
    }
  };

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-extrabold text-ink">Business owners</h1>
        <p className="text-sm text-ink-soft">Multi-shop owners see analytics and manage staff across the shops you assign them.</p>
      </div>

      {/* Create owner */}
      <section className="rounded-card border border-border bg-surface p-4 shadow-card">
        <h2 className="text-lg font-bold text-ink">Create owner account</h2>
        <div className="mt-3 grid gap-3 sm:grid-cols-[1fr,1fr,auto]">
          <input
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="owner@email.com"
            className="h-11 rounded-control border-2 border-border bg-white px-3 text-base focus:border-primary-600 focus:outline-none"
          />
          <div className="flex gap-2">
            <input
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="h-11 flex-1 rounded-control border-2 border-border bg-white px-3 text-base focus:border-primary-600 focus:outline-none"
            />
            <button type="button" onClick={() => setPassword(genPassword())} className="rounded-control border border-border px-3 text-sm font-semibold text-ink-soft">
              ↻
            </button>
          </div>
          <Button variant="primary" size="tap" onClick={create} disabled={creating || !email.includes("@") || password.length < 8}>
            {creating ? "Creating…" : "Create"}
          </Button>
        </div>
        {createErr && <p className="mt-2 text-sm font-semibold text-danger">{createErr}</p>}
        {created && (
          <div className="mt-3 rounded-control border border-primary-200 bg-primary-50 p-3 text-sm text-primary-900">
            Owner created — share these once: <b>{created.email}</b> / <b className="font-mono">{created.password}</b>
          </div>
        )}
      </section>

      {loading ? (
        <div className="flex justify-center py-12"><Spinner className="h-8 w-8 text-primary-600" /></div>
      ) : error ? (
        <div className="py-8 text-center">
          <p className="font-semibold text-danger">{error}</p>
          <Button variant="secondary" size="tap" className="mt-3" onClick={load}>Try again</Button>
        </div>
      ) : (
        <>
          {/* Owner portfolios */}
          <section>
            <h2 className="mb-2 text-lg font-bold text-ink">Owners ({owners.length})</h2>
            <p className="mb-2 text-sm text-ink-soft">Combined sales are across each owner's shops over the last 30 days.</p>
            {owners.length === 0 ? (
              <p className="text-ink-soft">No owner accounts yet.</p>
            ) : (
              <div className="grid gap-3 sm:grid-cols-2">
                {owners.map((o) => {
                  const p = portfolio[o.id] ?? { shops: [], gmv: 0 };
                  return (
                    <div key={o.id} className="rounded-card border border-border bg-surface p-4 shadow-card">
                      <div className="flex items-start justify-between gap-2">
                        <div className="min-w-0">
                          <div className="truncate font-bold text-ink">{o.email}</div>
                          <div className="text-sm text-ink-soft">
                            {p.shops.length} {p.shops.length === 1 ? "shop" : "shops"} · {o.is_active ? "Active" : "Inactive"}
                          </div>
                        </div>
                        <div className="shrink-0 text-right">
                          <div className="text-lg font-extrabold text-primary-700">{inr(p.gmv)}</div>
                          <div className="text-xs text-ink-soft">30-day sales</div>
                        </div>
                      </div>
                      {p.shops.length > 0 && (
                        <div className="mt-3 flex flex-wrap gap-1.5">
                          {p.shops.map((s) => (
                            <span key={s.id} className="inline-flex items-center gap-1 rounded-full bg-surface-muted px-2.5 py-1 text-sm font-medium text-ink">
                              <Store className="h-3.5 w-3.5 text-ink-soft" />
                              {s.name}
                            </span>
                          ))}
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            )}
          </section>

          {/* Assign shops — a shop can have several owners */}
          <section>
            <h2 className="mb-2 text-lg font-bold text-ink">Assign shops to owners</h2>
            <p className="mb-2 text-sm text-ink-soft">A shop can have more than one owner. Add as many as needed; remove with the ✕.</p>
            <div className="overflow-hidden rounded-card border border-border bg-surface">
              {shops.map((s) => {
                const linkedIds = new Set(s.owners.map((o) => o.id));
                const available = owners.filter((o) => !linkedIds.has(o.id));
                return (
                  <div key={s.id} className="flex flex-wrap items-center justify-between gap-3 border-b border-border px-4 py-3 last:border-0">
                    <div className="min-w-0 flex-1">
                      <div className="font-semibold text-ink">{s.name}</div>
                      <div className="text-sm text-ink-soft">Manager: {s.owner_email ?? "—"}</div>
                      <div className="mt-2 flex flex-wrap gap-1.5">
                        {s.owners.length === 0 ? (
                          <span className="text-sm text-ink-soft">No owners</span>
                        ) : (
                          s.owners.map((o) => (
                            <span
                              key={o.id}
                              className="inline-flex items-center gap-1 rounded-full bg-primary-50 py-1 pl-3 pr-1.5 text-sm font-semibold text-primary-700"
                            >
                              {o.email}
                              <button
                                type="button"
                                onClick={() => dropOwner(s, o.id)}
                                aria-label={`Remove ${o.email}`}
                                className="rounded-full p-0.5 text-primary-700 hover:bg-primary-100"
                              >
                                <X className="h-3.5 w-3.5" />
                              </button>
                            </span>
                          ))
                        )}
                      </div>
                    </div>
                    <select
                      value=""
                      onChange={(e) => addOwner(s, e.target.value)}
                      disabled={available.length === 0}
                      className="h-10 rounded-control border-2 border-border bg-white px-3 text-sm focus:border-primary-600 focus:outline-none disabled:opacity-50"
                    >
                      <option value="">{available.length === 0 ? "All owners added" : "+ Add owner…"}</option>
                      {available.map((o) => (
                        <option key={o.id} value={o.id}>
                          {o.email}
                        </option>
                      ))}
                    </select>
                  </div>
                );
              })}
            </div>
          </section>
        </>
      )}

      {toast && (
        <div className="fixed inset-x-0 top-4 z-[70] flex justify-center px-4">
          <div className="rounded-control bg-ink px-5 py-3 text-base font-semibold text-white shadow-card-lg">{toast}</div>
        </div>
      )}
    </div>
  );
}
