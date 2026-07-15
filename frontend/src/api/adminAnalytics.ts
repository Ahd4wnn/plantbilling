import { api } from "./client";

export interface AdminShopRow {
  shop_id: string;
  shop_name: string;
  is_active: boolean;
  owner_email: string | null;
  owner_count: number;
  created_at: string;
  bills_in_period: number;
  staff_count: number;
  last_bill_at: string | null;
}

export interface TrendPoint {
  date: string;
  bills: number;
  new_shops: number;
}

export interface AttentionItem {
  shop_id: string;
  shop_name: string;
  kind: "silent" | "inactive" | "no_owner";
  detail: string;
}

export interface AdminOverview {
  start_date: string;
  end_date: string;
  total_shops: number;
  active_shops: number;
  inactive_shops: number;
  new_shops: number;
  total_staff: number;
  total_owners: number;
  total_bills: number;
  shops: AdminShopRow[];
  trend: TrendPoint[];
  attention: AttentionItem[];
}

export interface AdminStaffRow {
  user_id: string;
  email: string;
  role: string;
  is_active: boolean;
  shop_id: string | null;
  shop_name: string | null;
  created_at: string;
  bill_count: number;
  last_bill_at: string | null;
}

export interface AdminActivity {
  created_at: string;
  salesperson_email: string | null;
  item_count: number;
}

export interface AdminShopDetail {
  shop_id: string;
  shop_name: string;
  is_active: boolean;
  created_at: string;
  business_name: string | null;
  business_address: string | null;
  business_phone: string | null;
  business_email: string | null;
  business_upi: string | null;
  owner_email: string | null;
  owner_emails: string[];
  staff_count: number;
  products_count: number;
  customers_count: number;
  bills_7: number;
  bills_30: number;
  last_bill_at: string | null;
  recent_activity: AdminActivity[];
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
