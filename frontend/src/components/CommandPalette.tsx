import { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { LayoutDashboard, Store, Users, UserCog, Contact, Bug, Wallet, Search, CornerDownLeft } from "lucide-react";
import { listShops, type ShopRow } from "@/api/admin";

interface Cmd {
  id: string;
  label: string;
  sub?: string;
  to: string;
  icon: React.ReactNode;
  keywords?: string;
}

const NAV_CMDS: Cmd[] = [
  { id: "nav-dashboard", label: "Dashboard", to: "/admin", icon: <LayoutDashboard className="h-4.5 w-4.5" /> },
  { id: "nav-shops", label: "Shops", to: "/admin/shops", icon: <Store className="h-4.5 w-4.5" /> },
  { id: "nav-ledger", label: "Sales & Expenses", sub: "Your own books", to: "/admin/ledger", icon: <Wallet className="h-4.5 w-4.5" />, keywords: "ledger sales expenses money due cash upi income" },
  { id: "nav-owners", label: "Owners", to: "/admin/owners", icon: <Users className="h-4.5 w-4.5" /> },
  { id: "nav-staff", label: "Staff", to: "/admin/staff", icon: <UserCog className="h-4.5 w-4.5" /> },
  { id: "nav-customers", label: "Customers", to: "/admin/customers", icon: <Contact className="h-4.5 w-4.5" /> },
  { id: "nav-crashes", label: "Crashes", to: "/admin/crashes", icon: <Bug className="h-4.5 w-4.5" /> },
];

/** Cmd/Ctrl-K launcher: jump to any page or shop. */
export function CommandPalette({ open, onClose }: { open: boolean; onClose: () => void }) {
  const navigate = useNavigate();
  const [query, setQuery] = useState("");
  const [active, setActive] = useState(0);
  const [shops, setShops] = useState<ShopRow[]>([]);
  const inputRef = useRef<HTMLInputElement>(null);

  // Lazy-load shops the first time the palette opens.
  useEffect(() => {
    if (open && shops.length === 0) {
      listShops().then(setShops).catch(() => {});
    }
    if (open) {
      setQuery("");
      setActive(0);
      setTimeout(() => inputRef.current?.focus(), 30);
    }
  }, [open]); // eslint-disable-line react-hooks/exhaustive-deps

  const results = useMemo<Cmd[]>(() => {
    const q = query.trim().toLowerCase();
    const shopCmds: Cmd[] = shops.map((s) => ({
      id: `shop-${s.id}`,
      label: s.name,
      sub: s.owner_email ?? "Shop",
      to: `/admin/shops?focus=${s.id}`,
      icon: <Store className="h-4.5 w-4.5" />,
      keywords: `${s.owner_email ?? ""} ${s.owner_name ?? ""}`,
    }));
    const all = [...NAV_CMDS, ...shopCmds];
    if (!q) return all.slice(0, 8);
    return all
      .filter((c) => `${c.label} ${c.sub ?? ""} ${c.keywords ?? ""}`.toLowerCase().includes(q))
      .slice(0, 12);
  }, [query, shops]);

  useEffect(() => {
    if (active >= results.length) setActive(0);
  }, [results.length]); // eslint-disable-line react-hooks/exhaustive-deps

  if (!open) return null;

  const go = (c: Cmd) => {
    navigate(c.to);
    onClose();
  };

  const onKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "ArrowDown") { e.preventDefault(); setActive((a) => Math.min(a + 1, results.length - 1)); }
    else if (e.key === "ArrowUp") { e.preventDefault(); setActive((a) => Math.max(a - 1, 0)); }
    else if (e.key === "Enter") { e.preventDefault(); if (results[active]) go(results[active]); }
    else if (e.key === "Escape") { e.preventDefault(); onClose(); }
  };

  return (
    <div className="fixed inset-0 z-[80] flex items-start justify-center bg-ink/40 px-4 pt-[12vh]" onClick={onClose}>
      <div
        className="w-full max-w-xl overflow-hidden rounded-card border border-border bg-white shadow-card-lg"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center gap-2 border-b border-border px-4">
          <Search className="h-5 w-5 text-ink-soft" />
          <input
            ref={inputRef}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={onKeyDown}
            placeholder="Search pages and shops…"
            className="h-14 flex-1 bg-transparent text-lg text-ink outline-none placeholder:text-ink-soft"
          />
          <kbd className="rounded border border-border px-1.5 py-0.5 text-xs text-ink-soft">Esc</kbd>
        </div>
        <div className="max-h-[50vh] overflow-y-auto p-2">
          {results.length === 0 ? (
            <div className="px-3 py-8 text-center text-ink-soft">No matches.</div>
          ) : (
            results.map((c, i) => (
              <button
                key={c.id}
                type="button"
                onMouseEnter={() => setActive(i)}
                onClick={() => go(c)}
                className={[
                  "flex w-full items-center gap-3 rounded-control px-3 py-2.5 text-left",
                  i === active ? "bg-primary-50 text-primary-800" : "text-ink hover:bg-surface-muted",
                ].join(" ")}
              >
                <span className={i === active ? "text-primary-700" : "text-ink-soft"}>{c.icon}</span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate font-semibold">{c.label}</span>
                  {c.sub && <span className="block truncate text-sm text-ink-soft">{c.sub}</span>}
                </span>
                {i === active && <CornerDownLeft className="h-4 w-4 text-ink-soft" />}
              </button>
            ))
          )}
        </div>
      </div>
    </div>
  );
}
