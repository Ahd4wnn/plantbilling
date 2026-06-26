"""Schemas for the multi-shop owner area (oversight + business details + staff)."""
from __future__ import annotations

import datetime as dt
import decimal
import re
import uuid
from typing import Literal

from pydantic import BaseModel, EmailStr, Field, field_serializer, field_validator

_MONEY = ("total_sales", "cash_total", "upi_total", "due_total", "total_expenses", "net_sales")


class OwnerShop(BaseModel):
    """One shop the owner owns — identity + editable business details."""

    id: uuid.UUID
    name: str
    is_active: bool
    business_name: str | None = None
    business_address: str | None = None
    business_phone: str | None = None
    business_email: str | None = None
    business_upi: str | None = None
    whatsapp_auto_send: bool = False

    model_config = {"from_attributes": True}


class OwnerShopUpdate(BaseModel):
    """Owner edits to a shop's business details. Cannot toggle is_active (admin only)."""

    business_name: str | None = None
    business_address: str | None = None
    business_phone: str | None = None
    business_email: str | None = None
    business_upi: str | None = None
    whatsapp_auto_send: bool | None = None
    whatsapp_message_template: str | None = None

    @field_validator("business_upi")
    @classmethod
    def validate_upi(cls, v: str | None) -> str | None:
        if v is None:
            return None
        v_clean = v.strip()
        if not v_clean:
            return None
        if not re.compile(r"^[a-zA-Z0-9.\-_]{2,256}@[a-zA-Z]{2,64}$").match(v_clean):
            raise ValueError("Invalid UPI ID (VPA). Must look like username@bank.")
        return v_clean


class ShopOverviewRow(BaseModel):
    """A single shop's takings within the overview period."""

    shop_id: uuid.UUID
    shop_name: str
    total_sales: decimal.Decimal
    bill_count: int
    cash_total: decimal.Decimal
    upi_total: decimal.Decimal
    due_total: decimal.Decimal
    total_expenses: decimal.Decimal
    net_sales: decimal.Decimal

    @field_serializer(*_MONEY)
    def _ser(self, v: decimal.Decimal) -> str:
        return f"{v:.2f}"


class StaffPerformance(BaseModel):
    """A staff member's sales across the owner's shops in the period."""

    user_id: uuid.UUID | None
    email: str | None
    shop_id: uuid.UUID
    shop_name: str
    role: str
    total_sales: decimal.Decimal
    bill_count: int

    @field_serializer("total_sales")
    def _ser(self, v: decimal.Decimal) -> str:
        return f"{v:.2f}"


class OwnerOverview(BaseModel):
    """Aggregate across all owned shops + per-shop breakdown + staff leaderboard."""

    start_date: dt.date
    end_date: dt.date
    shop_count: int
    total_sales: decimal.Decimal
    bill_count: int
    cash_total: decimal.Decimal
    upi_total: decimal.Decimal
    due_total: decimal.Decimal
    total_expenses: decimal.Decimal
    net_sales: decimal.Decimal
    shops: list[ShopOverviewRow]
    staff: list[StaffPerformance]

    @field_serializer(*_MONEY)
    def _ser(self, v: decimal.Decimal) -> str:
        return f"{v:.2f}"


class OwnerStaffCreate(BaseModel):
    email: EmailStr
    password: str = Field(min_length=8)
    role: Literal["manager", "salesperson"] = "salesperson"


class OwnerStaffOut(BaseModel):
    id: uuid.UUID
    email: EmailStr
    role: str
    is_active: bool
    shop_id: uuid.UUID | None
    created_at: dt.datetime

    model_config = {"from_attributes": True}


class OwnerStaffActivate(BaseModel):
    is_active: bool


class OwnerStaffResetPassword(BaseModel):
    new_password: str = Field(min_length=8)
