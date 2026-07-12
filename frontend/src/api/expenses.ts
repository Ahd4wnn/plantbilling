import { api } from "./client";

export type ExpenseMethod = "cash" | "upi";

export interface ExpenseRow {
  id: string;
  shop_id: string;
  amount: string;
  reason: string;
  payment_method: ExpenseMethod;
  created_by: string | null;
  created_at: string;
}

export async function createExpense(
  amount: string,
  reason: string,
  paymentMethod: ExpenseMethod = "cash",
): Promise<ExpenseRow> {
  const { data } = await api.post<ExpenseRow>("/expenses", {
    amount,
    reason,
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
  reason: string,
  paymentMethod: ExpenseMethod = "cash",
): Promise<ExpenseRow> {
  const { data } = await api.patch<ExpenseRow>(`/expenses/${id}`, {
    amount,
    reason,
    payment_method: paymentMethod,
  });
  return data;
}
