/**
 * Money helpers. All internal math is done in integer paise to avoid floating
 * point error; values cross the API boundary as 2-decimal strings (e.g. "120.00").
 */

const inr = new Intl.NumberFormat("en-IN", {
  style: "currency",
  currency: "INR",
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

/** Parse a user/string amount ("1,234.5", "120.00", 120) into integer paise. */
export function toPaise(input: string | number | null | undefined): number {
  if (input === null || input === undefined) return 0;
  const cleaned = String(input).replace(/[^0-9.]/g, "");
  if (!cleaned) return 0;
  const [rupeesRaw, fracRaw = ""] = cleaned.split(".");
  const rupees = parseInt(rupeesRaw || "0", 10);
  const frac = parseInt((fracRaw + "00").slice(0, 2) || "0", 10);
  return rupees * 100 + frac;
}

/** Integer paise → a 2-decimal string suitable for the API ("123.45"). */
export function fromPaise(paise: number): string {
  const p = Math.max(0, Math.round(paise));
  const rupees = Math.floor(p / 100);
  const frac = p % 100;
  return `${rupees}.${String(frac).padStart(2, "0")}`;
}

/** Integer paise → display currency with Indian grouping ("₹1,234.50"). */
export function formatINR(paise: number): string {
  return inr.format(Math.round(paise) / 100);
}

export type DiscountType = "flat" | "percent";

export interface Totals {
  subtotalPaise: number;
  discountPaise: number;
  totalPaise: number;
  /** True if the entered discount had to be capped (exceeded subtotal / 100%). */
  discountCapped: boolean;
}

/**
 * Mirror of the server's money math so the UI can show the total live.
 * The server remains the source of truth; we send the same (capped) values.
 */
export function computeTotals(
  lines: { unitPrice: string; quantity: number | null }[],
  discountType: DiscountType,
  discountValue: string,
): Totals {
  let subtotalPaise = 0;
  for (const l of lines) {
    // Quantity may be blank (null) while the operator is still filling the line in.
    subtotalPaise += toPaise(l.unitPrice) * Math.max(0, Math.floor(l.quantity ?? 0));
  }

  let discountPaise = 0;
  let discountCapped = false;
  if (discountType === "percent") {
    const pct = Math.min(100, Math.max(0, parseFloat(discountValue || "0") || 0));
    if ((parseFloat(discountValue || "0") || 0) > 100) discountCapped = true;
    discountPaise = Math.round((subtotalPaise * pct) / 100);
  } else {
    const enteredPaise = toPaise(discountValue);
    if (enteredPaise > subtotalPaise) discountCapped = true;
    discountPaise = Math.min(enteredPaise, subtotalPaise);
  }

  return {
    subtotalPaise,
    discountPaise,
    totalPaise: Math.max(0, subtotalPaise - discountPaise),
    discountCapped,
  };
}
