"""Labour roster + payments.

A manager sets up the shop's workers (name, phone, gender, default wage, overtime
rate). Both the manager and salespeople can then record a payment whenever a
worker is paid — the wage is pre-filled from the default but editable, and
overtime = hours × the worker's rate. Everything, including the time it was
recorded, is stored and shown in the Excel report.
"""
from __future__ import annotations

import datetime as dt
import uuid
from decimal import Decimal

from fastapi import APIRouter, Depends, HTTPException, Query, Response, status
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.auth.dependencies import get_db, require_shop_staff, require_manager_or_admin
from app.models.labour import Labourer, LabourPayment
from app.models.user import User
from app.routers.bills import ZERO, q2, _http422, _user_email, _ist_day_bounds_utc
from app.schemas.labour import (
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
        wage_amount=p.wage_amount,
        overtime_hours=p.overtime_hours,
        overtime_rate=p.overtime_rate,
        overtime_amount=p.overtime_amount,
        total_amount=p.total_amount,
        note=p.note,
        recorded_by_email=_user_email(db, p.created_by),
        created_at=p.created_at,
    )


# ── Labourers (manager configures; everyone can read to pick from) ───────────
@router.get("/labourers", response_model=list[LabourerOut])
def list_labourers(
    db: Session = Depends(get_db),
    user: User = Depends(require_shop_staff),
) -> list[Labourer]:
    _require_shop(user)
    return list(
        db.execute(select(Labourer).order_by(Labourer.name.asc())).scalars()
    )


@router.post("/labourers", response_model=LabourerOut, status_code=status.HTTP_201_CREATED)
def create_labourer(
    payload: LabourerCreate,
    db: Session = Depends(get_db),
    user: User = Depends(require_manager_or_admin),
) -> Labourer:
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
    return labourer


@router.patch("/labourers/{labourer_id}", response_model=LabourerOut)
def update_labourer(
    labourer_id: uuid.UUID,
    payload: LabourerUpdate,
    db: Session = Depends(get_db),
    user: User = Depends(require_manager_or_admin),
) -> Labourer:
    _require_shop(user)
    labourer = db.execute(
        select(Labourer).where(Labourer.id == labourer_id)
    ).scalar_one_or_none()
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
    return labourer


@router.delete("/labourers/{labourer_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_labourer(
    labourer_id: uuid.UUID,
    db: Session = Depends(get_db),
    user: User = Depends(require_manager_or_admin),
):
    _require_shop(user)
    labourer = db.execute(
        select(Labourer).where(Labourer.id == labourer_id)
    ).scalar_one_or_none()
    if labourer is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Labourer not found")
    # Past payments keep their denormalized name/gender (labourer_id → NULL).
    db.delete(labourer)
    db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)


# ── Payments (manager AND salesperson can record) ────────────────────────────
@router.get("/payments", response_model=list[LabourPaymentOut])
def list_payments(
    db: Session = Depends(get_db),
    user: User = Depends(require_shop_staff),
    date_from: dt.date | None = Query(default=None),
    date_to: dt.date | None = Query(default=None),
    limit: int = Query(default=100, ge=1, le=1000),
    offset: int = Query(default=0, ge=0),
) -> list[LabourPaymentOut]:
    _require_shop(user)
    stmt = select(LabourPayment)
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
    labourer = db.execute(
        select(Labourer).where(Labourer.id == payload.labourer_id)
    ).scalar_one_or_none()
    if labourer is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Labourer not found")

    wage = q2(payload.wage_amount)
    hours = q2(payload.overtime_hours)
    if wage < ZERO or hours < ZERO:
        raise _http422("Amounts can't be negative.")
    rate = q2(labourer.overtime_rate)
    overtime = q2(hours * rate)
    total = q2(wage + overtime)

    payment = LabourPayment(
        shop_id=shop_id,
        labourer_id=labourer.id,
        labourer_name=labourer.name,
        gender=labourer.gender,
        wage_amount=wage,
        overtime_hours=hours,
        overtime_rate=rate,
        overtime_amount=overtime,
        total_amount=total,
        note=(payload.note or "").strip() or None,
        created_by=user.id,
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
    payment = db.execute(
        select(LabourPayment).where(LabourPayment.id == payment_id)
    ).scalar_one_or_none()
    if payment is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Payment not found")

    if payload.wage_amount is not None:
        payment.wage_amount = q2(payload.wage_amount)
    if payload.overtime_hours is not None:
        payment.overtime_hours = q2(payload.overtime_hours)
    if payload.note is not None:
        payment.note = payload.note.strip() or None

    # Recompute overtime from the rate captured on the payment, then the total.
    payment.overtime_amount = q2(Decimal(payment.overtime_hours) * Decimal(payment.overtime_rate))
    payment.total_amount = q2(Decimal(payment.wage_amount) + Decimal(payment.overtime_amount))

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
    payment = db.execute(
        select(LabourPayment).where(LabourPayment.id == payment_id)
    ).scalar_one_or_none()
    if payment is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Payment not found")
    db.delete(payment)
    db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)
