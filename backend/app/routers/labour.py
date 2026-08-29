"""Labour roster, payments (cash/UPI/split/due), attendance.

Any shop staff (manager or salesperson) can add workers, record payments, and mark
attendance. A payment's wage is split across cash / UPI / due
like a bill — only the cash part lowers Cash in Hand; the due part is money still
owed to the worker, cleared later via a 'due_clear' payment. Editing/deleting a
worker or payment stays manager-only.
"""
from __future__ import annotations

import datetime as dt
import uuid
from decimal import Decimal

from fastapi import APIRouter, Depends, HTTPException, Query, Response, status
from sqlalchemy import func, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.auth.dependencies import get_db, require_shop_staff, require_manager_or_admin
from app.models.labour import Labourer, LabourPayment, LabourAttendance
from app.models.user import User
from app.routers.bills import ZERO, q2, _http422, _payment_method, _user_email, _ist_day_bounds_utc, _today_ist
from app.services.labour_wages import (
    STATUS_DAYS,
    STATUS_LEAVES,
    daily_earnings,
    month_start,
    monthly_earnings,
    unpaid_leaves_in_month,
)
from app.schemas.labour import (
    AttendanceMark,
    AttendanceOut,
    LabourDueClear,
    LabourerCreate,
    LabourerOut,
    LabourerUpdate,
    LabourPaymentCreate,
    LabourPaymentOut,
    LabourPaymentUpdate,
)

router = APIRouter(prefix="/labour", tags=["labour"])


def _num_str(v) -> str:
    """Compact number for display: '3' not '3.00', '2.5' not '2.50'."""
    d = Decimal(v or 0)
    return str(d.quantize(Decimal("1")) if d == d.to_integral_value() else d.normalize())


def labourers_with_ledger(db: Session, shop_id: uuid.UUID | None = None) -> list[LabourerOut]:
    """The worker roster with the attendance-driven ledger for each:
        days_worked (present + ½·half-day)
        earned      — wage/day × days for a daily worker; salary minus unpaid-leave
                      deductions for a monthly one (see app/services/labour_wages.py)
        total_paid (all cash+UPI handed over) → balance_to_pay (earned − paid).

    RLS scopes to the caller's shop; owner endpoints pass an explicit shop_id
    because an owner can own several shops.

    Attendance is fetched per month rather than as one lifetime total, because a
    monthly worker's paid-leave allowance resets every month — two leaves in March
    and two in April cost nothing, while four in March costs two days' pay. A
    single total cannot tell those apart. Daily workers just sum the months back up.
    """
    month_col = func.date_trunc("month", LabourAttendance.day)
    lab_stmt = select(Labourer).order_by(Labourer.name.asc())
    att_stmt = (
        select(
            LabourAttendance.labourer_id,
            month_col.label("month"),
            LabourAttendance.status,
            func.count().label("marks"),
        )
        .group_by(LabourAttendance.labourer_id, month_col, LabourAttendance.status)
    )
    paid_stmt = select(
        LabourPayment.labourer_id,
        func.coalesce(func.sum(LabourPayment.cash_amount + LabourPayment.upi_amount), 0),
    ).group_by(LabourPayment.labourer_id)
    if shop_id is not None:
        lab_stmt = lab_stmt.where(Labourer.shop_id == shop_id)
        att_stmt = att_stmt.where(LabourAttendance.shop_id == shop_id)
        paid_stmt = paid_stmt.where(LabourPayment.shop_id == shop_id)

    labourers = list(db.execute(lab_stmt).scalars())
    paid_by = {r[0]: Decimal(r[1]) for r in db.execute(paid_stmt).all()}

    # labourer_id -> {first-of-month -> {"days": Decimal, "leaves": Decimal}}
    marks: dict[uuid.UUID, dict[dt.date, dict[str, Decimal]]] = {}
    for labourer_id, month, status, count in db.execute(att_stmt).all():
        # date_trunc hands back a timestamp; the rest of this works in plain dates.
        key = month.date() if isinstance(month, dt.datetime) else month
        bucket = marks.setdefault(labourer_id, {}).setdefault(
            key, {"days": ZERO, "leaves": ZERO}
        )
        n = Decimal(count)
        bucket["days"] += STATUS_DAYS.get(status, ZERO) * n
        bucket["leaves"] += STATUS_LEAVES.get(status, ZERO) * n

    today = _today_ist()
    this_month = month_start(today)

    out: list[LabourerOut] = []
    for l in labourers:
        by_month = marks.get(l.id, {})
        days = sum((m["days"] for m in by_month.values()), ZERO)
        paid = paid_by.get(l.id, ZERO)

        if l.wage_type == "monthly":
            leaves_by_month = {month: m["leaves"] for month, m in by_month.items()}
            earned = monthly_earnings(
                monthly_wage=Decimal(l.monthly_wage),
                paid_leaves_per_month=l.paid_leaves_per_month,
                joined_on=l.joined_on,
                today=today,
                leaves_by_month=leaves_by_month,
            )
        else:
            earned = daily_earnings(Decimal(l.default_wage), days)

        # This month's leave position, so the app can explain a deduction instead of
        # just showing a smaller number.
        leaves_now = by_month.get(this_month, {}).get("leaves", ZERO)
        unpaid_now = unpaid_leaves_in_month(leaves_now, l.paid_leaves_per_month)

        out.append(
            LabourerOut(
                id=l.id, name=l.name, phone=l.phone, aadhaar=l.aadhaar, gender=l.gender,  # type: ignore[arg-type]
                wage_type=l.wage_type,  # type: ignore[arg-type]
                default_wage=l.default_wage,
                monthly_wage=l.monthly_wage,
                paid_leaves_per_month=l.paid_leaves_per_month,
                is_active=l.is_active,
                days_worked=_num_str(days), total_paid=q2(paid), earned=earned,
                balance_to_pay=q2(earned - paid), joined_on=l.joined_on,
                leaves_this_month=_num_str(leaves_now),
                unpaid_leaves_this_month=_num_str(unpaid_now),
                created_at=l.created_at,
            )
        )
    return out


