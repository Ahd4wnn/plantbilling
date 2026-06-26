import { useEffect, useMemo, useState } from "react";
import { Navigate } from "react-router-dom";
import { useAuth } from "@/store/auth";
import type { Product } from "@/api/types";
import {
  createBill,
  fetchProducts,
  type BillCreatePayload,
  type BillItemPayload,
  type BillOut,
} from "@/api/billing";
import { friendlyError } from "@/api/client";
import { Spinner } from "@/components/Spinner";
import { Button } from "@/components/Button";
import { computeTotals, fromPaise, toPaise } from "@/lib/money";
import { useBilling } from "@/store/billing";
import { ProductGrid } from "./bill/ProductGrid";
import { CartBar } from "./bill/CartBar";
import { CartSheet } from "./bill/CartSheet";
import { SuccessView } from "./bill/SuccessView";
import { QuickAddSheet } from "./bill/QuickAddSheet";
import { ScannerSheet } from "./bill/ScannerSheet";
import { CartForm } from "./bill/CartForm";

export function BillPage() {
  const user = useAuth((s) => s.user);
  if (user?.role === "manager") {
    return <Navigate to="/app/products" replace />;
  }

  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  // Transient checkout state.
  const [cartOpen, setCartOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [savedBill, setSavedBill] = useState<BillOut | null>(null);

  // Quick Add & Scanner sheet states
  const [quickAddOpen, setQuickAddOpen] = useState(false);
  const [scannerOpen, setScannerOpen] = useState(false);

  const {
    lines,
    addUnit,
    discountType,
    discountValue,
    cash,
    upi,
    due,
    customer,
    remarks,
    ensureIdempotencyKey,
    resetForNewBill,
  } = useBilling();

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      setLoadError(null);
      try {
        const data = await fetchProducts();
        if (!cancelled) setProducts(data);
      } catch (e) {
        if (!cancelled) setLoadError(friendlyError(e, "Couldn't load products."));
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const totals = useMemo(
    () => computeTotals(lines.map((l) => ({ unitPrice: l.unit_price, quantity: l.quantity })), discountType, discountValue),
    [lines, discountType, discountValue],
  );

  const cartQty = useMemo(() => {
    const m: Record<string, number> = {};
    for (const l of lines) m[l.product_id] = l.quantity;
    return m;
  }, [lines]);

  const itemCount = useMemo(() => lines.reduce((n, l) => n + l.quantity, 0), [lines]);

  // ── Handlers ────────────────────────────────────────────────────────────────
  const handleCheckout = async () => {
    setSaving(true);
    setSaveError(null);

    const items: BillItemPayload[] = lines.map((l) => ({
      product_id: l.product_id,
      quantity: l.quantity,
      unit_price: fromPaise(toPaise(l.unit_price)),
    }));

    const name = customer.name.trim();
    const payload: BillCreatePayload = {
      idempotency_key: ensureIdempotencyKey(),
      items,
      discount_type: discountType,
      discount_value: discountValue.trim() ? fromPaise(toPaise(discountValue)) : "0",
      cash_amount: fromPaise(toPaise(cash)),
      upi_amount: fromPaise(toPaise(upi)),
      due_amount: fromPaise(toPaise(due)),
      remarks: remarks.trim() || null,
      // Customer is optional: send it only when a name was entered.
      new_customer: name ? { name, phone: customer.phone.trim() || null } : null,
    };
    // Percent discounts are a plain number, not money — send the raw value.
    if (discountType === "percent") {
      payload.discount_value = discountValue.trim() || "0";
    }

    try {
      const bill = await createBill(payload);
      setCartOpen(false);
      setSavedBill(bill); // success screen (covers idempotent replay too — same bill)
    } catch (e) {
      // Recoverable: keep the cart and the idempotency key so a retry is safe.
      setSaveError(friendlyError(e, "Couldn't save the bill. Please try again."));
    } finally {
      setSaving(false);
    }
  };

  const handleNewBill = () => {
    setSavedBill(null);
    setSaveError(null);
    resetForNewBill();
  };

  // ── Render ──────────────────────────────────────────────────────────────────
  if (savedBill) {
    return <SuccessView bill={savedBill} onNewBill={handleNewBill} customerPhone={customer.phone || undefined} />;
  }

  return (
    <div className="w-full">
      {loading ? (
        <div className="flex justify-center py-16">
          <Spinner className="h-8 w-8 text-primary-600" />
        </div>
      ) : loadError ? (
        <div className="py-12 text-center">
          <p className="text-base font-semibold text-danger">{loadError}</p>
          <Button
            variant="secondary"
            size="action"
            className="mt-4"
            onClick={() => window.location.reload()}
          >
            Try again
          </Button>
        </div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
          {/* Products Column */}
          <div className="lg:col-span-7 xl:col-span-8">
            <ProductGrid
              products={products}
              cartQty={cartQty}
              onPick={addUnit}
              onOpenScanner={() => setScannerOpen(true)}
              onOpenQuickAdd={() => setQuickAddOpen(true)}
            />
          </div>

          {/* Sticky Tablet Checkout Column */}
          <div className="hidden lg:block lg:col-span-5 xl:col-span-4 sticky top-6 bg-white border border-border rounded-2xl p-5 shadow-sm max-h-[80vh] overflow-y-auto">
            <h2 className="text-xl font-bold text-ink mb-4">Checkout Summary</h2>
            <CartForm
              totals={totals}
              onCheckout={handleCheckout}
              saving={saving}
              errorMsg={saveError}
              inline
            />
          </div>
        </div>
      )}

      {/* Cart bar is hidden on tablet/large screen, visible on mobile */}
      <div className="lg:hidden">
        <CartBar
          itemCount={itemCount}
          totalPaise={totals.totalPaise}
          onOpenCheckout={() => setCartOpen(true)}
          onOpenQuickAdd={() => setQuickAddOpen(true)}
          onOpenScanner={() => setScannerOpen(true)}
        />
      </div>

      <CartSheet
        open={cartOpen}
        onClose={() => setCartOpen(false)}
        totals={totals}
        onCheckout={handleCheckout}
        saving={saving}
        errorMsg={saveError}
      />

      <QuickAddSheet
        open={quickAddOpen}
        onClose={() => setQuickAddOpen(false)}
        onAdded={(p, qty) => {
          setProducts((prev) => [p, ...prev]);
          addUnit(p, qty);
        }}
      />

      <ScannerSheet
        open={scannerOpen}
        onClose={() => setScannerOpen(false)}
        products={products}
        onScanned={(p) => addUnit(p)}
      />
    </div>
  );
}
