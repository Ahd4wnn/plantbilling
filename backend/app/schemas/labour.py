from __future__ import annotations

import datetime as dt
import uuid
from typing import Annotated, Literal

from pydantic import BaseModel, Field, StringConstraints

from app.schemas.money import MoneyIn, MoneyOut

NonEmptyStr = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1)]
Gender = Literal["male", "female"]
PayMethod = Literal["cash", "upi", "split", "due"]
AttendanceStatus = Literal["present", "absent", "half_day"]


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
    # Aggregates across this worker's payments.
    total_paid: MoneyOut = 0  # type: ignore[assignment]
    outstanding_due: MoneyOut = 0  # type: ignore[assignment]
    created_at: dt.datetime

    model_config = {"from_attributes": True}


# ── Labour payment (a record of paying a worker) ─────────────────────────────
class LabourPaymentCreate(BaseModel):
    labourer_id: uuid.UUID
    # Wage pre-filled from the default but editable; overtime = hours × the rate.
    wage_amount: MoneyIn = Field(default=0)  # type: ignore[assignment]
    overtime_hours: MoneyIn = Field(default=0)  # type: ignore[assignment]
    # How the total (wage + overtime) is settled. These must add up to the total.
    cash_amount: MoneyIn = Field(default=0)  # type: ignore[assignment]
    upi_amount: MoneyIn = Field(default=0)  # type: ignore[assignment]
    due_amount: MoneyIn = Field(default=0)  # type: ignore[assignment]
    note: str | None = None


class LabourPaymentUpdate(BaseModel):
    wage_amount: MoneyIn | None = None
    overtime_hours: MoneyIn | None = None
    cash_amount: MoneyIn | None = None
    upi_amount: MoneyIn | None = None
    due_amount: MoneyIn | None = None
    note: str | None = None


class LabourDueClear(BaseModel):
    """Pay off money owed to a worker (reduces their outstanding due)."""

    labourer_id: uuid.UUID
    cash_amount: MoneyIn = Field(default=0)  # type: ignore[assignment]
    upi_amount: MoneyIn = Field(default=0)  # type: ignore[assignment]
    note: str | None = None


class LabourPaymentOut(BaseModel):
    id: uuid.UUID
    labourer_id: uuid.UUID | None
    labourer_name: str
    gender: Gender
    kind: Literal["wage", "due_clear"] = "wage"
    wage_amount: MoneyOut
    overtime_hours: MoneyOut
    overtime_rate: MoneyOut
    overtime_amount: MoneyOut
    total_amount: MoneyOut
    cash_amount: MoneyOut
    upi_amount: MoneyOut
    due_amount: MoneyOut
    payment_method: PayMethod
    note: str | None = None
    recorded_by_email: str | None = None
    created_at: dt.datetime


# ── Attendance ───────────────────────────────────────────────────────────────
class AttendanceMark(BaseModel):
    labourer_id: uuid.UUID
    day: dt.date
    status: AttendanceStatus
    overtime_hours: MoneyIn = Field(default=0)  # type: ignore[assignment]


class AttendanceOut(BaseModel):
    id: uuid.UUID
    labourer_id: uuid.UUID
    labourer_name: str
    day: dt.date
    status: AttendanceStatus
    overtime_hours: MoneyOut
    created_at: dt.datetime
