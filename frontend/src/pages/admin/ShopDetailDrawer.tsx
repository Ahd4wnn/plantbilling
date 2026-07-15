import { useEffect, useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import { X, Pencil, KeyRound, Power, Trash2, Package, Users, Contact } from "lucide-react";
import { getAdminShopDetail, type AdminShopDetail } from "@/api/adminAnalytics";
import { friendlyError } from "@/api/client";
import { Spinner } from "@/components/Spinner";
import type { ShopRow } from "@/api/admin";

function dateTime(iso: string): string {
  return new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", hour: "numeric", minute: "2-digit" }).format(new Date(iso));
}
function dateOnly(iso: string): string {
  return new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", year: "numeric" }).format(new Date(iso));
}
function fromNow(iso: string | null): string {
  if (!iso) return "Never billed";
  const days = Math.floor((Date.now() - new Date(iso).getTime()) / 86_400_000);
  if (days <= 0) return "Active today";
  if (days === 1) return "1 day ago";
  if (days < 30) return `${days} days ago`;
  return dateOnly(iso);
}

interface Props {
  shop: ShopRow | null;
  onClose: () => void;
  onEditDetails: (s: ShopRow) => void;
  onReset: (s: ShopRow) => void;
  onToggle: (s: ShopRow) => void;
  onDelete: (s: ShopRow) => void;
}

/** Right-side slide-over: one shop's account, setup + engagement (no finances) + actions. */
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
            <div className="flex items-center justify-between gap-2 border-b border-border bg-white px-4 py-3.5">
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <span className={["h-2.5 w-2.5 shrink-0 rounded-full", shop.is_active ? "bg-success" : "bg-slate-300"].join(" ")} />
                  <span className="truncate text-lg font-extrabold tracking-tight text-ink">{shop.name}</span>
                </div>
                <div className="truncate text-sm font-medium text-ink-soft">{shop.owner_email ?? "No manager"}</div>
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
                  {/* Engagement */}
                  <div className="grid grid-cols-2 gap-3">
                    <Stat label="Bills · 7 days" value={detail.bills_7} accent="text-primary-700" />
                    <Stat label="Bills · 30 days" value={detail.bills_30} accent="text-ink" />
                  </div>
                  <div className="rounded-xl border border-slate-100 bg-slate-50 px-4 py-3">
                    <div className="text-xs font-bold uppercase tracking-wider text-ink-soft">Last active</div>
                    <div className="mt-0.5 text-lg font-extrabold tracking-tight text-ink">{fromNow(detail.last_bill_at)}</div>
                  </div>

                  {/* Catalogue setup */}
                  <div className="grid grid-cols-3 gap-3">
                    <IconStat icon={<Package className="h-4 w-4" />} label="Products" value={detail.products_count} />
                    <IconStat icon={<Users className="h-4 w-4" />} label="Staff" value={detail.staff_count} />
                    <IconStat icon={<Contact className="h-4 w-4" />} label="Customers" value={detail.customers_count} />
                  </div>

                  {/* Recent activity (no amounts) */}
                  <Section title="Recent activity">
                    {detail.recent_activity.length === 0 ? (
                      <p className="rounded-card border border-border bg-white p-3 text-ink-soft">No bills yet.</p>
                    ) : (
                      <div className="divide-y divide-border rounded-card border border-border bg-white">
                        {detail.recent_activity.map((a, i) => (
                          <div key={i} className="flex items-center justify-between px-3 py-2">
                            <span className="min-w-0">
                              <span className="block truncate text-ink">{a.salesperson_email ?? "—"}</span>
                              <span className="block text-xs text-ink-soft">{dateTime(a.created_at)}</span>
                            </span>
                            <span className="shrink-0 text-sm text-ink-soft">{a.item_count} {a.item_count === 1 ? "item" : "items"}</span>
                          </div>
                        ))}
                      </div>
                    )}
                  </Section>

                  {/* Account */}
                  <Section title="Account">
                    <div className="space-y-1 rounded-card border border-border bg-white p-3 text-sm">
                      <Row label="Status" value={detail.is_active ? "Active" : "Inactive"} />
                      <Row label="Onboarded" value={dateOnly(detail.created_at)} />
                      <Row label="Manager" value={detail.owner_email} />
                      <Row label="Owners" value={detail.owner_emails.length ? detail.owner_emails.join(", ") : "—"} />
                    </div>
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
            <div className="grid grid-cols-2 gap-2.5 border-t border-border bg-white p-3">
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

function Stat({ label, value, accent = "text-ink" }: { label: string; value: number; accent?: string }) {
  return (
    <div className="rounded-xl border border-slate-100 bg-slate-50 p-4">
      <div className="text-xs font-bold uppercase tracking-wider text-ink-soft">{label}</div>
      <div className={["mt-0.5 text-2xl font-extrabold tracking-tight", accent].join(" ")}>{value}</div>
    </div>
  );
}
function IconStat({ icon, label, value }: { icon: React.ReactNode; label: string; value: number }) {
  return (
    <div className="rounded-xl border border-slate-100 bg-slate-50 p-3 text-center">
      <div className="mx-auto flex h-8 w-8 items-center justify-center rounded-full bg-white text-ink-soft">{icon}</div>
      <div className="mt-1 text-lg font-extrabold tracking-tight text-ink">{value}</div>
      <div className="text-xs font-medium text-ink-soft">{label}</div>
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
    <button type="button" onClick={onClick} className={["flex h-11 items-center justify-center gap-1.5 rounded-control border text-sm font-bold", toneClass].join(" ")}>
      {icon}{children}
    </button>
  );
}
