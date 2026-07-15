import { useCallback, useEffect, useMemo, useState } from "react";
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
function fromNow(iso: string): string {
  const mins = Math.floor((Date.now() - new Date(iso).getTime()) / 60000);
  if (mins < 60) return `${Math.max(mins, 0)}m ago`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `${hrs}h ago`;
  const days = Math.floor(hrs / 24);
  return `${days}d ago`;
}

/** First non-empty line of a stack trace — the exception summary. */
function crashHeadline(trace: string | null): string {
  if (!trace) return "Unknown error";
  const first = trace.split("\n").find((l) => l.trim().length > 0);
  return first?.trim() ?? "Unknown error";
}

/** Group key: the exception class + message, ignoring line-specific noise. */
function signature(trace: string | null): string {
  return crashHeadline(trace);
}

interface CrashGroup {
  key: string;
  headline: string;
  count: number;
  lastSeen: string;
  versions: string[];
  devices: string[];
  items: CrashReportRow[];
}

function groupCrashes(rows: CrashReportRow[]): CrashGroup[] {
  const map = new Map<string, CrashGroup>();
  for (const c of rows) {
    const key = signature(c.stack_trace);
    let g = map.get(key);
    if (!g) {
      g = { key, headline: crashHeadline(c.stack_trace), count: 0, lastSeen: c.created_at, versions: [], devices: [], items: [] };
      map.set(key, g);
    }
    g.count += 1;
    g.items.push(c);
    if (new Date(c.created_at) > new Date(g.lastSeen)) g.lastSeen = c.created_at;
    if (c.app_version && !g.versions.includes(c.app_version)) g.versions.push(c.app_version);
    if (c.device && !g.devices.includes(c.device)) g.devices.push(c.device);
  }
  return [...map.values()].sort((a, b) => b.count - a.count || +new Date(b.lastSeen) - +new Date(a.lastSeen));
}

export function CrashReportsPage() {
  const [rows, setRows] = useState<CrashReportRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [hasMore, setHasMore] = useState(false);
  const [expanded, setExpanded] = useState<Set<string>>(new Set());
  const [view, setView] = useState<"grouped" | "timeline">("grouped");

  const groups = useMemo(() => groupCrashes(rows), [rows]);

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
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-extrabold text-ink">Crash reports</h1>
          <p className="text-sm text-ink-soft">
            {loading
              ? "Loading…"
              : view === "grouped"
                ? `${groups.length} distinct issue${groups.length === 1 ? "" : "s"} across ${rows.length} report${rows.length === 1 ? "" : "s"}`
                : "Latest app crashes reported from users' devices"}
          </p>
        </div>
        {!loading && rows.length > 0 && (
          <div className="flex rounded-control border border-border bg-white p-1">
            {(["grouped", "timeline"] as const).map((v) => (
              <button
                key={v}
                type="button"
                onClick={() => setView(v)}
                className={["rounded-control px-3 py-1.5 text-sm font-semibold capitalize transition-colors", view === v ? "bg-primary-600 text-white" : "text-ink-soft hover:bg-surface-muted"].join(" ")}
              >
                {v}
              </button>
            ))}
          </div>
        )}
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
      ) : view === "grouped" ? (
        <>
          <div className="mt-4 space-y-3">
            {groups.map((g) => {
              const isOpen = expanded.has(g.key);
              return (
                <div key={g.key} className="rounded-card border border-border bg-surface shadow-card">
                  <button
                    type="button"
                    onClick={() => toggle(g.key)}
                    className="flex w-full items-start gap-3 px-4 py-3 text-left"
                  >
                    <span className="mt-0.5 text-ink-soft">
                      {isOpen ? <ChevronDown className="h-5 w-5" /> : <ChevronRight className="h-5 w-5" />}
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="flex items-center gap-2">
                        <span className="min-w-0 flex-1 truncate font-semibold text-ink">{g.headline}</span>
                        <span className="shrink-0 rounded-full bg-danger-soft px-2 py-0.5 text-sm font-bold text-danger">×{g.count}</span>
                      </span>
                      <span className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-ink-soft">
                        <span>Last seen {fromNow(g.lastSeen)}</span>
                        {g.versions.length > 0 && <span>v{g.versions.join(", v")}</span>}
                        {g.devices.length > 0 && (
                          <span className="inline-flex items-center gap-1">
                            <Smartphone className="h-3.5 w-3.5" />
                            {g.devices.length === 1 ? g.devices[0] : `${g.devices.length} devices`}
                          </span>
                        )}
                      </span>
                    </span>
                  </button>

                  {isOpen && (
                    <div className="border-t border-border">
                      {g.items.map((c) => (
                        <div key={c.id} className="border-b border-border px-4 py-3 last:border-0">
                          <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-ink-soft">
                            <span>{formatWhen(c.created_at)}</span>
                            {c.device && <span className="inline-flex items-center gap-1"><Smartphone className="h-3.5 w-3.5" />{c.device}</span>}
                            {c.android_version && <span>Android {c.android_version}</span>}
                            {c.app_version && <span>v{c.app_version}</span>}
                          </div>
                          {c.user_comment && (
                            <p className="mt-1 text-sm text-ink"><span className="font-semibold">User note:</span> {c.user_comment}</p>
                          )}
                          <pre className="mt-2 max-h-72 overflow-auto rounded-control bg-surface-muted p-3 text-xs leading-relaxed text-ink">
                            {c.stack_trace ?? "No stack trace captured."}
                          </pre>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              );
            })}
          </div>

          {hasMore && (
            <Button variant="secondary" size="action" className="mt-4" loading={loadingMore} loadingLabel="Loading…" onClick={loadMore}>
              Load more
            </Button>
          )}
          <p className="mt-3 text-xs text-ink-soft/80">Grouped over the {rows.length} most recent report{rows.length === 1 ? "" : "s"}. Load more to widen the grouping.</p>
        </>
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
