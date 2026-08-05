import { create } from "zustand";
import type { Product } from "@/api/types";
import type { DiscountType } from "@/lib/money";

export type PayMethod = "cash" | "upi" | "split" | "due";

export interface CartLine {
  product_id: string;
  // Per-unit, decimal string. Starts BLANK ("") on add — the operator enters the
  // size-based price for every line (no saved-price prefill).
  unit_price: string;
  product_name: string;
  // Starts BLANK (null) on a manual add. null means "not filled in yet" — the line
  // stays in the cart but the bill can't be saved until it has a real quantity.
  quantity: number | null;
  photo_url: string | null;
}

/** True once every line has a real quantity (≥1) and a non-empty price, so the
 *  bill is safe to save. Blank lines keep Save disabled (they are never dropped). */
export function allLinesFilled(lines: CartLine[]): boolean {
  return lines.every(
    (l) => l.quantity != null && l.quantity >= 1 && l.unit_price.trim() !== "",
  );
}

/** Customer is entered fresh per bill and optional (blank name = no customer). */
export interface CustomerInput {
  name: string;
  phone: string;
}

interface BillingState {
  lines: CartLine[];
  discountType: DiscountType;
  discountValue: string;
  payMethod: PayMethod | null;
  cash: string;
  upi: string;
  due: string;
  customer: CustomerInput;
  remarks: string;
  /** Generated when the user first attempts to save; reused for retries of THIS cart. */
  idempotencyKey: string | null;

  /** Tap a product: add a BLANK line (or +qty if already in the cart, or when an
   *  explicit quantity is supplied — scanner/quick-add). */
  addUnit: (p: Product, quantity?: number) => void;
  setQuantity: (productId: string, quantity: number | null) => void;
  setLinePrice: (productId: string, unitPrice: string) => void;
  removeLine: (productId: string) => void;
  lineFor: (productId: string) => CartLine | undefined;

  setDiscount: (type: DiscountType, value: string) => void;
  setPayMethod: (m: PayMethod) => void;
  setCash: (v: string) => void;
  setUpi: (v: string) => void;
  setDue: (v: string) => void;
  setCustomer: (c: CustomerInput) => void;
  setRemarks: (v: string) => void;

  ensureIdempotencyKey: () => string;
  resetForNewBill: () => void;
}

function freshKey(): string {
  // Prefer the platform UUID; fall back for very old webviews.
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) return crypto.randomUUID();
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

const initial = {
  lines: [] as CartLine[],
  discountType: "flat" as DiscountType,
  discountValue: "",
  payMethod: null as PayMethod | null,
  cash: "",
  upi: "",
  due: "",
  customer: { name: "", phone: "" } as CustomerInput,
  remarks: "",
  idempotencyKey: null as string | null,
};

export const useBilling = create<BillingState>((set, get) => ({
  ...initial,

  addUnit: (p, quantity) =>
    set((s) => {
      const existing = s.lines.find((l) => l.product_id === p.id);
      if (existing) {
        // Tap/scan again: bump the quantity. A blank (null) counts as 0.
        const base = existing.quantity ?? 0;
        return {
          lines: s.lines.map((l) =>
            l.product_id === p.id ? { ...l, quantity: base + (quantity ?? 1) } : l,
          ),
        };
      }
      return {
        lines: [
          ...s.lines,
          {
            product_id: p.id,
            product_name: p.name,
            unit_price: "", // blank — the operator enters the size-based price
            // Blank on a plain add; honour an explicit quantity (scanner/quick-add).
            quantity: quantity ?? null,
            photo_url: p.photo_url,
          },
        ],
      };
    }),

  // Set a line's quantity. A blank field is null (not removed); the trash button is
  // the only way to remove a line. Server still enforces qty ≥ 1 on save.
  setQuantity: (productId, quantity) =>
    set((s) => ({
      lines: s.lines.map((l) => (l.product_id === productId ? { ...l, quantity } : l)),
    })),

  setLinePrice: (productId, unitPrice) =>
    set((s) => ({
      lines: s.lines.map((l) =>
        l.product_id === productId ? { ...l, unit_price: unitPrice } : l,
      ),
    })),

  removeLine: (productId) =>
    set((s) => ({ lines: s.lines.filter((l) => l.product_id !== productId) })),

  lineFor: (productId) => get().lines.find((l) => l.product_id === productId),

  setDiscount: (type, value) => set({ discountType: type, discountValue: value }),
  setPayMethod: (m) => set({ payMethod: m }),
  setCash: (v) => set({ cash: v }),
  setUpi: (v) => set({ upi: v }),
  setDue: (v) => set({ due: v }),
  setCustomer: (c) => set({ customer: c }),
  setRemarks: (v) => set({ remarks: v }),

  ensureIdempotencyKey: () => {
    const existing = get().idempotencyKey;
    if (existing) return existing;
    const key = freshKey();
    set({ idempotencyKey: key });
    return key;
  },

  // Clear everything and start a brand-new bill (fresh idempotency key on save).
  resetForNewBill: () => set({ ...initial }),
}));
