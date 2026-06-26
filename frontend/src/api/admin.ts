import { api } from "./client";

export interface ShopRow {
  id: string;
  name: string;
  owner_id: string | null;
  owner_email_account: string | null; // linked multi-shop owner's login
  owner_name: string | null;
  owner_phone: string | null;
  owner_email: string | null; // the shop's manager login
  is_active: boolean;
  whatsapp_auto_send: boolean;
  created_at: string;
  business_name?: string | null;
  business_address?: string | null;
  business_phone?: string | null;
  business_email?: string | null;
  business_upi?: string | null;
  whatsapp_message_template?: string | null;
}

export interface OwnerInfo {
  id: string;
  email: string;
  role: string;
  shop_id: string | null;
  is_active: boolean;
}

export interface ShopCreateResponse {
  shop: {
    id: string;
    name: string;
    owner_name: string | null;
    owner_phone: string | null;
    is_active: boolean;
    created_at: string;
  };
  owner: OwnerInfo;
}

export interface ShopCreatePayload {
  name: string;
  owner_name?: string | null;
  owner_phone?: string | null;
  owner_email: string;
  owner_password: string;
  owner_id?: string | null; // optional link to a multi-shop owner account
}

export interface OwnerAccount {
  id: string;
  email: string;
  is_active: boolean;
  created_at: string;
  shop_count: number;
}

export interface AdminCustomerRow {
  id: string;
  name: string;
  phone: string | null;
  shop_id: string;
  shop_name: string;
  created_at: string;
}

export interface AdminCustomerList {
  items: AdminCustomerRow[];
  total: number;
  limit: number;
  offset: number;
}

export async function listShops(): Promise<ShopRow[]> {
  const { data } = await api.get<ShopRow[]>("/admin/shops");
  return data;
}

export async function createShop(payload: ShopCreatePayload): Promise<ShopCreateResponse> {
  const { data } = await api.post<ShopCreateResponse>("/admin/shops", payload);
  return data;
}

export async function updateShop(
  shopId: string,
  payload: {
    is_active?: boolean;
    whatsapp_auto_send?: boolean;
    business_name?: string | null;
    business_address?: string | null;
    business_phone?: string | null;
    business_email?: string | null;
    business_upi?: string | null;
    whatsapp_message_template?: string | null;
  }
): Promise<void> {
  await api.patch(`/admin/shops/${shopId}`, payload);
}

export async function deleteShop(shopId: string): Promise<void> {
  await api.delete(`/admin/shops/${shopId}`);
}

export async function resetOwnerPassword(shopId: string, newPassword: string): Promise<void> {
  await api.post(`/admin/shops/${shopId}/reset-password`, { new_password: newPassword });
}

export interface AdminCustomerParams {
  q?: string;
  shop_id?: string;
  limit?: number;
  offset?: number;
}

export async function listAdminCustomers(params: AdminCustomerParams): Promise<AdminCustomerList> {
  const query: Record<string, string | number> = {
    limit: params.limit ?? 50,
    offset: params.offset ?? 0,
  };
  if (params.q?.trim()) query.q = params.q.trim();
  if (params.shop_id) query.shop_id = params.shop_id;
  const { data } = await api.get<AdminCustomerList>("/admin/customers", { params: query });
  return data;
}

export async function listShopUsers(shopId: string): Promise<OwnerInfo[]> {
  const { data } = await api.get<OwnerInfo[]>(`/admin/shops/${shopId}/users`);
  return data;
}

// ── Multi-shop owner accounts ────────────────────────────────────────────────
export async function listOwners(): Promise<OwnerAccount[]> {
  const { data } = await api.get<OwnerAccount[]>("/admin/owners");
  return data;
}

export async function createOwner(email: string, password: string): Promise<OwnerAccount> {
  const { data } = await api.post<OwnerAccount>("/admin/owners", { email, password });
  return data;
}

export async function assignShopOwner(shopId: string, ownerId: string | null): Promise<void> {
  await api.post(`/admin/shops/${shopId}/assign-owner`, { owner_id: ownerId });
}

export async function downloadAdminCustomersCSV(params: AdminCustomerParams): Promise<void> {
  const query: Record<string, string | number> = {};
  if (params.q?.trim()) query.q = params.q.trim();
  if (params.shop_id) query.shop_id = params.shop_id;

  const { data } = await api.get("/admin/customers/download", {
    params: query,
    responseType: "blob",
  });

  const url = window.URL.createObjectURL(new Blob([data]));
  const link = document.createElement("a");
  link.href = url;
  link.setAttribute("download", `admin_customers_export.csv`);
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(url);
}

