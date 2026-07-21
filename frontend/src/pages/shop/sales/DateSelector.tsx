import { ChevronLeft, ChevronRight, Calendar } from "lucide-react";
import { friendlyDayLabel, shiftDay, todayISO } from "@/lib/datetime";

interface DateSelectorProps {
  value: string; // YYYY-MM-DD
  onChange: (ymd: string) => void;
}

/** Large day picker: ‹ prev / readable label (+ hidden native picker) / next ›. */
export function DateSelector({ value, onChange }: DateSelectorProps) {
  const today = todayISO();
  const atToday = value === today;

  return (
    <div className="flex items-center gap-2">
      <StepBtn label="Previous day" onClick={() => onChange(shiftDay(value, -1))}>
        <ChevronLeft className="h-6 w-6" />
      </StepBtn>

      <label className="relative flex h-12 flex-1 items-center justify-center gap-2 rounded-control border-2 border-border bg-white px-3 text-lg font-bold text-ink">
        <Calendar className="h-5 w-5 text-primary-600" />
        {friendlyDayLabel(value)}
        {/* Native date input overlaid for full-day selection; visually hidden. */}
        <input
          type="date"
          value={value}
          max={today}
          onChange={(e) => e.target.value && onChange(e.target.value)}
          className="absolute inset-0 cursor-pointer opacity-0"
          aria-label="Pick a date"
        />
      </label>

      <StepBtn label="Next day" onClick={() => onChange(shiftDay(value, 1))} disabled={atToday}>
        <ChevronRight className="h-6 w-6" />
      </StepBtn>

      {!atToday && (
        <button
          type="button"
          onClick={() => onChange(today)}
          className="h-12 shrink-0 rounded-control bg-primary-50 px-4 text-base font-semibold text-primary-700"
        >
          Today
        </button>
      )}
    </div>
  );
}

function StepBtn({
  label,
  onClick,
  disabled,
  children,
}: {
  label: string;
  onClick: () => void;
  disabled?: boolean;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      aria-label={label}
      className="flex h-12 w-12 shrink-0 items-center justify-center rounded-control border-2 border-border
                 text-2xl font-bold text-ink hover:bg-surface-muted disabled:opacity-40"
    >
      {children}
    </button>
  );
}
