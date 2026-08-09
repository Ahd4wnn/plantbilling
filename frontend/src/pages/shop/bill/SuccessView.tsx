import { useState } from "react";
import { motion, useReducedMotion } from "motion/react";
import { Check, Printer } from "lucide-react";
import { type BillOut, sendBillWhatsApp } from "@/api/billing";
import { Button } from "@/components/Button";
import { formatINR, toPaise } from "@/lib/money";
import { useBluetoothPrinter } from "@/store/bluetooth";

interface SuccessViewProps {
  bill: BillOut;
  onNewBill: () => void;
  customerPhone?: string;
}

/**
 * The satisfying end-of-sale confirmation. Big check, the total in large type,
 * how it was paid, and the bill reference. "New Bill" is the one clear next
 * action. Print + WhatsApp are visible placeholders (out of scope this prompt).
 */
export function SuccessView({ bill, onNewBill, customerPhone }: SuccessViewProps) {
  const reduce = useReducedMotion();
  const [printing, setPrinting] = useState(false);
  const [sendingWa, setSendingWa] = useState(false);
  const { status } = useBluetoothPrinter();

  const handleShareWhatsApp = async () => {
    setSendingWa(true);
    try {
      const res = await sendBillWhatsApp(bill.id);
      alert(res.detail || "WhatsApp invoice queued for sending successfully!");
    } catch (err) {
      alert(err instanceof Error ? err.message : "Failed to queue WhatsApp invoice.");
    } finally {
      setSendingWa(false);
    }
  };

  const handlePrint = async () => {
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
  const cash = toPaise(bill.cash_amount);
  const upi = toPaise(bill.upi_amount);
  const due = bill.due_amount ? toPaise(bill.due_amount) : 0;

  const channels = [];
  if (cash > 0) channels.push(`Cash ${formatINR(cash)}`);
  if (upi > 0) channels.push(`UPI ${formatINR(upi)}`);
  if (due > 0) channels.push(`Due ${formatINR(due)}`);

  let payLabel = channels.join(" + ");
  if (!payLabel) {
    payLabel = "Paid by Cash";
  } else if (cash > 0 && upi === 0 && due === 0) {
    payLabel = "Paid by Cash";
  } else if (upi > 0 && cash === 0 && due === 0) {
    payLabel = "Paid by UPI";
  } else if (due > 0 && cash === 0 && upi === 0) {
    payLabel = `Due ${formatINR(due)}`;
  }

  const when = new Date(bill.created_at);
  const timeLabel = isNaN(when.getTime())
    ? ""
    : when.toLocaleString("en-IN", { day: "numeric", month: "short", hour: "numeric", minute: "2-digit" });
  const billRef = bill.bill_no ? `#${bill.bill_no}` : `#${bill.id.slice(0, 8).toUpperCase()}`;

  return (
    <div className="flex flex-col items-center pt-6 text-center">
      <motion.div
        className="flex h-24 w-24 items-center justify-center rounded-full bg-success-soft"
        initial={reduce ? { opacity: 0 } : { scale: 0.6, opacity: 0 }}
        animate={reduce ? { opacity: 1 } : { scale: 1, opacity: 1 }}
        transition={{ type: "spring", stiffness: 320, damping: 18 }}
      >
        <Check className="h-12 w-12 text-success" strokeWidth={3} />
      </motion.div>

      <h1 className="mt-5 text-2xl font-extrabold text-ink">Bill Saved</h1>

      <div className="mt-1 text-4xl font-extrabold tracking-tight text-ink">{formatINR(toPaise(bill.total))}</div>
      <div className="mt-1 text-lg font-semibold text-ink-soft">{payLabel}</div>

      {bill.customer_name && (
        <div className="mt-1 text-base text-ink-soft">For {bill.customer_name}</div>
      )}

      {customerPhone && (
        <div className="mt-3 rounded-xl bg-emerald-50 border border-emerald-100 px-4 py-2.5 text-sm font-semibold text-emerald-800 flex items-center justify-center gap-1.5 animate-pulse max-w-xs mx-auto">
          <span className="h-2 w-2 rounded-full bg-emerald-500 shrink-0 animate-ping" />
          <span>Sending invoice to {customerPhone} via WhatsApp</span>
        </div>
      )}

      <div className="mt-2 text-base text-ink-soft font-medium">
        {billRef}
        {timeLabel && <> · {timeLabel}</>}
        {bill.salesperson_email && <> · Billed by: {bill.salesperson_email}</>}
      </div>

      <div className="mt-8 w-full max-w-xs space-y-3">
        <Button variant="primary" size="action" className="w-full" onClick={onNewBill}>
          New Bill
        </Button>

        <Button
          variant="secondary"
          size="action"
          className="w-full font-bold flex items-center justify-center gap-2 border-2 bg-white"
          loading={printing}
          onClick={handlePrint}
        >
          <Printer className="h-5 w-5" />
          {status === "connected" ? "Print Receipt" : "Connect & Print"}
        </Button>
        <Button
          variant="secondary"
          size="action"
          className="w-full font-bold flex items-center justify-center gap-2 border-2 bg-white"
          loading={sendingWa}
          onClick={handleShareWhatsApp}
        >
          Share on WhatsApp
        </Button>
      </div>
    </div>
  );
}
