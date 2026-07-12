from __future__ import annotations

import datetime as dt
import uuid
from typing import Annotated, Literal

from pydantic import BaseModel, Field, StringConstraints

from app.schemas.money import MoneyIn, MoneyOut

NonEmptyStr = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1)]
Gender = Literal["male", "female"]


# ── Labourer (the worker profile) ────────────────────────────────────────────
class LabourerCreate(BaseModel):
    name: NonEmptyStr
    phone: str | None = None
    gender: Gender
    default_wage: MoneyIn = Field(default=0)  # type: ignore[assignment]
    overtime_rate: MoneyIn = Field(default=0)  # type: ignore[assignment]


class LabourerUpdate(BaseModel):
    name: NonEmptyStr | None = None
    phone: str | None = None
    gender: Gender | None = None
    default_wage: MoneyIn | None = None
    overtime_rate: MoneyIn | None = None
    is_active: bool | None = None


class LabourerOut(BaseModel):
    id: uuid.UUID
    name: str
    phone: str | None = None
    gender: Gender
    default_wage: MoneyOut
    overtime_rate: MoneyOut
    is_active: bool
    created_at: dt.datetime

    model_config = {"from_attributes": True}


# ── Labour payment (a record of paying a worker) ─────────────────────────────
class LabourPaymentCreate(BaseModel):
    labourer_id: uuid.UUID
    # Pre-filled from the labourer's default wage on the client, but editable.
    wage_amount: MoneyIn = Field(default=0)  # type: ignore[assignment]
    overtime_hours: MoneyIn = Field(default=0)  # type: ignore[assignment]
    note: str | None = None


class LabourPaymentUpdate(BaseModel):
    wage_amount: MoneyIn | None = None
    overtime_hours: MoneyIn | None = None
    note: str | None = None


class LabourPaymentOut(BaseModel):
    id: uuid.UUID
    labourer_id: uuid.UUID | None
    labourer_name: str
    gender: Gender
    wage_amount: MoneyOut
    overtime_hours: MoneyOut
    overtime_rate: MoneyOut
    overtime_amount: MoneyOut
    total_amount: MoneyOut
    note: str | None = None
    recorded_by_email: str | None = None
    created_at: dt.datetime

    model_config = {"from_attributes": True}
