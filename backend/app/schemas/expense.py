from __future__ import annotations

import datetime as dt
import uuid
from typing import Annotated, Literal

from pydantic import BaseModel, Field, StringConstraints

from app.schemas.money import MoneyIn, MoneyOut

NonEmptyStr = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1)]

# How the expense was paid — drives Cash in Hand (cash sales − cash expenses).
ExpenseMethod = Literal["cash", "upi"]


# ── Expense categories (manager-curated, shop-scoped) ─────────────────────────
class ExpenseCategoryCreate(BaseModel):
    name: NonEmptyStr


class ExpenseCategoryOut(BaseModel):
    id: uuid.UUID
    name: str
    created_at: dt.datetime

    model_config = {"from_attributes": True}


# ── Expenses ──────────────────────────────────────────────────────────────────
class ExpenseCreate(BaseModel):
    amount: MoneyIn = Field(..., gt=0, description="Expense amount, e.g. 150.00")
    # Pick a category (preferred) — its name is snapshotted into `reason`. `reason`
    # remains accepted for the legacy free-text path / backward compatibility; at
    # least one of the two must be present (validated in the router).
    category_id: uuid.UUID | None = None
    reason: NonEmptyStr | None = None
    # Optional free-text remark with the specifics of this spend.
    note: str | None = None
    payment_method: ExpenseMethod = "cash"


class ExpenseUpdate(BaseModel):
    amount: MoneyIn | None = Field(default=None, gt=0, description="Expense amount, e.g. 150.00")
    category_id: uuid.UUID | None = None
    reason: NonEmptyStr | None = None
    note: str | None = None
    payment_method: ExpenseMethod | None = None


class ExpenseOut(BaseModel):
    id: uuid.UUID
    shop_id: uuid.UUID
    amount: MoneyOut
    reason: str
    category_id: uuid.UUID | None = None
    category_name: str | None = None
    note: str | None = None
    payment_method: ExpenseMethod = "cash"
    created_by: uuid.UUID | None
    created_at: dt.datetime

    model_config = {"from_attributes": True}
