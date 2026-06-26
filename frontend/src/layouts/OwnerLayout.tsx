import { Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "@/store/auth";
import { Wordmark } from "@/components/LeafMark";

/**
 * Owner shell: a calm, single-column dashboard for the multi-shop business owner.
 * Uses the same tokens as the admin/shop areas. Shop drill-downs are pushed as
 * sub-routes (/owner/shops/:id) from the dashboard.
 */
export function OwnerLayout() {
  const user = useAuth((s) => s.user);
  const logout = useAuth((s) => s.logout);
  const navigate = useNavigate();

  const onLogout = () => {
    logout();
    navigate("/login", { replace: true });
  };

  return (
    <div className="min-h-dvh bg-surface-muted">
      <header className="border-b border-border bg-white pt-[env(safe-area-inset-top)]">
        <div className="mx-auto flex h-14 w-full max-w-6xl items-center justify-between px-4">
          <button type="button" onClick={() => navigate("/owner")} className="flex items-center gap-2">
            <Wordmark />
            <span className="rounded-full bg-primary-50 px-2 py-0.5 text-xs font-bold text-primary-700">Owner</span>
          </button>
          <div className="flex items-center gap-3">
            <span className="hidden max-w-[12rem] truncate text-sm text-ink-soft sm:block" title={user?.email}>
              {user?.email}
            </span>
            <button
              type="button"
              onClick={onLogout}
              className="rounded-control border border-border-strong px-3 py-1.5 text-sm font-semibold text-ink hover:bg-surface-muted"
            >
              Log out
            </button>
          </div>
        </div>
      </header>

      <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-6">
        <Outlet />
      </main>
    </div>
  );
}
