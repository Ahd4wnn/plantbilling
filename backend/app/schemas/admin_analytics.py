"""Schemas for the admin command-center: cross-shop analytics + export status.

Admin sees every shop (admin RLS policy), so — unlike the owner area — nothing is
scoped by ownership; all shops are aggregated. Money is always emitted as a 2dp
string, mirroring the owner schemas.
"""
from __future__ import annotations

import datetime as dt
import decimal
import uuid

from pydantic import BaseModel, field_serializer

_MONEY = ("total_sales", "cash_total", "upi_total", "due_total", "total_expenses", "net_sales")


class AdminShopRow(BaseModel):
    """One shop's takings within the overview period + a couple of health signals."""

    shop_id: uuid.UUID
    shop_name: str
    is_active: bool
    owner_email: str | None
    total_sales: decimal.Decimal
    bill_count: int
    cash_total: decimal.Decimal
    upi_total: decimal.Decimal
    due_total: decimal.Decimal
    total_expenses: decimal.Decimal
    net_sales: decimal.Decimal
    staff_count: int
    last_bill_at: dt.datetime | None  # most recent bill ever (health / churn signal)

    @field_serializer(*_MONEY)
    def _ser(self, v: decimal.Decimal) -> str:
        return f"{v:.2f}"


class TrendPoint(BaseModel):
    """Platform-wide sales for one calendar day (IST)."""

    date: dt.date
    sales: decimal.Decimal
    bill_count: int

    @field_serializer("sales")
    def _ser(self, v: decimal.Decimal) -> str:
        return f"{v:.2f}"


class AttentionItem(BaseModel):
    """A shop that needs the admin's attention (churn / setup gap)."""

    shop_id: uuid.UUID
    shop_name: str
    kind: str      # "silent" | "inactive" | "no_owner"
    detail: str    # human-readable reason


class AdminStaffPerformance(BaseModel):
    """A staff member's sales in the overview period (top-sellers leaderboard)."""

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


class AdminOverview(BaseModel):
    """Everything the platform dashboard needs, in a single round trip."""

    start_date: dt.date
    end_date: dt.date
    total_shops: int
    active_shops: int
    total_sales: decimal.Decimal
    bill_count: int
    cash_total: decimal.Decimal
    upi_total: decimal.Decimal
    due_total: decimal.Decimal
    total_expenses: decimal.Decimal
    net_sales: decimal.Decimal
    shops: list[AdminShopRow]
    trend: list[TrendPoint]
    attention: list[AttentionItem]
    staff: list[AdminStaffPerformance]

    @field_serializer(*_MONEY)
    def _ser(self, v: decimal.Decimal) -> str:
        return f"{v:.2f}"


class AdminStaffRow(BaseModel):
    """One staff member in the cross-shop directory (managers + salespeople)."""

    user_id: uuid.UUID
    email: str
    role: str
    is_active: bool
    shop_id: uuid.UUID | None
    shop_name: str | None
    created_at: dt.datetime
    total_sales: decimal.Decimal
    bill_count: int
    last_bill_at: dt.datetime | None

    @field_serializer("total_sales")
    def _ser(self, v: decimal.Decimal) -> str:
        return f"{v:.2f}"


class AdminShopDetail(BaseModel):
    """The per-shop drawer bundle: identity + a 30-day report + staff + cash + bills."""

    shop_id: uuid.UUID
    shop_name: str
    is_active: bool
    business_name: str | None
    business_address: str | None
    business_phone: str | None
    business_email: str | None
    business_upi: str | None
    owner_email: str | None
    cash_in_hand_running: decimal.Decimal
    last_bill_at: dt.datetime | None
    staff_count: int
    # A ready-made report for the last 30 days (totals, top products, categories,
    # expenses). Kept as a nested object so the drawer needs no extra calls.
    report: dict
    recent_bills: list["AdminRecentBill"]

    @field_serializer("cash_in_hand_running")
    def _ser(self, v: decimal.Decimal) -> str:
        return f"{v:.2f}"


class AdminRecentBill(BaseModel):
    id: uuid.UUID
    created_at: dt.datetime
    total: decimal.Decimal
    payment_method: str
    customer_name: str | None
    salesperson_email: str | None

    @field_serializer("total")
    def _ser(self, v: decimal.Decimal) -> str:
        return f"{v:.2f}"


class ExportStatus(BaseModel):
    """Where the customer-export watermark stands, for the export UI."""

    last_exported_at: dt.datetime | None
    new_since_last: int   # customers created after the watermark (0 if never exported → total)
    total_customers: int


AdminShopDetail.model_rebuild()
