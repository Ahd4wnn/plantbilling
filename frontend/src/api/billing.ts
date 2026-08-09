import { api } from "./client";
import type { Product } from "./types";
import type { SendWhatsAppResult } from "./sales";

export async function fetchProducts(): Promise<Product[]> {
  // Active products only (default). Filtering/search is done client-side for snappy UX.
  const { data } = await api.get<Product[]>("/products", { params: { active: "true" } });
  return data;
}

// ── Bills / checkout ─────────────────────────────────────────────────────────
export interface BillItemPayload {
  product_id: string;
  quantity: number;
  unit_price: string; // editable per line; server recomputes the line total
}

export interface NewCustomerPayload {
  name: string;
  phone?: string | null;
}

export interface BillCreatePayload {
  idempotency_key: string;
  items: BillItemPayload[];
  discount_type: "flat" | "percent";
  discount_value: string;
  cash_amount: string;
  upi_amount: string;
  due_amount: string;
  remarks?: string | null;
  // Customer is entered fresh per bill and optional.
  new_customer?: NewCustomerPayload | null;
}

export interface BillItemOut {
  product_id: string | null;
  product_name: string;
  unit_price: string;
  quantity: number;
  line_total: string;
}

export interface BillOut {
  id: string;
  /** Human-facing per-shop bill number, e.g. "0001". Null for legacy bills. */
  bill_no: string | null;
  bill_type: "retail" | "wholesale";
  subtotal: string;
  discount_type: "flat" | "percent";
  discount_value: string;
  discount_amount: string;
  total: string;
  cash_amount: string;
  upi_amount: string;
  due_amount: string;
  customer_id: string | null;
  customer_name: string | null;
  created_by: string | null;
  salesperson_email: string | null;
  remarks: string | null;
  created_at: string;
  items: BillItemOut[];
  idempotent_replay: boolean;
  idempotency_key: string | null;
}

export async function createBill(payload: BillCreatePayload): Promise<BillOut> {
  const { data } = await api.post<BillOut>("/bills", payload);
  return data;
}

export async function sendBillWhatsApp(billId: string): Promise<SendWhatsAppResult> {
  const { data } = await api.post<SendWhatsAppResult>(`/bills/${billId}/send-whatsapp`);
  return data;
}

export interface WhatsAppStatusOut {
  status: string;
  message_id: string | null;
  error: string | null;
  sent_at: string | null;
  last_retry_at: string | null;
  retry_count: number;
  invoice_url: string | null;
}

export async function resendBillWhatsApp(billId: string): Promise<SendWhatsAppResult> {
  const { data } = await api.post<SendWhatsAppResult>(`/bills/${billId}/resend-whatsapp`);
  return data;
}

export async function getBillWhatsAppStatus(billId: string): Promise<WhatsAppStatusOut> {
  const { data } = await api.get<WhatsAppStatusOut>(`/bills/${billId}/whatsapp-status`);
  return data;
}

export async function fetchPublicBill(billId: string): Promise<BillOut> {
  const { data } = await api.get<BillOut>(`/bills/public/${billId}`);
  return data;
}
