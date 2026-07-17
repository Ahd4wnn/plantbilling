import { useEffect, useRef, useState } from "react";
import { AnimatePresence, motion, useReducedMotion } from "motion/react";
import { Button } from "./Button";

interface TypeToConfirmDialogProps {
  open: boolean;
  title: string;
  /** The exact value the user must type to enable the destructive action (e.g. an email). */
  expected: string;
  /** What the value is, shown in the prompt. Defaults to "email". */
  label?: string;
  body?: React.ReactNode;
  confirmLabel?: string;
  loading?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

/**
 * A hard-stop confirmation for irreversible account deletions: the user must
 * re-type the account's email (case-insensitive) before the Delete button is
 * enabled. Prevents accidental one-tap deletions of the wrong account.
 */
export function TypeToConfirmDialog({
  open,
  title,
  expected,
  label = "email",
  body,
  confirmLabel = "Delete",
  loading = false,
  onConfirm,
  onCancel,
}: TypeToConfirmDialogProps) {
  const reduce = useReducedMotion();
  const [value, setValue] = useState("");
  const inputRef = useRef<HTMLInputElement>(null);

  // Reset the field each time the dialog opens.
  useEffect(() => {
    if (open) {
      setValue("");
      // Focus after the enter animation settles.
      const t = setTimeout(() => inputRef.current?.focus(), 120);
      return () => clearTimeout(t);
    }
  }, [open]);

  const matches = value.trim().toLowerCase() === expected.trim().toLowerCase() && expected.trim() !== "";

  return (
    <AnimatePresence>
      {open && (
        <div className="fixed inset-0 z-[70] flex items-center justify-center p-6">
          <motion.div
            className="absolute inset-0 bg-black/40"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.16 }}
            onClick={() => !loading && onCancel()}
          />
          <motion.div
            className="relative w-full max-w-sm rounded-card bg-surface p-6 shadow-card-lg"
            role="alertdialog"
            aria-modal="true"
            initial={reduce ? { opacity: 0 } : { opacity: 0, scale: 0.96, y: 8 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={reduce ? { opacity: 0 } : { opacity: 0, scale: 0.96 }}
            transition={{ duration: 0.18, ease: "easeOut" }}
          >
            <h2 className="text-xl font-bold text-ink">{title}</h2>
            {body && <div className="mt-2 text-base text-ink-soft">{body}</div>}

            <p className="mt-4 text-sm font-semibold text-ink-soft">
              Type <span className="font-bold text-ink break-all">{expected}</span> to confirm.
            </p>
            <input
              ref={inputRef}
              type="text"
              value={value}
              onChange={(e) => setValue(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter" && matches && !loading) onConfirm();
              }}
              autoComplete="off"
              autoCapitalize="none"
              spellCheck={false}
              placeholder={`Type the ${label} here`}
              className="mt-2 h-12 w-full rounded-control border-2 border-border bg-white px-3 text-base text-ink focus:border-danger focus:outline-none focus:ring-4 focus:ring-danger/15"
            />

            <div className="mt-6 flex flex-col gap-3">
              <Button
                variant="primary"
                size="action"
                disabled={!matches || loading}
                loading={loading}
                onClick={onConfirm}
                className="!bg-danger hover:!bg-danger disabled:!bg-danger/40"
              >
                {confirmLabel}
              </Button>
              <Button variant="ghost" size="action" disabled={loading} onClick={onCancel}>
                Cancel
              </Button>
            </div>
          </motion.div>
        </div>
      )}
    </AnimatePresence>
  );
}
