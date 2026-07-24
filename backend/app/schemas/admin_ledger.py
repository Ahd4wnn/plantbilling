"""Schemas for the platform's own books (admin sales & expenses)."""
from __future__ import annotations

import datetime as dt
import uuid
from typing import Annotated, Literal

from pydantic import BaseModel, Field, StringConstraints

from app.schemas.money import MoneyIn, MoneyOut

NonEmptyStr = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1)]
OptStr = Annotated[str, StringConstraints(strip_whitespace=True)]

PaymentMethod = Literal["cash", "upi"]


# ── Sales ──────────────────────────────────────────────────────────────────
class AdminSaleCreate(BaseModel):
    title: NonEmptyStr = Field(..., description="What was sold, e.g. 'Thermal printer — Green Leaf'")
    amount: MoneyIn = Field(..., gt=0, description="Total sale amount")
    cash_amount: MoneyIn = Field(default=0, description="Paid in cash")
    upi_amount: MoneyIn = Field(default=0, description="Paid by UPI")
    due_amount: MoneyIn = Field(default=0, description="Owed / to be paid later")
    customer_name: OptStr | None = None
    customer_phone: OptStr | None = None
    note: OptStr | None = None
    occurred_on: dt.date | None = Field(default=None, description="Business date (IST). Defaults to today.")


class AdminSaleUpdate(BaseModel):
    title: NonEmptyStr | None = None
    amount: MoneyIn | None = Field(default=None, gt=0)
    cash_amount: MoneyIn | None = None
    upi_amount: MoneyIn | None = None
    due_amount: MoneyIn | None = None
    customer_name: OptStr | None = None
    customer_phone: OptStr | None = None
    note: OptStr | None = None
    occurred_on: dt.date | None = None


class CollectDueRequest(BaseModel):
    """Collect part or all of an outstanding due, as cash or UPI."""

    amount: MoneyIn = Field(..., gt=0, description="Amount now collected")
    method: PaymentMethod = "cash"


class AdminSaleOut(BaseModel):
    id: uuid.UUID
    title: str
    amount: MoneyOut
    cash_amount: MoneyOut
    upi_amount: MoneyOut
    due_amount: MoneyOut
    customer_name: str | None
    customer_phone: str | None
    note: str | None
    occurred_on: dt.date
    created_at: dt.datetime

    model_config = {"from_attributes": True}


class AdminSaleList(BaseModel):
    items: list[AdminSaleOut]
    has_more: bool


# ── Expenses ───────────────────────────────────────────────────────────────
class AdminExpenseCreate(BaseModel):
    reason: NonEmptyStr = Field(..., description="What the money was spent on")
    amount: MoneyIn = Field(..., gt=0)
    payment_method: PaymentMethod = "cash"
    note: OptStr | None = None
    occurred_on: dt.date | None = None


class AdminExpenseUpdate(BaseModel):
    reason: NonEmptyStr | None = None
    amount: MoneyIn | None = Field(default=None, gt=0)
    payment_method: PaymentMethod | None = None
    note: OptStr | None = None
    occurred_on: dt.date | None = None


class AdminExpenseOut(BaseModel):
    id: uuid.UUID
    reason: str
    amount: MoneyOut
    payment_method: PaymentMethod
    note: str | None
    occurred_on: dt.date
    created_at: dt.datetime

    model_config = {"from_attributes": True}


class AdminExpenseList(BaseModel):
    items: list[AdminExpenseOut]
    has_more: bool


# ── Dashboard summary ──────────────────────────────────────────────────────
class LedgerTrendPoint(BaseModel):
    date: dt.date
    sales: MoneyOut
    expenses: MoneyOut


class LedgerSummary(BaseModel):
    date_from: dt.date
    date_to: dt.date
    # Sales
    total_sales: MoneyOut          # sum of sale amounts
    sales_count: int
    cash_collected: MoneyOut       # cash portion of sales (already in hand)
    upi_collected: MoneyOut        # upi portion of sales
    outstanding_due: MoneyOut      # sum of due still owed (in range)
    # Expenses
    total_expenses: MoneyOut
    expenses_count: int
    # Net = collected (cash+upi) − expenses
    net_collected: MoneyOut
    trend: list[LedgerTrendPoint]
