import { api } from "./client";

export interface ShopCustomer {
  id: string;
  name: string;
  phone: string | null;
  whatsapp_opted_out: boolean;
  whatsapp_opted_out_at: string | null;
  created_at: string;
}

export interface CustomerLookup {
  found: boolean;
  name: string | null;
  visit_count: number;
}

/** Returning-customer hint for the billing screen. Scoped to this shop by RLS. */
export async function lookupCustomer(phone: string): Promise<CustomerLookup> {
  const { data } = await api.get<CustomerLookup>("/customers/lookup", {
    params: { phone },
  });
  return data;
}

export async function fetchShopCustomers(q?: string): Promise<ShopCustomer[]> {
  const query: Record<string, string> = {};
  if (q) query.q = q;
  const { data } = await api.get<ShopCustomer[]>("/customers", { params: query });
  return data;
}

export async function downloadCustomersCSV(q?: string): Promise<void> {
  const query: Record<string, string> = {};
  if (q) query.q = q;

  const { data } = await api.get("/customers/download", {
    params: query,
    responseType: "blob",
  });

  const url = window.URL.createObjectURL(new Blob([data]));
  const link = document.createElement("a");
  link.href = url;
  link.setAttribute("download", `customers_export.csv`);
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(url);
}
