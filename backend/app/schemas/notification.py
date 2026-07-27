from __future__ import annotations

import datetime as dt
import uuid
from typing import Literal

from pydantic import BaseModel, ConfigDict, field_validator, model_validator


# ── Admin composer ────────────────────────────────────────────────────────────
class NotificationCreate(BaseModel):
    """Admin composes a notification, targeting all shops or specific shops."""

    title: str
    body: str
    action_url: str | None = None
    target: Literal["all", "shops"] = "all"
    shop_ids: list[uuid.UUID] = []

    @field_validator("title", "body")
    @classmethod
    def _non_empty(cls, v: str) -> str:
        v = (v or "").strip()
        if not v:
            raise ValueError("must not be empty")
        return v

    @field_validator("action_url")
    @classmethod
    def _clean_url(cls, v: str | None) -> str | None:
        v = (v or "").strip()
        return v or None

    @model_validator(mode="after")
    def _targets_present(self) -> "NotificationCreate":
        if self.target == "shops" and not self.shop_ids:
            raise ValueError("Pick at least one shop, or send to all shops")
        if self.target == "all":
            self.shop_ids = []
        return self


class AdminNotificationOut(BaseModel):
    """A sent notification for the admin history, with delivery/read stats."""

    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    title: str
    body: str
    action_url: str | None = None
    target: str
    shop_count: int  # shops addressed (0 for 'all' — meaning every shop)
    read_count: int  # distinct users who have read it
    created_at: dt.datetime


class AdminNotificationListOut(BaseModel):
    items: list[AdminNotificationOut]
    limit: int
    offset: int
    has_more: bool


# ── Shop-facing ───────────────────────────────────────────────────────────────
class NotificationOut(BaseModel):
    """A notification as seen by a shop user, with this user's read state."""

    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    title: str
    body: str
    action_url: str | None = None
    read: bool
    created_at: dt.datetime


class NotificationFeedOut(BaseModel):
    items: list[NotificationOut]
    unread_count: int
