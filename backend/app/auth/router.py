"""Authentication routes: /auth/login and /auth/me."""
from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.auth.dependencies import get_current_user, get_db
from app.auth.security import create_access_token, verify_password
from app.database import privileged_session
from app.models.shop import Shop
from app.models.user import ROLE_MANAGER, ROLE_SALESPERSON, User
from app.schemas.auth import CurrentUser, LoginRequest, Token

router = APIRouter(prefix="/auth", tags=["auth"])


@router.post("/login", response_model=Token)
def login(payload: LoginRequest) -> Token:
    """Verify credentials and issue a JWT.

    Uses a privileged (admin-context) session because there is no JWT yet and we
    must look up the user by email across the whole users table.
    """
    invalid = HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Invalid email or password",
    )

    with privileged_session() as db:
        user = db.execute(
            select(User).where(User.email == str(payload.email))
        ).scalar_one_or_none()

        if user is None or not verify_password(payload.password, user.password_hash):
            raise invalid
        if not user.is_active:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN, detail="User account is inactive"
            )

        if user.role in (ROLE_MANAGER, ROLE_SALESPERSON):
            shop = db.execute(select(Shop).where(Shop.id == user.shop_id)).scalar_one_or_none()
            if shop is None or not shop.is_active:
                raise HTTPException(
                    status_code=status.HTTP_403_FORBIDDEN, detail="Shop is inactive"
                )

        token = create_access_token(user_id=user.id, role=user.role, shop_id=user.shop_id)

    return Token(access_token=token)


@router.get("/me", response_model=CurrentUser)
def me(
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> CurrentUser:
    shop_name = None
    business_name = None
    business_upi = None
    if user.shop_id is not None:
        shop = db.execute(select(Shop).where(Shop.id == user.shop_id)).scalar_one_or_none()
        if shop:
            shop_name = shop.name
            business_name = shop.business_name
            business_upi = shop.business_upi

    return CurrentUser(
        id=user.id,
        email=user.email,
        role=user.role,
        shop_id=user.shop_id,
        is_active=user.is_active,
        shop_name=shop_name,
        business_name=business_name,
        business_upi=business_upi,
    )
