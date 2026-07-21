"""Collecting (closing) outstanding dues, with a manager-approval step.

Who does what:
- A **salesperson** collecting a due creates a PENDING request. The money is NOT
  applied to the bill yet — a manager must approve it (verifying the cash/UPI
  actually arrived). The bill stays outstanding meanwhile and is flagged so it
  can't be collected twice.
- A **manager/admin** collecting a due applies it immediately (an already-approved
  settlement is recorded for the trail).
- A **manager/admin** approves or rejects a salesperson's pending request.

The amount may be split across cash and UPI; the two must add up to the bill's
current due. All money math is server-authoritative, reusing the helpers in
`bills.py`.
"""
from __future__ import annotations

import datetime as dt
import uuid

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.auth.dependencies import get_db, require_shop_staff, require_manager_or_admin
from app.models.bill import Bill
from app.models.customer import Customer
from app.models.due_settlement import DueSettlement
from app.models.user import User
from app.routers.bills import ZERO, q2, _http422, _record_bill_audit, _user_email
from app.schemas.settlement import (
    SettlementActionResult,
    SettlementCreate,
    SettlementListOut,
    SettlementOut,
)

router = APIRouter(prefix="/settlements", tags=["settlements"])


def _apply_to_bill(db: Session, bill: Bill, cash, upi, *, actor: User) -> None:
    """Move a collected due onto the bill: add to cash/UPI and reduce the due by
    what was collected. A partial collection leaves the remainder still owed."""
    collected = q2(cash + upi)
    bill.cash_amount = q2(bill.cash_amount + cash)
    bill.upi_amount = q2(bill.upi_amount + upi)
    remaining = q2(bill.due_amount - collected)
    bill.due_amount = remaining if remaining > ZERO else ZERO
    _record_bill_audit(
        db,
        bill=bill,
        action="edit",
        actor=actor,
        summary=f"Due collected: cash INR {cash}, UPI INR {upi}" + (f" (INR {bill.due_amount} still owed)" if bill.due_amount > ZERO else ""),
        details={"settlement": {"cash": str(cash), "upi": str(upi), "remaining_due": str(bill.due_amount)}},
    )


@router.post("", response_model=SettlementActionResult, status_code=status.HTTP_201_CREATED)
def create_settlement(
    payload: SettlementCreate,
    db: Session = Depends(get_db),
    user: User = Depends(require_shop_staff),
) -> SettlementActionResult:
    """Collect a due. Salespeople create a pending request; managers apply now."""
    bill = db.execute(select(Bill).where(Bill.id == payload.bill_id)).scalar_one_or_none()
    if bill is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Bill not found")

    due = q2(bill.due_amount)
    if due <= ZERO:
        raise _http422("This bill has no due left to collect.")

    cash = q2(payload.cash_amount)
    upi = q2(payload.upi_amount)
    if cash < ZERO or upi < ZERO:
        raise _http422("Amounts can't be negative.")
    collected = q2(cash + upi)
    if collected <= ZERO:
        raise _http422("Enter an amount to collect.")
    # Partial collection is allowed — the rest stays owed. Can't collect more than owed.
    if collected > due:
        raise _http422(
            f"Cash + UPI (₹{collected:.2f}) can't be more than the amount owed (₹{due:.2f})."
        )

    # Guard against a second collection for the same bill sitting in the queue.
    already = db.execute(
        select(DueSettlement.id).where(
            DueSettlement.bill_id == bill.id,
            DueSettlement.status == "pending",
        )
    ).scalar_one_or_none()
    if already is not None:
        raise _http422("A collection for this bill is already waiting for approval.")

    is_manager = user.role in ("manager", "admin")
    now = dt.datetime.now(dt.timezone.utc)

    rec = DueSettlement(
        shop_id=bill.shop_id,
        bill_id=bill.id,
        cash_amount=cash,
        upi_amount=upi,
        status="approved" if is_manager else "pending",
        requested_by=user.id,
        reviewed_by=user.id if is_manager else None,
        reviewed_at=now if is_manager else None,
    )
    db.add(rec)

    if is_manager:
        # Manager/admin: collect immediately.
        _apply_to_bill(db, bill, cash, upi, actor=user)

    try:
        db.flush()
    except IntegrityError:
        # Lost a race on the one-pending-per-bill unique index.
        db.rollback()
        raise _http422("A collection for this bill is already waiting for approval.")
    db.refresh(rec)

    return SettlementActionResult(
        id=rec.id,
        bill_id=rec.bill_id,
        status=rec.status,  # type: ignore[arg-type]
        cash_amount=rec.cash_amount,
        upi_amount=rec.upi_amount,
    )


