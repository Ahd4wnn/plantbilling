import type { BillDetail } from "@/api/sales";
import { formatINR, toPaise } from "@/lib/money";
import { formatDateTime } from "@/lib/datetime";

/**
 * The canonical in-app rendering of a bill (receipt-like, but app-styled — not
 * the thermal format). Reused by the Sales detail view and available for the
 * billing success screen, so reprint/reshare operate on identical data.
 */
export function BillReceipt({ bill }: { bill: BillDetail }) {
  const cash = toPaise(bill.cash_amount);
  const upi = toPaise(bill.upi_amount);
  const discount = toPaise(bill.discount_amount);
  const discountLabel =
    bill.discount_type === "percent"
      ? `Discount (${Number(bill.discount_value)}%)`
      : "Discount";

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="text-center">
        <h2 className="text-xl font-extrabold text-ink">{bill.business_name || bill.shop_name || "Bill"}</h2>
        {bill.business_address && (
          <p className="mt-1.5 text-base font-semibold text-ink-soft whitespace-pre-wrap leading-tight">{bill.business_address}</p>
        )}
        {bill.business_phone && (
          <p className="mt-1 text-base font-semibold text-ink-soft">Contact: {bill.business_phone}</p>
        )}
        <p className="mt-1 text-base text-ink-soft">{formatDateTime(bill.created_at)}</p>
        {bill.salesperson_email && (
          <p className="mt-1 text-sm font-semibold text-ink-soft">
            Billed by: <span className="text-ink">{bill.salesperson_email}</span>
          </p>
        )}
        <div className="mt-2 flex items-center justify-center gap-2">
          <span className="text-sm font-mono text-ink-soft">#{bill.bill_no ?? bill.id.slice(0, 8).toUpperCase()}</span>
        </div>
      </div>

      {/* Customer */}
      {bill.customer_name && (
        <div className="rounded-control bg-surface-muted px-4 py-3">
          <div className="text-sm font-semibold text-ink-soft">Customer</div>
          <div className="text-lg font-bold text-ink">{bill.customer_name}</div>
          {bill.customer_phone && <div className="text-base text-ink-soft">{bill.customer_phone}</div>}
        </div>
      )}

      {/* Remarks */}
      {bill.remarks && (
        <div className="rounded-control bg-surface-muted px-4 py-3">
          <div className="text-sm font-semibold text-ink-soft">Remarks</div>
          <div className="text-base font-medium text-ink break-words whitespace-pre-wrap">{bill.remarks}</div>
        </div>
      )}

      {/* Line items */}
      <div>
        <div className="mb-1 text-sm font-semibold uppercase tracking-wide text-ink-soft">Items</div>
        <ul className="divide-y divide-border">
          {bill.items.map((it, i) => (
            <li key={i} className="flex items-start justify-between gap-3 py-3">
              <div className="min-w-0 flex-1">
                <div className="text-lg font-semibold text-ink">{it.product_name}</div>
                <div className="text-base text-ink-soft">
                  {formatINR(toPaise(it.unit_price))} × {it.quantity}
                </div>
              </div>
              <div className="shrink-0 text-lg font-bold text-ink">{formatINR(toPaise(it.line_total))}</div>
            </li>
          ))}
        </ul>
      </div>

      {/* Totals */}
      <div className="space-y-2 border-t border-border pt-4">
        <Row label="Subtotal" value={formatINR(toPaise(bill.subtotal))} />
        {discount > 0 && <Row label={discountLabel} value={`− ${formatINR(discount)}`} />}
        <div className="flex items-center justify-between pt-1 text-2xl font-extrabold text-ink">
          <span>Total</span>
          <span>{formatINR(toPaise(bill.total))}</span>
        </div>
      </div>

      {/* Payment */}
      <div className="space-y-2 rounded-control bg-surface-muted px-4 py-3">
        <div className="text-sm font-semibold uppercase tracking-wide text-ink-soft">Payment</div>
        {cash > 0 && (
          <div className="flex items-center justify-between">
            <span className="text-lg font-semibold text-cash">Cash</span>
            <span className="text-lg font-bold text-ink">{formatINR(cash)}</span>
          </div>
        )}
        {upi > 0 && (
          <div className="flex items-center justify-between">
            <span className="text-lg font-semibold text-upi">UPI</span>
            <span className="text-lg font-bold text-ink">{formatINR(upi)}</span>
          </div>
        )}
        {bill.due_amount && toPaise(bill.due_amount) > 0 && (
          <div className="flex items-center justify-between">
            <span className="text-lg font-semibold text-danger">Due</span>
            <span className="text-lg font-bold text-ink">{formatINR(toPaise(bill.due_amount))}</span>
          </div>
        )}
        {cash === 0 && upi === 0 && (!bill.due_amount || toPaise(bill.due_amount) === 0) && (
          <div className="flex items-center justify-between">
            <span className="text-lg font-semibold text-ink-soft">Cash</span>
            <span className="text-lg font-bold text-ink">{formatINR(0)}</span>
          </div>
        )}
      </div>
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between text-base">
      <span className="text-ink-soft">{label}</span>
      <span className="font-semibold text-ink">{value}</span>
    </div>
  );
}
