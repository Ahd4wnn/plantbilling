"""Borrowings — money the shop borrowed from people (a lenders ledger).

A debt note per lender. The CASH portion of a borrowing flows through the drawer:
cash received bumps Cash in Hand up, cash repaid brings it back down (the UPI legs
never touch the cash drawer). See `cash_flows_through` in bills.py. Repayment can
be partial — the remaining balance stays owed until fully paid. RLS scopes every
row to the shop (see the d1e2f3a4b5c6 migration).
"""
from __future__ import annotations

import datetime as dt
import uuid
from decimal import Decimal

from fastapi import APIRouter, Depends, HTTPException, Query, Response, status
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.auth.dependencies import get_db, require_shop_staff
from app.models.borrowing import Borrowing
from app.models.user import User
from app.routers.bills import _http422, q2
from app.schemas.borrowing import (
    BorrowingCreate,
    BorrowingList,
    BorrowingOut,
    BorrowingPay,
)

router = APIRouter(prefix="/borrowings", tags=["borrowings"])


def _method(cash: Decimal, upi: Decimal) -> str:
    if cash > 0 and upi > 0:
        return "split"
    if upi > 0:
        return "upi"
    if cash > 0:
        return "cash"
    return "none"


def _serialize(b: Borrowing) -> BorrowingOut:
    return BorrowingOut(
        id=b.id,
        lender_name=b.lender_name,
        lender_phone=b.lender_phone,
        amount=b.amount,
        cash_amount=b.cash_amount,
        upi_amount=b.upi_amount,
        method=_method(b.cash_amount, b.upi_amount),
        remarks=b.remarks,
        is_paid=b.is_paid,
        paid_cash_amount=b.paid_cash_amount,
        paid_upi_amount=b.paid_upi_amount,
        paid_method=_method(b.paid_cash_amount, b.paid_upi_amount),
        outstanding=q2(b.amount - b.paid_cash_amount - b.paid_upi_amount),
        paid_at=b.paid_at,
        created_at=b.created_at,
    )


@router.get("", response_model=BorrowingList)
def list_borrowings(
    db: Session = Depends(get_db),
    _user: User = Depends(require_shop_staff),
    status_filter: str = Query(default="all", alias="status", pattern="^(all|open|paid)$"),
) -> BorrowingList:
    """List borrowings (newest first). Unpaid entries sort above paid ones."""
    stmt = select(Borrowing)
    if status_filter == "open":
        stmt = stmt.where(Borrowing.is_paid.is_(False))
    elif status_filter == "paid":
        stmt = stmt.where(Borrowing.is_paid.is_(True))
    stmt = stmt.order_by(Borrowing.is_paid.asc(), Borrowing.created_at.desc())
    rows = list(db.execute(stmt).scalars())

    # Sum what's still owed (amount minus anything already repaid) across open rows.
    outstanding = db.execute(
        select(
            func.coalesce(
                func.sum(Borrowing.amount - Borrowing.paid_cash_amount - Borrowing.paid_upi_amount),
                0,
            )
        ).where(Borrowing.is_paid.is_(False))
    ).scalar_one()

    return BorrowingList(
        items=[_serialize(b) for b in rows],
        total_outstanding=q2(outstanding),
    )


@router.post("", response_model=BorrowingOut, status_code=status.HTTP_201_CREATED)
def create_borrowing(
    payload: BorrowingCreate,
    db: Session = Depends(get_db),
    user: User = Depends(require_shop_staff),
) -> BorrowingOut:
    if user.shop_id is None:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="User must be associated with a shop.",
        )
    amount = q2(payload.amount)
    if amount <= 0:
        raise _http422("The amount must be more than ₹0.")
    cash = q2(payload.cash_amount)
    upi = q2(payload.upi_amount)
    if cash + upi != amount:
        raise _http422(
            f"Cash + UPI (₹{cash + upi:.2f}) must equal the amount (₹{amount:.2f})."
        )

    b = Borrowing(
        shop_id=user.shop_id,
        lender_name=payload.lender_name.strip(),
        lender_phone=(payload.lender_phone or "").strip() or None,
        amount=amount,
        cash_amount=cash,
        upi_amount=upi,
        remarks=(payload.remarks or "").strip() or None,
        created_by=user.id,
    )
    db.add(b)
    db.flush()
    db.refresh(b)
    return _serialize(b)


def _get_or_404(db: Session, borrowing_id: uuid.UUID) -> Borrowing:
    b = db.execute(
        select(Borrowing).where(Borrowing.id == borrowing_id)
    ).scalar_one_or_none()
    if b is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Borrowing not found")
    return b


@router.post("/{borrowing_id}/pay", response_model=BorrowingOut)
def pay_borrowing(
    borrowing_id: uuid.UUID,
    payload: BorrowingPay,
    db: Session = Depends(get_db),
    _user: User = Depends(require_shop_staff),
) -> BorrowingOut:
    """Record a repayment (full or partial). The remainder stays owed until paid."""
    b = _get_or_404(db, borrowing_id)
    if b.is_paid:
        raise _http422("This borrowing is already fully paid.")
    cash = q2(payload.paid_cash_amount)
    upi = q2(payload.paid_upi_amount)
    if cash < 0 or upi < 0:
        raise _http422("Amounts can't be negative.")
    pay = q2(cash + upi)
    if pay <= 0:
        raise _http422("Enter an amount to pay back.")
    remaining = q2(b.amount - b.paid_cash_amount - b.paid_upi_amount)
    if pay > remaining:
        raise _http422(f"You can pay back at most the ₹{remaining:.2f} still owed.")

    b.paid_cash_amount = q2(b.paid_cash_amount + cash)
    b.paid_upi_amount = q2(b.paid_upi_amount + upi)
    b.paid_at = dt.datetime.now(tz=dt.timezone.utc)
    if q2(b.paid_cash_amount + b.paid_upi_amount) >= b.amount:
        b.is_paid = True
    db.flush()
    db.refresh(b)
    return _serialize(b)


@router.delete("/{borrowing_id}", status_code=status.HTTP_204_NO_CONTENT, response_class=Response)
def delete_borrowing(
    borrowing_id: uuid.UUID,
    db: Session = Depends(get_db),
    _user: User = Depends(require_shop_staff),
):
    b = _get_or_404(db, borrowing_id)
    db.delete(b)
    db.flush()
