import { api } from "./client";

export type BorrowMethod = "cash" | "upi" | "split" | "none";

export interface Borrowing {
  id: string;
  lender_name: string;
  lender_phone: string | null;
  amount: string;
  cash_amount: string;
  upi_amount: string;
  method: BorrowMethod;
  remarks: string | null;
  is_paid: boolean;
  paid_cash_amount: string;
  paid_upi_amount: string;
  paid_method: BorrowMethod;
  outstanding: string;
  paid_at: string | null;
  created_at: string;
}

export interface BorrowingList {
  items: Borrowing[];
  total_outstanding: string;
}

export interface BorrowingPayload {
  lender_name: string;
  lender_phone?: string | null;
  amount: string;
  cash_amount: string;
  upi_amount: string;
  remarks?: string | null;
}

export type BorrowingStatus = "all" | "open" | "paid";

export async function listBorrowings(status: BorrowingStatus = "all"): Promise<BorrowingList> {
  const { data } = await api.get<BorrowingList>("/borrowings", { params: { status } });
  return data;
}

export async function createBorrowing(payload: BorrowingPayload): Promise<Borrowing> {
  const { data } = await api.post<Borrowing>("/borrowings", payload);
  return data;
}

export async function payBorrowing(
  id: string,
  payload: { paid_cash_amount: string; paid_upi_amount: string },
): Promise<Borrowing> {
  const { data } = await api.post<Borrowing>(`/borrowings/${id}/pay`, payload);
  return data;
}

export async function deleteBorrowing(id: string): Promise<void> {
  await api.delete(`/borrowings/${id}`);
}
