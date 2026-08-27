/**
 * Platform (Dofida) own books — admin-only sales & expenses.
 *
 * Separate from any shop's billing. Money crosses the API as 2-decimal strings
 * ("120.00"); the UI converts to integer paise via toPaise() for display/math.
 */
import { api } from "./client";

export type PaymentMethod = "cash" | "upi";

export interface AdminSale {
  id: string;
  title: string;
  amount: string;
  cash_amount: string;
  upi_amount: string;
  due_amount: string;
  customer_name: string | null;
  customer_phone: string | null;
  note: string | null;
  occurred_on: string; // YYYY-MM-DD
  created_at: string;
}

export interface AdminExpense {
  id: string;
  reason: string;
  amount: string;
  payment_method: PaymentMethod;
  note: string | null;
  occurred_on: string;
  created_at: string;
}

export interface LedgerTrendPoint {
  date: string;
  sales: string;
  expenses: string;
}

export interface LedgerSummary {
  date_from: string;
  date_to: string;
  total_sales: string;
  sales_count: number;
  cash_collected: string;
  upi_collected: string;
  /** Dues on sales dated inside the window. Feeds the trend, not the KPI tile. */
  outstanding_due: string;
  /** Every rupee still owed, ignoring the date window — what the tile shows. */
  outstanding_due_all_time: string;
  dues_count_all_time: number;
  total_expenses: string;
  expenses_count: number;
  net_collected: string;
  trend: LedgerTrendPoint[];
}

export interface TrashedEntry {
  kind: "sale" | "expense";
  id: string;
  label: string;
  amount: string;
  occurred_on: string;
  deleted_at: string;
  deleted_by_email: string | null;
}

export interface SaleCreate {
  title: string;
  amount: string;
  cash_amount: string;
  upi_amount: string;
  due_amount: string;
  customer_name?: string | null;
  customer_phone?: string | null;
  note?: string | null;
  occurred_on?: string | null;
}

export interface ExpenseCreate {
  reason: string;
  amount: string;
  payment_method: PaymentMethod;
  note?: string | null;
  occurred_on?: string | null;
}

// ── Sales ──────────────────────────────────────────────────────────────────
export async function listSales(params: {
  date_from?: string;
  date_to?: string;
  due_only?: boolean;
  limit?: number;
  offset?: number;
}): Promise<{ items: AdminSale[]; has_more: boolean }> {
  const { data } = await api.get("/admin/ledger/sales", { params });
  return data;
}

export async function createSale(payload: SaleCreate): Promise<AdminSale> {
  const { data } = await api.post<AdminSale>("/admin/ledger/sales", payload);
  return data;
}

export async function updateSale(id: string, payload: Partial<SaleCreate>): Promise<AdminSale> {
  const { data } = await api.patch<AdminSale>(`/admin/ledger/sales/${id}`, payload);
  return data;
}

export async function collectDue(
  id: string,
  amount: string,
  method: PaymentMethod,
): Promise<AdminSale> {
  const { data } = await api.post<AdminSale>(`/admin/ledger/sales/${id}/collect`, { amount, method });
  return data;
}

/** Soft delete — the entry moves to Recently deleted and can be restored. */
export async function deleteSale(id: string): Promise<void> {
  await api.delete(`/admin/ledger/sales/${id}`);
}

export async function restoreSale(id: string): Promise<AdminSale> {
  const { data } = await api.post<AdminSale>(`/admin/ledger/sales/${id}/restore`);
  return data;
}

// ── Expenses ─────────────────────────────────────────────────────────────
export async function listExpenses(params: {
  date_from?: string;
  date_to?: string;
  limit?: number;
  offset?: number;
}): Promise<{ items: AdminExpense[]; has_more: boolean }> {
  const { data } = await api.get("/admin/ledger/expenses", { params });
  return data;
}

export async function createExpense(payload: ExpenseCreate): Promise<AdminExpense> {
  const { data } = await api.post<AdminExpense>("/admin/ledger/expenses", payload);
  return data;
}

export async function updateExpense(id: string, payload: Partial<ExpenseCreate>): Promise<AdminExpense> {
  const { data } = await api.patch<AdminExpense>(`/admin/ledger/expenses/${id}`, payload);
  return data;
}

/** Soft delete — the entry moves to Recently deleted and can be restored. */
export async function deleteExpense(id: string): Promise<void> {
  await api.delete(`/admin/ledger/expenses/${id}`);
}

export async function restoreExpense(id: string): Promise<AdminExpense> {
  const { data } = await api.post<AdminExpense>(`/admin/ledger/expenses/${id}/restore`);
  return data;
}

// ── Recently deleted ───────────────────────────────────────────────────────
export async function listTrash(params: {
  limit?: number;
  offset?: number;
}): Promise<{ items: TrashedEntry[]; has_more: boolean }> {
  const { data } = await api.get("/admin/ledger/trash", { params });
  return data;
}

// ── Summary ────────────────────────────────────────────────────────────────
export async function getLedgerSummary(
  dateFrom?: string,
  dateTo?: string,
  allTime = false,
): Promise<LedgerSummary> {
  const params: Record<string, string | boolean> = {};
  if (dateFrom) params.date_from = dateFrom;
  if (dateTo) params.date_to = dateTo;
  // Without this the server falls back to "last 30 days", which is exactly the
  // hidden window that made older entries look deleted.
  if (allTime) params.all_time = true;
  const { data } = await api.get<LedgerSummary>("/admin/ledger/summary", { params });
  return data;
}
