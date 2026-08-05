import { useState, useEffect } from "react";
import { BottomSheet } from "@/components/BottomSheet";
import { Button } from "@/components/Button";
import { Download, Share2, Send, AlertCircle, CheckCircle } from "lucide-react";
import { fetchDetailedReport, downloadReportCSV, sendReportWhatsApp, type DetailedReportResponse } from "@/api/sales";
import { friendlyError } from "@/api/client";
import { todayISO } from "@/lib/datetime";
import { formatINR, toPaise } from "@/lib/money";

interface DetailedReportSheetProps {
  open: boolean;
  onClose: () => void;
  staffList: Array<{ id: string; email: string }>;
  currentUserEmail: string | undefined;
  currentUserId: string | undefined;
  shopId?: string;
}

type Period = "daily" | "weekly" | "monthly" | "custom";

function getWeekBounds(dateStr: string) {
  const date = new Date(dateStr);
  const day = date.getDay();
  // adjust when day is sunday (0) to make monday (1) the start of the week
  const diff = date.getDate() - day + (day === 0 ? -6 : 1);
  const monday = new Date(date.setDate(diff));
  const sunday = new Date(monday);
  sunday.setDate(monday.getDate() + 6);

  const toISO = (d: Date) => d.toISOString().split("T")[0];
  return {
    from: toISO(monday),
    to: toISO(sunday),
  };
}

function getMonthBounds(yearMonthStr: string) {
  const [year, month] = yearMonthStr.split("-").map(Number);
  const firstDay = new Date(year, month - 1, 1);
  const lastDay = new Date(year, month, 0);

  const toISO = (d: Date) => {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, "0");
    const day = String(d.getDate()).padStart(2, "0");
    return `${y}-${m}-${day}`;
  };

  return {
    from: toISO(firstDay),
    to: toISO(lastDay),
  };
}