def _check_wages(wage_type: str, default_wage, monthly_wage) -> None:
    """A worker must have a wage in the mode they're actually paid in.

    Saving a monthly worker with a blank salary would quietly show them earning ₹0
    forever, which reads as a broken app rather than a missing field — so refuse it
    up front, naming the field the manager needs to fill in.
    """
    if wage_type == "monthly":
        if Decimal(monthly_wage or 0) <= ZERO:
            raise _http422("Enter the monthly wage for this worker.")
    elif Decimal(default_wage or 0) <= ZERO:
        raise _http422("Enter the wage per day for this worker.")


def _bare_labourer_out(l: Labourer) -> LabourerOut:
    """A just-created or fallback worker record, before any attendance exists."""
    return LabourerOut(
        id=l.id, name=l.name, phone=l.phone, aadhaar=l.aadhaar, gender=l.gender,  # type: ignore[arg-type]
        wage_type=l.wage_type,  # type: ignore[arg-type]
        default_wage=l.default_wage,
        monthly_wage=l.monthly_wage,
        paid_leaves_per_month=l.paid_leaves_per_month,
        is_active=l.is_active, days_worked="0", total_paid=ZERO, earned=ZERO,
        balance_to_pay=ZERO, joined_on=l.joined_on,
        leaves_this_month="0", unpaid_leaves_this_month="0",
        created_at=l.created_at,
    )


def _checked_joining_date(value: dt.date | None) -> dt.date:
    """Default to today (IST, like every other day boundary here). A future date
    is always a typo — nobody joins next month — so refuse it in plain language."""
    today = _today_ist()
    if value is None:
        return today
    if value > today:
        raise _http422("The joining date can't be in the future.")
    return value


def _require_shop(user: User) -> uuid.UUID:
    if user.shop_id is None:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="You must be part of a shop to manage labour.",
        )
    return user.shop_id


