import { api } from "./client";

export interface ShopSettings {
  id: string;
  name: string;
  owner_name: string | null;
  owner_phone: string | null;
  is_active: boolean;
  business_name: string | null;
  business_address: string | null;
  business_phone: string | null;
  business_email: string | null;
  business_upi: string | null;
  whatsapp_auto_send: boolean;
  whatsapp_enable_pdf: boolean;
  whatsapp_message_template: string | null;
  whatsapp_footer_message: string | null;
  whatsapp_language: string;
}

export interface ShopSettingsUpdate {
  business_name?: string | null;
  business_address?: string | null;
  business_phone?: string | null;
  business_email?: string | null;
  business_upi?: string | null;
  whatsapp_auto_send?: boolean | null;
  whatsapp_enable_pdf?: boolean | null;
  whatsapp_message_template?: string | null;
  whatsapp_footer_message?: string | null;
  whatsapp_language?: string | null;
}

export async function getShopSettings(): Promise<ShopSettings> {
  const { data } = await api.get<ShopSettings>("/shop");
  return data;
}

export async function updateShopSettings(payload: ShopSettingsUpdate): Promise<ShopSettings> {
  const { data } = await api.patch<ShopSettings>("/shop", payload);
  return data;
}

/** Manager/admin: reset the running cash-in-hand to an exact amount, now. */
export async function setCashInHand(amount: string): Promise<{ cash_in_hand_running: string }> {
  const { data } = await api.post<{ cash_in_hand_running: string }>("/shop/cash-in-hand", { amount });
  return data;
}
