"""Manager-curated expense categories (e.g. "Petrol", "Electricity").

Listing is open to any counter staff (they pick a category when logging an
expense); creating/renaming/deleting is manager-or-admin, mirroring the guards on
editing expenses themselves. RLS scopes every row to the caller's shop; shop_id
is taken from the JWT on insert, never the request body.
"""
from __future__ import annotations

import uuid

from fastapi import APIRouter, Depends, HTTPException, Response, status
from sqlalchemy import func, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.auth.dependencies import get_db, require_manager_or_admin, require_shop_staff
from app.models.expense_category import ExpenseCategory
from app.models.user import User
from app.schemas.expense import ExpenseCategoryCreate, ExpenseCategoryOut

router = APIRouter(prefix="/expense-categories", tags=["expenses"])


def _require_shop(user: User) -> uuid.UUID:
    if user.shop_id is None:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="User must be associated with a shop to manage expense categories.",
        )
    return user.shop_id


@router.get("", response_model=list[ExpenseCategoryOut])
def list_categories(
    db: Session = Depends(get_db),
    _user: User = Depends(require_shop_staff),
) -> list[ExpenseCategory]:
    stmt = select(ExpenseCategory).order_by(func.lower(ExpenseCategory.name).asc())
    return list(db.execute(stmt).scalars())


@router.post("", response_model=ExpenseCategoryOut, status_code=status.HTTP_201_CREATED)
def create_category(
    payload: ExpenseCategoryCreate,
    db: Session = Depends(get_db),
    user: User = Depends(require_manager_or_admin),
) -> ExpenseCategory:
    shop_id = _require_shop(user)
    category = ExpenseCategory(shop_id=shop_id, name=payload.name.strip())
    db.add(category)
    try:
        db.flush()
    except IntegrityError:
        db.rollback()
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="A category with this name already exists.",
        )
    db.refresh(category)
    return category


@router.patch("/{category_id}", response_model=ExpenseCategoryOut)
def rename_category(
    category_id: uuid.UUID,
    payload: ExpenseCategoryCreate,
    db: Session = Depends(get_db),
    _user: User = Depends(require_manager_or_admin),
) -> ExpenseCategory:
    category = db.execute(
        select(ExpenseCategory).where(ExpenseCategory.id == category_id)
    ).scalar_one_or_none()
    if category is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Category not found.")

    category.name = payload.name.strip()
    try:
        db.flush()
    except IntegrityError:
        db.rollback()
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="A category with this name already exists.",
        )
    db.refresh(category)
    return category


@router.delete("/{category_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_category(
    category_id: uuid.UUID,
    db: Session = Depends(get_db),
    _user: User = Depends(require_manager_or_admin),
):
    """Delete a category. Historical expenses keep their snapshotted `reason`;
    their `category_id` becomes NULL (ON DELETE SET NULL)."""
    category = db.execute(
        select(ExpenseCategory).where(ExpenseCategory.id == category_id)
    ).scalar_one_or_none()
    if category is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Category not found.")

    db.delete(category)
    db.flush()
    return Response(status_code=status.HTTP_204_NO_CONTENT)