def _payment_out(db: Session, p: LabourPayment) -> LabourPaymentOut:
    return LabourPaymentOut(
        id=p.id,
        labourer_id=p.labourer_id,
        labourer_name=p.labourer_name,
        gender=p.gender,  # type: ignore[arg-type]
        kind=p.kind,  # type: ignore[arg-type]
        wage_amount=p.wage_amount,
        days=_num_str(p.days) if p.days is not None else None,
        total_amount=p.total_amount,
        cash_amount=p.cash_amount,
        upi_amount=p.upi_amount,
        due_amount=p.due_amount,
        payment_method=_payment_method(p.cash_amount, p.upi_amount, p.due_amount),  # type: ignore[arg-type]
        note=p.note,
        recorded_by_email=_user_email(db, p.created_by),
        created_at=p.created_at,
    )


# ── Labourers ────────────────────────────────────────────────────────────────
@router.get("/labourers", response_model=list[LabourerOut])
def list_labourers(
    db: Session = Depends(get_db),
    user: User = Depends(require_shop_staff),
) -> list[LabourerOut]:
    _require_shop(user)
    return labourers_with_ledger(db)


@router.post("/labourers", response_model=LabourerOut, status_code=status.HTTP_201_CREATED)
def create_labourer(
    payload: LabourerCreate,
    db: Session = Depends(get_db),
    user: User = Depends(require_shop_staff),
) -> LabourerOut:
    shop_id = _require_shop(user)
    _check_wages(payload.wage_type, payload.default_wage, payload.monthly_wage)
    labourer = Labourer(
        shop_id=shop_id,
        name=payload.name.strip(),
        phone=(payload.phone or "").strip() or None,
        aadhaar=(payload.aadhaar or "").strip() or None,
        gender=payload.gender,
        wage_type=payload.wage_type,
        default_wage=q2(payload.default_wage),
        monthly_wage=q2(payload.monthly_wage),
        paid_leaves_per_month=payload.paid_leaves_per_month,
        joined_on=_checked_joining_date(payload.joined_on),
        created_by=user.id,
    )
    db.add(labourer)
    db.flush()
    db.refresh(labourer)
    return _bare_labourer_out(labourer)


@router.patch("/labourers/{labourer_id}", response_model=LabourerOut)
def update_labourer(
    labourer_id: uuid.UUID,
    payload: LabourerUpdate,
    db: Session = Depends(get_db),
    user: User = Depends(require_manager_or_admin),
) -> LabourerOut:
    _require_shop(user)
    labourer = db.execute(select(Labourer).where(Labourer.id == labourer_id)).scalar_one_or_none()
    if labourer is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Labourer not found")

    if payload.name is not None:
        labourer.name = payload.name.strip()
    if payload.phone is not None:
        labourer.phone = payload.phone.strip() or None
    if payload.aadhaar is not None:
        labourer.aadhaar = payload.aadhaar.strip() or None
    if payload.gender is not None:
        labourer.gender = payload.gender
    if payload.wage_type is not None:
        labourer.wage_type = payload.wage_type
    if payload.default_wage is not None:
        labourer.default_wage = q2(payload.default_wage)
    if payload.monthly_wage is not None:
        labourer.monthly_wage = q2(payload.monthly_wage)
    if payload.paid_leaves_per_month is not None:
        labourer.paid_leaves_per_month = payload.paid_leaves_per_month
    if payload.is_active is not None:
        labourer.is_active = payload.is_active
    if payload.joined_on is not None:
        labourer.joined_on = _checked_joining_date(payload.joined_on)

    # Validate the merged result, not the payload: switching someone to monthly
    # without sending a monthly wage in the same request must still be caught.
    _check_wages(labourer.wage_type, labourer.default_wage, labourer.monthly_wage)

    db.flush()
    db.refresh(labourer)
    # Recompute the ledger so the edited wage reflects in the balance immediately.
    for entry in labourers_with_ledger(db):
        if entry.id == labourer.id:
            return entry
    return _bare_labourer_out(labourer)


@router.delete("/labourers/{labourer_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_labourer(
    labourer_id: uuid.UUID,
    db: Session = Depends(get_db),
    user: User = Depends(require_manager_or_admin),
):
    _require_shop(user)
    labourer = db.execute(select(Labourer).where(Labourer.id == labourer_id)).scalar_one_or_none()
    if labourer is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Labourer not found")
    db.delete(labourer)
    db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)


