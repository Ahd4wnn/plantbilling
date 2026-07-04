import { api } from "./client";

export interface CrashReportRow {
  id: string;
  created_at: string;
  app_version: string | null;
  android_version: string | null;
  device: string | null;
  installation_id: string | null;
  stack_trace: string | null;
  user_comment: string | null;
}

export interface CrashReportList {
  items: CrashReportRow[];
  limit: number;
  offset: number;
  has_more: boolean;
}

export async function listCrashReports(limit = 50, offset = 0): Promise<CrashReportList> {
  const { data } = await api.get<CrashReportList>("/admin/crash-reports", {
    params: { limit, offset },
  });
  return data;
}
