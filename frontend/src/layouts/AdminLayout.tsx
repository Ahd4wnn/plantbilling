import { useEffect, useState } from "react";
import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { LayoutDashboard, Store, Users, UserCog, Contact, Bug, Search, LogOut } from "lucide-react";
import { useAuth } from "@/store/auth";
import { Wordmark } from "@/components/LeafMark";
import { CommandPalette } from "@/components/CommandPalette";

const NAV = [
  { to: "/admin", label: "Dashboard", icon: LayoutDashboard, end: true },
  { to: "/admin/shops", label: "Shops", icon: Store },
  { to: "/admin/owners", label: "Owners", icon: Users },
  { to: "/admin/staff", label: "Staff", icon: UserCog },
  { to: "/admin/customers", label: "Customers", icon: Contact },
  { to: "/admin/crashes", label: "Crashes", icon: Bug },
];

const isMac = typeof navigator !== "undefined" && /Mac|iPhone|iPad/.test(navigator.platform);

/**
 * Admin command center: an icon sidebar (desktop) / top-tabs (mobile) with a
 * Cmd/Ctrl-K command palette. Denser than the shop app, same design tokens.
 */
export function AdminLayout() {
  const user = useAuth((s) => s.user);
  const logout = useAuth((s) => s.logout);
  const navigate = useNavigate();
  const [paletteOpen, setPaletteOpen] = useState(false);

  const onLogout = () => {
    logout();
    navigate("/login", { replace: true });
  };

  // Global Cmd/Ctrl-K to open the palette.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        setPaletteOpen((o) => !o);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  return (
    <div className="min-h-dvh bg-surface-muted md:flex">
      {/* Sidebar (desktop) */}
      <aside className="hidden w-60 shrink-0 flex-col border-r border-border bg-white md:flex">
        <div className="flex h-16 items-center gap-2 border-b border-border px-4">
          <Wordmark />
          <span className="rounded-full bg-primary-50 px-2 py-0.5 text-xs font-bold text-primary-700">Admin</span>
        </div>
        <div className="px-3 pt-3">
          <button
            type="button"
            onClick={() => setPaletteOpen(true)}
            className="flex w-full items-center gap-2 rounded-control border border-border bg-surface-muted px-3 py-2 text-sm text-ink-soft transition-colors hover:border-border-strong hover:text-ink"
          >
            <Search className="h-4 w-4" />
            <span className="flex-1 text-left">Search…</span>
            <kbd className="rounded border border-border bg-white px-1.5 py-0.5 text-[11px] font-semibold">{isMac ? "⌘" : "Ctrl"} K</kbd>
          </button>
        </div>
        <nav className="flex-1 p-3">
          {NAV.map((item) => (
            <NavLink key={item.to} to={item.to} end={item.end} className={navClass}>
              <item.icon className="h-5 w-5 shrink-0" />
              <span>{item.label}</span>
            </NavLink>
          ))}
        </nav>
        <div className="border-t border-border p-3">
          <div className="truncate px-2 text-sm text-ink-soft" title={user?.email}>
            {user?.email}
          </div>
          <button
            type="button"
            onClick={onLogout}
            className="mt-2 flex w-full items-center justify-center gap-2 rounded-control border border-border-strong px-3 py-2 text-sm font-semibold text-ink hover:bg-surface-muted"
          >
            <LogOut className="h-4 w-4" /> Log out
          </button>
        </div>
      </aside>

      {/* Main column */}
      <div className="flex min-w-0 flex-1 flex-col">
        {/* Mobile top bar + tabs */}
        <header className="border-b border-border bg-white md:hidden pt-[env(safe-area-inset-top)]">
          <div className="flex h-14 items-center justify-between px-4">
            <div className="flex items-center gap-2">
              <Wordmark />
              <span className="rounded-full bg-primary-50 px-2 py-0.5 text-xs font-bold text-primary-700">Admin</span>
            </div>
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => setPaletteOpen(true)}
                aria-label="Search"
                className="rounded-control border border-border-strong p-2 text-ink"
              >
                <Search className="h-5 w-5" />
              </button>
              <button
                type="button"
                onClick={onLogout}
                className="rounded-control border border-border-strong px-3 py-1.5 text-sm font-semibold text-ink"
              >
                Log out
              </button>
            </div>
          </div>
          <nav className="flex gap-1 overflow-x-auto px-3 pb-2">
            {NAV.map((item) => (
              <NavLink key={item.to} to={item.to} end={item.end} className={mobileTabClass}>
                {item.label}
              </NavLink>
            ))}
          </nav>
        </header>

        <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-6">
          <Outlet />
        </main>
      </div>

      <CommandPalette open={paletteOpen} onClose={() => setPaletteOpen(false)} />
    </div>
  );
}

function navClass({ isActive }: { isActive: boolean }): string {
  return [
    "mb-1 flex items-center gap-3 rounded-control px-3 py-2 text-base font-semibold transition-colors",
    isActive ? "bg-primary-50 text-primary-700" : "text-ink-soft hover:bg-surface-muted hover:text-ink",
  ].join(" ");
}

function mobileTabClass({ isActive }: { isActive: boolean }): string {
  return [
    "shrink-0 rounded-control px-3 py-2 text-center text-base font-semibold transition-colors",
    isActive ? "bg-primary-600 text-white" : "bg-surface-muted text-ink-soft",
  ].join(" ");
}
