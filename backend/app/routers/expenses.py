from __future__ import annotations

import uuid
from fastapi import APIRouter, Depends, HTTPException, Query, Response, status
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.auth.dependencies import get_db, require_shop_staff, require_manager_or_admin
from app.models.expense import Expense
from app.models.expense_category import ExpenseCategory
from app.models.user import User
from app.schemas.expense import ExpenseCreate, ExpenseOut, ExpenseUpdate


def _resolve_category(db: Session, category_id) -> ExpenseCategory:
    """Load a category (RLS-scoped to the shop) or 404. Its name is snapshotted
    into the expense's `reason` so a later rename never rewrites history."""
    category = db.execute(
        select(ExpenseCategory).where(ExpenseCategory.id == category_id)
    ).scalar_one_or_none()
    if category is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Expense category not found.",
        )
    return category

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

    # Prefer a category (its name becomes the snapshotted reason); fall back to the
    # legacy free-text reason. Exactly one path must supply a label.
    category_id = None
    if payload.category_id is not None:
        category = _resolve_category(db, payload.category_id)
        category_id = category.id
        reason = category.name
    elif payload.reason:
        reason = payload.reason.strip()
    else:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Pick a category (or enter a reason) for the expense.",
        )

    note = payload.note.strip() if payload.note and payload.note.strip() else None

    expense = Expense(
        shop_id=user.shop_id,
        amount=payload.amount,
        reason=reason,
        category_id=category_id,
        note=note,
        payment_method=payload.payment_method,
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
    if payload.category_id is not None:
        # Switch category → resnapshot its name into reason.
        category = _resolve_category(db, payload.category_id)
        expense.category_id = category.id
        expense.reason = category.name
    elif payload.reason is not None:
        # Free-text edit clears any category link.
        expense.reason = payload.reason.strip()
        expense.category_id = None
    if payload.note is not None:
        expense.note = payload.note.strip() or None
    if payload.payment_method is not None:
        expense.payment_method = payload.payment_method

    # Flush + refresh INSIDE the request transaction; the RLS context (set via
    # SET LOCAL) only lives until commit, so committing here and then refreshing
    # would run the reload with no RLS context and the row would be invisible
    # (500). get_rls_session commits on success.
    db.flush()
    db.refresh(expense)
    return expense

