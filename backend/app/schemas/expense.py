from __future__ import annotations

import datetime as dt
import uuid
from typing import Annotated, Literal

from pydantic import BaseModel, Field, StringConstraints

from app.schemas.money import MoneyIn, MoneyOut

NonEmptyStr = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1)]

# How the expense was paid — drives Cash in Hand (cash sales − cash expenses).
ExpenseMethod = Literal["cash", "upi"]


class ExpenseCreate(BaseModel):
    amount: MoneyIn = Field(..., gt=0, description="Expense amount, e.g. 150.00")
    reason: NonEmptyStr
    payment_method: ExpenseMethod = "cash"


class ExpenseUpdate(BaseModel):
    amount: MoneyIn | None = Field(default=None, gt=0, description="Expense amount, e.g. 150.00")
    reason: NonEmptyStr | None = None
    payment_method: ExpenseMethod | None = None


class ExpenseOut(BaseModel):
    id: uuid.UUID
    shop_id: uuid.UUID
    amount: MoneyOut
    reason: str
    payment_method: ExpenseMethod = "cash"
    created_by: uuid.UUID | None
    created_at: dt.datetime

    model_config = {"from_attributes": True}

