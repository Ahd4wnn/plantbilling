import { Leaf } from "lucide-react";

interface LeafMarkProps {
  className?: string;
}

/** Plantora leaf mark, using the lucide Leaf glyph. */
export function LeafMark({ className = "h-8 w-8" }: LeafMarkProps) {
  return <Leaf className={className} aria-hidden="true" />;
}

/** Text + leaf wordmark used on the login screen and top bar. */
export function Wordmark({ className = "" }: { className?: string }) {
  return (
    <div className={`flex items-center gap-2 ${className}`}>
      <LeafMark className="h-8 w-8 text-primary-600" />
      <span className="text-2xl font-extrabold tracking-tight text-ink">PlantBill</span>
    </div>
  );
}