export function DetailedReportSheet({ open, onClose, staffList, currentUserEmail, currentUserId, shopId }: DetailedReportSheetProps) {
  const [period, setPeriod] = useState<Period>("daily");
  const [selectedDate, setSelectedDate] = useState(todayISO());
  const [selectedMonth, setSelectedMonth] = useState(todayISO().substring(0, 7));
  const [dateFrom, setDateFrom] = useState(todayISO());
  const [dateTo, setDateTo] = useState(todayISO());
  const [staffId, setStaffId] = useState("");

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [report, setReport] = useState<DetailedReportResponse | null>(null);

  // WhatsApp states
  const [whatsappPhone, setWhatsappPhone] = useState("");
  const [sendingWa, setSendingWa] = useState(false);
  const [waResult, setWaResult] = useState<{ status: string; detail: string; url: string | null } | null>(null);
  const [waError, setWaError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    setReport(null);
    setError(null);
    setWhatsappPhone("");
    setWaResult(null);
    setWaError(null);
    setPeriod("daily");
    setSelectedDate(todayISO());
    setSelectedMonth(todayISO().substring(0, 7));
    setDateFrom(todayISO());
    setDateTo(todayISO());
    setStaffId("");
  }, [open]);

  // Helper to calculate active dates based on selection
  const getDates = () => {
    if (period === "daily") {
      return { from: selectedDate, to: selectedDate };
    } else if (period === "weekly") {
      return getWeekBounds(selectedDate);
    } else if (period === "monthly") {
      return getMonthBounds(selectedMonth);
    } else {
      return { from: dateFrom, to: dateTo };
    }
  };

  const handleGenerate = async () => {
    setLoading(true);
    setError(null);
    setReport(null);
    setWaResult(null);
    setWaError(null);

    const { from, to } = getDates();

    try {
      const data = await fetchDetailedReport({
        date_from: from,
        date_to: to,
        created_by: staffId || undefined,
        shopId,
      });
      setReport(data);
    } catch (err) {
      setError(friendlyError(err, "Failed to load report. Please try again."));
    } finally {
      setLoading(false);
    }
  };

  const handleDownloadCSV = async () => {
    const { from, to } = getDates();
    try {
      await downloadReportCSV({
        date_from: from,
        date_to: to,
        created_by: staffId || undefined,
        shopId,
      });
    } catch (err) {
      alert(friendlyError(err, "Failed to download the Excel report."));
    }
  };

  const handleSendWhatsApp = async () => {
    if (!whatsappPhone.trim()) {
      setWaError("Please enter a valid phone number.");
      return;
    }
    setSendingWa(true);
    setWaError(null);
    setWaResult(null);

    const { from, to } = getDates();

    try {
      const res = await sendReportWhatsApp({
        phone: whatsappPhone.trim(),
        date_from: from,
        date_to: to,
        created_by: staffId || undefined,
        shop_id: shopId,
      });

      setWaResult({
        status: res.status,
        detail: res.detail,
        url: res.wa_me_url,
      });

      if (res.wa_me_url && res.status === "fallback_wa_me") {
        // Automatically open the fallback link for manual sharing in a new window
        window.open(res.wa_me_url, "_blank");
      }
    } catch (err) {
      setWaError(friendlyError(err, "Failed to send report on WhatsApp."));
    } finally {
      setSendingWa(false);
    }
  };

  return (
    <BottomSheet
      open={open}
      onClose={onClose}
      title="Detailed Sales Report"
      footer={
        report && (
          <div className="flex gap-3 w-full">
            <Button
              variant="secondary"
              size="action"
              className="flex-1 border-2 bg-white flex items-center justify-center gap-2"
              onClick={handleDownloadCSV}
            >
              <Download className="h-5 w-5" /> Download Excel report
            </Button>
            <Button
              variant="primary"
              size="action"
              className="flex-1 flex items-center justify-center gap-2"
              onClick={() => {
                const element = document.getElementById("report-whatsapp-section");
                if (element) {
                  element.scrollIntoView({ behavior: "smooth" });
                }
              }}
            >
              <Share2 className="h-5 w-5" /> Share Report
            </Button>
          </div>
        )
      }
    >
      <div className="space-y-6">
        {/* Step 1: Config Filters */}
        <div className="rounded-xl border border-border bg-slate-50/50 p-4 space-y-4">
          <h3 className="font-bold text-ink text-base">1. Select Report Period</h3>
          
          {/* Period Selector */}
          <div className="grid grid-cols-4 gap-1.5 rounded-xl bg-surface-muted p-1 border border-border">
            {(["daily", "weekly", "monthly", "custom"] as Period[]).map((p) => (
              <button
                key={p}
                type="button"
                onClick={() => setPeriod(p)}
                className={[
                  "py-2 text-center text-sm font-bold rounded-lg capitalize transition-all",
                  period === p
                    ? "bg-white text-primary-700 shadow-sm"
                    : "text-ink-soft hover:text-ink hover:bg-white/50",
                ].join(" ")}
              >
                {p}
              </button>
            ))}
          </div>

          {/* Dynamic Date Inputs based on Period */}
          <div className="space-y-3">
            {period === "daily" && (
              <div>
                <label className="field-label">Date</label>
                <input
                  type="date"
                  value={selectedDate}
                  max={todayISO()}
                  onChange={(e) => e.target.value && setSelectedDate(e.target.value)}
                  className="field"
                />
              </div>
            )}

            {period === "weekly" && (
              <div>
                <label className="field-label">Choose week (select any day in the week)</label>
                <input
                  type="date"
                  value={selectedDate}
                  max={todayISO()}
                  onChange={(e) => e.target.value && setSelectedDate(e.target.value)}
                  className="field"
                />
                {selectedDate && (
                  <p className="mt-1.5 text-sm font-semibold text-primary-700">
                    Week Bounds: {getWeekBounds(selectedDate).from} to {getWeekBounds(selectedDate).to}
                  </p>
                )}
              </div>
            )}

            {period === "monthly" && (
              <div>
                <label className="field-label">Month</label>
                <input
                  type="month"
                  value={selectedMonth}
                  max={todayISO().substring(0, 7)}
                  onChange={(e) => e.target.value && setSelectedMonth(e.target.value)}
                  className="field font-bold"
                />
                {selectedMonth && (
                  <p className="mt-1.5 text-sm font-semibold text-primary-700">
                    Month Bounds: {getMonthBounds(selectedMonth).from} to {getMonthBounds(selectedMonth).to}
                  </p>
                )}
              </div>
            )}

            {period === "custom" && (
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="field-label">From Date</label>
                  <input
                    type="date"
                    value={dateFrom}
                    max={todayISO()}
                    onChange={(e) => e.target.value && setDateFrom(e.target.value)}
                    className="field"
                  />
                </div>
                <div>
                  <label className="field-label">To Date</label>
                  <input
                    type="date"
                    value={dateTo}
                    max={todayISO()}
                    onChange={(e) => e.target.value && setDateTo(e.target.value)}
                    className="field"
                  />
                </div>
              </div>
            )}
          </div>

          {/* Salesperson Filter */}
          <div>
            <label className="field-label">Filter by Staff (optional)</label>
            <select
              value={staffId}
              onChange={(e) => setStaffId(e.target.value)}
              className="field bg-white"
            >
              <option value="">All Staff</option>
              {currentUserId && currentUserEmail && (
                <option value={currentUserId}>{currentUserEmail} (Owner)</option>
              )}
              {staffList.map((sp) => (
                <option key={sp.id} value={sp.id}>
                  {sp.email}
                </option>
              ))}
            </select>
          </div>

          <Button
            variant="primary"
            size="action"
            className="w-full flex items-center justify-center gap-2"
            onClick={handleGenerate}
            loading={loading}
            loadingLabel="Generating Report…"
          >
            Generate Report
          </Button>
        </div>

        {error && (
          <p className="rounded-control bg-danger-soft px-4 py-3 text-base font-semibold text-danger">
            {error}
          </p>
        )}

        {/* Report Results */}
        {report && (
          <div className="space-y-6 animate-fade-in">
            {/* Header / Meta */}
            <div className="text-center">
              <span className="text-sm font-bold uppercase tracking-wider text-primary-700 bg-primary-50 px-3 py-1 rounded-full">
                Sales summary
              </span>
              <p className="mt-2 text-base text-ink-soft font-semibold">
                {report.start_date === report.end_date
                  ? `For ${report.start_date}`
                  : `From ${report.start_date} to ${report.end_date}`}
              </p>
            </div>

            {/* Total sales card */}
            <div className="rounded-2xl bg-gradient-to-br from-emerald-600 to-teal-700 p-6 text-white text-center shadow-card-lg">
              <span className="text-base text-emerald-100 font-medium">Total Sales Revenue</span>
              <h2 className="text-4xl font-extrabold tracking-tight mt-1">
                {formatINR(toPaise(report.total_sales))}
              </h2>
              <div className="mt-4 flex justify-around border-t border-white/20 pt-4 text-sm font-medium">
                <div>
                  <span className="text-emerald-100 block">Total Bills</span>
                  <span className="text-lg font-bold block mt-0.5">{report.bill_count}</span>
                </div>
                <div className="border-l border-white/20 h-8 self-center" />
                <div>
                  <span className="text-emerald-100 block">Avg Bill Value</span>
                  <span className="text-lg font-bold block mt-0.5">{formatINR(toPaise(report.average_bill_value))}</span>
                </div>
              </div>
            </div>

            {/* Expenses & Net Income Metrics Grid */}
            <div className="grid grid-cols-2 gap-4">
              <div className="rounded-2xl border border-border bg-slate-50 p-4 text-center">
                <span className="text-xs font-bold text-ink-soft uppercase tracking-wider block">Total Expenses</span>
                <span className="text-2xl font-black text-danger mt-1 block">
                  {parseFloat(report.total_expenses) > 0 ? "− " : ""}
                  {formatINR(toPaise(report.total_expenses))}
                </span>
              </div>
              <div className="rounded-2xl border border-border bg-slate-50 p-4 text-center">
                <span className="text-xs font-bold text-ink-soft uppercase tracking-wider block">Net Income</span>
                <span className={`text-2xl font-black mt-1 block ${
                  parseFloat(report.net_sales) >= 0 ? "text-emerald-600" : "text-danger"
                }`}>
                  {parseFloat(report.net_sales) < 0 ? "− " : ""}
                  {formatINR(toPaise(Math.abs(parseFloat(report.net_sales)).toString()))}
                </span>
              </div>
            </div>

            {/* Split collected statistics */}
            <div className="grid grid-cols-3 gap-3">
              <div className="rounded-xl border border-border bg-white px-4 py-3 shadow-sm">
                <span className="text-base font-semibold text-cash block">Cash</span>
                <span className="text-lg font-bold text-ink block mt-0.5">{formatINR(toPaise(report.cash_total))}</span>
              </div>
              <div className="rounded-xl border border-border bg-white px-4 py-3 shadow-sm">
                <span className="text-base font-semibold text-upi block">UPI</span>
                <span className="text-lg font-bold text-ink block mt-0.5">{formatINR(toPaise(report.upi_total))}</span>
              </div>
              <div className="rounded-xl border border-border bg-white px-4 py-3 shadow-sm">
                <span className="text-base font-semibold text-danger block">Due</span>
                <span className="text-lg font-bold text-ink block mt-0.5">{formatINR(toPaise(report.due_total))}</span>
              </div>
            </div>

            {/* Sales by Category table */}
            <div className="space-y-2">
              <h4 className="text-lg font-bold text-ink">Sales by Category</h4>
              {report.categories.length === 0 ? (
                <p className="text-sm text-ink-soft italic">No categories sold during this period.</p>
              ) : (
                <div className="rounded-xl border border-border overflow-hidden bg-white shadow-sm">
                  <table className="w-full text-left text-base border-collapse">
                    <thead>
                      <tr className="bg-slate-50 border-b border-border text-ink-soft font-bold">
                        <th className="px-4 py-3">Category</th>
                        <th className="px-4 py-3 text-center">Qty</th>
                        <th className="px-4 py-3 text-right">Revenue</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-border font-medium text-ink">
                      {report.categories.map((cat, idx) => (
                        <tr key={idx} className="hover:bg-slate-50/50">
                          <td className="px-4 py-3 capitalize">{cat.category || "Uncategorized"}</td>
                          <td className="px-4 py-3 text-center text-ink-soft">{cat.quantity}</td>
                          <td className="px-4 py-3 text-right font-bold">{formatINR(toPaise(cat.total_sales))}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>

            {/* Top Products table */}
            <div className="space-y-2">
              <h4 className="text-lg font-bold text-ink">Top 15 Products Sold</h4>
              {report.top_products.length === 0 ? (
                <p className="text-sm text-ink-soft italic">No products sold during this period.</p>
              ) : (
                <div className="rounded-xl border border-border overflow-hidden bg-white shadow-sm font-medium">
                  <table className="w-full text-left text-base border-collapse">
                    <thead>
                      <tr className="bg-slate-50 border-b border-border text-ink-soft font-bold">
                        <th className="px-4 py-3">Product Name</th>
                        <th className="px-4 py-3 text-center">Qty</th>
                        <th className="px-4 py-3 text-right">Revenue</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-border text-ink">
                      {report.top_products.map((prod, idx) => (
                        <tr key={idx} className="hover:bg-slate-50/50">
                          <td className="px-4 py-3">{prod.product_name}</td>
                          <td className="px-4 py-3 text-center text-ink-soft">{prod.quantity}</td>
                          <td className="px-4 py-3 text-right font-bold">{formatINR(toPaise(prod.total_sales))}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>

            {/* Expenses by Category — total spend per category across the period */}
            <div className="space-y-2">
              <h4 className="text-lg font-bold text-ink">Expenses by Category</h4>
              {!report.expenses_by_category || report.expenses_by_category.length === 0 ? (
                <p className="text-sm text-ink-soft italic">No expenses recorded during this period.</p>
              ) : (
                <div className="rounded-xl border border-border overflow-hidden bg-white shadow-sm">
                  <table className="w-full text-left text-base border-collapse">
                    <thead>
                      <tr className="bg-slate-50 border-b border-border text-ink-soft font-bold">
                        <th className="px-4 py-3">Category</th>
                        <th className="px-4 py-3 text-center">Count</th>
                        <th className="px-4 py-3 text-right">Total Spent</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-border font-medium text-ink">
                      {report.expenses_by_category.map((c, idx) => (
                        <tr key={idx} className="hover:bg-slate-50/50">
                          <td className="px-4 py-3">{c.category}</td>
                          <td className="px-4 py-3 text-center text-ink-soft">{c.count}</td>
                          <td className="px-4 py-3 text-right font-bold text-danger">
                            − {formatINR(toPaise(c.total))}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>

            {/* Detailed Expenses Log */}
            <div className="space-y-2">
              <h4 className="text-lg font-bold text-ink">Detailed Expenses Log</h4>
              {!report.expenses || report.expenses.length === 0 ? (
                <div className="py-6 text-center border-2 border-dashed border-slate-200 rounded-xl bg-slate-50/50">
                  <p className="text-sm font-medium text-ink-soft">No expenses recorded during this period.</p>
                </div>
              ) : (
                <div className="rounded-xl border border-border overflow-hidden bg-white shadow-sm font-medium">
                  <table className="w-full text-left text-base border-collapse">
                    <thead>
                      <tr className="bg-slate-50 border-b border-border text-ink-soft font-bold">
                        <th className="px-4 py-3">Date</th>
                        <th className="px-4 py-3">Category / Remark</th>
                        <th className="px-4 py-3 text-right">Amount</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-border text-ink">
                      {report.expenses.map((exp, idx) => (
                        <tr key={idx} className="hover:bg-slate-50/50">
                          <td className="px-4 py-3 text-ink-soft text-sm">
                            {new Intl.DateTimeFormat("en-IN", {
                              day: "numeric",
                              month: "short",
                              hour: "numeric",
                              minute: "numeric",
                            }).format(new Date(exp.created_at))}
                          </td>
                          <td className="px-4 py-3">
                            <div className="font-semibold">{exp.category_name || exp.reason}</div>
                            {exp.note ? <div className="text-xs text-ink-soft mt-0.5">{exp.note}</div> : null}
                          </td>
                          <td className="px-4 py-3 text-right font-bold text-danger">
                            − ₹{parseFloat(exp.amount).toFixed(2)}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>

            {/* WhatsApp send section */}
            <div
              id="report-whatsapp-section"
              className="rounded-xl border border-border bg-slate-50 p-4 space-y-3"
            >
              <h4 className="font-bold text-ink text-base flex items-center gap-2">
                <Share2 className="h-5 w-5 text-primary-600" /> Share via WhatsApp
              </h4>
              <p className="text-sm text-ink-soft leading-relaxed">
                Send the summary statistics of this generated report directly to a phone number.
              </p>

              {waError && (
                <p className="rounded-control bg-danger-soft px-3 py-2 text-sm font-semibold text-danger">
                  {waError}
                </p>
              )}

              {waResult && (
                <div className={[
                  "rounded-control px-3 py-2 text-sm font-semibold flex items-start gap-2",
                  waResult.status === "sent_via_wati"
                    ? "bg-emerald-50 text-emerald-800"
                    : "bg-amber-50 text-amber-800"
                ].join(" ")}>
                  {waResult.status === "sent_via_wati" ? (
                    <CheckCircle className="h-5 w-5 shrink-0 text-emerald-600" />
                  ) : (
                    <AlertCircle className="h-5 w-5 shrink-0 text-amber-600" />
                  )}
                  <div>
                    <p>{waResult.detail}</p>
                    {waResult.url && (
                      <a
                        href={waResult.url}
                        target="_blank"
                        rel="noreferrer"
                        className="text-primary-700 underline font-bold mt-1 inline-block"
                      >
                        Open WhatsApp Link Manually
                      </a>
                    )}
                  </div>
                </div>
              )}

              <div className="flex gap-2">
                <div className="relative flex-1">
                  <span className="absolute left-3 top-3 text-base text-ink-soft font-bold">+91</span>
                  <input
                    type="tel"
                    placeholder="Enter 10-digit phone"
                    value={whatsappPhone}
                    onChange={(e) => setWhatsappPhone(e.target.value.replace(/\D/g, "").substring(0, 10))}
                    className="field pl-12"
                  />
                </div>
                <Button
                  variant="primary"
                  size="tap"
                  className="flex items-center gap-2"
                  onClick={handleSendWhatsApp}
                  loading={sendingWa}
                  disabled={whatsappPhone.length < 10}
                >
                  <Send className="h-4 w-4" /> Send
                </Button>
              </div>
            </div>
          </div>
        )}
      </div>
    </BottomSheet>
  );
}
