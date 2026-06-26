import { useEffect } from "react";
import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { useAuth } from "@/store/auth";
import { ProtectedRoute, roleHome } from "@/routes/ProtectedRoute";
import { ShopLayout } from "@/layouts/ShopLayout";
import { AdminLayout } from "@/layouts/AdminLayout";
import { LoginPage } from "@/pages/LoginPage";
import { BillPage } from "@/pages/shop/BillPage";
import { ProductsPage } from "@/pages/shop/ProductsPage";
import { SalesPage } from "@/pages/shop/SalesPage";
import { MorePage } from "@/pages/shop/MorePage";
import { StyleGuide } from "@/pages/StyleGuide";
import { NotFound } from "@/pages/NotFound";
import { ShopsPage } from "@/pages/admin/ShopsPage";
import { OwnersPage } from "@/pages/admin/OwnersPage";
import { CustomersPage } from "@/pages/admin/CustomersPage";
import { CustomersPage as ShopCustomersPage } from "@/pages/shop/CustomersPage";
import { PublicBillReceiptPage } from "@/pages/shop/PublicBillReceiptPage";
import { OwnerLayout } from "@/layouts/OwnerLayout";
import { OwnerDashboard } from "@/pages/owner/OwnerDashboard";
import { OwnerShopDetail } from "@/pages/owner/OwnerShopDetail";

/** Sends an already-authenticated user away from /login to their role home. */
function RootRedirect() {
  const user = useAuth((s) => s.user);
  const token = useAuth((s) => s.token);
  if (token && user) return <Navigate to={roleHome(user.role)} replace />;
  return <Navigate to="/login" replace />;
}

function AppIndexRedirect() {
  const user = useAuth((s) => s.user);
  const target = user?.role === "manager" ? "/app/products" : "/app/bill";
  return <Navigate to={target} replace />;
}

export default function App() {
  const init = useAuth((s) => s.init);

  // On app load: hydrate the session from a persisted token (calls /auth/me).
  useEffect(() => {
    void init();
  }, [init]);

  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<RootRedirect />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/public/share/bill/:billId" element={<PublicBillReceiptPage />} />

        {/* Dev-only design system reference (not linked in nav). */}
        {import.meta.env.DEV && <Route path="/_styleguide" element={<StyleGuide />} />}

        {/* Shop owner & salesperson area */}
        <Route element={<ProtectedRoute role={["manager", "salesperson"]} />}>
          <Route path="/app" element={<ShopLayout />}>
            <Route index element={<AppIndexRedirect />} />
            <Route path="bill" element={<BillPage />} />
            <Route path="products" element={<ProductsPage />} />
            <Route path="sales" element={<SalesPage />} />
            <Route path="customers" element={<ShopCustomersPage />} />
            <Route path="more" element={<MorePage />} />
          </Route>
        </Route>

        {/* Multi-shop owner area */}
        <Route element={<ProtectedRoute role="owner" />}>
          <Route path="/owner" element={<OwnerLayout />}>
            <Route index element={<OwnerDashboard />} />
            <Route path="shops/:shopId" element={<OwnerShopDetail />} />
          </Route>
        </Route>

        {/* Admin area (Sales removed — admin no longer views sales) */}
        <Route element={<ProtectedRoute role="admin" />}>
          <Route path="/admin" element={<AdminLayout />}>
            <Route index element={<Navigate to="/admin/shops" replace />} />
            <Route path="shops" element={<ShopsPage />} />
            <Route path="owners" element={<OwnersPage />} />
            <Route path="customers" element={<CustomersPage />} />
          </Route>
        </Route>

        <Route path="*" element={<NotFound />} />
      </Routes>
    </BrowserRouter>
  );
}
