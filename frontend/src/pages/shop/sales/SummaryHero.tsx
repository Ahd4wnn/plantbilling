import { useState, useEffect } from "react";
import type { BillSummary } from "@/api/sales";
import { formatINR, toPaise } from "@/lib/money";
import { friendlyDayLabel } from "@/lib/datetime";
import { Spinner } from "@/components/Spinner";
import { Button } from "@/components/Button";
import { DateSelector } from "./DateSelector";
import { createExpense, deleteExpense, updateExpense, type ExpenseRow } from "@/api/expenses";
import { BottomSheet } from "@/components/BottomSheet";
import { TextInput } from "@/components/TextInput";
import { friendlyError } from "@/api/client";
import { Trash2, Pencil } from "lucide-react";
import { useAuth } from "@/store/auth";

interface SummaryHeroProps {
  date: string;
  onDateChange: (ymd: string) => void;
  summary: BillSummary | null;
  loading: boolean;
  error: string | null;
  onRetry: () => void;
  onExpenseAdded?: () => void;
}

export function SummaryHero({
  date,
  onDateChange,
  summary,
  loading,
  error,
  onRetry,
  onExpenseAdded,
}: SummaryHeroProps) {
  const label = friendlyDayLabel(date);
  const salesLabel = label === "Today" ? "Today's Summary" : `Summary · ${label}`;
  const [expenseOpen, setExpenseOpen] = useState(false);
  const user = useAuth((s) => s.user);
  const isOwner = user?.role === "manager" || user?.role === "admin";

  const [editingExpense, setEditingExpense] = useState<ExpenseRow | null>(null);

  const handleDeleteExpense = async (id: string) => {
    if (!window.confirm("Are you sure you want to delete this expense?")) return;
    try {
      await deleteExpense(id);
      if (onExpenseAdded) onExpenseAdded();
    } catch (err) {
      alert(friendlyError(err, "Failed to delete expense."));
    }
  };

  // Chart aggregation
  const salesNum = parseFloat(summary?.total_sales || "0");
  const expensesNum = parseFloat(summary?.total_expenses || "0");
  const totalFlow = salesNum + expensesNum;
  const hasData = totalFlow > 0;

  const salesPercent = totalFlow > 0 ? (salesNum / totalFlow) * 100 : 0;
  const expensesPercent = totalFlow > 0 ? (expensesNum / totalFlow) * 100 : 0;

  const activeSlices = [];
  if (hasData) {
    if (salesNum > 0) {
      activeSlices.push({
        label: "Sales",
        amount: salesNum,
        percentage: salesPercent,
        color: "#6366f1", // Indigo
      });
    }
    if (expensesNum > 0) {
      activeSlices.push({
        label: "Expenses",
        amount: expensesNum,
        percentage: expensesPercent,
        color: "#f43f5e", // Rose
      });
    }
  }

  // SVG math
  const r = 45;
  const strokeWidth = 12;
  const C = 2 * Math.PI * r; // ~282.74

  let accumulatedPercent = 0;
  const slices = activeSlices.map((slice) => {
    const strokeDasharray = `${(slice.percentage / 100) * C} ${C}`;
    const strokeDashoffset = -(accumulatedPercent / 100) * C;

    const startAngle = (accumulatedPercent / 100) * 360 - 90;
    const endAngle = ((accumulatedPercent + slice.percentage) / 100) * 360 - 90;
    const midAngle = (startAngle + endAngle) / 2;
    const rad = (midAngle * Math.PI) / 180;
    const textX = 60 + r * Math.cos(rad);
    const textY = 60 + r * Math.sin(rad);

    accumulatedPercent += slice.percentage;

    return {
      ...slice,
      strokeDasharray,
      strokeDashoffset,
      textX,
      textY,
    };
  });

  return (
    <section className="rounded-card border border-border bg-surface p-5 shadow-card space-y-6">
      <DateSelector value={date} onChange={onDateChange} />

      <div className="min-h-[7rem]">
        {loading ? (
          <div className="flex h-28 items-center justify-center">
            <Spinner className="h-8 w-8 text-primary-600" />
          </div>
        ) : error ? (
          <div className="py-6 text-center">
            <p className="text-base font-semibold text-danger">{error}</p>
            <Button variant="secondary" size="tap" className="mt-3" onClick={onRetry}>
              Try again
            </Button>
          </div>
        ) : summary ? (
          <div className="space-y-6">
            <div>
              <p className="text-base font-semibold text-ink-soft">{salesLabel}</p>
              
              {/* Metrics Cards Grid */}
              <div className="mt-3 grid grid-cols-1 sm:grid-cols-3 gap-4 border-b border-border pb-5">
                <div className="bg-slate-50 border border-slate-100 rounded-xl p-4">
                  <span className="text-xs font-bold text-ink-soft uppercase tracking-wider block">Total Sales</span>
                  <span className="text-3xl font-extrabold tracking-tight text-ink mt-1 block">
                    {formatINR(toPaise(summary.total_sales))}
                  </span>
                  <span className="text-xs text-ink-soft mt-1 block">
                    {summary.bill_count} {summary.bill_count === 1 ? "bill" : "bills"}
                  </span>
                </div>

                <div className="bg-slate-50 border border-slate-100 rounded-xl p-4">
                  <span className="text-xs font-bold text-ink-soft uppercase tracking-wider block">Total Expenses</span>
                  <span className="text-3xl font-extrabold tracking-tight text-danger mt-1 block">
                    {parseFloat(summary.total_expenses) > 0 ? "− " : ""}
                    {formatINR(toPaise(summary.total_expenses))}
                  </span>
                  <span className="text-xs text-ink-soft mt-1 block">
                    Daily spending
                  </span>
                </div>

                <div className="bg-slate-50 border border-slate-100 rounded-xl p-4">
                  <span className="text-xs font-bold text-ink-soft uppercase tracking-wider block">Net Income</span>
                  <span className={`text-3xl font-extrabold tracking-tight mt-1 block ${
                    parseFloat(summary.net_sales) >= 0 ? "text-emerald-600" : "text-danger"
                  }`}>
                    {parseFloat(summary.net_sales) < 0 ? "− " : ""}
                    {formatINR(toPaise(Math.abs(parseFloat(summary.net_sales)).toString()))}
                  </span>
                  <span className="text-xs text-ink-soft mt-1 block">
                    Sales minus expenses
                  </span>
                </div>
              </div>
            </div>

            {/* Cash, UPI, Due breakdown */}
            <div className="grid grid-cols-3 gap-3">
              <Stat label="Cash" value={formatINR(toPaise(summary.cash_total))} accent="cash" />
              <Stat label="UPI" value={formatINR(toPaise(summary.upi_total))} accent="upi" />
              <Stat label="Due" value={formatINR(toPaise(summary.due_total || "0"))} accent="due" />
            </div>

            {/* Spending & Cashflow Chart Section */}
            <div className="border-t border-border pt-6">
              <div className="flex items-center justify-between gap-3 mb-5">
                <h3 className="text-lg font-bold text-ink">Your spending</h3>
                <Button
                  variant="secondary"
                  size="tap"
                  className="border-2 bg-white flex items-center gap-1.5 font-bold hover:bg-slate-50 text-sm"
                  onClick={() => setExpenseOpen(true)}
                >
                  + Add Expense
                </Button>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-6 items-center">
                {/* Donut Chart Comparison */}
                <div className="flex flex-col items-center justify-center p-4 bg-slate-50/50 border border-slate-100 rounded-2xl">
                  <div className="relative w-44 h-44">
                    <svg viewBox="0 0 120 120" className="w-full h-full">
                      {/* Fallback grey circle */}
                      {!hasData && (
                        <circle
                          cx="60"
                          cy="60"
                          r={r}
                          stroke="#e2e8f0"
                          strokeWidth={strokeWidth}
                          fill="transparent"
                        />
                      )}

                      {/* Dynamic Slices (Sales vs Expenses) */}
                      {hasData &&
                        slices.map((slice, i) => (
                          <circle
                            key={i}
                            cx="60"
                            cy="60"
                            r={r}
                            stroke={slice.color}
                            strokeWidth={strokeWidth}
                            fill="transparent"
                            strokeDasharray={slice.strokeDasharray}
                            strokeDashoffset={slice.strokeDashoffset}
                            transform="rotate(-90 60 60)"
                            style={{ transition: "stroke-dashoffset 0.5s ease" }}
                          />
                        ))}

                      {/* Slice Labels */}
                      {hasData &&
                        slices.map((slice, i) => {
                          if (slice.percentage < 10) return null;
                          return (
                            <text
                              key={`text-${i}`}
                              x={slice.textX}
                              y={slice.textY}
                              fill="#ffffff"
                              fontSize="8"
                              fontWeight="bold"
                              textAnchor="middle"
                              dominantBaseline="central"
                              className="select-none font-sans"
                            >
                              {Math.round(slice.percentage)}%
                            </text>
                          );
                        })}
                    </svg>

                    {/* Centered Net Income */}
                    <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
                      <span className="text-[10px] font-semibold text-ink-soft uppercase tracking-wider">
                        Net Income
                      </span>
                      <span className={`text-base font-black mt-0.5 ${
                        parseFloat(summary.net_sales) >= 0 ? "text-emerald-600" : "text-danger"
                      }`}>
                        {parseFloat(summary.net_sales) < 0 ? "− " : ""}
                        {formatINR(toPaise(Math.abs(parseFloat(summary.net_sales)).toString()))}
                      </span>
                    </div>
                  </div>

                  {/* Horizontal Custom Legend */}
                  <div className="flex flex-wrap items-center justify-center gap-x-6 gap-y-2 mt-5">
                    <div className="flex items-center text-xs font-semibold text-ink-soft">
                      <span
                        className="h-3 w-3 rounded-full border-2 bg-white mr-1.5 inline-block shrink-0"
                        style={{ borderColor: "#6366f1" }}
                      />
                      <span>
                        Sales: {formatINR(toPaise(summary.total_sales))} {hasData && `(${Math.round(salesPercent)}%)`}
                      </span>
                    </div>
                    <div className="flex items-center text-xs font-semibold text-ink-soft">
                      <span
                        className="h-3 w-3 rounded-full border-2 bg-white mr-1.5 inline-block shrink-0"
                        style={{ borderColor: "#f43f5e" }}
                      />
                      <span>
                        Expenses: {formatINR(toPaise(summary.total_expenses))} {hasData && `(${Math.round(expensesPercent)}%)`}
                      </span>
                    </div>
                  </div>

                  {/* Expense Ratio Progress Bar */}
                  {salesNum > 0 && (
                    <div className="w-full mt-4 px-2">
                      <div className="flex justify-between text-xs font-bold text-ink-soft mb-1">
                        <span>Expense Ratio</span>
                        <span className={expensesNum > salesNum ? "text-danger" : "text-ink"}>
                          {Math.round((expensesNum / salesNum) * 100)}% of sales
                        </span>
                      </div>
                      <div className="h-2 w-full bg-slate-200 rounded-full overflow-hidden">
                        <div
                          className={`h-full rounded-full transition-all duration-500 ease-out ${
                            expensesNum > salesNum ? "bg-rose-500" : "bg-primary-600"
                          }`}
                          style={{ width: `${Math.min((expensesNum / salesNum) * 100, 100)}%` }}
                        />
                      </div>
                      {expensesNum > salesNum && (
                        <p className="text-[10px] text-danger font-bold mt-1 text-center">
                          ⚠️ Expenses exceed daily sales!
                        </p>
                      )}
                    </div>
                  )}
                </div>

                {/* Expenses History List */}
                <div className="flex flex-col h-full justify-between">
                  <div>
                    <h4 className="text-sm font-bold text-ink-soft mb-3">Expenses list</h4>
                    {summary.expenses.length === 0 ? (
                      <div className="py-10 text-center border-2 border-dashed border-slate-200 rounded-xl bg-slate-50/50">
                        <p className="text-sm font-medium text-ink-soft">No expenses recorded for this day.</p>
                      </div>
                    ) : (
                      <div className="divide-y divide-border border-y border-border max-h-48 overflow-y-auto pr-1">
                        {summary.expenses.map((e) => (
                          <div key={e.id} className="py-2.5 flex items-center justify-between gap-3 text-sm">
                            <div className="min-w-0 flex-1">
                              <div className="font-semibold text-ink truncate">{e.reason}</div>
                              <div className="text-xs text-ink-soft mt-0.5">
                                {new Intl.DateTimeFormat("en-IN", {
                                  hour: "numeric",
                                  minute: "numeric",
                                }).format(new Date(e.created_at))}
                              </div>
                            </div>
                            <div className="flex items-center gap-3 shrink-0">
                              <span className="font-bold text-danger">
                                − ₹{parseFloat(e.amount).toFixed(2)}
                              </span>
                              {isOwner && (
                                <div className="flex items-center gap-1">
                                  <button
                                    type="button"
                                    onClick={() => setEditingExpense(e)}
                                    className="p-1 text-ink-faint hover:text-primary-600 hover:bg-primary-50 rounded-lg transition-all"
                                    title="Edit expense"
                                  >
                                    <Pencil className="h-4 w-4" />
                                  </button>
                                  <button
                                    type="button"
                                    onClick={() => handleDeleteExpense(e.id)}
                                    className="p-1 text-ink-faint hover:text-danger hover:bg-danger-soft/10 rounded-lg transition-all"
                                    title="Delete expense"
                                  >
                                    <Trash2 className="h-4.5 w-4.5" />
                                  </button>
                                </div>
                              )}
                            </div>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                </div>
              </div>
            </div>
          </div>
        ) : null}
      </div>

      <CreateExpenseSheet
        open={expenseOpen}
        onClose={() => setExpenseOpen(false)}
        onCreated={() => {
          if (onExpenseAdded) onExpenseAdded();
        }}
      />

      <EditExpenseSheet
        expense={editingExpense}
        onClose={() => setEditingExpense(null)}
        onCreated={() => {
          if (onExpenseAdded) onExpenseAdded();
        }}
      />
    </section>
  );
}

function Stat({ label, value, accent }: { label: string; value: string; accent?: "cash" | "upi" | "due" }) {
  const labelColor =
    accent === "cash"
      ? "text-cash"
      : accent === "upi"
        ? "text-upi"
        : accent === "due"
          ? "text-danger"
          : "text-ink-soft";
  return (
    <div className="rounded-control bg-surface-muted px-4 py-3">
      <div className={`text-base font-semibold ${labelColor}`}>{label}</div>
      <div className="mt-0.5 text-xl font-extrabold text-ink">{value}</div>
    </div>
  );
}

function CreateExpenseSheet({
  open,
  onClose,
  onCreated,
}: {
  open: boolean;
  onClose: () => void;
  onCreated: () => void;
}) {
  const [amount, setAmount] = useState("");
  const [reason, setReason] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    setAmount("");
    setReason("");
    setError(null);
  }, [open]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!amount.trim() || !reason.trim()) return;

    setLoading(true);
    setError(null);

    try {
      await createExpense(amount.trim(), reason.trim());
      onCreated();
      onClose();
    } catch (err) {
      setError(friendlyError(err, "Couldn't record expense."));
    } finally {
      setLoading(false);
    }
  };

  return (
    <BottomSheet
      open={open}
      onClose={onClose}
      title="Record Nursery Expense"
      footer={
        <Button
          variant="primary"
          size="action"
          className="w-full font-bold"
          disabled={!amount.trim() || !reason.trim() || loading}
          loading={loading}
          onClick={handleSubmit}
        >
          Save Expense
        </Button>
      }
    >
      <form onSubmit={handleSubmit} className="space-y-4">
        {error && (
          <p className="rounded-control bg-danger-soft px-4 py-3 text-base font-semibold text-danger">
            {error}
          </p>
        )}

        <TextInput
          label="Amount (INR)"
          type="text"
          inputMode="decimal"
          placeholder="e.g. 250.00"
          value={amount}
          onChange={(e) => setAmount(e.target.value.replace(/[^0-9.]/g, ""))}
          disabled={loading}
          required
        />

        <TextInput
          label="Reason / Description"
          type="text"
          placeholder="e.g. Soil bag purchase, electric bill"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          disabled={loading}
          required
          autoComplete="off"
        />

        <div className="flex flex-wrap gap-1.5 mt-1.5">
          {["Tea & Snacks", "Soil & Pots", "Electricity", "Labor Wages", "Transport", "Others"].map((s) => (
            <button
              key={s}
              type="button"
              onClick={() => setReason(s)}
              className={`px-2.5 py-1 rounded-full text-xs font-semibold border transition-all ${
                reason === s
                  ? "bg-primary-50 text-primary-700 border-primary-300 ring-2 ring-primary-600/10"
                  : "bg-white text-ink-soft border-border hover:bg-slate-50"
              }`}
            >
              {s}
            </button>
          ))}
        </div>
      </form>
    </BottomSheet>
  );
}

function EditExpenseSheet({
  expense,
  onClose,
  onCreated,
}: {
  expense: ExpenseRow | null;
  onClose: () => void;
  onCreated: () => void;
}) {
  const [amount, setAmount] = useState("");
  const [reason, setReason] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!expense) return;
    setAmount(parseFloat(expense.amount).toFixed(2));
    setReason(expense.reason);
    setError(null);
  }, [expense]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!expense || !amount.trim() || !reason.trim()) return;

    setLoading(true);
    setError(null);

    try {
      await updateExpense(expense.id, amount.trim(), reason.trim());
      onCreated();
      onClose();
    } catch (err) {
      setError(friendlyError(err, "Couldn't save changes."));
    } finally {
      setLoading(false);
    }
  };

  return (
    <BottomSheet
      open={expense !== null}
      onClose={onClose}
      title="Edit Nursery Expense"
      footer={
        <Button
          variant="primary"
          size="action"
          className="w-full font-bold"
          disabled={!amount.trim() || !reason.trim() || loading}
          loading={loading}
          onClick={handleSubmit}
        >
          Save Changes
        </Button>
      }
    >
      <form onSubmit={handleSubmit} className="space-y-4">
        {error && (
          <p className="rounded-control bg-danger-soft px-4 py-3 text-base font-semibold text-danger">
            {error}
          </p>
        )}

        <TextInput
          label="Amount (INR)"
          type="text"
          inputMode="decimal"
          placeholder="e.g. 250.00"
          value={amount}
          onChange={(e) => setAmount(e.target.value.replace(/[^0-9.]/g, ""))}
          disabled={loading}
          required
        />

        <TextInput
          label="Reason / Description"
          type="text"
          placeholder="e.g. Soil bag purchase, electric bill"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          disabled={loading}
          required
          autoComplete="off"
        />

        <div className="flex flex-wrap gap-1.5 mt-1.5">
          {["Tea & Snacks", "Soil & Pots", "Electricity", "Labor Wages", "Transport", "Others"].map((s) => (
            <button
              key={s}
              type="button"
              onClick={() => setReason(s)}
              className={`px-2.5 py-1 rounded-full text-xs font-semibold border transition-all ${
                reason === s
                  ? "bg-primary-50 text-primary-700 border-primary-300 ring-2 ring-primary-600/10"
                  : "bg-white text-ink-soft border-border hover:bg-slate-50"
              }`}
            >
              {s}
            </button>
          ))}
        </div>
      </form>
    </BottomSheet>
  );
}
