// Typed API contracts. User is complete now; Product/Bill are stubs to be
// expanded in later prompts.

export type Role = "admin" | "owner" | "manager" | "salesperson";

export interface User {
  id: string;
  email: string;
  role: Role;
  shop_id: string | null;
  is_active: boolean;
  // Optional shop display name if the backend includes it on /auth/me later.
  shop_name?: string | null;
  business_name?: string | null;
  business_upi?: string | null;
}

export interface LoginResponse {
  access_token: string;
  token_type: string;
}

// ── Stubs (expanded later) ───────────────────────────────────────────────────
export interface Product {
  id: string;
  name: string;
  category: string | null;
  retail_price: string; // money as 2-decimal string
  last_wholesale_price: string | null;
  photo_url: string | null;
  is_active: boolean;
  created_at: string;
}

export interface Bill {
  id: string;
  bill_type: "retail" | "wholesale";
  total: string;
  created_at: string;
}

// Shape the backend uses for errors: { "detail": "..." } (or a list for 422).
export interface ApiErrorBody {
  detail?: string | { msg: string }[];
}
