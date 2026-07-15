import { useEffect, useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import { X, Pencil, KeyRound, Power, Trash2 } from "lucide-react";
import { getAdminShopDetail, type AdminShopDetail } from "@/api/adminAnalytics";
import { friendlyError } from "@/api/client";
import { Spinner } from "@/components/Spinner";
import type { ShopRow } from "@/api/admin";

function inr(v: string): string {
  const n = Number(v);
  return "₹" + (isFinite(n) ? n : 0).toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}
function dateTime(iso: string): string {
  return new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", hour: "numeric", minute: "2-digit" }).format(new Date(iso));
}

interface Props {
  shop: ShopRow | null;
  onClose: () => void;
  onEditDetails: (s: ShopRow) => void;
  onReset: (s: ShopRow) => void;
  onToggle: (s: ShopRow) => void;
  onDelete: (s: ShopRow) => void;
}

/** Right-side slide-over: one shop's analytics, recent bills, details + actions. */
export function ShopDetailDrawer({ shop, onClose, onEditDetails, onReset, onToggle, onDelete }: Props) {
  const [detail, setDetail] = useState<AdminShopDetail | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!shop) return;
    let alive = true;
    setDetail(null);
    setLoading(true);
    setError(null);
    getAdminShopDetail(shop.id)
      .then((d) => alive && setDetail(d))
      .catch((e) => alive && setError(friendlyError(e, "Couldn't load shop details.")))
      .finally(() => alive && setLoading(false));
    return () => { alive = false; };
  }, [shop?.id]); // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <AnimatePresence>
      {shop && (
        <motion.div
          className="fixed inset-0 z-[75] flex justify-end bg-ink/40"
          initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
          onClick={onClose}
        >
          <motion.aside
            className="flex h-full w-full max-w-md flex-col bg-surface-muted shadow-card-lg"
            initial={{ x: "100%" }} animate={{ x: 0 }} exit={{ x: "100%" }}
            transition={{ type: "tween", duration: 0.22 }}
            onClick={(e) => e.stopPropagation()}
          >
            {/* Header */}
            <div className="flex items-center justify-between gap-2 border-b border-border bg-white px-4 py-3">
              <div className="min-w-0">
                <div className="truncate text-lg font-extrabold text-ink">{shop.name}</div>
                <div className="text-sm text-ink-soft">{shop.owner_email ?? "No manager"}</div>
              </div>
              <button type="button" onClick={onClose} aria-label="Close" className="rounded-control border border-border p-2 text-ink hover:bg-surface-muted">
                <X className="h-5 w-5" />
              </button>
            </div>

            <div className="flex-1 overflow-y-auto p-4">
              {loading && <div className="flex justify-center py-16"><Spinner className="h-8 w-8 text-primary-600" /></div>}
              {error && !loading && <p className="py-8 text-center font-semibold text-danger">{error}</p>}

              {detail && !loading && (
                <div className="space-y-4">
                  {/* KPIs (last 30 days) */}
                  <div className="grid grid-cols-2 gap-2">
                    <Stat label="Sales · 30d" value={inr(detail.report.total_sales)} accent="text-primary-700" />
                    <Stat label="Bills · 30d" value={detail.report.bill_count.toLocaleString("en-IN")} accent="text-ink" />
                    <Stat label="Cash in hand" value={inr(detail.cash_in_hand_running)} accent="text-emerald-700" />
                    <Stat label="Staff" value={String(detail.staff_count)} accent="text-ink" />
                  </div>

                  <div className="grid grid-cols-3 gap-2">
                    <Stat label="Cash" value={inr(detail.report.cash_total)} small />
                    <Stat label="UPI" value={inr(detail.report.upi_total)} small />
                    <Stat label="Due" value={inr(detail.report.due_total)} small />
                  </div>

                  {/* Top products */}
                  {detail.report.top_products.length > 0 && (
                    <Section title="Top products · 30d">
                      <div className="divide-y divide-border rounded-card border border-border bg-white">
                        {detail.report.top_products.slice(0, 5).map((p, i) => (
                          <div key={i} className="flex items-center justify-between px-3 py-2">
                            <span className="min-w-0 truncate text-ink">{p.product_name}</span>
                            <span className="shrink-0 text-sm text-ink-soft">{p.quantity} · {inr(p.total_sales)}</span>
                          </div>
                        ))}
                      </div>
                    </Section>
                  )}

                  {/* Recent bills */}
                  <Section title="Recent bills">
                    {detail.recent_bills.length === 0 ? (
                      <p className="rounded-card border border-border bg-white p-3 text-ink-soft">No bills yet.</p>
                    ) : (
                      <div className="divide-y divide-border rounded-card border border-border bg-white">
                        {detail.recent_bills.map((b) => (
                          <div key={b.id} className="flex items-center justify-between px-3 py-2">
                            <span className="min-w-0">
                              <span className="block truncate text-ink">{b.customer_name ?? "Walk-in"}</span>
                              <span className="block text-xs text-ink-soft">{dateTime(b.created_at)} · {b.payment_method}</span>
                            </span>
                            <span className="shrink-0 font-semibold text-ink">{inr(b.total)}</span>
                          </div>
                        ))}
                      </div>
                    )}
                  </Section>

                  {/* Business details */}
                  <Section title="Business details">
                    <div className="space-y-1 rounded-card border border-border bg-white p-3 text-sm">
                      <Row label="Business name" value={detail.business_name} />
                      <Row label="Address" value={detail.business_address} />
                      <Row label="Phone" value={detail.business_phone} />
                      <Row label="Email" value={detail.business_email} />
                      <Row label="UPI" value={detail.business_upi} />
                    </div>
                  </Section>
                </div>
              )}
            </div>

            {/* Actions */}
            <div className="grid grid-cols-2 gap-2 border-t border-border bg-white p-3">
              <ActionBtn onClick={() => onEditDetails(shop)} icon={<Pencil className="h-4 w-4" />}>Business details</ActionBtn>
              <ActionBtn onClick={() => onReset(shop)} icon={<KeyRound className="h-4 w-4" />}>Reset password</ActionBtn>
              <ActionBtn onClick={() => onToggle(shop)} icon={<Power className="h-4 w-4" />} tone={shop.is_active ? "danger" : "success"}>
                {shop.is_active ? "Deactivate" : "Activate"}
              </ActionBtn>
              <ActionBtn onClick={() => onDelete(shop)} icon={<Trash2 className="h-4 w-4" />} tone="danger">Delete</ActionBtn>
            </div>
          </motion.aside>
        </motion.div>
      )}
    </AnimatePresence>
  );
}

