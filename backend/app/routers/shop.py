from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.auth.dependencies import get_db, require_shop_staff
from app.models.shop import Shop
from app.models.user import User
from app.schemas.shop import ShopSettingsOut, ShopSettingsUpdate

router = APIRouter(prefix="/shop", tags=["shop"])


@router.get("", response_model=ShopSettingsOut)
def get_shop_settings(
    db: Session = Depends(get_db),
    owner: User = Depends(require_shop_staff),
) -> Shop:
    """Fetch the settings and profile for the authenticated user's shop."""
    if owner.shop_id is None:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="User is not associated with any shop"
        )
        
    shop = db.execute(select(Shop).where(Shop.id == owner.shop_id)).scalar_one_or_none()
    if not shop:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Shop not found"
        )
    return shop


@router.patch("", response_model=ShopSettingsOut)
def update_shop_settings(
    payload: ShopSettingsUpdate,
    db: Session = Depends(get_db),
    owner: User = Depends(require_shop_staff),
) -> Shop:
    """Update settings for the authenticated user's shop."""
    if owner.shop_id is None:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="User is not associated with any shop"
        )
        
    shop = db.execute(select(Shop).where(Shop.id == owner.shop_id)).scalar_one_or_none()
    if not shop:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Shop not found"
        )

    # Perform validation on UPI if it is updated
    if payload.business_upi is not None:
        trimmed_upi = payload.business_upi.strip()
        if trimmed_upi:
            upi_regex = r"^[a-zA-Z0-9.\-_]{2,256}@[a-zA-Z]{2,64}$"
            import re
            if not re.match(upi_regex, trimmed_upi):
                raise HTTPException(
                    status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
                    detail="Invalid UPI ID (VPA) format. Must be like username@bank."
                )
            shop.business_upi = trimmed_upi
        else:
            shop.business_upi = None

    # Update columns
    if payload.business_name is not None:
        shop.business_name = payload.business_name.strip() or None
    if payload.business_address is not None:
        shop.business_address = payload.business_address.strip() or None
    if payload.business_phone is not None:
        shop.business_phone = payload.business_phone.strip() or None
    if payload.business_email is not None:
        shop.business_email = payload.business_email.strip() or None

    # Update properties mapping to settings JSONB
    if payload.whatsapp_auto_send is not None:
        shop.whatsapp_auto_send = payload.whatsapp_auto_send
    if payload.whatsapp_enable_pdf is not None:
        shop.whatsapp_enable_pdf = payload.whatsapp_enable_pdf
    if payload.whatsapp_message_template is not None:
        shop.whatsapp_message_template = payload.whatsapp_message_template
    if payload.whatsapp_footer_message is not None:
        shop.whatsapp_footer_message = payload.whatsapp_footer_message
    if payload.whatsapp_language is not None:
        shop.whatsapp_language = payload.whatsapp_language

    db.flush()
    db.refresh(shop)
    return shop
