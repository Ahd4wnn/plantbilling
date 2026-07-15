import { api } from "./client";

export interface AdminShopRow {
  shop_id: string;
  shop_name: string;
  is_active: boolean;
  owner_email: string | null;
  total_sales: string;
  bill_count: number;
  cash_total: string;
  upi_total: string;
  due_total: string;
  total_expenses: string;
  net_sales: string;
  staff_count: number;
  last_bill_at: string | null;
}

export interface TrendPoint {
  date: string;
  sales: string;
  bill_count: number;
}

export interface AttentionItem {
  shop_id: string;
  shop_name: string;
  kind: "silent" | "inactive" | "no_owner";
  detail: string;
}

export interface AdminStaffPerformance {
  user_id: string | null;
  email: string | null;
  shop_id: string;
  shop_name: string;
  role: string;
  total_sales: string;
  bill_count: number;
}

export interface AdminOverview {
  start_date: string;
  end_date: string;
  total_shops: number;
  active_shops: number;
  total_sales: string;
  bill_count: number;
  cash_total: string;
  upi_total: string;
  due_total: string;
  total_expenses: string;
  net_sales: string;
  shops: AdminShopRow[];
  trend: TrendPoint[];
  attention: AttentionItem[];
  staff: AdminStaffPerformance[];
}

export interface AdminStaffRow {
  user_id: string;
  email: string;
  role: string;
  is_active: boolean;
  shop_id: string | null;
  shop_name: string | null;
  created_at: string;
  total_sales: string;
  bill_count: number;
  last_bill_at: string | null;
}

/** Mirrors the backend DetailedReportResponse (money as 2dp strings). */
export interface ReportData {
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
  expenses: { id: string; amount: string; reason: string; created_at: string }[];
  categories: { category: string | null; quantity: number; total_sales: string }[];
  top_products: { product_name: string; quantity: number; total_sales: string }[];
}

export interface AdminRecentBill {
  id: string;
  created_at: string;
  total: string;
  payment_method: string;
  customer_name: string | null;
  salesperson_email: string | null;
}

export interface AdminShopDetail {
  shop_id: string;
  shop_name: string;
  is_active: boolean;
  business_name: string | null;
  business_address: string | null;
  business_phone: string | null;
  business_email: string | null;
  business_upi: string | null;
  owner_email: string | null;
  cash_in_hand_running: string;
  last_bill_at: string | null;
  staff_count: number;
  report: ReportData;
  recent_bills: AdminRecentBill[];
}

export interface ExportStatus {
  last_exported_at: string | null;
  new_since_last: number;
  total_customers: number;
}

export async function getAdminOverview(dateFrom?: string, dateTo?: string): Promise<AdminOverview> {
  const params: Record<string, string> = {};
  if (dateFrom) params.date_from = dateFrom;
  if (dateTo) params.date_to = dateTo;
  const { data } = await api.get<AdminOverview>("/admin/overview", { params });
  return data;
}

export async function getAdminShopDetail(shopId: string): Promise<AdminShopDetail> {
  const { data } = await api.get<AdminShopDetail>(`/admin/shops/${shopId}/detail`);
  return data;
}

export async function getAdminStaff(): Promise<AdminStaffRow[]> {
  const { data } = await api.get<AdminStaffRow[]>("/admin/staff");
  return data;
}

export async function getExportStatus(): Promise<ExportStatus> {
  const { data } = await api.get<ExportStatus>("/admin/customers/export-status");
  return data;
}