function Stat({ label, value, accent = "text-ink", small }: { label: string; value: string; accent?: string; small?: boolean }) {
  return (
    <div className="rounded-card border border-border bg-white p-3">
      <div className="text-xs font-medium uppercase tracking-wide text-ink-soft">{label}</div>
      <div className={[small ? "text-base" : "text-lg", "font-bold", accent].join(" ")}>{value}</div>
    </div>
  );
}
function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section>
      <h3 className="mb-1.5 text-sm font-bold uppercase tracking-wide text-ink-soft">{title}</h3>
      {children}
    </section>
  );
}
function Row({ label, value }: { label: string; value: string | null }) {
  return (
    <div className="flex justify-between gap-3">
      <span className="text-ink-soft">{label}</span>
      <span className="min-w-0 truncate text-right font-medium text-ink">{value || "—"}</span>
    </div>
  );
}
function ActionBtn({ onClick, icon, children, tone = "neutral" }: { onClick: () => void; icon: React.ReactNode; children: React.ReactNode; tone?: "neutral" | "danger" | "success" }) {
  const toneClass = tone === "danger" ? "text-danger border-danger-soft hover:bg-danger-soft" : tone === "success" ? "text-success border-border-strong hover:bg-success-soft" : "text-ink border-border-strong hover:bg-surface-muted";
  return (
    <button type="button" onClick={onClick} className={["flex items-center justify-center gap-1.5 rounded-control border px-3 py-2.5 text-sm font-semibold", toneClass].join(" ")}>
      {icon}{children}
    </button>
  );
}