# ── Payments ─────────────────────────────────────────────────────────────────
@router.get("/payments", response_model=list[LabourPaymentOut])
def list_payments(
    db: Session = Depends(get_db),
    user: User = Depends(require_shop_staff),
    labourer_id: uuid.UUID | None = Query(default=None),
    date_from: dt.date | None = Query(default=None),
    date_to: dt.date | None = Query(default=None),
    limit: int = Query(default=100, ge=1, le=1000),
    offset: int = Query(default=0, ge=0),
) -> list[LabourPaymentOut]:
    _require_shop(user)
    stmt = select(LabourPayment)
    if labourer_id is not None:
        stmt = stmt.where(LabourPayment.labourer_id == labourer_id)
    if date_from is not None:
        stmt = stmt.where(LabourPayment.created_at >= _ist_day_bounds_utc(date_from)[0])
    if date_to is not None:
        stmt = stmt.where(LabourPayment.created_at < _ist_day_bounds_utc(date_to)[1])
    stmt = stmt.order_by(LabourPayment.created_at.desc()).offset(offset).limit(limit)
    return [_payment_out(db, p) for p in db.execute(stmt).scalars()]


@router.post("/payments", response_model=LabourPaymentOut, status_code=status.HTTP_201_CREATED)
def create_payment(
    payload: LabourPaymentCreate,
    db: Session = Depends(get_db),
    user: User = Depends(require_shop_staff),
) -> LabourPaymentOut:
    shop_id = _require_shop(user)
    labourer = db.execute(select(Labourer).where(Labourer.id == payload.labourer_id)).scalar_one_or_none()
    if labourer is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Labourer not found")

    wage = q2(payload.wage_amount)
    cash = q2(payload.cash_amount)
    upi = q2(payload.upi_amount)
    due = q2(payload.due_amount)
    days = q2(payload.days) if payload.days is not None else None
    if min(wage, cash, upi, due) < ZERO or (days is not None and days < ZERO):
        raise _http422("Amounts can't be negative.")
    total = wage
    if cash + upi + due != total:
        raise _http422(
            f"Cash + UPI + Due (₹{cash + upi + due:.2f}) must equal the total (₹{total:.2f})."
        )

    payment = LabourPayment(
        shop_id=shop_id, labourer_id=labourer.id, labourer_name=labourer.name, gender=labourer.gender,
        kind=payload.kind, wage_amount=wage, days=days,
        total_amount=total, cash_amount=cash, upi_amount=upi, due_amount=due,
        note=(payload.note or "").strip() or None, created_by=user.id,
    )
    db.add(payment)
    db.flush()
    db.refresh(payment)
    return _payment_out(db, payment)


@router.post("/due-clear", response_model=LabourPaymentOut, status_code=status.HTTP_201_CREATED)
def clear_due(
    payload: LabourDueClear,
    db: Session = Depends(get_db),
    user: User = Depends(require_shop_staff),
) -> LabourPaymentOut:
    """Pay off money owed to a worker (cash/UPI). Recorded as a 'due_clear' so it
    reduces their outstanding without counting as new wages."""
    shop_id = _require_shop(user)
    labourer = db.execute(select(Labourer).where(Labourer.id == payload.labourer_id)).scalar_one_or_none()
    if labourer is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Labourer not found")

    cash = q2(payload.cash_amount)
    upi = q2(payload.upi_amount)
    if cash < ZERO or upi < ZERO:
        raise _http422("Amounts can't be negative.")
    total = q2(cash + upi)
    if total <= ZERO:
        raise _http422("Enter an amount to pay.")

    payment = LabourPayment(
        shop_id=shop_id, labourer_id=labourer.id, labourer_name=labourer.name, gender=labourer.gender,
        kind="due_clear", wage_amount=ZERO,
        total_amount=total, cash_amount=cash, upi_amount=upi, due_amount=ZERO,
        note=(payload.note or "").strip() or "Cleared outstanding due", created_by=user.id,
    )
    db.add(payment)
    db.flush()
    db.refresh(payment)
    return _payment_out(db, payment)


