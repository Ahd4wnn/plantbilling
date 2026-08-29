import { useEffect, useRef, useState } from "react";
import { deleteShopLogo, updateShop, uploadShopLogo, type ShopRow } from "@/api/admin";
import { friendlyError, getMediaUrl } from "@/api/client";
import { Modal } from "@/components/admin/Modal";
import { Button } from "@/components/Button";
import { validateImageFile } from "@/lib/image";

interface BusinessDetailsModalProps {
  shop: ShopRow | null;
  onClose: () => void;
  onSaved: () => void;
}

export function BusinessDetailsModal({ shop, onClose, onSaved }: BusinessDetailsModalProps) {
  const [businessName, setBusinessName] = useState("");
  const [businessAddress, setBusinessAddress] = useState("");
  const [businessPhone, setBusinessPhone] = useState("");
  const [businessEmail, setBusinessEmail] = useState("");
  const [businessUpi, setBusinessUpi] = useState("");
  const [whatsappMessageTemplate, setWhatsappMessageTemplate] = useState("");

  // The logo is uploaded/removed immediately (it's a file, not a form field),
  // while the on/off switch rides along with Save Details like everything else.
  const [logoUrl, setLogoUrl] = useState<string | null>(null);
  const [logoEnabled, setLogoEnabled] = useState(true);
  const [logoBusy, setLogoBusy] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);

  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!shop) return;
    setBusinessName(shop.business_name || "");
    setBusinessAddress(shop.business_address || "");
    setBusinessPhone(shop.business_phone || "");
    setBusinessEmail(shop.business_email || "");
    setBusinessUpi(shop.business_upi || "");
    setWhatsappMessageTemplate(shop.whatsapp_message_template || "");
    setLogoUrl(shop.logo_url ?? null);
    setLogoEnabled(shop.logo_enabled ?? true);
    setError(null);
    setSubmitting(false);
  }, [shop]);

  async function handleLogoPicked(file: File | undefined) {
    if (!shop || !file) return;
    const problem = validateImageFile(file);
    if (problem) { setError(problem); return; }
    setLogoBusy(true);
    setError(null);
    try {
      const updated = await uploadShopLogo(shop.id, file);
      setLogoUrl(updated.logo_url ?? null);
      onSaved();
    } catch (err) {
      setError(friendlyError(err, "Couldn't upload the logo. Please try again."));
    } finally {
      setLogoBusy(false);
      // Clear the input so picking the same file again still fires onChange.
      if (fileRef.current) fileRef.current.value = "";
    }
  }

  async function handleLogoRemove() {
    if (!shop) return;
    setLogoBusy(true);
    setError(null);
    try {
      await deleteShopLogo(shop.id);
      setLogoUrl(null);
      onSaved();
    } catch (err) {
      setError(friendlyError(err, "Couldn't remove the logo. Please try again."));
    } finally {
      setLogoBusy(false);
    }
  }

  async function handleSave() {
    if (!shop) return;
    const trimmedUpi = businessUpi.trim();
    if (trimmedUpi) {
      const upiRegex = /^[a-zA-Z0-9.\-_]{2,256}@[a-zA-Z]{2,64}$/;
      if (!upiRegex.test(trimmedUpi)) {
        setError("Invalid UPI ID (VPA) format. Must be like username@bank.");
        return;
      }
    }
    setSubmitting(true);
    setError(null);
    try {
      await updateShop(shop.id, {
        business_name: businessName.trim() || null,
        business_address: businessAddress.trim() || null,
        business_phone: businessPhone.trim() || null,
        business_email: businessEmail.trim() || null,
        business_upi: trimmedUpi || null,
        whatsapp_message_template: whatsappMessageTemplate.trim() || null,
        logo_enabled: logoEnabled,
      });
      onSaved();
      onClose();
    } catch (err) {
      setError(friendlyError(err, "Couldn't save business details. Please try again."));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal
      open={shop !== null}
      onClose={onClose}
      title={`Business details for ${shop?.name || "nursery"}`}
      dismissible={!submitting}
      footer={
        <div className="flex gap-3">
          <Button variant="ghost" size="action" onClick={onClose} disabled={submitting}>
            Cancel
          </Button>
          <Button variant="primary" size="action" loading={submitting} loadingLabel="Saving…" onClick={handleSave}>
            Save Details
          </Button>
        </div>
      }
    >
      <div className="space-y-4">
        {error && (
          <p className="rounded-control bg-danger-soft px-3 py-2 text-base font-semibold text-danger" role="alert">
            {error}
          </p>
        )}

        <div>
          <label className="mb-1 block text-sm font-semibold text-ink">Business Name</label>
          <input
            className="h-11 w-full rounded-control border-2 border-border bg-white px-3 text-base text-ink focus:border-primary-600 focus:outline-none focus:ring-4 focus:ring-primary-600/20"
            value={businessName}
            onChange={(e) => setBusinessName(e.target.value)}
            placeholder={shop?.name || "Business name"}
          />
        </div>

        <div>
          <label className="mb-1 block text-sm font-semibold text-ink">Business Address</label>
          <textarea
            className="w-full rounded-control border-2 border-border bg-white px-3 py-2 text-base text-ink focus:border-primary-600 focus:outline-none focus:ring-4 focus:ring-primary-600/20 min-h-[5rem]"
            value={businessAddress}
            onChange={(e) => setBusinessAddress(e.target.value)}
            placeholder="Nursery address, City, Pin code"
          />
        </div>

        <div>
          <label className="mb-1 block text-sm font-semibold text-ink">Mobile Number</label>
          <input
            type="tel"
            className="h-11 w-full rounded-control border-2 border-border bg-white px-3 text-base text-ink focus:border-primary-600 focus:outline-none focus:ring-4 focus:ring-primary-600/20"
            value={businessPhone}
            onChange={(e) => setBusinessPhone(e.target.value)}
            placeholder="10 digit mobile number"
          />
        </div>

        <div>
          <label className="mb-1 block text-sm font-semibold text-ink">Email ID</label>
          <input
            type="email"
            className="h-11 w-full rounded-control border-2 border-border bg-white px-3 text-base text-ink focus:border-primary-600 focus:outline-none focus:ring-4 focus:ring-primary-600/20"
            value={businessEmail}
            onChange={(e) => setBusinessEmail(e.target.value)}
            placeholder="contact@nursery.com"
          />
        </div>

        <div>
          <label className="mb-1 block text-sm font-semibold text-ink">UPI ID (VPA)</label>
          <input
            className="h-11 w-full rounded-control border-2 border-border bg-white px-3 text-base text-ink focus:border-primary-600 focus:outline-none focus:ring-4 focus:ring-primary-600/20"
            value={businessUpi}
            onChange={(e) => setBusinessUpi(e.target.value)}
            placeholder="merchant@ybl"
          />
        </div>

        <div className="rounded-control border-2 border-border p-3">
          <label className="mb-1 block text-sm font-semibold text-ink">Bill Logo</label>
          <p className="mb-2 text-xs text-ink-soft leading-normal">
            Printed at the top of this nursery&rsquo;s bills. Only you can change it —
            the shop can see it but not edit it. JPG, PNG or WebP, up to 5&nbsp;MB.
          </p>

          <div className="flex items-center gap-3">
            <div className="flex h-16 w-16 shrink-0 items-center justify-center overflow-hidden rounded-control border-2 border-border bg-surface-muted">
              {logoUrl ? (
                <img src={getMediaUrl(logoUrl) ?? undefined} alt="Shop logo" className="h-full w-full object-contain" />
              ) : (
                <span className="text-xs text-ink-soft">None</span>
              )}
            </div>
            <div className="flex flex-wrap gap-2">
              <Button variant="secondary" size="tap" disabled={logoBusy} onClick={() => fileRef.current?.click()}>
                {logoBusy ? "Working…" : logoUrl ? "Replace" : "Upload"}
              </Button>
              {logoUrl && (
                <Button variant="ghost" size="tap" disabled={logoBusy} onClick={handleLogoRemove}>
                  Remove
                </Button>
              )}
            </div>
            <input
              ref={fileRef}
              type="file"
              accept="image/jpeg,image/png,image/webp"
              className="sr-only"
              onChange={(e) => handleLogoPicked(e.target.files?.[0])}
            />
          </div>

          {logoUrl && (
            <label className="mt-3 flex items-center gap-2 text-sm font-semibold text-ink">
              <input
                type="checkbox"
                className="h-4 w-4"
                checked={logoEnabled}
                onChange={(e) => setLogoEnabled(e.target.checked)}
              />
              Print this logo on bills
            </label>
          )}
          {logoUrl && !logoEnabled && (
            <p className="mt-1 text-xs text-ink-soft">
              Turned off — the file is kept, but bills print without it.
            </p>
          )}
        </div>

        <div>
          <label className="mb-1 block text-sm font-semibold text-ink">Custom WhatsApp Message Template</label>
          <p className="mb-1.5 text-xs text-ink-soft leading-normal">
            Define your custom receipt message. You can use these dynamic placeholders:
            <br />
            <code>{"{{customer_name}}"}</code>, <code>{"{{shop_name}}"}</code>, <code>{"{{bill_total}}"}</code>, <code>{"{{items}}"}</code>, <code>{"{{cash_amount}}"}</code>, <code>{"{{upi_amount}}"}</code>, <code>{"{{due_amount}}"}</code>, <code>{"{{bill_id}}"}</code>, <code>{"{{invoice_ninja_url}}"}</code>
          </p>
          <textarea
            className="w-full rounded-control border-2 border-border bg-white px-3 py-2 text-base text-ink focus:border-primary-600 focus:outline-none focus:ring-4 focus:ring-primary-600/20 min-h-[7rem] font-mono text-sm"
            value={whatsappMessageTemplate}
            onChange={(e) => setWhatsappMessageTemplate(e.target.value)}
            placeholder="Hello {{customer_name}}, here is your bill of {{bill_total}} from {{shop_name}}. Items: {{items}}. Invoice link: {{invoice_ninja_url}}"
          />
        </div>
      </div>
    </Modal>
  );
}
