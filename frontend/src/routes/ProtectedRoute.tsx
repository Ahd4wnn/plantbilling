import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "@/store/auth";
import type { Role } from "@/api/types";
import { Spinner } from "@/components/Spinner";

interface ProtectedRouteProps {
  /** If set, only these roles may enter; other roles are redirected to their home. */
  role?: Role | Role[];
}

/** Full-screen loader shown while the initial /auth/me hydration is in flight. */
function FullScreenLoader() {
  return (
    <div className="flex min-h-dvh items-center justify-center text-primary-600">
      <Spinner className="h-10 w-10" label="Loading Plantora" />
    </div>
  );
}

export function roleHome(role: Role | undefined): string {
  if (role === "admin") return "/admin";
  if (role === "owner") return "/owner";
  if (role === "manager") return "/app/products";
  return "/app/bill";
}

export function ProtectedRoute({ role }: ProtectedRouteProps) {
  const location = useLocation();
  const initializing = useAuth((s) => s.initializing);
  const user = useAuth((s) => s.user);
  const token = useAuth((s) => s.token);

  if (initializing) return <FullScreenLoader />;

  // Not authenticated → login (remember where they were going).
  if (!token || !user) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  // Wrong role → send to their own home, never the other role's area.
  if (role) {
    const allowed = Array.isArray(role) ? role : [role];
    if (!allowed.includes(user.role)) {
      return <Navigate to={roleHome(user.role)} replace />;
    }
  }

  return <Outlet />;
}
