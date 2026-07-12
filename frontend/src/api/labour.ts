import { api } from "./client";

export type Gender = "male" | "female";

export interface Labourer {
  id: string;
  name: string;
  phone: string | null;
  gender: Gender;
  default_wage: string;
  overtime_rate: string;
  is_active: boolean;
  created_at: string;
}

export interface LabourPayment {
  id: string;
  labourer_id: string | null;
  labourer_name: string;
  gender: Gender;
  wage_amount: string;
  overtime_hours: string;
  overtime_rate: string;
  overtime_amount: string;
  total_amount: string;
  note: string | null;
  recorded_by_email: string | null;
  created_at: string;
}

// ── Labourers ────────────────────────────────────────────────────────────────
export async function listLabourers(): Promise<Labourer[]> {
  const { data } = await api.get<Labourer[]>("/labour/labourers");
  return data;
}

export interface LabourerPayload {
  name: string;
  phone?: string | null;
  gender: Gender;
  default_wage?: string;
  overtime_rate?: string;
}

export async function createLabourer(payload: LabourerPayload): Promise<Labourer> {
  const { data } = await api.post<Labourer>("/labour/labourers", payload);
  return data;
}

export async function updateLabourer(id: string, payload: Partial<LabourerPayload> & { is_active?: boolean }): Promise<Labourer> {
  const { data } = await api.patch<Labourer>(`/labour/labourers/${id}`, payload);
  return data;
}

export async function deleteLabourer(id: string): Promise<void> {
  await api.delete(`/labour/labourers/${id}`);
}

// ── Payments ─────────────────────────────────────────────────────────────────
export async function listLabourPayments(): Promise<LabourPayment[]> {
  const { data } = await api.get<LabourPayment[]>("/labour/payments");
  return data;
}

export interface LabourPaymentPayload {
  labourer_id: string;
  wage_amount: string;
  overtime_hours: string;
  note?: string | null;
}

export async function createLabourPayment(payload: LabourPaymentPayload): Promise<LabourPayment> {
  const { data } = await api.post<LabourPayment>("/labour/payments", payload);
  return data;
}

export async function updateLabourPayment(
  id: string,
  payload: { wage_amount?: string; overtime_hours?: string; note?: string | null },
): Promise<LabourPayment> {
  const { data } = await api.patch<LabourPayment>(`/labour/payments/${id}`, payload);
  return data;
}

export async function deleteLabourPayment(id: string): Promise<void> {
  await api.delete(`/labour/payments/${id}`);
}
