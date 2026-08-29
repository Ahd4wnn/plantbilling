from __future__ import annotations

import datetime as dt
import uuid
from typing import Annotated, Literal

from pydantic import BaseModel, Field, StringConstraints

from app.schemas.money import MoneyIn, MoneyOut

NonEmptyStr = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1)]
Gender = Literal["male", "female"]
PayMethod = Literal["cash", "upi", "split", "due"]
PayKind = Literal["wage", "advance", "due_clear"]
AttendanceStatus = Literal["present", "absent", "half_day"]
# How a worker is paid. 'daily' reads default_wage; 'monthly' reads monthly_wage
# and paid_leaves_per_month. Only one of the wage fields is ever meaningful.
WageType = Literal["daily", "monthly"]


# ── Labourer (the worker profile) ────────────────────────────────────────────
class LabourerCreate(BaseModel):
    name: NonEmptyStr
    phone: str | None = None
    aadhaar: str | None = None
    gender: Gender
    wage_type: WageType = "daily"
    default_wage: MoneyIn = Field(default=0)  # type: ignore[assignment]   per day
    monthly_wage: MoneyIn = Field(default=0)  # type: ignore[assignment]   per month
    # Leaves allowed each month without losing pay. Resets monthly; doesn't carry
    # forward. Only meaningful for monthly workers.
    paid_leaves_per_month: int = Field(default=0, ge=0, le=31)
    # Omit to use today (IST) — the usual case, since a worker is normally added
    # on the day they start. Set it when entering someone who started earlier.
    joined_on: dt.date | None = None


class LabourerUpdate(BaseModel):
    name: NonEmptyStr | None = None
    phone: str | None = None
    aadhaar: str | None = None
    gender: Gender | None = None
    wage_type: WageType | None = None
    default_wage: MoneyIn | None = None
    monthly_wage: MoneyIn | None = None
    paid_leaves_per_month: int | None = Field(default=None, ge=0, le=31)
    is_active: bool | None = None
    joined_on: dt.date | None = None


class LabourerOut(BaseModel):
    id: uuid.UUID
    name: str
    phone: str | None = None
    aadhaar: str | None = None
    gender: Gender
    wage_type: WageType = "daily"
    default_wage: MoneyOut          # wage per day   (daily workers)
    monthly_wage: MoneyOut = 0  # type: ignore[assignment]   (monthly workers)
    paid_leaves_per_month: int = 0
    is_active: bool
    # Attendance-driven ledger (see app/services/labour_wages.py for the rules):
    #   days_worked = present + 0.5×half-day (from labour_attendance)
    #   earned      — daily:   wage_per_day × days_worked
    #                 monthly: salary per month, part months pro-rated at
    #                          monthly_wage/30 per day, minus monthly_wage/30 for
    #                          each leave beyond that month's paid-leave allowance
    #   total_paid  = every rupee handed over (wage + advance + legacy due-clear)
    #   balance_to_pay = earned − total_paid   (negative = paid ahead / advance)
    days_worked: str = "0"
    total_paid: MoneyOut = 0  # type: ignore[assignment]
    earned: MoneyOut = 0  # type: ignore[assignment]
    balance_to_pay: MoneyOut = 0  # type: ignore[assignment]
    joined_on: dt.date
    # This calendar month only, so the app can show WHY a monthly worker's pay was
    # reduced rather than just showing a smaller number. absent = 1, half-day = ½.
    leaves_this_month: str = "0"
    unpaid_leaves_this_month: str = "0"
    created_at: dt.datetime

    model_config = {"from_attributes": True}


# ── Labour payment (a record of paying a worker) ─────────────────────────────
class LabourPaymentCreate(BaseModel):
    labourer_id: uuid.UUID
    # A normal wage payment, or an advance paid ahead of work.
    kind: Literal["wage", "advance"] = "wage"
    # Wage pre-filled from wage_per_day × days but editable.
    wage_amount: MoneyIn = Field(default=0)  # type: ignore[assignment]
    days: MoneyIn | None = None
    # How the amount is settled. These must add up to the total.
    cash_amount: MoneyIn = Field(default=0)  # type: ignore[assignment]
    upi_amount: MoneyIn = Field(default=0)  # type: ignore[assignment]
    due_amount: MoneyIn = Field(default=0)  # type: ignore[assignment]
    note: str | None = None


class LabourPaymentUpdate(BaseModel):
    wage_amount: MoneyIn | None = None
    days: MoneyIn | None = None
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
    kind: PayKind = "wage"
    wage_amount: MoneyOut
    days: str | None = None
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


class AttendanceOut(BaseModel):
    id: uuid.UUID
    labourer_id: uuid.UUID
    labourer_name: str
    day: dt.date
    status: AttendanceStatus
    created_at: dt.datetime
