from __future__ import annotations

import datetime as dt
import uuid
from typing import Annotated, Literal

from pydantic import BaseModel, StringConstraints

from app.schemas.money import MoneyIn, MoneyOut, MoneyOutOpt

NonEmptyStr = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1)]

# Derived from which of cash/upi are non-zero (same convention as bills).
PaymentMethod = Literal["cash", "upi", "split", "none"]


class BorrowingCreate(BaseModel):
    lender_name: NonEmptyStr
    lender_phone: str | None = None
    amount: MoneyIn
    # How the money was received. Must sum to `amount`. Informational only.
    cash_amount: MoneyIn = 0
    upi_amount: MoneyIn = 0
    remarks: str | None = None


class BorrowingPay(BaseModel):
    """Mark a borrowing fully repaid, recording how it was paid back."""

    paid_cash_amount: MoneyIn = 0
    paid_upi_amount: MoneyIn = 0


class BorrowingOut(BaseModel):
    id: uuid.UUID
    lender_name: str
    lender_phone: str | None = None
    amount: MoneyOut
    cash_amount: MoneyOut
    upi_amount: MoneyOut
    method: PaymentMethod
    remarks: str | None = None
    is_paid: bool
    paid_cash_amount: MoneyOut
    paid_upi_amount: MoneyOut
    paid_method: PaymentMethod
    # How much is still owed to the lender (amount − what's been paid back so far).
    outstanding: MoneyOut
    paid_at: dt.datetime | None = None
    created_at: dt.datetime


class BorrowingList(BaseModel):
    items: list[BorrowingOut]
    # Total still owed (sum of unpaid borrowings) — the number the owner cares about.
    total_outstanding: MoneyOut
