import { api } from "./client";
import type { ExpenseRow } from "./expenses";

export type PaymentMethod = "cash" | "upi" | "split" | "due";

export interface BillSummary {
  date: string; // YYYY-MM-DD (shop timezone)
  total_sales: string;
  bill_count: number;
  cash_total: string;
  upi_total: string;
  due_total: string;
  total_expenses: string;
  cash_expenses: string;
  upi_expenses: string;
  labour_total: string;
  labour_cash: string;
  cash_in_hand_running: string;
  net_sales: string;
  expenses: ExpenseRow[];
}

export interface BillListItem {
  id: string;
  /** Human-facing per-shop bill number, e.g. "0001". Null for legacy bills. */
  bill_no: string | null;
  created_at: string;
  total: string;
  due_amount: string;
  customer_name: string | null;
  customer_phone: string | null;
  item_count: number;
  payment_method: PaymentMethod;
  is_edited: boolean;
  /** A salesperson's due-collection is waiting for a manager to approve it. */
  pending_settlement: boolean;
}

export interface BillListResponse {
  items: BillListItem[];
  limit: number;
  offset: number;
  has_more: boolean;
}

export interface BillDetailItem {
  product_id: string | null;
  product_name: string;
  unit_price: string;
  quantity: number;
  line_total: string;
}

export interface BillDetail {
  id: string;
  /** Human-facing per-shop bill number, e.g. "0001". Null for legacy bills. */
  bill_no: string | null;
  shop_name: string | null;
  business_name: string | null;
  business_address: string | null;
  business_phone: string | null;
  subtotal: string;
  discount_type: "flat" | "percent";
  discount_value: string;
  discount_amount: string;
  total: string;
  cash_amount: string;
  upi_amount: string;
  due_amount: string;
  payment_method: PaymentMethod;
  customer_id: string | null;
  customer_name: string | null;
  customer_phone: string | null;
  salesperson_email: string | null;
  remarks: string | null;
  is_edited: boolean;
  created_at: string;
  whatsapp_status?: string | null;
  whatsapp_error?: string | null;
  invoice_url?: string | null;
  items: BillDetailItem[];
}

/** date optional (YYYY-MM-DD, shop time). Defaults to today on the server. */
export async function fetchTodaySummary(date?: string, createdBy?: string, shopId?: string): Promise<BillSummary> {
  const query: Record<string, string> = {};
  if (date) query.date = date;
  if (createdBy) query.created_by = createdBy;
  if (shopId) query.shop_id = shopId;
  const { data } = await api.get<BillSummary>("/bills/summary/today", {
    params: query,
  });
  return data;
}

export interface BillListParams {
  date_from?: string;
  date_to?: string;
  created_by?: string;
  is_edited?: boolean;
  has_due?: boolean;
  shop_id?: string;
  limit?: number;
  offset?: number;
}

export async function fetchBills(params: BillListParams): Promise<BillListResponse> {
  const query: Record<string, string | number | boolean> = {
    limit: params.limit ?? 20,
    offset: params.offset ?? 0,
  };
  if (params.date_from) query.date_from = params.date_from;
  if (params.date_to) query.date_to = params.date_to;
  if (params.created_by) query.created_by = params.created_by;
  if (params.is_edited !== undefined) query.is_edited = params.is_edited;
  if (params.has_due !== undefined) query.has_due = params.has_due;
  if (params.shop_id) query.shop_id = params.shop_id;
  const { data } = await api.get<BillListResponse>("/bills", { params: query });
  return data;
}

export async function fetchBillDetail(id: string): Promise<BillDetail> {
  const { data } = await api.get<BillDetail>(`/bills/${id}`);
  return data;
}

export interface CategorySales {
  category: string | null;
  quantity: number;
  total_sales: string;
}

export interface ProductSales {
  product_name: string;
  quantity: number;
  total_sales: string;
}

export interface ExpenseCategoryTotal {
  category: string;
  total: string;
  count: number;
}

export interface DetailedReportResponse {
  start_date: string;
  end_date: string;
  total_sales: string;
  bill_count: number;
  cash_total: string;
  upi_total: string;
  due_total: string;
  average_bill_value: string;
  total_expenses: string;
  net_sales: string;
  expenses: ExpenseRow[];
  expenses_by_category: ExpenseCategoryTotal[];
  categories: CategorySales[];
  top_products: ProductSales[];
}

export interface ReportQueryParams {
  date_from?: string;
  date_to?: string;
  created_by?: string;
  shopId?: string;
}

export async function fetchDetailedReport(params: ReportQueryParams): Promise<DetailedReportResponse> {
  const query: Record<string, string> = {};
  if (params.date_from) query.date_from = params.date_from;
  if (params.date_to) query.date_to = params.date_to;
  if (params.created_by) query.created_by = params.created_by;
  if (params.shopId) query.shop_id = params.shopId;
  const { data } = await api.get<DetailedReportResponse>("/bills/summary/report", { params: query });
  return data;
}

export async function downloadReportCSV(params: ReportQueryParams): Promise<void> {
  const query: Record<string, string> = {};
  if (params.date_from) query.date_from = params.date_from;
  if (params.date_to) query.date_to = params.date_to;
  if (params.created_by) query.created_by = params.created_by;
  if (params.shopId) query.shop_id = params.shopId;

  const { data } = await api.get("/bills/summary/report/download", {
    params: query,
    responseType: "blob",
  });

  const url = window.URL.createObjectURL(
    new Blob([data], {
      type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    }),
  );
  const link = document.createElement("a");
  link.href = url;

  const filename = `sales_report_${params.date_from || "start"}_to_${params.date_to || "end"}.xlsx`;
  link.setAttribute("download", filename);
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(url);
}

export interface SendReportWhatsAppPayload {
  phone: string;
  date_from?: string;
  date_to?: string;
  created_by?: string;
  shop_id?: string;
}

export interface SendWhatsAppResult {
  status: "sent_via_wati" | "fallback_wa_me" | "disabled" | "not_eligible";
  wa_me_url: string | null;
  detail: string;
}

export async function sendReportWhatsApp(payload: SendReportWhatsAppPayload): Promise<SendWhatsAppResult> {
  const { data } = await api.post<SendWhatsAppResult>("/bills/summary/report/send-whatsapp", payload);
  return data;
}

export interface BillUpdatePayload {
  cash_amount?: number;
  upi_amount?: number;
  due_amount?: number;
  remarks?: string | null;
}

export async function editBill(id: string, payload: BillUpdatePayload): Promise<BillDetail> {
  const { data } = await api.patch<BillDetail>(`/bills/${id}`, payload);
  return data;
}

export async function deleteBill(id: string): Promise<void> {
  await api.delete(`/bills/${id}`);
}
