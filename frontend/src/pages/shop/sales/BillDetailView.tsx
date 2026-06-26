import { useEffect, useState } from "react";
import { AnimatePresence, motion, useReducedMotion } from "motion/react";
import { ChevronLeft, Printer, FileText } from "lucide-react";
import { useBluetoothPrinter } from "@/store/bluetooth";
import { fetchBillDetail, deleteBill, type BillDetail } from "@/api/sales";
import { resendBillWhatsApp } from "@/api/billing";
import { friendlyError, getMediaUrl } from "@/api/client";
import { Spinner } from "@/components/Spinner";
import { Button } from "@/components/Button";
import { BillReceipt } from "./BillReceipt";
import { useAuth } from "@/store/auth";
import { EditBillSheet } from "./EditBillSheet";
import { ConfirmDialog } from "@/components/ConfirmDialog";

interface BillDetailViewProps {
  billId: string | null;
  onClose: () => void;
  onUpdated?: () => void;
}

/**
 * Full-screen bill detail that slides in over the Sales tab. Self-contained:
 * fetches the bill, renders the shared receipt, and hosts the (placeholder)
 * Print / WhatsApp actions that later prompts will wire up.
 */
export function BillDetailView({ billId, onClose, onUpdated }: BillDetailViewProps) {
  const reduce = useReducedMotion();
  const [bill, setBill] = useState<BillDetail | null>(null);
  const [printing, setPrinting] = useState(false);
  const [sendingWa, setSendingWa] = useState(false);
  const { status } = useBluetoothPrinter();

  const handleShareWhatsApp = async () => {
    if (!bill) return;
    setSendingWa(true);
    try {
      const res = await resendBillWhatsApp(bill.id);
      alert(res.detail || "WhatsApp invoice queued for sending!");
      // Reload details immediately
      const detail = await fetchBillDetail(bill.id);
      setBill(detail);
    } catch (err) {
      alert(friendlyError(err, "Failed to share on WhatsApp."));
    } finally {
      setSendingWa(false);
    }
  };

  const renderWhatsAppBadge = (status: string) => {
    let classes = "text-slate-500 bg-slate-100";
    let text = "Not Sent";

    switch (status) {
      case "queued":
        classes = "text-amber-700 bg-amber-50 animate-pulse";
        text = "Queued";
        break;
      case "sending":
        classes = "text-blue-700 bg-blue-50 animate-pulse";
        text = "Sending";
        break;
      case "sent":
        classes = "text-emerald-600 bg-emerald-50";
        text = "Sent";
        break;
      case "delivered":
        classes = "text-emerald-700 bg-emerald-100";
        text = "Delivered";
        break;
      case "read":
        classes = "text-emerald-800 bg-emerald-200";
        text = "Read";
        break;
      case "failed":
        classes = "text-red-700 bg-red-50";
        text = "Failed";
        break;
    }

    return (
      <span className={`px-2.5 py-0.5 rounded-full text-xs font-bold ${classes}`}>
        {text}
      </span>
    );
  };

  const handlePrint = async () => {
    if (!bill) return;
    setPrinting(true);
    try {
      const printerStore = useBluetoothPrinter.getState();
      if (printerStore.status !== "connected") {
        const pref = printerStore.preferredConnectionType;
        if (pref === "serial") {
          await printerStore.connectSerial();
        } else if (pref === "usb") {
          await printerStore.connectUsb();
        } else {
          await printerStore.connect();
        }
      }
      if (useBluetoothPrinter.getState().status === "connected") {
        await useBluetoothPrinter.getState().printReceipt(bill);
      }
    } catch (err) {
      alert(err instanceof Error ? err.message : "Failed to print receipt.");
    } finally {
      setPrinting(false);
    }
  };
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notFound, setNotFound] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [deleteConfirm, setDeleteConfirm] = useState(false);
  const [deleting, setDeleting] = useState(false);

  const user = useAuth((s) => s.user);
  const canEdit = user?.role === "manager" || user?.role === "admin";
  const canDelete = user?.role === "admin";

  useEffect(() => {
    if (!billId) return;
    let cancelled = false;
    setBill(null);
    setError(null);
    setNotFound(false);
    setLoading(true);
    (async () => {
      try {
        const data = await fetchBillDetail(billId);
        if (!cancelled) setBill(data);
      } catch (e) {
        if (cancelled) return;
        const status = (e as { response?: { status?: number } })?.response?.status;
        if (status === 404) setNotFound(true);
        else setError(friendlyError(e, "Couldn't load this bill."));
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [billId]);

  // Polling WhatsApp status if queued or sending
  useEffect(() => {
    if (!billId || !bill) return;
    const transientStates = ["queued", "sending"];
    if (!transientStates.includes(bill.whatsapp_status || "")) return;

    const timer = setInterval(async () => {
      try {
        const detail = await fetchBillDetail(billId);
        if (detail.whatsapp_status !== bill.whatsapp_status) {
          setBill(detail);
        }
      } catch (err) {
        // Silently fail polling
      }
    }, 3000);

    return () => clearInterval(timer);
  }, [billId, bill]);

  // Close on Escape.
  useEffect(() => {
    if (!billId) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [billId, onClose]);

  const handleBillUpdated = (updatedBill: BillDetail) => {
    setBill(updatedBill);
    if (onUpdated) onUpdated();
  };

  const handleDelete = async () => {
    if (!billId) return;
    setDeleting(true);
    try {
      await deleteBill(billId);
      setDeleteConfirm(false);
      if (onUpdated) onUpdated();
      onClose();
    } catch (e) {
      alert(friendlyError(e, "Failed to delete the bill."));
    } finally {
      setDeleting(false);
    }
  };

  return (
    <AnimatePresence>
      {billId && (
        <motion.div
          className="fixed inset-0 z-50 flex flex-col bg-surface-muted"
          initial={reduce ? { opacity: 0 } : { x: "100%" }}
          animate={reduce ? { opacity: 1 } : { x: 0 }}
          exit={reduce ? { opacity: 0 } : { x: "100%" }}
          transition={{ type: "tween", duration: 0.22, ease: "easeOut" }}
          role="dialog"
          aria-modal="true"
          aria-label="Bill detail"
        >
          {/* Top bar */}
          <div className="flex items-center gap-2 border-b border-border bg-surface px-3 py-3 pt-[max(0.75rem,env(safe-area-inset-top))]">
            <button
              type="button"
              onClick={onClose}
              className="flex h-tap items-center gap-1 rounded-control px-3 text-base font-semibold text-primary-700 hover:bg-surface-muted"
            >
              <ChevronLeft className="h-5 w-5" /> Back
            </button>
            <h1 className="text-lg font-bold text-ink">Bill</h1>
          </div>

          {/* Body */}
          <div className="mx-auto w-full max-w-screen-sm flex-1 overflow-y-auto px-4 py-5">
            {loading ? (
              <div className="flex justify-center py-20">
                <Spinner className="h-8 w-8 text-primary-600" />
              </div>
            ) : notFound ? (
              <div className="py-20 text-center">
                <p className="text-xl font-bold text-ink">Bill not found</p>
                <p className="mt-1 text-base text-ink-soft">It may have been removed.</p>
                <Button variant="secondary" size="action" className="mt-6 max-w-xs" onClick={onClose}>
                  Back to Sales
                </Button>
              </div>
            ) : error ? (
              <div className="py-20 text-center">
                <p className="text-base font-semibold text-danger">{error}</p>
                <Button variant="secondary" size="action" className="mt-4 max-w-xs" onClick={onClose}>
                  Back to Sales
                </Button>
              </div>
            ) : bill ? (
              <div className="space-y-4">
                <div className="rounded-card border border-border bg-surface p-5 shadow-card">
                  <BillReceipt bill={bill} />
                </div>

                {/* WhatsApp Status Card */}
                {bill.customer_phone ? (
                  <div className="rounded-card border border-border bg-white p-5 shadow-sm space-y-3">
                    <div className="flex items-center justify-between">
                      <h3 className="font-bold text-ink text-base">WhatsApp Delivery</h3>
                      <div className="flex items-center gap-2">
                        {renderWhatsAppBadge(bill.whatsapp_status || "none")}
                      </div>
                    </div>

                    <div className="text-sm text-ink-soft leading-normal">
                      {bill.whatsapp_status === "queued" && "Invoice is in queue to be sent to WhatsApp."}
                      {bill.whatsapp_status === "sending" && "PDF invoice is being generated and uploaded."}
                      {bill.whatsapp_status === "sent" && `Message sent to ${bill.customer_phone}.`}
                      {bill.whatsapp_status === "delivered" && `Message delivered to ${bill.customer_phone}.`}
                      {bill.whatsapp_status === "read" && `Customer read the invoice message.`}
                      {bill.whatsapp_status === "failed" && (
                        <div className="text-danger bg-danger-soft border border-danger/10 rounded-xl p-3 font-semibold text-xs mt-1 space-y-1">
                          <p className="font-bold">Error details:</p>
                          <p>{bill.whatsapp_error || "Unknown network error."}</p>
                        </div>
                      )}
                      {(bill.whatsapp_status === "none" || !bill.whatsapp_status) && "No WhatsApp message sent yet."}
                    </div>

                    <div className="border-t border-border pt-3 flex flex-wrap gap-3">
                      {bill.invoice_url && (
                        <a
                          href={getMediaUrl(bill.invoice_url) || "#"}
                          target="_blank"
                          rel="noreferrer"
                          className="flex-1 min-w-[140px] text-center inline-flex items-center justify-center gap-2 h-10 rounded-xl border border-border text-sm font-semibold text-ink hover:bg-slate-50 active:scale-98"
                        >
                          <FileText className="h-4.5 w-4.5" />
                          View A4 PDF
                        </a>
                      )}
                      <Button
                        type="button"
                        variant="secondary"
                        size="tap"
                        className="flex-1 min-w-[140px] text-sm h-10 border-2 font-bold"
                        loading={sendingWa}
                        onClick={handleShareWhatsApp}
                      >
                        Resend Invoice
                      </Button>
                    </div>
                  </div>
                ) : (
                  <div className="rounded-card border border-border bg-slate-50 p-4 text-center text-xs text-ink-soft">
                    No phone number associated with this bill. WhatsApp delivery disabled.
                  </div>
                )}
              </div>
            ) : null}
          </div>

          {/* Actions */}
          {bill && !loading && (
            <div className="border-t border-border bg-surface px-4 py-4 pb-[max(1rem,env(safe-area-inset-bottom))]">
              <div className="mx-auto flex flex-col w-full max-w-screen-sm gap-3">
                {(canEdit || canDelete) && (
                  <div className="flex gap-3">
                    {canEdit && (
                      <Button
                        variant="primary"
                        size="action"
                        onClick={() => setEditOpen(true)}
                        className={`${canDelete ? "flex-1" : "w-full"} font-bold`}
                      >
                        Edit Bill
                      </Button>
                    )}
                    {canDelete && (
                      <Button
                        variant="ghost"
                        size="action"
                        onClick={() => setDeleteConfirm(true)}
                        className={`${canEdit ? "flex-1" : "w-full"} font-bold !text-danger hover:!bg-danger-soft border-2 border-danger/20 hover:border-danger/30`}
                      >
                        Delete Bill
                      </Button>
                    )}
                  </div>
                )}
                <div className="grid grid-cols-2 gap-3">
                  <Button
                    variant="secondary"
                    size="action"
                    className="font-bold flex items-center justify-center gap-2 border-2 bg-white"
                    loading={printing}
                    onClick={handlePrint}
                  >
                    <Printer className="h-5 w-5" />
                    {status === "connected" ? "Print" : "Connect & Print"}
                  </Button>
                  <Button
                    variant="secondary"
                    size="action"
                    className="font-bold flex items-center justify-center gap-2 border-2 bg-white"
                    loading={sendingWa}
                    onClick={handleShareWhatsApp}
                  >
                    WhatsApp
                  </Button>
                </div>
              </div>
            </div>
          )}

          {bill && (
            <EditBillSheet
              open={editOpen}
              onClose={() => setEditOpen(false)}
              bill={bill}
              onUpdated={handleBillUpdated}
            />
          )}

          {bill && (
            <ConfirmDialog
              open={deleteConfirm}
              title="Delete Bill"
              body="Are you sure you want to delete this bill? This action is permanent and cannot be undone."
              confirmLabel={deleting ? "Deleting..." : "Delete"}
              destructive
              onConfirm={handleDelete}
              onCancel={() => !deleting && setDeleteConfirm(false)}
            />
          )}
        </motion.div>
      )}
    </AnimatePresence>
  );
}
