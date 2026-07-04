import { useCallback, useEffect, useState } from "react";
import { listCrashReports, type CrashReportRow } from "@/api/crash";
import { friendlyError } from "@/api/client";
import { Spinner } from "@/components/Spinner";
import { Button } from "@/components/Button";
import { ChevronDown, ChevronRight, Smartphone } from "lucide-react";

const PAGE_SIZE = 50;

function formatWhen(iso: string): string {
  return new Intl.DateTimeFormat("en-IN", {
    day: "numeric",
    month: "short",
    year: "numeric",
    hour: "numeric",
    minute: "2-digit",
  }).format(new Date(iso));
}

/** First non-empty line of a stack trace — the exception summary. */
function crashHeadline(trace: string | null): string {
  if (!trace) return "Unknown error";
  const first = trace.split("\n").find((l) => l.trim().length > 0);
  return first?.trim() ?? "Unknown error";
}

export function CrashReportsPage() {
  const [rows, setRows] = useState<CrashReportRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [hasMore, setHasMore] = useState(false);
  const [expanded, setExpanded] = useState<Set<string>>(new Set());

  const loadFirst = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await listCrashReports(PAGE_SIZE, 0);
      setRows(res.items);
      setHasMore(res.has_more);
    } catch (e) {
      setError(friendlyError(e, "Couldn't load crash reports."));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadFirst();
  }, [loadFirst]);

  const loadMore = async () => {
    setLoadingMore(true);
    try {
      const res = await listCrashReports(PAGE_SIZE, rows.length);
      setRows((prev) => [...prev, ...res.items]);
      setHasMore(res.has_more);
    } catch {
      // keep current rows; the button stays for retry
    } finally {
      setLoadingMore(false);
    }
  };

  const toggle = (id: string) =>
    setExpanded((prev) => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });

  return (
    <div>
      <div>
        <h1 className="text-2xl font-extrabold text-ink">Crash reports</h1>
        <p className="text-sm text-ink-soft">
          {loading ? "Loading…" : `Latest app crashes reported from users' devices`}
        </p>
      </div>

      {loading ? (
        <div className="flex justify-center py-16">
          <Spinner className="h-8 w-8 text-primary-600" />
        </div>
      ) : error ? (
        <div className="py-12 text-center">
          <p className="text-base font-semibold text-danger">{error}</p>
          <Button variant="secondary" size="tap" className="mt-4" onClick={loadFirst}>
            Try again
          </Button>
        </div>
      ) : rows.length === 0 ? (
        <p className="py-12 text-center text-base text-ink-soft">
          No crashes reported. 🎉 The app hasn't sent any crash reports yet.
        </p>
      ) : (
        <>
          <div className="mt-4 space-y-3">
            {rows.map((c) => {
              const isOpen = expanded.has(c.id);
              return (
                <div key={c.id} className="rounded-card border border-border bg-surface shadow-card">
                  <button
                    type="button"
                    onClick={() => toggle(c.id)}
                    className="flex w-full items-start gap-3 px-4 py-3 text-left"
                  >
                    <span className="mt-0.5 text-ink-soft">
                      {isOpen ? <ChevronDown className="h-5 w-5" /> : <ChevronRight className="h-5 w-5" />}
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block truncate font-semibold text-ink">{crashHeadline(c.stack_trace)}</span>
                      <span className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-ink-soft">
                        <span>{formatWhen(c.created_at)}</span>
                        {c.device && (
                          <span className="inline-flex items-center gap-1">
                            <Smartphone className="h-3.5 w-3.5" />
                            {c.device}
                          </span>
                        )}
                        {c.android_version && <span>Android {c.android_version}</span>}
                        {c.app_version && <span>v{c.app_version}</span>}
                      </span>
                    </span>
                  </button>

                  {isOpen && (
                    <div className="border-t border-border px-4 py-3">
                      {c.user_comment && (
                        <p className="mb-2 text-sm text-ink">
                          <span className="font-semibold">User note:</span> {c.user_comment}
                        </p>
                      )}
                      <pre className="max-h-96 overflow-auto rounded-control bg-surface-muted p-3 text-xs leading-relaxed text-ink">
                        {c.stack_trace ?? "No stack trace captured."}
                      </pre>
                    </div>
                  )}
                </div>
              );
            })}
          </div>

          {hasMore && (
            <Button
              variant="secondary"
              size="action"
              className="mt-4"
              loading={loadingMore}
              loadingLabel="Loading…"
              onClick={loadMore}
            >
              Load more
            </Button>
          )}
        </>
      )}
    </div>
  );
}
