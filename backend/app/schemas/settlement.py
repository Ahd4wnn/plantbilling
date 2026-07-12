from __future__ import annotations

import datetime as dt
import uuid
from typing import Literal

from pydantic import BaseModel

from app.schemas.money import MoneyIn, MoneyOut


class SettlementCreate(BaseModel):
    """Collect an outstanding due, split across cash and UPI. The two must add up
    to the bill's current due amount (the server validates)."""

    bill_id: uuid.UUID
    cash_amount: MoneyIn = 0  # type: ignore[assignment]
    upi_amount: MoneyIn = 0  # type: ignore[assignment]


class SettlementActionResult(BaseModel):
    """Outcome of creating a settlement.

    status is 'approved' when a manager/admin collected the due directly (applied
    immediately) or 'pending' when a salesperson's request is waiting for a
    manager to approve it.
    """

    id: uuid.UUID
    bill_id: uuid.UUID
    status: Literal["pending", "approved", "rejected"]
    cash_amount: MoneyOut
    upi_amount: MoneyOut


class SettlementOut(BaseModel):
    """A settlement row for the manager's approval queue."""

    id: uuid.UUID
    bill_id: uuid.UUID
    status: Literal["pending", "approved", "rejected"]
    cash_amount: MoneyOut
    upi_amount: MoneyOut
    # The bill this due belongs to, for context in the queue.
    bill_total: MoneyOut
    customer_name: str | None = None
    customer_phone: str | None = None
    requested_by_email: str | None = None
    created_at: dt.datetime


class SettlementListOut(BaseModel):
    items: list[SettlementOut]
