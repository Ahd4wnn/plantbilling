import { useEffect, useState } from "react";
import type { CustomerInput } from "@/store/billing";
import { lookupCustomer } from "@/api/customers";

interface CustomerSectionProps {
  value: CustomerInput;
  onChange: (c: CustomerInput) => void;
}

/**
 * Customer details, entered fresh on each bill and optional. Name + phone only;
 * the phone field is clearly labeled for its purpose (giving the number is the
 * customer's consent to receive receipts there). When a full 10-digit number that
 * already belongs to THIS shop is entered, we show a small returning-customer hint
 * with the visit count (scoped by the server via RLS).
 */
export function CustomerSection({ value, onChange }: CustomerSectionProps) {
  const phone = value.phone;
  const [hint, setHint] = useState<{ name: string | null; visits: number } | null>(null);

  useEffect(() => {
    // Only look up a complete 10-digit number. Below that, clear any prior hint.
    if (phone.replace(/\D/g, "").length !== 10) {
      setHint(null);
      return;
    }
    let cancelled = false;
    const t = setTimeout(async () => {
      try {
        const res = await lookupCustomer(phone);
        if (!cancelled) setHint(res.found ? { name: res.name, visits: res.visit_count } : null);
      } catch {
        if (!cancelled) setHint(null); // lookup never blocks billing
      }
    }, 350);
    return () => {
      cancelled = true;
      clearTimeout(t);
    };
  }, [phone]);

  const tooShort = phone.length > 0 && phone.length < 10;

  return (
    <div>
      <span className="mb-2 block text-base font-semibold text-ink">
        Customer <span className="font-normal text-ink-soft">(optional)</span>
      </span>
      <div className="space-y-3">
        <input
          type="text"
          value={value.name}
          onChange={(e) => onChange({ ...value, name: e.target.value })}
          placeholder="Customer name"
          className="field"
          aria-label="Customer name"
          autoComplete="off"
        />
        <div>
          <input
            type="tel"
            inputMode="tel"
            value={value.phone}
            onChange={(e) => onChange({ ...value, phone: e.target.value.replace(/\D/g, "").substring(0, 10) })}
            placeholder="10-digit phone number"
            className={`field ${tooShort ? "border-danger focus:border-danger focus:ring-danger/20" : ""}`}
            aria-label="Customer phone number for the receipt"
            autoComplete="off"
          />
          {tooShort ? (
            <p className="mt-1.5 text-sm font-semibold text-danger">
              Phone number must be exactly 10 digits.
            </p>
          ) : hint ? (
            <p className="mt-1.5 text-sm font-semibold text-emerald-700">
              {hint.name ? `${hint.name} — ` : ""}
              returning customer · came {hint.visits} {hint.visits === 1 ? "time" : "times"} before
            </p>
          ) : (
            <p className="mt-1.5 text-sm text-ink-soft">
              We'll only use this number to send this customer their bill.
            </p>
          )}
        </div>
      </div>
    </div>
  );
}
