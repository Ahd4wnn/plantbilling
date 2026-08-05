import { Button } from "@/components/Button";
import { formatINR, toPaise } from "@/lib/money";
import type { Totals } from "@/lib/money";
import { useBilling, allLinesFilled } from "@/store/billing";
import { DiscountSection } from "./DiscountSection";
import { CustomerSection } from "./CustomerSection";
import { PaymentSection } from "./PaymentSection";
import { Trash2 } from "lucide-react";

interface CartFormProps {
  totals: Totals;
  onCheckout: () => void;
  saving: boolean;
  errorMsg: string | null;
  inline?: boolean;
}

export function CartForm({ totals, onCheckout, saving, errorMsg, inline = false }: CartFormProps) {
  const {
    lines,
    removeLine,
    discountType,
    discountValue,
    setDiscount,
    payMethod,
    cash,
    upi,
    due,
    setPayMethod,
    setCash,
    setUpi,
    setDue,
    customer,
    setCustomer,
    remarks,
    setRemarks,
  } = useBilling();

  const sumPaise = toPaise(cash) + toPaise(upi) + toPaise(due);
  const balanced = sumPaise === totals.totalPaise;
  const phoneValid = !customer.phone.trim() || customer.phone.replace(/\D/g, "").length === 10;
  const linesFilled = allLinesFilled(lines);
  const canSave = lines.length > 0 && linesFilled && payMethod !== null && balanced && !saving && phoneValid;

  return (
    <div className={`space-y-6 ${inline ? "" : "pb-6"}`}>
      {lines.length === 0 ? (
        <p className="py-10 text-center text-base text-ink-soft">
          Your cart is empty. Add items to the bill.
        </p>
      ) : (
        <div className="space-y-6">
          {/* Read-only Items Review */}
          <div className="space-y-2">
            <h3 className="text-base font-semibold text-ink-soft">Items Review</h3>
            <ul className="divide-y divide-border rounded-2xl border border-border bg-surface px-4 py-2 max-h-48 overflow-y-auto">
              {lines.map((l) => {
                const incomplete = l.quantity == null || l.quantity < 1 || l.unit_price.trim() === "";
                const lineTotal = toPaise(l.unit_price) * (l.quantity ?? 0);
                return (
                  <li key={l.product_id} className="flex items-center justify-between py-2 text-base font-semibold text-ink">
                    <span className="min-w-0 flex-1 truncate">
                      {l.product_name}{" "}
                      {incomplete ? (
                        <span className="ml-1 text-sm font-semibold text-danger">enter qty &amp; price</span>
                      ) : (
                        <span className="text-ink-soft ml-1">x{l.quantity}</span>
                      )}
                    </span>
                    <div className="flex items-center gap-3">
                      <span className="tnum shrink-0">
                        {incomplete ? "—" : formatINR(lineTotal)}
                      </span>
                      <button
                        type="button"
                        onClick={() => removeLine(l.product_id)}
                        className="flex h-8 w-8 items-center justify-center rounded-lg border border-red-200
                                   bg-red-50 text-red-500 hover:bg-red-100 active:scale-95"
                        aria-label={`Remove ${l.product_name}`}
                      >
                        <Trash2 className="h-4 w-4" />
                      </button>
                    </div>
                  </li>
                );
              })}
            </ul>
          </div>

          {/* Totals Summary */}
          <div className="space-y-1 rounded-2xl bg-surface-muted px-4 py-3 border border-border">
            <div className="flex items-center justify-between text-base text-ink-soft">
              <span>Subtotal</span>
              <span className="font-semibold text-ink">{formatINR(totals.subtotalPaise)}</span>
            </div>
            {totals.discountPaise > 0 && (
              <div className="flex items-center justify-between text-base text-ink-soft">
                <span>Discount</span>
                <span className="font-semibold text-ink">− {formatINR(totals.discountPaise)}</span>
              </div>
            )}
            <div className="flex items-center justify-between pt-1 text-xl font-extrabold text-ink">
              <span>Total</span>
              <span className="tnum">{formatINR(totals.totalPaise)}</span>
            </div>
          </div>

          <DiscountSection
            type={discountType}
            value={discountValue}
            discountPaise={totals.discountPaise}
            capped={totals.discountCapped}
            onChange={setDiscount}
          />

          <CustomerSection value={customer} onChange={setCustomer} />

          <div>
            <span className="mb-2 block text-base font-semibold text-ink">
              Remarks <span className="font-normal text-ink-soft">(optional)</span>
            </span>
            <input
              type="text"
              value={remarks}
              onChange={(e) => setRemarks(e.target.value)}
              placeholder="Add remarks or notes about this bill"
              className="field"
              aria-label="Bill remarks"
              autoComplete="off"
            />
          </div>

          <PaymentSection
            totalPaise={totals.totalPaise}
            method={payMethod}
            cash={cash}
            upi={upi}
            due={due}
            setMethod={setPayMethod}
            setCash={setCash}
            setUpi={setUpi}
            setDue={setDue}
          />

          {/* Action Buttons inside inline cart form */}
          {inline && (
            <div className="space-y-3 pt-4 border-t border-border">
              {errorMsg && (
                <p className="rounded-control bg-danger-soft px-4 py-3 text-base font-semibold text-danger" role="alert">
                  {errorMsg}
                </p>
              )}
              <Button
                variant="primary"
                size="action"
                className="w-full font-bold"
                disabled={!canSave}
                loading={saving}
                loadingLabel="Saving bill…"
                onClick={onCheckout}
              >
                Save Bill · {formatINR(totals.totalPaise)}
              </Button>
              {!linesFilled && lines.length > 0 && (
                <p className="text-center text-sm font-semibold text-ink-soft">
                  Enter a quantity and price for every item.
                </p>
              )}
              {linesFilled && !balanced && lines.length > 0 && payMethod !== null && (
                <p className="text-center text-sm font-semibold text-ink-soft">
                  Payments must add up to {formatINR(totals.totalPaise)}.
                </p>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
