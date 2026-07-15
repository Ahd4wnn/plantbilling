/**
 * Dependency-free SVG charts for the admin command center. Kept intentionally
 * small: a Sparkline for inline trends and a Bars chart for the dashboard.
 */

interface SparklineProps {
  values: number[];
  className?: string;
  strokeClass?: string; // tailwind text-* colour (uses currentColor)
  height?: number;
}

/** A tiny inline trend line. Renders nothing meaningful for <2 points. */
export function Sparkline({ values, className = "", strokeClass = "text-primary-600", height = 28 }: SparklineProps) {
  const w = 100;
  const h = height;
  if (values.length === 0) {
    return <svg viewBox={`0 0 ${w} ${h}`} className={className} preserveAspectRatio="none" />;
  }
  const max = Math.max(...values, 1);
  const min = Math.min(...values, 0);
  const span = max - min || 1;
  const step = values.length > 1 ? w / (values.length - 1) : 0;
  const pts = values.map((v, i) => {
    const x = i * step;
    const y = h - ((v - min) / span) * (h - 2) - 1;
    return `${x.toFixed(2)},${y.toFixed(2)}`;
  });
  const line = pts.join(" ");
  const area = `0,${h} ${line} ${w},${h}`;
  return (
    <svg viewBox={`0 0 ${w} ${h}`} className={`${strokeClass} ${className}`} preserveAspectRatio="none">
      <polygon points={area} fill="currentColor" opacity={0.1} />
      <polyline points={line} fill="none" stroke="currentColor" strokeWidth={2} strokeLinejoin="round" strokeLinecap="round" vectorEffect="non-scaling-stroke" />
    </svg>
  );
}

interface BarsProps {
  data: { label: string; value: number }[];
  className?: string;
  height?: number;
  format?: (v: number) => string;
}

/** A responsive bar chart with hover tooltips (title). */
export function Bars({ data, className = "", height = 180, format }: BarsProps) {
  const max = Math.max(...data.map((d) => d.value), 1);
  return (
    <div className={`flex items-end gap-1 ${className}`} style={{ height }}>
      {data.map((d, i) => {
        const pct = (d.value / max) * 100;
        const fmt = format ? format(d.value) : String(d.value);
        return (
          <div key={i} className="group relative flex flex-1 flex-col items-center justify-end" title={`${d.label}: ${fmt}`}>
            <div
              className="w-full rounded-t bg-primary-500/80 transition-all group-hover:bg-primary-600"
              style={{ height: `${Math.max(pct, 1.5)}%` }}
            />
          </div>
        );
      })}
    </div>
  );
}
