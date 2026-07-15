"""Schemas for the admin command-center: PLATFORM health, not shop finances.

PlantBill is sold to shops that aren't ours, so a shop's sales/cash/revenue is the
shop's private data — never surfaced to the platform admin. These schemas expose
only SaaS-operator signals: how many shops exist, who's active, who's gone quiet,
onboarding, staff, and app usage (bill *counts* as an engagement metric, never
amounts).
"""
from __future__ import annotations

import datetime as dt
import uuid

from pydantic import BaseModel


class AdminShopRow(BaseModel):
    """One shop's health/engagement within the overview period (no money)."""

    shop_id: uuid.UUID
    shop_name: str
    is_active: bool
    owner_email: str | None       # the shop's manager login
    owner_count: int              # linked multi-shop owner accounts
    created_at: dt.datetime
    bills_in_period: int          # app usage / engagement, not revenue
    staff_count: int
    last_bill_at: dt.datetime | None


class TrendPoint(BaseModel):
    """Platform activity for one calendar day (IST)."""

    date: dt.date
    bills: int        # total bills created across all shops (usage)
    new_shops: int    # shops onboarded that day


class AttentionItem(BaseModel):
    """A shop that needs the admin's attention (churn / setup gap)."""

    shop_id: uuid.UUID
    shop_name: str
    kind: str      # "silent" | "inactive" | "no_owner"
    detail: str    # human-readable reason


class AdminOverview(BaseModel):
    """Platform health in a single round trip — no shop financials."""

    start_date: dt.date
    end_date: dt.date
    total_shops: int
    active_shops: int       # billed at least once in the period (engagement)
    inactive_shops: int     # deactivated accounts
    new_shops: int          # onboarded in the period
    total_staff: int        # managers + salespeople across all shops
    total_owners: int       # multi-shop owner accounts
    total_bills: int        # bills created in the period (platform usage)
    shops: list[AdminShopRow]
    trend: list[TrendPoint]
    attention: list[AttentionItem]


class AdminStaffRow(BaseModel):
    """One staff member in the cross-shop directory (managers + salespeople)."""

    user_id: uuid.UUID
    email: str
    role: str
    is_active: bool
    shop_id: uuid.UUID | None
    shop_name: str | None
    created_at: dt.datetime
    bill_count: int              # bills created (usage), not revenue
    last_bill_at: dt.datetime | None


class AdminActivity(BaseModel):
    """A recent bill as an activity signal — timestamp + who, never the amount."""

    created_at: dt.datetime
    salesperson_email: str | None
    item_count: int


class AdminShopDetail(BaseModel):
    """Per-shop drawer bundle: account + setup + engagement (no money)."""

    shop_id: uuid.UUID
    shop_name: str
    is_active: bool
    created_at: dt.datetime
    business_name: str | None
    business_address: str | None
    business_phone: str | None
    business_email: str | None
    business_upi: str | None
    owner_email: str | None
    owner_emails: list[str]      # linked multi-shop owners
    staff_count: int
    products_count: int
    customers_count: int
    bills_7: int                 # bills in the last 7 days
    bills_30: int                # bills in the last 30 days
    last_bill_at: dt.datetime | None
    recent_activity: list[AdminActivity]


class ExportStatus(BaseModel):
    """Where the customer-export watermark stands, for the export UI."""

    last_exported_at: dt.datetime | None
    new_since_last: int   # customers created after the watermark (0 if never exported → total)
    total_customers: int
