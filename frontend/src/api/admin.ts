import { api } from "./client";

export interface ShopOwnerLink {
  id: string;
  email: string;
}

export interface ShopRow {
  id: string;
  name: string;
  owners: ShopOwnerLink[]; // linked multi-shop owner accounts (0..many)
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
  /** Shop logo for printed bills. The URL is the file; the flag is whether it prints. */
  logo_url?: string | null;
  logo_enabled?: boolean;
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
  cash_in_hand_opening?: string; // opening cash on hand (₹), defaults to "0"
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
    logo_enabled?: boolean;
  }
): Promise<void> {
  await api.patch(`/admin/shops/${shopId}`, payload);
}

/**
 * Set the shop's logo for printed bills. Admin-only — shop staff can see the
 * logo but not change it. To stop printing it without discarding the file,
 * call updateShop({ logo_enabled: false }) instead.
 */
export async function uploadShopLogo(shopId: string, file: File): Promise<ShopRow> {
  const form = new FormData();
  form.append("file", file);
  const { data } = await api.post<ShopRow>(`/admin/shops/${shopId}/logo`, form, {
    headers: { "Content-Type": "multipart/form-data" },
  });
  return data;
}

export async function deleteShopLogo(shopId: string): Promise<ShopRow> {
  const { data } = await api.delete<ShopRow>(`/admin/shops/${shopId}/logo`);
  return data;
}

/**
 * Permanently delete a shop and everything in it (bills, expenses, products,
 * customers, labourers — all via ON DELETE CASCADE). `confirmName` must be the
 * shop's exact name; the server rejects the call otherwise, so a stray or
 * replayed request can't wipe a shop on its own.
 */
export async function deleteShop(shopId: string, confirmName: string): Promise<void> {
  await api.delete(`/admin/shops/${shopId}`, { params: { confirm_name: confirmName } });
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

/** Set a new password for a multi-shop owner account. */
export async function resetOwnerAccountPassword(ownerId: string, newPassword: string): Promise<OwnerAccount> {
  const { data } = await api.post<OwnerAccount>(`/admin/owners/${ownerId}/reset-password`, { new_password: newPassword });
  return data;
}

/** Delete an owner login. Shop links are auto-removed; shops are untouched. */
export async function deleteOwner(ownerId: string): Promise<void> {
  await api.delete(`/admin/owners/${ownerId}`);
}

export async function listShopOwners(shopId: string): Promise<ShopOwnerLink[]> {
  const { data } = await api.get<ShopOwnerLink[]>(`/admin/shops/${shopId}/owners`);
  return data;
}

/** Link a multi-shop owner to a shop (a shop may have several owners). Returns
 *  the shop's updated owner list. */
export async function addShopOwner(shopId: string, ownerId: string): Promise<ShopOwnerLink[]> {
  const { data } = await api.post<ShopOwnerLink[]>(`/admin/shops/${shopId}/owners`, { owner_id: ownerId });
  return data;
}

/** Unlink a multi-shop owner from a shop. Returns the updated owner list. */
export async function removeShopOwner(shopId: string, ownerId: string): Promise<ShopOwnerLink[]> {
  const { data } = await api.delete<ShopOwnerLink[]>(`/admin/shops/${shopId}/owners/${ownerId}`);
  return data;
}

export interface CustomerExportOptions extends AdminCustomerParams {
  /** since_last (default): only rows created after the last download; all: every
   *  row; range: rows created within [created_from, created_to]. */
  mode?: "since_last" | "all" | "range";
  created_from?: string; // YYYY-MM-DD (range mode)
  created_to?: string;   // YYYY-MM-DD (range mode)
}

export async function downloadAdminCustomersCSV(opts: CustomerExportOptions = {}): Promise<void> {
  const query: Record<string, string | number> = { mode: opts.mode ?? "since_last" };
  if (opts.q?.trim()) query.q = opts.q.trim();
  if (opts.shop_id) query.shop_id = opts.shop_id;
  if (opts.created_from) query.created_from = opts.created_from;
  if (opts.created_to) query.created_to = opts.created_to;

  const { data } = await api.get("/admin/customers/download", {
    params: query,
    responseType: "blob",
  });

  const url = window.URL.createObjectURL(new Blob([data]));
  const link = document.createElement("a");
  link.href = url;
  const stamp = new Date().toISOString().slice(0, 10);
  link.setAttribute("download", `customers_${opts.mode ?? "since_last"}_${stamp}.csv`);
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(url);
}

