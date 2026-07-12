"""Labour roster, payments (cash/UPI/split/due), attendance.

Any shop staff (manager or salesperson) can add workers, record payments, and mark
attendance. A payment's total (wage + overtime) is split across cash / UPI / due
like a bill — only the cash part lowers Cash in Hand; the due part is money still
owed to the worker, cleared later via a 'due_clear' payment. Editing/deleting a
worker or payment stays manager-only.
"""
from __future__ import annotations

import datetime as dt
import uuid
from decimal import Decimal

from fastapi import APIRouter, Depends, HTTPException, Query, Response, status
from sqlalchemy import case, func, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.auth.dependencies import get_db, require_shop_staff, require_manager_or_admin
from app.models.labour import Labourer, LabourPayment, LabourAttendance
from app.models.user import User
from app.routers.bills import ZERO, q2, _http422, _payment_method, _user_email, _ist_day_bounds_utc, _today_ist
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
        overtime_hours=p.overtime_hours,
        overtime_rate=p.overtime_rate,
        overtime_amount=p.overtime_amount,
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
    labourers = list(db.execute(select(Labourer).order_by(Labourer.name.asc())).scalars())

    # Per-worker aggregates: money actually paid, and what's still owed.
    agg_rows = db.execute(
        select(
            LabourPayment.labourer_id,
            func.coalesce(func.sum(LabourPayment.cash_amount + LabourPayment.upi_amount), 0),
            func.coalesce(func.sum(LabourPayment.due_amount), 0),
            func.coalesce(
                func.sum(
                    case(
                        (LabourPayment.kind == "due_clear", LabourPayment.cash_amount + LabourPayment.upi_amount),
                        else_=0,
                    )
                ),
                0,
            ),
        ).group_by(LabourPayment.labourer_id)
    ).all()
    paid_by = {r[0]: (r[1], r[2], r[3]) for r in agg_rows}

    out: list[LabourerOut] = []
    for l in labourers:
        paid, due_raised, due_cleared = paid_by.get(l.id, (0, 0, 0))
        out.append(
            LabourerOut(
                id=l.id, name=l.name, phone=l.phone, gender=l.gender,  # type: ignore[arg-type]
                default_wage=l.default_wage, overtime_rate=l.overtime_rate, is_active=l.is_active,
                total_paid=q2(Decimal(paid)),
                outstanding_due=q2(Decimal(due_raised) - Decimal(due_cleared)),
                created_at=l.created_at,
            )
        )
    return out


@router.post("/labourers", response_model=LabourerOut, status_code=status.HTTP_201_CREATED)
def create_labourer(
    payload: LabourerCreate,
    db: Session = Depends(get_db),
    user: User = Depends(require_shop_staff),
) -> LabourerOut:
    shop_id = _require_shop(user)
    labourer = Labourer(
        shop_id=shop_id,
        name=payload.name.strip(),
        phone=(payload.phone or "").strip() or None,
        gender=payload.gender,
        default_wage=q2(payload.default_wage),
        overtime_rate=q2(payload.overtime_rate),
        created_by=user.id,
    )
    db.add(labourer)
    db.flush()
    db.refresh(labourer)
    return LabourerOut(
        id=labourer.id, name=labourer.name, phone=labourer.phone, gender=labourer.gender,  # type: ignore[arg-type]
        default_wage=labourer.default_wage, overtime_rate=labourer.overtime_rate,
        is_active=labourer.is_active, total_paid=ZERO, outstanding_due=ZERO, created_at=labourer.created_at,
    )


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
    if payload.gender is not None:
        labourer.gender = payload.gender
    if payload.default_wage is not None:
        labourer.default_wage = q2(payload.default_wage)
    if payload.overtime_rate is not None:
        labourer.overtime_rate = q2(payload.overtime_rate)
    if payload.is_active is not None:
        labourer.is_active = payload.is_active

    db.flush()
    db.refresh(labourer)
    return LabourerOut(
        id=labourer.id, name=labourer.name, phone=labourer.phone, gender=labourer.gender,  # type: ignore[arg-type]
        default_wage=labourer.default_wage, overtime_rate=labourer.overtime_rate,
        is_active=labourer.is_active, total_paid=ZERO, outstanding_due=ZERO, created_at=labourer.created_at,
    )


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
    hours = q2(payload.overtime_hours)
    cash = q2(payload.cash_amount)
    upi = q2(payload.upi_amount)
    due = q2(payload.due_amount)
    if min(wage, hours, cash, upi, due) < ZERO:
        raise _http422("Amounts can't be negative.")
    rate = q2(labourer.overtime_rate)
    overtime = q2(hours * rate)
    total = q2(wage + overtime)
    if cash + upi + due != total:
        raise _http422(
            f"Cash + UPI + Due (₹{cash + upi + due:.2f}) must equal the total (₹{total:.2f})."
        )

    payment = LabourPayment(
        shop_id=shop_id, labourer_id=labourer.id, labourer_name=labourer.name, gender=labourer.gender,
        kind="wage", wage_amount=wage, overtime_hours=hours, overtime_rate=rate,
        overtime_amount=overtime, total_amount=total, cash_amount=cash, upi_amount=upi, due_amount=due,
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
        kind="due_clear", wage_amount=ZERO, overtime_hours=ZERO, overtime_rate=ZERO,
        overtime_amount=ZERO, total_amount=total, cash_amount=cash, upi_amount=upi, due_amount=ZERO,
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
    if payload.overtime_hours is not None:
        payment.overtime_hours = q2(payload.overtime_hours)
    payment.overtime_amount = q2(Decimal(payment.overtime_hours) * Decimal(payment.overtime_rate))
    payment.total_amount = q2(Decimal(payment.wage_amount) + Decimal(payment.overtime_amount))

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
            status=a.status, overtime_hours=a.overtime_hours, created_at=a.created_at,  # type: ignore[arg-type]
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
        existing.overtime_hours = q2(payload.overtime_hours)
        existing.updated_at = now
        rec = existing
    else:
        rec = LabourAttendance(
            shop_id=shop_id, labourer_id=payload.labourer_id, day=payload.day,
            status=payload.status, overtime_hours=q2(payload.overtime_hours), created_by=user.id,
        )
        db.add(rec)
    db.flush()
    db.refresh(rec)
    return AttendanceOut(
        id=rec.id, labourer_id=rec.labourer_id, labourer_name=labourer.name, day=rec.day,
        status=rec.status, overtime_hours=rec.overtime_hours, created_at=rec.created_at,  # type: ignore[arg-type]
    )
