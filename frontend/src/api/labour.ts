import { api } from "./client";

export type Gender = "male" | "female";

export type PayMethod = "cash" | "upi" | "split" | "due";
export type AttendanceStatus = "present" | "absent" | "half_day";

export interface Labourer {
  id: string;
  name: string;
  phone: string | null;
  gender: Gender;
  default_wage: string;
  overtime_rate: string;
  is_active: boolean;
  total_paid: string;
  outstanding_due: string;
  created_at: string;
}

export interface LabourPayment {
  id: string;
  labourer_id: string | null;
  labourer_name: string;
  gender: Gender;
  kind: "wage" | "due_clear";
  wage_amount: string;
  overtime_hours: string;
  overtime_rate: string;
  overtime_amount: string;
  total_amount: string;
  cash_amount: string;
  upi_amount: string;
  due_amount: string;
  payment_method: PayMethod;
  note: string | null;
  recorded_by_email: string | null;
  created_at: string;
}

export interface Attendance {
  id: string;
  labourer_id: string;
  labourer_name: string;
  day: string;
  status: AttendanceStatus;
  overtime_hours: string;
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
export async function listLabourPayments(labourerId?: string): Promise<LabourPayment[]> {
  const { data } = await api.get<LabourPayment[]>("/labour/payments", {
    params: labourerId ? { labourer_id: labourerId } : {},
  });
  return data;
}

export interface LabourPaymentPayload {
  labourer_id: string;
  wage_amount: string;
  overtime_hours: string;
  cash_amount: string;
  upi_amount: string;
  due_amount: string;
  note?: string | null;
}

export async function clearLabourDue(payload: { labourer_id: string; cash_amount: string; upi_amount: string; note?: string | null }): Promise<LabourPayment> {
  const { data } = await api.post<LabourPayment>("/labour/due-clear", payload);
  return data;
}

export async function listAttendance(day?: string): Promise<Attendance[]> {
  const { data } = await api.get<Attendance[]>("/labour/attendance", { params: day ? { day } : {} });
  return data;
}

export async function markAttendance(payload: { labourer_id: string; day: string; status: AttendanceStatus; overtime_hours: string }): Promise<Attendance> {
  const { data } = await api.post<Attendance>("/labour/attendance", payload);
  return data;
}

export async function createLabourPayment(payload: LabourPaymentPayload): Promise<LabourPayment> {
  const { data } = await api.post<LabourPayment>("/labour/payments", payload);
  return data;
}

export async function updateLabourPayment(
  id: string,
  payload: { wage_amount?: string; overtime_hours?: string; cash_amount?: string; upi_amount?: string; due_amount?: string; note?: string | null },
): Promise<LabourPayment> {
  const { data } = await api.patch<LabourPayment>(`/labour/payments/${id}`, payload);
  return data;
}

export async function deleteLabourPayment(id: string): Promise<void> {
  await api.delete(`/labour/payments/${id}`);
}
