from __future__ import annotations

import uuid
from fastapi import APIRouter, Depends, HTTPException, Query, Response, status
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.auth.dependencies import get_db, require_shop_staff, require_manager_or_admin
from app.models.expense import Expense
from app.models.user import User
from app.schemas.expense import ExpenseCreate, ExpenseOut, ExpenseUpdate

router = APIRouter(prefix="/expenses", tags=["expenses"])


@router.post("", response_model=ExpenseOut, status_code=status.HTTP_201_CREATED)
def create_expense(
    payload: ExpenseCreate,
    db: Session = Depends(get_db),
    user: User = Depends(require_shop_staff),
) -> Expense:
    """Create a new expense for the nursery.

    Available to both Shop Owners and Salespeople.
    """
    if user.shop_id is None:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="User must be associated with a shop to create an expense.",
        )

    expense = Expense(
        shop_id=user.shop_id,
        amount=payload.amount,
        reason=payload.reason.strip(),
        created_by=user.id,
    )
    db.add(expense)
    db.flush()
    db.refresh(expense)
    return expense


@router.get("", response_model=list[ExpenseOut])
def list_expenses(
    db: Session = Depends(get_db),
    user: User = Depends(require_shop_staff),
    limit: int = Query(default=100, ge=1, le=1000),
    offset: int = Query(default=0, ge=0),
) -> list[Expense]:
    """List nursery expenses ordered by creation date descending.

    Available to both Shop Owners and Salespeople.
    RLS restricts this to the current shop.
    """
    if user.shop_id is None:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="User must be associated with a shop to view expenses.",
        )

    stmt = (
        select(Expense)
        .where(Expense.shop_id == user.shop_id)
        .order_by(Expense.created_at.desc())
        .offset(offset)
        .limit(limit)
    )
    return list(db.execute(stmt).scalars())


@router.delete("/{expense_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_expense(
    expense_id: uuid.UUID,
    db: Session = Depends(get_db),
    user: User = Depends(require_manager_or_admin),
):
    """Delete an expense by ID.

    Available to Shop Owners and Admins.
    """
    expense = db.execute(
        select(Expense).where(Expense.id == expense_id)
    ).scalar_one_or_none()

    if not expense:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Expense not found.",
        )

    if user.role != "admin":
        if user.shop_id is None or expense.shop_id != user.shop_id:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="You do not have permission to delete this expense.",
            )

    db.delete(expense)
    db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.patch("/{expense_id}", response_model=ExpenseOut)
def update_expense(
    expense_id: uuid.UUID,
    payload: ExpenseUpdate,
    db: Session = Depends(get_db),
    user: User = Depends(require_manager_or_admin),
) -> Expense:
    """Update an expense by ID.

    Available to Shop Owners and Admins.
    """
    expense = db.execute(
        select(Expense).where(Expense.id == expense_id)
    ).scalar_one_or_none()

    if not expense:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Expense not found.",
        )

    if user.role != "admin":
        if user.shop_id is None or expense.shop_id != user.shop_id:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="You do not have permission to update this expense.",
            )

    if payload.amount is not None:
        expense.amount = payload.amount
    if payload.reason is not None:
        expense.reason = payload.reason.strip()

    db.commit()
    db.refresh(expense)
    return expense

