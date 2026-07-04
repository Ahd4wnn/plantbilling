from __future__ import annotations

import datetime as dt
import uuid

from pydantic import BaseModel, ConfigDict


class CrashReportOut(BaseModel):
    """A stored crash, for the admin crash list/detail."""

    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    created_at: dt.datetime
    app_version: str | None = None
    android_version: str | None = None
    device: str | None = None
    installation_id: str | None = None
    stack_trace: str | None = None
    user_comment: str | None = None


class CrashReportListOut(BaseModel):
    items: list[CrashReportOut]
    limit: int
    offset: int
    has_more: bool
