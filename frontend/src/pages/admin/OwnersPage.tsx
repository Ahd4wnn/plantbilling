import { useCallback, useEffect, useState } from "react";
import {
  assignShopOwner,
  createOwner,
  listOwners,
  listShops,
  type OwnerAccount,
  type ShopRow,
} from "@/api/admin";
import { friendlyError } from "@/api/client";
import { Spinner } from "@/components/Spinner";
import { Button } from "@/components/Button";

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

  const assign = async (shop: ShopRow, ownerId: string) => {
    try {
      await assignShopOwner(shop.id, ownerId || null);
      setToast(ownerId ? `Assigned ${shop.name} to an owner.` : `Removed owner from ${shop.name}.`);
      setTimeout(() => setToast(null), 3000);
      await load();
    } catch (e) {
      setToast(friendlyError(e, "Couldn't assign owner."));
      setTimeout(() => setToast(null), 3500);
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
          {/* Owner list */}
          <section>
            <h2 className="mb-2 text-lg font-bold text-ink">Owners ({owners.length})</h2>
            {owners.length === 0 ? (
              <p className="text-ink-soft">No owner accounts yet.</p>
            ) : (
              <div className="overflow-hidden rounded-card border border-border bg-surface">
                {owners.map((o) => (
                  <div key={o.id} className="flex items-center justify-between border-b border-border px-4 py-3 last:border-0">
                    <span className="font-semibold text-ink">{o.email}</span>
                    <span className="text-sm text-ink-soft">
                      {o.shop_count} {o.shop_count === 1 ? "shop" : "shops"} · {o.is_active ? "Active" : "Inactive"}
                    </span>
                  </div>
                ))}
              </div>
            )}
          </section>

          {/* Assign shops */}
          <section>
            <h2 className="mb-2 text-lg font-bold text-ink">Assign shops to owners</h2>
            <div className="overflow-hidden rounded-card border border-border bg-surface">
              {shops.map((s) => (
                <div key={s.id} className="flex flex-wrap items-center justify-between gap-3 border-b border-border px-4 py-3 last:border-0">
                  <div className="min-w-0">
                    <div className="font-semibold text-ink">{s.name}</div>
                    <div className="text-sm text-ink-soft">Manager: {s.owner_email ?? "—"}</div>
                  </div>
                  <select
                    value={s.owner_id ?? ""}
                    onChange={(e) => assign(s, e.target.value)}
                    className="h-10 rounded-control border-2 border-border bg-white px-3 text-sm focus:border-primary-600 focus:outline-none"
                  >
                    <option value="">— No owner —</option>
                    {owners.map((o) => (
                      <option key={o.id} value={o.id}>
                        {o.email}
                      </option>
                    ))}
                  </select>
                </div>
              ))}
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
