from __future__ import annotations

import uuid

from fastapi import APIRouter, Depends, HTTPException, Response, status
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.auth.dependencies import get_db, require_manager_only
from app.auth.security import hash_password
from app.models.user import ROLE_SALESPERSON, User
from app.schemas.shop_user import (
    SalespersonActivateRequest,
    SalespersonCreate,
    SalespersonOut,
    SalespersonResetPasswordRequest,
)

router = APIRouter(
    prefix="/shop/users",
    tags=["shop_users"],
    dependencies=[Depends(require_manager_only)],
)


@router.get("", response_model=list[SalespersonOut])
def list_salespeople(
    db: Session = Depends(get_db),
    _owner: User = Depends(require_manager_only),
) -> list[User]:
    """List all salespeople in the shop. RLS enforces shop isolation."""
    stmt = select(User).where(User.role == ROLE_SALESPERSON).order_by(User.created_at.desc())
    return list(db.execute(stmt).scalars().all())


@router.post("", response_model=SalespersonOut, status_code=status.HTTP_201_CREATED)
def create_salesperson(
    payload: SalespersonCreate,
    db: Session = Depends(get_db),
    owner: User = Depends(require_manager_only),
) -> User:
    """Create a new salesperson account under the owner's shop."""
    existing = db.execute(
        select(User).where(User.email == str(payload.email))
    ).scalar_one_or_none()
    if existing is not None:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="A user with this email already exists",
        )

    salesperson = User(
        shop_id=owner.shop_id,  # Scopes automatically to current owner's shop
        email=str(payload.email),
        password_hash=hash_password(payload.password),
        role=ROLE_SALESPERSON,
    )
    db.add(salesperson)

    try:
        db.flush()
    except IntegrityError:
        db.rollback()
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="A user with this email already exists",
        )

    db.refresh(salesperson)
    return salesperson


def _get_salesperson_or_404(db: Session, user_id: uuid.UUID) -> User:
    """Fetch salesperson user or raise 404. RLS prevents fetching other shops' users."""
    user = db.execute(
        select(User).where(User.id == user_id, User.role == ROLE_SALESPERSON)
    ).scalar_one_or_none()
    if user is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Salesperson not found",
        )
    return user


@router.patch("/{user_id}", response_model=SalespersonOut)
def update_salesperson_status(
    user_id: uuid.UUID,
    payload: SalespersonActivateRequest,
    db: Session = Depends(get_db),
    _owner: User = Depends(require_manager_only),
) -> User:
    """Activate/Deactivate a salesperson."""
    salesperson = _get_salesperson_or_404(db, user_id)
    salesperson.is_active = payload.is_active
    db.flush()
    db.refresh(salesperson)
    return salesperson


@router.post("/{user_id}/reset-password", response_model=SalespersonOut)
def reset_salesperson_password(
    user_id: uuid.UUID,
    payload: SalespersonResetPasswordRequest,
    db: Session = Depends(get_db),
    _owner: User = Depends(require_manager_only),
) -> User:
    """Reset a salesperson's password."""
    salesperson = _get_salesperson_or_404(db, user_id)
    salesperson.password_hash = hash_password(payload.new_password)
    db.flush()
    db.refresh(salesperson)
    return salesperson


@router.delete("/{user_id}", status_code=status.HTTP_204_NO_CONTENT, response_class=Response)
def delete_salesperson(
    user_id: uuid.UUID,
    db: Session = Depends(get_db),
    _owner: User = Depends(require_manager_only),
):
    """Delete a salesperson. RLS enforces shop isolation."""
    salesperson = _get_salesperson_or_404(db, user_id)
    db.delete(salesperson)
    db.flush()

