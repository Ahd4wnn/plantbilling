import { useEffect, useMemo, useState } from "react";
import { Modal } from "@/components/admin/Modal";
import { Button } from "@/components/Button";
import { ConfirmDialog } from "@/components/ConfirmDialog";
import { friendlyError } from "@/api/client";
import { createNotification } from "@/api/adminNotifications";
import { listShops, type ShopRow } from "@/api/admin";

const fieldClass =
  "w-full rounded-xl border-2 border-border bg-white px-3 text-base font-semibold text-ink focus:border-primary-600 focus:outline-none focus:ring-4 focus:ring-primary-600/20";
const labelClass = "mb-1 block text-sm font-bold text-ink-soft";

interface Props {
  open: boolean;
  onClose: () => void;
  onSent: () => void;
}

/** Admin composes a notification and sends it to all shops or specific shops. */
export function ComposeNotificationModal({ open, onClose, onSent }: Props) {
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [actionUrl, setActionUrl] = useState("");
  const [target, setTarget] = useState<"all" | "shops">("all");
  const [shopIds, setShopIds] = useState<Set<string>>(new Set());
  const [shopQuery, setShopQuery] = useState("");

  const [shops, setShops] = useState<ShopRow[]>([]);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [confirmAll, setConfirmAll] = useState(false);

  // Reset the form each time the modal opens; lazy-load shops once.
  useEffect(() => {
    if (!open) return;
    setTitle("");
    setBody("");
    setActionUrl("");
    setTarget("all");
    setShopIds(new Set());
    setShopQuery("");
    setError(null);
    if (shops.length === 0) listShops().then(setShops).catch(() => setShops([]));
  }, [open]); // eslint-disable-line react-hooks/exhaustive-deps

  const filteredShops = useMemo(() => {
    const q = shopQuery.trim().toLowerCase();
    if (!q) return shops;
    return shops.filter((s) =>
      `${s.name} ${s.owner_email ?? ""} ${s.owner_name ?? ""}`.toLowerCase().includes(q),
    );
  }, [shops, shopQuery]);

  const toggleShop = (id: string) =>
    setShopIds((prev) => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });

  const validate = (): string | null => {
    if (!title.trim()) return "Please enter a title.";
    if (!body.trim()) return "Please enter a message.";
    if (target === "shops" && shopIds.size === 0)
      return "Pick at least one shop, or send to all shops.";
    return null;
  };

  const submit = async () => {
    const v = validate();
    if (v) {
      setError(v);
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await createNotification({
        title: title.trim(),
        body: body.trim(),
        action_url: actionUrl.trim() || null,
        target,
        shop_ids: target === "shops" ? [...shopIds] : [],
      });
      onSent();
      onClose();
    } catch (e) {
      setError(friendlyError(e, "Couldn't send the notification."));
    } finally {
      setSaving(false);
    }
  };

  const onSendClick = () => {
    const v = validate();
    if (v) {
      setError(v);
      return;
    }
    // Broadcasting to every shop is worth a confirm; targeted sends go straight through.
    if (target === "all") setConfirmAll(true);
    else void submit();
  };

  return (
    <>
      <Modal
        open={open}
        onClose={onClose}
        title="New notification"
        footer={
          <div className="flex gap-3">
            <Button variant="ghost" size="action" onClick={onClose} disabled={saving}>
              Cancel
            </Button>
            <Button
              variant="primary"
              size="action"
              onClick={onSendClick}
              loading={saving}
              loadingLabel="Sending…"
            >
              Send
            </Button>
          </div>
        }
      >
        <div className="space-y-4">
          {error && (
            <p className="rounded-control bg-danger-soft px-3 py-2 text-sm font-semibold text-danger">
              {error}
            </p>
          )}

          <div>
            <label className={labelClass} htmlFor="notif-title">
              Title
            </label>
            <input
              id="notif-title"
              className={`${fieldClass} h-11`}
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="e.g. New feature: daily cash book"
              maxLength={120}
            />
          </div>

          <div>
            <label className={labelClass} htmlFor="notif-body">
              Message
            </label>
            <textarea
              id="notif-body"
              className={`${fieldClass} min-h-[120px] py-2`}
              value={body}
              onChange={(e) => setBody(e.target.value)}
              placeholder="Write the announcement the shops will see…"
              maxLength={2000}
            />
          </div>

          <div>
            <label className={labelClass} htmlFor="notif-url">
              Action link <span className="font-normal text-ink-faint">(optional)</span>
            </label>
            <input
              id="notif-url"
              className={`${fieldClass} h-11`}
              value={actionUrl}
              onChange={(e) => setActionUrl(e.target.value)}
              placeholder="https://…"
            />
          </div>

          {/* Target segmented control */}
          <div>
            <span className={labelClass}>Send to</span>
            <div className="flex rounded-xl border-2 border-border bg-white p-1">
              {(["all", "shops"] as const).map((t) => (
                <button
                  key={t}
                  type="button"
                  onClick={() => setTarget(t)}
                  className={[
                    "flex-1 rounded-lg px-3 py-2 text-base font-bold transition-colors",
                    target === t ? "bg-primary-600 text-white" : "text-ink-soft hover:bg-surface-muted",
                  ].join(" ")}
                >
                  {t === "all" ? "All shops" : "Specific shops"}
                </button>
              ))}
            </div>
          </div>

          {target === "shops" && (
            <div>
              <div className="mb-1 flex items-center justify-between">
                <span className={labelClass}>
                  Shops{" "}
                  <span className="font-normal text-ink-faint">
                    ({shopIds.size} selected)
                  </span>
                </span>
                {shopIds.size > 0 && (
                  <button
                    type="button"
                    className="text-sm font-semibold text-primary-700 hover:underline"
                    onClick={() => setShopIds(new Set())}
                  >
                    Clear
                  </button>
                )}
              </div>
              <input
                className={`${fieldClass} mb-2 h-10 text-sm`}
                value={shopQuery}
                onChange={(e) => setShopQuery(e.target.value)}
                placeholder="Search shops…"
              />
              <div className="max-h-56 overflow-y-auto rounded-xl border-2 border-border">
                {filteredShops.length === 0 ? (
                  <p className="px-3 py-6 text-center text-sm text-ink-soft">No shops found.</p>
                ) : (
                  filteredShops.map((s) => {
                    const checked = shopIds.has(s.id);
                    return (
                      <label
                        key={s.id}
                        className="flex cursor-pointer items-center gap-3 border-b border-border px-3 py-2.5 last:border-0 hover:bg-surface-muted"
                      >
                        <input
                          type="checkbox"
                          checked={checked}
                          onChange={() => toggleShop(s.id)}
                          className="h-5 w-5 accent-primary-600"
                        />
                        <span className="min-w-0 flex-1">
                          <span className="block truncate font-semibold text-ink">{s.name}</span>
                          {s.owner_email && (
                            <span className="block truncate text-sm text-ink-soft">{s.owner_email}</span>
                          )}
                        </span>
                        {!s.is_active && (
                          <span className="shrink-0 rounded-full bg-surface-muted px-2 py-0.5 text-xs font-bold text-ink-soft">
                            inactive
                          </span>
                        )}
                      </label>
                    );
                  })
                )}
              </div>
            </div>
          )}
        </div>
      </Modal>

      <ConfirmDialog
        open={confirmAll}
        title="Send to all shops?"
        body="Every shop using Plantora will see this notification."
        confirmLabel="Send to all"
        cancelLabel="Cancel"
        onConfirm={() => {
          setConfirmAll(false);
          void submit();
        }}
        onCancel={() => setConfirmAll(false)}
      />
    </>
  );
}
