import { useEffect, useState, type ReactNode } from "react";
import { AnimatePresence, motion, useReducedMotion } from "motion/react";
import { Button } from "./Button";

interface ConfirmDialogProps {
  open: boolean;
  title: string;
  body?: ReactNode;
  confirmLabel?: string;
  cancelLabel?: string;
  destructive?: boolean;
  /**
   * When set, the operator must type this exact text before Confirm enables.
   * For actions that destroy data irreversibly — a one-tap confirm is too easy
   * to fire on the wrong row in a list.
   */
  requireTyped?: string;
  requireTypedLabel?: string;
  onConfirm: () => void;
  onCancel: () => void;
}

/** Centered confirmation dialog for safe, reversible decisions (mode switch, deletes). */
export function ConfirmDialog({
  open,
  title,
  body,
  confirmLabel = "Confirm",
  cancelLabel = "Cancel",
  destructive = false,
  requireTyped,
  requireTypedLabel,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  const reduce = useReducedMotion();
  const [typed, setTyped] = useState("");

  // Clear between openings, so the previous target's name can't carry over and
  // pre-arm the button for a different row.
  useEffect(() => {
    if (!open) setTyped("");
  }, [open]);

  const armed = !requireTyped || typed.trim() === requireTyped.trim();

  return (
    <AnimatePresence>
      {open && (
        <div className="fixed inset-0 z-[60] flex items-center justify-center p-6">
          <motion.div
            className="absolute inset-0 bg-black/40"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.16 }}
            onClick={onCancel}
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
            {requireTyped && (
              <div className="mt-4">
                <label className="mb-1 block text-sm font-bold text-ink-soft">
                  {requireTypedLabel ?? `Type “${requireTyped}” to confirm`}
                </label>
                <input
                  className="h-11 w-full rounded-xl border-2 border-border bg-white px-3 text-base font-semibold text-ink focus:border-danger focus:outline-none focus:ring-4 focus:ring-danger/20"
                  value={typed}
                  onChange={(e) => setTyped(e.target.value)}
                  placeholder={requireTyped}
                  autoFocus
                  autoComplete="off"
                />
              </div>
            )}
            <div className="mt-6 flex flex-col gap-3">
              <Button
                variant="primary"
                size="action"
                onClick={onConfirm}
                disabled={!armed}
                className={destructive ? "!bg-danger hover:!bg-danger" : ""}
              >
                {confirmLabel}
              </Button>
              <Button variant="ghost" size="action" onClick={onCancel}>
                {cancelLabel}
              </Button>
            </div>
          </motion.div>
        </div>
      )}
    </AnimatePresence>
  );
}
