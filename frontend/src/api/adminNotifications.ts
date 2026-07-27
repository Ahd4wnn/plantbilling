import { api } from "./client";

export interface NotificationRow {
  id: string;
  title: string;
  body: string;
  action_url: string | null;
  target: "all" | "shops";
  shop_count: number; // shops addressed (0 for 'all' — every shop)
  read_count: number; // distinct users who have read it
  created_at: string;
}

export interface NotificationList {
  items: NotificationRow[];
  limit: number;
  offset: number;
  has_more: boolean;
}

export interface NotificationCreatePayload {
  title: string;
  body: string;
  action_url?: string | null;
  target: "all" | "shops";
  shop_ids?: string[];
}

export async function listNotifications(limit = 50, offset = 0): Promise<NotificationList> {
  const { data } = await api.get<NotificationList>("/admin/notifications", {
    params: { limit, offset },
  });
  return data;
}

export async function createNotification(payload: NotificationCreatePayload): Promise<NotificationRow> {
  const { data } = await api.post<NotificationRow>("/admin/notifications", payload);
  return data;
}

export async function deleteNotification(id: string): Promise<void> {
  await api.delete(`/admin/notifications/${id}`);
}
