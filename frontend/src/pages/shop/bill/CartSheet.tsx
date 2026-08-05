import { BottomSheet } from "@/components/BottomSheet";
import { Button } from "@/components/Button";
import { formatINR, toPaise } from "@/lib/money";
import type { Totals } from "@/lib/money";
import { useBilling, allLinesFilled } from "@/store/billing";
import { CartForm } from "./CartForm";

interface CartSheetProps {
  open: boolean;
  onClose: () => void;
  totals: Totals;
  /** Save handler (page owns the API call + idempotency key). */
  onCheckout: () => void;
  saving: boolean;
  errorMsg: string | null;
}

export function CartSheet({ open, onClose, totals, onCheckout, saving, errorMsg }: CartSheetProps) {
  const { lines, payMethod, cash, upi, due, customer } = useBilling();

  const sumPaise = toPaise(cash) + toPaise(upi) + toPaise(due);
  const balanced = sumPaise === totals.totalPaise;
  const phoneValid = !customer.phone.trim() || customer.phone.replace(/\D/g, "").length === 10;
  const linesFilled = allLinesFilled(lines);
  const canSave = lines.length > 0 && linesFilled && payMethod !== null && balanced && !saving && phoneValid;

  return (
    <BottomSheet
      open={open}
      onClose={onClose}
      title="Checkout Summary"
      footer={
        <div className="space-y-3">
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
            <p className="text-center text-base font-semibold text-ink-soft">
              Enter a quantity and price for every item.
            </p>
          )}
          {linesFilled && !balanced && lines.length > 0 && payMethod !== null && (
            <p className="text-center text-base font-semibold text-ink-soft">
              Payments must add up to {formatINR(totals.totalPaise)}.
            </p>
          )}
        </div>
      }
    >
      <CartForm
        totals={totals}
        onCheckout={onCheckout}
        saving={saving}
        errorMsg={errorMsg}
      />
    </BottomSheet>
  );
}
