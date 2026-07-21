import { api } from "./client";

export type SettlementStatus = "pending" | "approved" | "rejected";

export interface SettlementActionResult {
  id: string;
  bill_id: string;
  status: SettlementStatus;
  cash_amount: string;
  upi_amount: string;
}

export interface PendingSettlement {
  id: string;
  bill_id: string;
  status: SettlementStatus;
  cash_amount: string;
  upi_amount: string;
  bill_total: string;
  customer_name: string | null;
  customer_phone: string | null;
  requested_by_email: string | null;
  created_at: string;
}

/** Collect a due. A manager applies it now; a salesperson's goes to the queue. */
export async function collectDue(
  billId: string,
  cashAmount: string,
  upiAmount: string,
): Promise<SettlementActionResult> {
  const { data } = await api.post<SettlementActionResult>("/settlements", {
    bill_id: billId,
    cash_amount: cashAmount,
    upi_amount: upiAmount,
  });
  return data;
}

export async function listPendingSettlements(): Promise<PendingSettlement[]> {
  const { data } = await api.get<{ items: PendingSettlement[] }>("/settlements/pending");
  return data.items;
}

export async function approveSettlement(id: string): Promise<SettlementActionResult> {
  const { data } = await api.post<SettlementActionResult>(`/settlements/${id}/approve`);
  return data;
}

export async function rejectSettlement(id: string): Promise<SettlementActionResult> {
  const { data } = await api.post<SettlementActionResult>(`/settlements/${id}/reject`);
  return data;
}
