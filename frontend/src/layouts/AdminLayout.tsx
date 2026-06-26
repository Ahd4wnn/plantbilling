import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "@/store/auth";
import { Wordmark } from "@/components/LeafMark";

const NAV = [
  { to: "/admin/shops", label: "Shops" },
  { to: "/admin/owners", label: "Owners" },
  { to: "/admin/customers", label: "Customers" },
];

/**
 * Admin shell: a conventional sidebar (desktop) / top-tabs (mobile) layout.
 * Denser than the shop app, but uses the same design tokens for consistency.
 */
export function AdminLayout() {
  const user = useAuth((s) => s.user);
  const logout = useAuth((s) => s.logout);
  const navigate = useNavigate();

  const onLogout = () => {
    logout();
    navigate("/login", { replace: true });
  };

  return (
    <div className="min-h-dvh bg-surface-muted md:flex">
      {/* Sidebar (desktop) */}
      <aside className="hidden w-56 shrink-0 flex-col border-r border-border bg-white md:flex">
        <div className="flex h-14 items-center gap-2 border-b border-border px-4">
          <Wordmark />
          <span className="rounded-full bg-primary-50 px-2 py-0.5 text-xs font-bold text-primary-700">Admin</span>
        </div>
        <nav className="flex-1 p-3">
          {NAV.map((item) => (
            <NavLink key={item.to} to={item.to} className={navClass}>
              {item.label}
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
            className="mt-2 w-full rounded-control border border-border-strong px-3 py-2 text-sm font-semibold text-ink hover:bg-surface-muted"
          >
            Log out
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
            <button
              type="button"
              onClick={onLogout}
              className="rounded-control border border-border-strong px-3 py-1.5 text-sm font-semibold text-ink"
            >
              Log out
            </button>
          </div>
          <nav className="flex gap-1 px-3 pb-2">
            {NAV.map((item) => (
              <NavLink key={item.to} to={item.to} className={mobileTabClass}>
                {item.label}
              </NavLink>
            ))}
          </nav>
        </header>

        <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

function navClass({ isActive }: { isActive: boolean }): string {
  return [
    "mb-1 block rounded-control px-3 py-2 text-base font-semibold transition-colors",
    isActive ? "bg-primary-50 text-primary-700" : "text-ink-soft hover:bg-surface-muted hover:text-ink",
  ].join(" ");
}

function mobileTabClass({ isActive }: { isActive: boolean }): string {
  return [
    "flex-1 rounded-control px-3 py-2 text-center text-base font-semibold transition-colors",
    isActive ? "bg-primary-600 text-white" : "bg-surface-muted text-ink-soft",
  ].join(" ");
}
