import { NavLink } from "react-router-dom";
import { motion, useReducedMotion } from "motion/react";
import { BarChart3, MoreHorizontal, Package, ReceiptText, Users, type LucideIcon } from "lucide-react";
import { useAuth } from "@/store/auth";

interface NavItem {
  to: string;
  label: string;
  Icon: LucideIcon;
  end?: boolean;
}

// Bill is the default/home tab (billing is the primary job).
const items: NavItem[] = [
  { to: "/app/bill", label: "Bill", Icon: ReceiptText },
  { to: "/app/products", label: "Products", Icon: Package },
  { to: "/app/sales", label: "Sales", Icon: BarChart3 },
  { to: "/app/customers", label: "Customers", Icon: Users },
  { to: "/app/more", label: "More", Icon: MoreHorizontal },
];

/**
 * Persistent, thumb-reachable bottom navigation. Labels are ALWAYS visible
 * (never icon-only) for clarity. Each item is a ≥56px touch target with a clear
 * active state in brand green.
 */
export function BottomNav() {
  const reduce = useReducedMotion();
  const user = useAuth((s) => s.user);

  const filteredItems = items.filter(
    (item) => !(item.to === "/app/bill" && user?.role === "manager")
  );

  return (
    <nav
      className="fixed inset-x-0 bottom-0 z-30 border-t border-border bg-white/90 backdrop-blur-lg
                 pb-[env(safe-area-inset-bottom)]"
      aria-label="Primary"
    >
      <ul className="mx-auto flex max-w-screen-sm">
        {filteredItems.map(({ to, label, Icon }) => (
          <li key={to} className="flex-1">
            <NavLink
              to={to}
              className={({ isActive }) =>
                [
                  "flex h-[64px] flex-col items-center justify-center gap-1 px-1",
                  "text-sm font-semibold transition-colors duration-gentle",
                  isActive ? "text-primary-700" : "text-ink-soft hover:text-ink",
                ].join(" ")
              }
            >
              {({ isActive }) => (
                <>
                  {/* Icon sits in a pill that fills with mint + animates when active. */}
                  <span className="relative flex h-9 w-14 items-center justify-center">
                    {isActive && (
                      <motion.span
                        layoutId={reduce ? undefined : "nav-active-pill"}
                        className="absolute inset-0 rounded-full bg-primary-50"
                        transition={{ type: "spring", stiffness: 420, damping: 34 }}
                      />
                    )}
                    <Icon
                      className={`relative h-7 w-7 ${isActive ? "text-primary-600" : "text-ink-soft"}`}
                      strokeWidth={isActive ? 2.25 : 2}
                    />
                  </span>
                  <span>{label}</span>
                </>
              )}
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  );
}