@router.get("/pending", response_model=SettlementListOut)
def list_pending(
    db: Session = Depends(get_db),
    user: User = Depends(require_manager_or_admin),
    limit: int = Query(default=100, ge=1, le=500),
) -> SettlementListOut:
    """The manager's approval queue — salesperson collections awaiting review."""
    rows = db.execute(
        select(
            DueSettlement.id,
            DueSettlement.bill_id,
            DueSettlement.status,
            DueSettlement.cash_amount,
            DueSettlement.upi_amount,
            DueSettlement.created_at,
            DueSettlement.requested_by,
            Bill.total.label("bill_total"),
            Customer.name.label("customer_name"),
            Customer.phone.label("customer_phone"),
        )
        .join(Bill, Bill.id == DueSettlement.bill_id)
        .outerjoin(Customer, Customer.id == Bill.customer_id)
        .where(DueSettlement.status == "pending")
        .order_by(DueSettlement.created_at.asc())
        .limit(limit)
    ).all()

    items = [
        SettlementOut(
            id=r.id,
            bill_id=r.bill_id,
            status=r.status,
            cash_amount=r.cash_amount,
            upi_amount=r.upi_amount,
            bill_total=r.bill_total,
            customer_name=r.customer_name,
            customer_phone=r.customer_phone,
            requested_by_email=_user_email(db, r.requested_by),
            created_at=r.created_at,
        )
        for r in rows
    ]
    return SettlementListOut(items=items)


@router.post("/{settlement_id}/approve", response_model=SettlementActionResult)
def approve_settlement(
    settlement_id: uuid.UUID,
    db: Session = Depends(get_db),
    user: User = Depends(require_manager_or_admin),
) -> SettlementActionResult:
    """Approve a pending collection — applies the split to the bill and closes it."""
    rec = db.execute(
        select(DueSettlement).where(DueSettlement.id == settlement_id)
    ).scalar_one_or_none()
    if rec is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Request not found")
    if rec.status != "pending":
        raise _http422("This request has already been reviewed.")

    bill = db.execute(select(Bill).where(Bill.id == rec.bill_id)).scalar_one_or_none()
    if bill is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Bill not found")

    # The due may have shrunk since the request was raised (e.g. the bill was
    # edited or another collection landed). We can't apply more than is still owed.
    if q2(rec.cash_amount + rec.upi_amount) > q2(bill.due_amount):
        raise _http422(
            "This bill's amount owed has changed since the request. Reject it and collect again."
        )

    _apply_to_bill(db, bill, rec.cash_amount, rec.upi_amount, actor=user)
    rec.status = "approved"
    rec.reviewed_by = user.id
    rec.reviewed_at = dt.datetime.now(dt.timezone.utc)
    db.flush()
    db.refresh(rec)

    return SettlementActionResult(
        id=rec.id,
        bill_id=rec.bill_id,
        status="approved",
        cash_amount=rec.cash_amount,
        upi_amount=rec.upi_amount,
    )


@router.post("/{settlement_id}/reject", response_model=SettlementActionResult)
def reject_settlement(
    settlement_id: uuid.UUID,
    db: Session = Depends(get_db),
    user: User = Depends(require_manager_or_admin),
) -> SettlementActionResult:
    """Reject a pending collection — the due stays outstanding."""
    rec = db.execute(
        select(DueSettlement).where(DueSettlement.id == settlement_id)
    ).scalar_one_or_none()
    if rec is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Request not found")
    if rec.status != "pending":
        raise _http422("This request has already been reviewed.")

    rec.status = "rejected"
    rec.reviewed_by = user.id
    rec.reviewed_at = dt.datetime.now(dt.timezone.utc)
    db.flush()
    db.refresh(rec)

    return SettlementActionResult(
        id=rec.id,
        bill_id=rec.bill_id,
        status="rejected",
        cash_amount=rec.cash_amount,
        upi_amount=rec.upi_amount,
    )
