import { useMemo, useState } from "react";
import type { Product } from "@/api/types";
import { formatINR, toPaise } from "@/lib/money";
import { ProductThumb } from "@/components/ProductThumb";
import { useBilling } from "@/store/billing";
import { Trash2, Plus, Minus, Search } from "lucide-react";
import { VoiceSearchButton } from "@/components/VoiceSearchButton";

interface ProductGridProps {
  products: Product[];
  /** quantity currently in the cart, keyed by product id (for the badge). */
  cartQty: Record<string, number>;
  onPick: (p: Product) => void;
  onOpenScanner?: () => void;
  onOpenQuickAdd?: () => void;
}

export function ProductGrid({ products, onOpenScanner, onOpenQuickAdd }: ProductGridProps) {
  const [query, setQuery] = useState("");
  const [category, setCategory] = useState<string | null>(null);

  const { lines, addUnit, setQuantity, setLinePrice, removeLine } = useBilling();

  const categories = useMemo(() => {
    const set = new Set<string>();
    for (const p of products) if (p.category) set.add(p.category);
    return Array.from(set).sort();
  }, [products]);

  const candidates = useMemo(() => {
    const names = products.map((p) => p.name);
    return [...names, ...categories];
  }, [products, categories]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return products.filter((p) => {
      if (category && p.category !== category) return false;
      if (q && !p.name.toLowerCase().includes(q)) return false;
      return true;
    });
  }, [products, query, category]);

  return (
    <div className="space-y-4">
      {/* Search & Actions Container */}
      <div className="flex gap-3 items-center">
        <div className="relative flex-1">
          <span className="absolute inset-y-0 left-0 flex items-center pl-3 pointer-events-none text-ink-soft">
            <Search className="h-5 w-5" />
          </span>
          <input
            type="search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search by item name, SKU ID or category"
            className="field pl-10 pr-12"
            aria-label="Search products"
          />
          <div className="absolute inset-y-0 right-0 flex items-center pr-2">
            <VoiceSearchButton
              candidates={candidates}
              onTranscript={(text) => {
                setQuery(text);
                setCategory(null);
              }}
            />
          </div>
        </div>

        {/* Tablet/Desktop shortcuts for Scanner and Quick Add */}
        {onOpenScanner && (
          <button
            type="button"
            onClick={onOpenScanner}
            className="hidden lg:flex h-11 px-4 items-center gap-2 rounded-xl border border-border bg-white font-bold text-ink hover:bg-slate-50 active:scale-95 shrink-0 transition-all text-sm"
            title="Scan barcode"
          >
            <Search className="h-4 w-4 text-ink-soft" />
            <span>Scan Barcode</span>
          </button>
        )}
        {onOpenQuickAdd && (
          <button
            type="button"
            onClick={onOpenQuickAdd}
            className="hidden lg:flex h-11 px-4 items-center gap-2 rounded-xl border border-border bg-white font-bold text-ink hover:bg-slate-50 active:scale-95 shrink-0 transition-all text-sm"
            title="Quick add custom product"
          >
            <Plus className="h-4 w-4 text-ink-soft" />
            <span>Quick Add</span>
          </button>
        )}
      </div>

      {/* Category Tabs */}
      {categories.length > 0 && (
        <div className="flex gap-2 overflow-x-auto pb-1 scrollbar-none">
          <Chip active={category === null} onClick={() => setCategory(null)}>
            All
          </Chip>
          {categories.map((c) => (
            <Chip key={c} active={category === c} onClick={() => setCategory(c)}>
              {c}
            </Chip>
          ))}
        </div>
      )}

      {/* Product List */}
      {filtered.length === 0 ? (
        <p className="mt-10 text-center text-base text-ink-soft">
          {products.length === 0
            ? "No products yet. Add products from the Products tab."
            : "No products match your search."}
        </p>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          {filtered.map((p) => {
            const line = lines.find((l) => l.product_id === p.id);
            const inCart = line !== undefined;
            // Blank while unfilled — no 0, no saved-price prefill.
            const qtyValue = line?.quantity ?? null;
            const qtyDisplay = qtyValue == null ? "" : String(qtyValue);
            const currentPrice = line?.unit_price ?? "";

            // SKU is the first segment of the UUID or index
            const sku = p.id.split("-")[0]?.toUpperCase() || p.id.slice(0, 8).toUpperCase();

            return (
              <div
                key={p.id}
                className="flex flex-col gap-3 rounded-2xl border border-border bg-white p-4 shadow-sm"
              >
                {/* Top Section */}
                <div className="flex items-start justify-between gap-3">
                  <div className="flex gap-3">
                    <ProductThumb
                      name={p.name}
                      photoUrl={p.photo_url}
                      className="h-16 w-16 min-w-[4rem] rounded-xl font-bold text-xl"
                    />
                    <div className="space-y-1">
                      <h3 className="text-base font-semibold leading-snug text-ink line-clamp-2">
                        {p.name}
                      </h3>
                      <p className="text-xs font-medium text-ink-soft">
                        SKU ID: {sku}
                      </p>
                    </div>
                  </div>
                  <div className="text-right">
                    <span className="text-base font-bold text-ink">
                      {formatINR(toPaise(p.retail_price))}
                    </span>
                    <p className="text-xs font-medium text-ink-soft">per unit</p>
                  </div>
                </div>

                {/* Bottom Section (Actions) */}
                <div>
                  {!inCart ? (
                    <button
                      type="button"
                      onClick={() => addUnit(p)}
                      className="flex h-[44px] w-full items-center justify-center gap-2 rounded-xl
                                 bg-emerald-50 text-emerald-700 font-bold transition-all border border-emerald-100
                                 active:scale-[0.98] hover:bg-emerald-100"
                    >
                      <Plus className="h-5 w-5 bg-emerald-700 text-white rounded-full p-0.5" />
                      Add item to bill
                    </button>
                  ) : (
                    <div className="flex items-center justify-between gap-4">
                      {/* Stepper */}
                      <div className="flex items-center gap-2">
                        <button
                          type="button"
                          onClick={() => setQuantity(p.id, Math.max(1, (qtyValue ?? 1) - 1))}
                          className="flex h-10 w-10 items-center justify-center rounded-full border border-border
                                     bg-white text-xl font-bold text-ink-soft hover:bg-surface-muted active:scale-95"
                          aria-label="Decrease quantity"
                        >
                          <Minus className="h-4 w-4" />
                        </button>
                        <input
                          type="text"
                          inputMode="numeric"
                          pattern="[0-9]*"
                          value={qtyDisplay}
                          placeholder="Qty"
                          onChange={(e) => {
                            const cleaned = e.target.value.replace(/[^0-9]/g, "");
                            if (cleaned === "") {
                              setQuantity(p.id, null); // blank stays blank, line kept
                              return;
                            }
                            const n = parseInt(cleaned, 10);
                            setQuantity(p.id, Number.isFinite(n) ? n : null);
                          }}
                          className="h-10 w-14 rounded-xl border border-border text-center text-base font-semibold text-ink placeholder:text-ink-soft/60 focus:outline-none"
                          aria-label="Quantity"
                        />
                        <button
                          type="button"
                          onClick={() => setQuantity(p.id, (qtyValue ?? 0) + 1)}
                          className="flex h-10 w-10 items-center justify-center rounded-full bg-emerald-500
                                     text-xl font-bold text-white hover:bg-emerald-600 active:scale-95"
                          aria-label="Increase quantity"
                        >
                          <Plus className="h-4 w-4" />
                        </button>
                      </div>

                      {/* Editable price & Trash */}
                      <div className="flex items-center gap-2 flex-1 justify-end">
                        <div className="flex h-10 w-24 items-center gap-1 rounded-xl border border-border bg-white px-2">
                          <span className="text-base font-semibold text-ink-soft">₹</span>
                          <input
                            type="text"
                            inputMode="decimal"
                            value={currentPrice}
                            onChange={(e) => setLinePrice(p.id, e.target.value.replace(/[^0-9.]/g, ""))}
                            placeholder="0.00"
                            className="w-full text-center text-base font-semibold text-ink outline-none"
                            aria-label={`Price for ${p.name}`}
                          />
                        </div>

                        <button
                          type="button"
                          onClick={() => removeLine(p.id)}
                          className="flex h-10 w-10 items-center justify-center rounded-xl border border-red-200
                                     bg-red-50 text-red-500 hover:bg-red-100 active:scale-95"
                          aria-label="Remove item"
                        >
                          <Trash2 className="h-4 w-4" />
                        </button>
                      </div>
                    </div>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

function Chip({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={[
        "h-tap shrink-0 rounded-full border-2 px-4 text-base font-semibold transition-colors",
        active
          ? "border-primary-600 bg-primary-600 text-white"
          : "border-border bg-surface text-ink-soft hover:border-border-strong",
      ].join(" ")}
    >
      {children}
    </button>
  );
}
