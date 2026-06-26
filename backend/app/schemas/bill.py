from __future__ import annotations

import datetime as dt
import uuid
from decimal import Decimal
from typing import Annotated, Literal

from pydantic import BaseModel, Field, StringConstraints

from app.schemas.money import MoneyIn, MoneyOut, MoneyOutOpt
from app.schemas.expense import ExpenseOut


NonEmptyStr = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1)]


# ── Request ──────────────────────────────────────────────────────────────────
class BillItemIn(BaseModel):
    product_id: uuid.UUID
    quantity: int = Field(ge=1)
    # The per-line price. Pre-filled from the product's saved price on the client
    # but always editable (plants of the same kind sell at different prices by
    # size). Required for every line; the server recomputes the line total.
    unit_price: MoneyIn


class NewCustomerIn(BaseModel):
    name: NonEmptyStr
    phone: str | None = None


class BillCreate(BaseModel):
    # Client-generated UUID; reused across retries of the same cart.
    idempotency_key: NonEmptyStr
    items: list[BillItemIn] = Field(min_length=1)
    discount_type: Literal["flat", "percent"] = "flat"
    discount_value: MoneyIn = Field(default=0)  # type: ignore[assignment]
    cash_amount: MoneyIn = Field(default=0)  # type: ignore[assignment]
    upi_amount: MoneyIn = Field(default=0)  # type: ignore[assignment]
    due_amount: MoneyIn = Field(default=0)  # type: ignore[assignment]
    remarks: str | None = None
    # Customer is always entered fresh per bill (name + optional phone) and is
    # optional. The "select existing customer" feature has been removed.
    new_customer: NewCustomerIn | None = None


class BillUpdate(BaseModel):
    cash_amount: MoneyIn | None = None
    upi_amount: MoneyIn | None = None
    due_amount: MoneyIn | None = None
    remarks: str | None = None
    # Owners (and admins) may also correct the line items themselves — edit a
    # price, change a quantity, add or remove a plant. When `items` is provided the
    # server recomputes subtotal/discount/total and rewrites the bill's items. The
    # payment split sent alongside must balance to the new total.
    items: list[BillItemIn] | None = Field(default=None, min_length=1)
    discount_type: Literal["flat", "percent"] | None = None
    discount_value: MoneyIn | None = None


# ── Response ─────────────────────────────────────────────────────────────────
class BillItemOut(BaseModel):
    product_id: uuid.UUID | None
    product_name: str
    unit_price: MoneyOut
    quantity: int
    line_total: MoneyOut

    model_config = {"from_attributes": True}


class BillOut(BaseModel):
    id: uuid.UUID
    bill_type: str
    subtotal: MoneyOut
    discount_type: str
    discount_value: MoneyOut
    discount_amount: MoneyOut
    total: MoneyOut
    cash_amount: MoneyOut
    upi_amount: MoneyOut
    due_amount: MoneyOut
    customer_id: uuid.UUID | None
    customer_name: str | None = None
    created_by: uuid.UUID | None
    salesperson_email: str | None = None
    remarks: str | None = None
    is_edited: bool = False
    created_at: dt.datetime
    items: list[BillItemOut]
    # True when this response replays a previously-saved bill (idempotent retry).
    idempotent_replay: bool = False
    # Echo so the client can confirm which key produced this bill.
    idempotency_key: str | None = None

    model_config = {"from_attributes": True}


# ── Sales tab: summary, history list, detail ─────────────────────────────────
PaymentMethod = Literal["cash", "upi", "split", "due"]


class BillSummaryOut(BaseModel):
    """Aggregated takings for a single day (shop timezone)."""

    date: dt.date
    total_sales: MoneyOut
    bill_count: int
    cash_total: MoneyOut
    upi_total: MoneyOut
    due_total: MoneyOut
    total_expenses: MoneyOut
    net_sales: MoneyOut
    expenses: list[ExpenseOut]



class BillListItem(BaseModel):
    """A compact history row."""

    id: uuid.UUID
    created_at: dt.datetime
    bill_type: str
    total: MoneyOut
    due_amount: MoneyOut = Decimal("0.00")  # type: ignore[assignment]
    customer_name: str | None = None
    customer_phone: str | None = None
    item_count: int
    payment_method: PaymentMethod
    is_edited: bool = False


class BillListOut(BaseModel):
    items: list[BillListItem]
    limit: int
    offset: int
    has_more: bool


class BillDetailOut(BaseModel):
    """A complete, self-contained bill for the detail / reprint surface."""

    id: uuid.UUID
    shop_name: str | None = None
    business_name: str | None = None
    business_address: str | None = None
    business_phone: str | None = None
    bill_type: str
    subtotal: MoneyOut
    discount_type: str
    discount_value: MoneyOut
    discount_amount: MoneyOut
    total: MoneyOut
    cash_amount: MoneyOut
    upi_amount: MoneyOut
    due_amount: MoneyOut
    payment_method: PaymentMethod
    customer_id: uuid.UUID | None
    customer_name: str | None = None
    customer_phone: str | None = None
    salesperson_email: str | None = None
    remarks: str | None = None
    is_edited: bool = False
    created_at: dt.datetime
    items: list[BillItemOut]
