import { useEffect, useState } from "react";
import { createShop, type ShopCreatePayload } from "@/api/admin";
import { friendlyError } from "@/api/client";
import { Modal } from "@/components/admin/Modal";
import { Button } from "@/components/Button";
import { CredentialDisplay } from "@/components/admin/CredentialDisplay";
import { generatePassword } from "@/lib/password";

interface CreateShopModalProps {
  open: boolean;
  onClose: () => void;
  onCreated: () => void;
}

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

interface Errors {
  name?: string;
  owner_email?: string;
  owner_password?: string;
}

export function CreateShopModal({ open, onClose, onCreated }: CreateShopModalProps) {
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [openingCash, setOpeningCash] = useState("");

  const [errors, setErrors] = useState<Errors>({});
  const [submitting, setSubmitting] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);
  const [done, setDone] = useState<{ email: string; password: string; shopName: string } | null>(null);

  // Reset everything whenever the modal (re)opens.
  useEffect(() => {
    if (!open) return;
    setName("");
    setEmail("");
    setPassword(generatePassword());
    setOpeningCash("");
    setErrors({});
    setServerError(null);
    setDone(null);
  }, [open]);

  function validate(): Errors {
    const e: Errors = {};
    if (!name.trim()) e.name = "Shop name is required.";
    if (!email.trim()) e.owner_email = "Owner email is required.";
    else if (!EMAIL_RE.test(email.trim())) e.owner_email = "Enter a valid email address.";
    if (password.length < 8) e.owner_password = "Password must be at least 8 characters.";
    return e;
  }

  async function handleSubmit() {
    const e = validate();
    setErrors(e);
    if (Object.keys(e).length > 0) return;

    setSubmitting(true);
    setServerError(null);
    const payload: ShopCreatePayload = {
      name: name.trim(),
      owner_name: null,
      owner_phone: null,
      owner_email: email.trim(),
      owner_password: password,
      cash_in_hand_opening: openingCash.trim() || "0",
    };
    try {
      const res = await createShop(payload);
      setDone({ email: res.owner.email, password, shopName: res.shop.name });
      onCreated(); // refresh the list behind the modal
    } catch (err) {
      setServerError(friendlyError(err, "Couldn't create the shop. Please try again."));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={done ? "Shop created" : "Add shop"}
      dismissible={!submitting}
      footer={
        done ? (
          <Button variant="primary" size="action" onClick={onClose}>
            Done
          </Button>
        ) : (
          <div className="flex gap-3">
            <Button variant="ghost" size="action" onClick={onClose} disabled={submitting}>
              Cancel
            </Button>
            <Button
              variant="primary"
              size="action"
              loading={submitting}
              loadingLabel="Creating…"
              onClick={handleSubmit}
            >
              Create shop
            </Button>
          </div>
        )
      }
    >
      {done ? (
        <CredentialDisplay
          title="Hand these login details to the shop owner:"
          shopName={done.shopName}
          email={done.email}
          password={done.password}
        />
      ) : (
        <div className="space-y-4">
          {serverError && (
            <p className="rounded-control bg-danger-soft px-3 py-2 text-base font-semibold text-danger" role="alert">
              {serverError}
            </p>
          )}

          <Field label="Shop name" error={errors.name}>
            <input className={inputCls(errors.name)} value={name} onChange={(e) => setName(e.target.value)} placeholder="Green Leaf Nursery" />
          </Field>


          <Field label="Owner login email" error={errors.owner_email}>
            <input className={inputCls(errors.owner_email)} value={email} onChange={(e) => setEmail(e.target.value)} placeholder="owner@example.com" inputMode="email" autoComplete="off" />
          </Field>

          <Field label="Owner password" error={errors.owner_password}>
            <div className="flex gap-2">
              <input className={`${inputCls(errors.owner_password)} font-mono`} value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="off" />
              <button
                type="button"
                onClick={() => setPassword(generatePassword())}
                className="shrink-0 rounded-control border border-border-strong px-3 text-sm font-semibold text-ink hover:bg-surface-muted"
              >
                Generate
              </button>
            </div>
            <p className="mt-1 text-sm text-ink-soft">You'll see this password once after creating — copy it then.</p>
          </Field>

          <Field label="Opening cash in hand (₹)">
            <input
              className={inputCls()}
              value={openingCash}
              onChange={(e) => setOpeningCash(e.target.value.replace(/[^0-9.]/g, ""))}
              placeholder="0"
              inputMode="decimal"
            />
            <p className="mt-1 text-sm text-ink-soft">Cash the shop already has when they start. The running cash-in-hand builds from here. Leave blank for 0.</p>
          </Field>
        </div>
      )}
    </Modal>
  );
}

function Field({ label, error, children }: { label: string; error?: string; children: React.ReactNode }) {
  return (
    <div>
      <label className="mb-1 block text-sm font-semibold text-ink">{label}</label>
      {children}
      {error && <p className="mt-1 text-sm font-semibold text-danger">{error}</p>}
    </div>
  );
}

function inputCls(error?: string): string {
  return [
    "h-11 w-full rounded-control border-2 bg-white px-3 text-base text-ink",
    "focus:outline-none focus:ring-4 focus:ring-primary-600/20",
    error ? "border-danger focus:border-danger" : "border-border focus:border-primary-600",
  ].join(" ");
}
