import { api } from "./client";
import type { Product } from "./types";

export type ActiveFilter = "true" | "false" | "all";

export interface ProductListParams {
  q?: string;
  category?: string;
  active?: ActiveFilter;
}

export async function listProducts(params: ProductListParams = {}): Promise<Product[]> {
  const query: Record<string, string> = { active: params.active ?? "true" };
  if (params.q?.trim()) query.q = params.q.trim();
  if (params.category) query.category = params.category;
  const { data } = await api.get<Product[]>("/products", { params: query });
  return data;
}

export interface ProductWritePayload {
  name: string;
  category: string | null;
  retail_price: string; // 2-decimal money string
  last_wholesale_price: string | null;
}

export async function createProduct(payload: ProductWritePayload): Promise<Product> {
  const { data } = await api.post<Product>("/products", payload);
  return data;
}

/** Partial update — send only the keys you want changed. */
export async function updateProduct(
  id: string,
  patch: Partial<ProductWritePayload> & { is_active?: boolean },
): Promise<Product> {
  const { data } = await api.patch<Product>(`/products/${id}`, patch);
  return data;
}

/** Soft delete (retire): keeps history, hides from billing. */
export async function retireProduct(id: string): Promise<void> {
  await api.delete(`/products/${id}`);
}

export async function reactivateProduct(id: string): Promise<Product> {
  return updateProduct(id, { is_active: true });
}

/** Image upload (multipart). Replaces any existing image server-side. */
export async function uploadProductImage(id: string, file: File): Promise<Product> {
  const form = new FormData();
  form.append("file", file);
  const { data } = await api.post<Product>(`/products/${id}/image`, form, {
    headers: { "Content-Type": "multipart/form-data" },
  });
  return data;
}

export async function deleteProductImage(id: string): Promise<Product> {
  const { data } = await api.delete<Product>(`/products/${id}/image`);
  return data;
}

export async function bulkUploadProducts(file: File): Promise<Product[]> {
  const form = new FormData();
  form.append("file", file);
  const { data } = await api.post<Product[]>("/products/bulk-upload", form, {
    headers: { "Content-Type": "multipart/form-data" },
  });
  return data;
}

export async function downloadSampleFile(): Promise<void> {
  const { data } = await api.get("/products/sample-file", {
    responseType: "blob",
  });
  const url = window.URL.createObjectURL(new Blob([data]));
  const link = document.createElement("a");
  link.href = url;
  link.setAttribute("download", "plantbill_product_upload_sample.csv");
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(url);
}

export interface BulkDeleteResponse {
  detail: string;
  hard_deleted: number;
  soft_deleted: number;
}

export async function bulkDeleteProducts(productIds: string[]): Promise<BulkDeleteResponse> {
  const { data } = await api.post<BulkDeleteResponse>("/products/bulk-delete", { product_ids: productIds });
  return data;
}

export interface BulkPhotosResponse {
  detail: string;
  matched: number;
  errors: string[];
}

export async function bulkUploadPhotos(file: File): Promise<BulkPhotosResponse> {
  const form = new FormData();
  form.append("file", file);
  const { data } = await api.post<BulkPhotosResponse>("/products/bulk-photos", form, {
    headers: { "Content-Type": "multipart/form-data" },
  });
  return data;
}



