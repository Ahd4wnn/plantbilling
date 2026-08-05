import { api } from "./client";

export type ExpenseMethod = "cash" | "upi";

export interface ExpenseRow {
  id: string;
  shop_id: string;
  amount: string;
  reason: string;
  category_id: string | null;
  category_name: string | null;
  note: string | null;
  payment_method: ExpenseMethod;
  created_by: string | null;
  created_at: string;
}

export interface ExpenseCategory {
  id: string;
  name: string;
  created_at: string;
}

export async function listExpenseCategories(): Promise<ExpenseCategory[]> {
  const { data } = await api.get<ExpenseCategory[]>("/expense-categories");
  return data;
}

export async function createExpenseCategory(name: string): Promise<ExpenseCategory> {
  const { data } = await api.post<ExpenseCategory>("/expense-categories", { name });
  return data;
}

export async function renameExpenseCategory(id: string, name: string): Promise<ExpenseCategory> {
  const { data } = await api.patch<ExpenseCategory>(`/expense-categories/${id}`, { name });
  return data;
}

export async function deleteExpenseCategory(id: string): Promise<void> {
  await api.delete(`/expense-categories/${id}`);
}

export async function createExpense(
  amount: string,
  categoryId: string,
  note: string | null,
  paymentMethod: ExpenseMethod = "cash",
): Promise<ExpenseRow> {
  const { data } = await api.post<ExpenseRow>("/expenses", {
    amount,
    category_id: categoryId,
    note: note?.trim() || null,
    payment_method: paymentMethod,
  });
  return data;
}

export async function listExpenses(limit = 100, offset = 0): Promise<ExpenseRow[]> {
  const { data } = await api.get<ExpenseRow[]>("/expenses", {
    params: { limit, offset },
  });
  return data;
}

export async function deleteExpense(id: string): Promise<void> {
  await api.delete(`/expenses/${id}`);
}

export async function updateExpense(
  id: string,
  amount: string,
  categoryId: string,
  note: string | null,
  paymentMethod: ExpenseMethod = "cash",
): Promise<ExpenseRow> {
  const { data } = await api.patch<ExpenseRow>(`/expenses/${id}`, {
    amount,
    category_id: categoryId,
    note: note?.trim() || null,
    payment_method: paymentMethod,
  });
  return data;
}
