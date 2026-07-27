import { useEffect, useState } from "react";
import { Plus, Trash2, Users, Store, CheckCheck } from "lucide-react";
import { Button } from "@/components/Button";
import { Spinner } from "@/components/Spinner";
import { ConfirmDialog } from "@/components/ConfirmDialog";
import { friendlyError } from "@/api/client";
import {
  listNotifications,
  deleteNotification,
  type NotificationRow,
} from "@/api/adminNotifications";
import { ComposeNotificationModal } from "./ComposeNotificationModal";

function formatWhen(iso: string): string {
  return new Intl.DateTimeFormat("en-IN", {
    day: "numeric",
    month: "short",
    year: "numeric",
    hour: "numeric",
    minute: "2-digit",
  }).format(new Date(iso));
}

function targetLabel(n: NotificationRow): string {
  if (n.target === "all") return "All shops";
  return n.shop_count === 1 ? "1 shop" : `${n.shop_count} shops`;
}

export function NotificationsPage() {
  const [rows, setRows] = useState<NotificationRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [composeOpen, setComposeOpen] = useState(false);
  const [pendingDelete, setPendingDelete] = useState<NotificationRow | null>(null);

  useEffect(() => {
    let alive = true;
    setLoading(true);
    setError(null);
    listNotifications(100, 0)
      .then((r) => alive && setRows(r.items))
      .catch((e) => alive && setError(friendlyError(e, "Couldn't load notifications.")))
      .finally(() => alive && setLoading(false));
    return () => {
      alive = false;
    };
  }, [reloadKey]);

  const reload = () => setReloadKey((k) => k + 1);

  const confirmDelete = async () => {
    if (!pendingDelete) return;
    const id = pendingDelete.id;
    setPendingDelete(null);
    try {
      await deleteNotification(id);
      setRows((prev) => prev.filter((n) => n.id !== id));
    } catch {
      // Re-fetch to reflect the true server state on failure.
      reload();
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-extrabold tracking-tight text-ink">Notifications</h1>
          <p className="text-base font-semibold text-ink-soft">
            Send announcements to the shops using Plantora
          </p>
        </div>
        <Button variant="primary" size="tap" onClick={() => setComposeOpen(true)}>
          <Plus className="h-5 w-5" /> New notification
        </Button>
      </div>

      {loading ? (
        <div className="flex justify-center py-16">
          <Spinner className="h-8 w-8 text-primary-600" />
        </div>
      ) : error ? (
        <div className="py-12 text-center">
          <p className="text-base font-semibold text-danger">{error}</p>
          <Button variant="secondary" size="tap" className="mt-4" onClick={reload}>
            Try again
          </Button>
        </div>
      ) : rows.length === 0 ? (
        <div className="rounded-card border border-border bg-slate-50 py-14 text-center shadow-card">
          <p className="text-base font-semibold text-ink">No notifications sent yet.</p>
          <p className="mt-1 text-sm text-ink-soft">
            Send an announcement and every targeted shop will see it in the app.
          </p>
        </div>
      ) : (
        <div className="space-y-3">
          {rows.map((n) => (
            <div
              key={n.id}
              className="rounded-card border border-border bg-white px-4 py-3 shadow-card"
            >
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0 flex-1">
                  <div className="font-bold text-ink">{n.title}</div>
                  <p className="mt-0.5 whitespace-pre-wrap break-words text-sm text-ink-soft">
                    {n.body}
                  </p>
                  {n.action_url && (
                    <a
                      href={n.action_url}
                      target="_blank"
                      rel="noreferrer"
                      className="mt-1 inline-block max-w-full truncate text-sm font-semibold text-primary-700 hover:underline"
                    >
                      {n.action_url}
                    </a>
                  )}
                  <div className="mt-2 flex flex-wrap items-center gap-2 text-sm text-ink-soft">
                    <span className="inline-flex items-center gap-1 rounded-full bg-surface-muted px-2 py-0.5 font-semibold">
                      {n.target === "all" ? (
                        <Users className="h-3.5 w-3.5" />
                      ) : (
                        <Store className="h-3.5 w-3.5" />
                      )}
                      {targetLabel(n)}
                    </span>
                    <span className="inline-flex items-center gap-1 rounded-full bg-success-soft px-2 py-0.5 font-bold text-success">
                      <CheckCheck className="h-3.5 w-3.5" />
                      {n.read_count} read
                    </span>
                    <span>{formatWhen(n.created_at)}</span>
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => setPendingDelete(n)}
                  aria-label="Delete notification"
                  className="shrink-0 rounded-control p-2 text-ink-soft hover:bg-danger-soft hover:text-danger"
                >
                  <Trash2 className="h-5 w-5" />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      <ComposeNotificationModal
        open={composeOpen}
        onClose={() => setComposeOpen(false)}
        onSent={reload}
      />

      <ConfirmDialog
        open={pendingDelete !== null}
        title="Delete this notification?"
        body="It will be removed for every shop that received it. This can't be undone."
        confirmLabel="Delete"
        cancelLabel="Keep"
        destructive
        onConfirm={confirmDelete}
        onCancel={() => setPendingDelete(null)}
      />
    </div>
  );
}