@router.patch("/payments/{payment_id}", response_model=LabourPaymentOut)
def update_payment(
    payment_id: uuid.UUID,
    payload: LabourPaymentUpdate,
    db: Session = Depends(get_db),
    user: User = Depends(require_manager_or_admin),
) -> LabourPaymentOut:
    _require_shop(user)
    payment = db.execute(select(LabourPayment).where(LabourPayment.id == payment_id)).scalar_one_or_none()
    if payment is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Payment not found")

    if payload.wage_amount is not None:
        payment.wage_amount = q2(payload.wage_amount)
    if payload.days is not None:
        payment.days = q2(payload.days)
    payment.total_amount = q2(Decimal(payment.wage_amount))

    cash = q2(payload.cash_amount) if payload.cash_amount is not None else Decimal(payment.cash_amount)
    upi = q2(payload.upi_amount) if payload.upi_amount is not None else Decimal(payment.upi_amount)
    due = q2(payload.due_amount) if payload.due_amount is not None else Decimal(payment.due_amount)
    if cash + upi + due != payment.total_amount:
        raise _http422(
            f"Cash + UPI + Due (₹{cash + upi + due:.2f}) must equal the total (₹{payment.total_amount:.2f})."
        )
    payment.cash_amount = cash
    payment.upi_amount = upi
    payment.due_amount = due
    if payload.note is not None:
        payment.note = payload.note.strip() or None

    db.flush()
    db.refresh(payment)
    return _payment_out(db, payment)


@router.delete("/payments/{payment_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_payment(
    payment_id: uuid.UUID,
    db: Session = Depends(get_db),
    user: User = Depends(require_manager_or_admin),
):
    _require_shop(user)
    payment = db.execute(select(LabourPayment).where(LabourPayment.id == payment_id)).scalar_one_or_none()
    if payment is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Payment not found")
    db.delete(payment)
    db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)


# ── Attendance (any staff can mark) ──────────────────────────────────────────
@router.get("/attendance", response_model=list[AttendanceOut])
def list_attendance(
    db: Session = Depends(get_db),
    user: User = Depends(require_shop_staff),
    day: dt.date | None = Query(default=None),
    date_from: dt.date | None = Query(default=None),
    date_to: dt.date | None = Query(default=None),
) -> list[AttendanceOut]:
    _require_shop(user)
    stmt = select(LabourAttendance, Labourer.name).join(
        Labourer, Labourer.id == LabourAttendance.labourer_id
    )
    if day is not None:
        stmt = stmt.where(LabourAttendance.day == day)
    if date_from is not None:
        stmt = stmt.where(LabourAttendance.day >= date_from)
    if date_to is not None:
        stmt = stmt.where(LabourAttendance.day <= date_to)
    stmt = stmt.order_by(LabourAttendance.day.desc(), Labourer.name.asc())
    rows = db.execute(stmt).all()
    return [
        AttendanceOut(
            id=a.id, labourer_id=a.labourer_id, labourer_name=name, day=a.day,
            status=a.status, created_at=a.created_at,  # type: ignore[arg-type]
        )
        for a, name in rows
    ]


@router.post("/attendance", response_model=AttendanceOut, status_code=status.HTTP_201_CREATED)
def mark_attendance(
    payload: AttendanceMark,
    db: Session = Depends(get_db),
    user: User = Depends(require_shop_staff),
) -> AttendanceOut:
    """Mark (or update) a worker's attendance for a day. One record per worker/day."""
    shop_id = _require_shop(user)
    labourer = db.execute(select(Labourer).where(Labourer.id == payload.labourer_id)).scalar_one_or_none()
    if labourer is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Labourer not found")

    existing = db.execute(
        select(LabourAttendance).where(
            LabourAttendance.labourer_id == payload.labourer_id,
            LabourAttendance.day == payload.day,
        )
    ).scalar_one_or_none()

    now = dt.datetime.now(dt.timezone.utc)
    if existing is not None:
        existing.status = payload.status
        existing.updated_at = now
        rec = existing
    else:
        rec = LabourAttendance(
            shop_id=shop_id, labourer_id=payload.labourer_id, day=payload.day,
            status=payload.status, created_by=user.id,
        )
        db.add(rec)
    db.flush()
    db.refresh(rec)
    return AttendanceOut(
        id=rec.id, labourer_id=rec.labourer_id, labourer_name=labourer.name, day=rec.day,
        status=rec.status, created_at=rec.created_at,  # type: ignore[arg-type]
    )
