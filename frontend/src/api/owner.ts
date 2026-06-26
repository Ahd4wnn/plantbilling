import { api } from "./client";

export interface OwnerShop {
  id: string;
  name: string;
  is_active: boolean;
  business_name: string | null;
  business_address: string | null;
  business_phone: string | null;
  business_email: string | null;
  business_upi: string | null;
  whatsapp_auto_send: boolean;
}

export interface OwnerShopUpdate {
  business_name?: string | null;
  business_address?: string | null;
  business_phone?: string | null;
  business_email?: string | null;
  business_upi?: string | null;
  whatsapp_auto_send?: boolean;
  whatsapp_message_template?: string | null;
}

export interface ShopOverviewRow {
  shop_id: string;
  shop_name: string;
  total_sales: string;
  bill_count: number;
  cash_total: string;
  upi_total: string;
  due_total: string;
  total_expenses: string;
  net_sales: string;
}

export interface StaffPerformance {
  user_id: string | null;
  email: string | null;
  shop_id: string;
  shop_name: string;
  role: string;
  total_sales: string;
  bill_count: number;
}

export interface OwnerOverview {
  start_date: string;
  end_date: string;
  shop_count: number;
  total_sales: string;
  bill_count: number;
  cash_total: string;
  upi_total: string;
  due_total: string;
  total_expenses: string;
  net_sales: string;
  shops: ShopOverviewRow[];
  staff: StaffPerformance[];
}

export interface OwnerReport {
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

export interface OwnerStaff {
  id: string;
  email: string;
  role: string;
  is_active: boolean;
  shop_id: string | null;
  created_at: string;
}

export async function listOwnerShops(): Promise<OwnerShop[]> {
  const { data } = await api.get<OwnerShop[]>("/owner/shops");
  return data;
}

export async function updateOwnerShop(shopId: string, payload: OwnerShopUpdate): Promise<OwnerShop> {
  const { data } = await api.patch<OwnerShop>(`/owner/shops/${shopId}`, payload);
  return data;
}

export async function getOwnerOverview(dateFrom?: string, dateTo?: string): Promise<OwnerOverview> {
  const params: Record<string, string> = {};
  if (dateFrom) params.date_from = dateFrom;
  if (dateTo) params.date_to = dateTo;
  const { data } = await api.get<OwnerOverview>("/owner/overview", { params });
  return data;
}

export async function getShopReport(shopId: string, dateFrom?: string, dateTo?: string): Promise<OwnerReport> {
  const params: Record<string, string> = {};
  if (dateFrom) params.date_from = dateFrom;
  if (dateTo) params.date_to = dateTo;
  const { data } = await api.get<OwnerReport>(`/owner/shops/${shopId}/report`, { params });
  return data;
}

export async function listShopStaff(shopId: string): Promise<OwnerStaff[]> {
  const { data } = await api.get<OwnerStaff[]>(`/owner/shops/${shopId}/staff`);
  return data;
}

export async function createShopStaff(
  shopId: string,
  payload: { email: string; password: string; role: "manager" | "salesperson" }
): Promise<OwnerStaff> {
  const { data } = await api.post<OwnerStaff>(`/owner/shops/${shopId}/staff`, payload);
  return data;
}

export async function setStaffActive(shopId: string, userId: string, isActive: boolean): Promise<OwnerStaff> {
  const { data } = await api.patch<OwnerStaff>(`/owner/shops/${shopId}/staff/${userId}`, { is_active: isActive });
  return data;
}

export async function resetStaffPassword(shopId: string, userId: string, newPassword: string): Promise<void> {
  await api.post(`/owner/shops/${shopId}/staff/${userId}/reset-password`, { new_password: newPassword });
}

export async function deleteShopStaff(shopId: string, userId: string): Promise<void> {
  await api.delete(`/owner/shops/${shopId}/staff/${userId}`);
}
