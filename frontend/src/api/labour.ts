import { api } from "./client";

export type Gender = "male" | "female";

export type PayMethod = "cash" | "upi" | "split" | "due";
export type AttendanceStatus = "present" | "absent" | "half_day";

export type PayKind = "wage" | "advance" | "due_clear";

/** How a worker is paid. Only one of the two wage fields is ever meaningful. */
export type WageType = "daily" | "monthly";

export interface Labourer {
  id: string;
  name: string;
  phone: string | null;
  aadhaar: string | null;
  gender: Gender;
  wage_type: WageType;
  default_wage: string;       // wage per day     (daily workers)
  monthly_wage: string;       // wage per month   (monthly workers)
  paid_leaves_per_month: number;
  is_active: boolean;
  days_worked: string;        // present + ½·half-day (from attendance)
  total_paid: string;
  /**
   * Daily:   wage_per_day × days_worked.
   * Monthly: the salary, with part months pro-rated at monthly_wage/30 per day,
   *          minus monthly_wage/30 for every leave beyond that month's allowance.
   *          The current month counts only up to `accrued_through`.
   * The server is the only place this is computed — never recalculate it here.
   */
  earned: string;
  balance_to_pay: string;     // earned − total_paid (negative = paid ahead)
  joined_on: string;          // YYYY-MM-DD
  /** This calendar month only, so the app can explain a deduction. */
  leaves_this_month: string;
  unpaid_leaves_this_month: string;
  /**
   * Monthly workers only: the newest day attendance was marked this month, i.e.
   * how far `earned` has counted. Null when nothing is marked yet this month.
   */
  accrued_through: string | null;   // YYYY-MM-DD
  created_at: string;
}

export interface LabourPayment {
  id: string;
  labourer_id: string | null;
  labourer_name: string;
  gender: Gender;
  kind: PayKind;
  wage_amount: string;
  days: string | null;
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
  aadhaar?: string | null;
  gender: Gender;
  wage_type?: WageType;
  default_wage?: string;
  monthly_wage?: string;
  paid_leaves_per_month?: number;
  /** YYYY-MM-DD. Omit for today (IST). Affects the wage, so send it when known. */
  joined_on?: string | null;
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
  kind?: "wage" | "advance";
  wage_amount: string;
  days?: string | null;
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

export async function markAttendance(payload: { labourer_id: string; day: string; status: AttendanceStatus }): Promise<Attendance> {
  const { data } = await api.post<Attendance>("/labour/attendance", payload);
  return data;
}

export async function createLabourPayment(payload: LabourPaymentPayload): Promise<LabourPayment> {
  const { data } = await api.post<LabourPayment>("/labour/payments", payload);
  return data;
}

export async function updateLabourPayment(
  id: string,
  payload: { wage_amount?: string; days?: string | null; cash_amount?: string; upi_amount?: string; due_amount?: string; note?: string | null },
): Promise<LabourPayment> {
  const { data } = await api.patch<LabourPayment>(`/labour/payments/${id}`, payload);
  return data;
}

export async function deleteLabourPayment(id: string): Promise<void> {
  await api.delete(`/labour/payments/${id}`);
}
