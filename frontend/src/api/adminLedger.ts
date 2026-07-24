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
  outstanding_due: string;
  total_expenses: string;
  expenses_count: number;
  net_collected: string;
  trend: LedgerTrendPoint[];
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

export async function deleteSale(id: string): Promise<void> {
  await api.delete(`/admin/ledger/sales/${id}`);
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

export async function deleteExpense(id: string): Promise<void> {
  await api.delete(`/admin/ledger/expenses/${id}`);
}

// ── Summary ────────────────────────────────────────────────────────────────
export async function getLedgerSummary(dateFrom?: string, dateTo?: string): Promise<LedgerSummary> {
  const params: Record<string, string> = {};
  if (dateFrom) params.date_from = dateFrom;
  if (dateTo) params.date_to = dateTo;
  const { data } = await api.get<LedgerSummary>("/admin/ledger/summary", { params });
  return data;
}
