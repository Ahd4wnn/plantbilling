from __future__ import annotations

import datetime as dt
import uuid

import re
from pydantic import BaseModel, EmailStr, Field, field_validator


class ShopCreateRequest(BaseModel):
    name: str = Field(min_length=1)
    owner_name: str | None = None
    owner_phone: str | None = None
    # Login for the shop's first MANAGER (the per-shop operator).
    owner_email: EmailStr
    owner_password: str = Field(min_length=8)
    # Optionally link the shop to an existing multi-shop owner account.
    owner_id: uuid.UUID | None = None


class OwnerAccountCreate(BaseModel):
    """Create a multi-shop owner login (no shop attached at creation)."""

    email: EmailStr
    password: str = Field(min_length=8)


class OwnerAccountInfo(BaseModel):
    id: uuid.UUID
    email: EmailStr
    is_active: bool
    created_at: dt.datetime
    shop_count: int = 0

    model_config = {"from_attributes": True}


class AssignOwnerRequest(BaseModel):
    """Set (or clear, with null) the business owner of a shop."""

    owner_id: uuid.UUID | None = None


class OwnerInfo(BaseModel):
    """Owner user info — never includes the password or its hash."""

    id: uuid.UUID
    email: EmailStr
    role: str
    shop_id: uuid.UUID | None
    is_active: bool

    model_config = {"from_attributes": True}


class ShopSummary(BaseModel):
    id: uuid.UUID
    name: str
    owner_id: uuid.UUID | None = None
    owner_name: str | None
    owner_phone: str | None
    is_active: bool
    created_at: dt.datetime
    whatsapp_auto_send: bool
    business_name: str | None = None
    business_address: str | None = None
    business_phone: str | None = None
    business_email: str | None = None
    business_upi: str | None = None
    whatsapp_message_template: str | None = None

    model_config = {"from_attributes": True}


class ShopCreateResponse(BaseModel):
    shop: ShopSummary
    owner: OwnerInfo


class ShopListRow(BaseModel):
    """A shop row for the admin dashboard, including the owner's login email."""

    id: uuid.UUID
    name: str
    owner_id: uuid.UUID | None = None
    owner_email_account: EmailStr | None = None  # the linked multi-shop owner's login
    owner_name: str | None
    owner_phone: str | None
    owner_email: EmailStr | None  # the shop's manager login
    is_active: bool
    created_at: dt.datetime
    whatsapp_auto_send: bool
    business_name: str | None = None
    business_address: str | None = None
    business_phone: str | None = None
    business_email: str | None = None
    business_upi: str | None = None
    whatsapp_message_template: str | None = None


class ShopUpdateRequest(BaseModel):
    is_active: bool | None = None
    whatsapp_auto_send: bool | None = None
    business_name: str | None = None
    business_address: str | None = None
    business_phone: str | None = None
    business_email: str | None = None
    business_upi: str | None = None
    whatsapp_message_template: str | None = None

    @field_validator("business_upi")
    @classmethod
    def validate_upi(cls, v: str | None) -> str | None:
        if v is None:
            return None
        v_clean = v.strip()
        if not v_clean:
            return None
        
        # Pattern matching: ^[a-zA-Z0-9.\-_]{2,256}@[a-zA-Z]{2,64}$
        upi_pattern = re.compile(r"^[a-zA-Z0-9.\-_]{2,256}@[a-zA-Z]{2,64}$")
        if not upi_pattern.match(v_clean):
            raise ValueError("Invalid UPI ID (VPA) format. Must match standard format like username@bank.")
        return v_clean


class ResetPasswordRequest(BaseModel):
    new_password: str = Field(min_length=8)


class AdminCustomerRow(BaseModel):
    """A customer in the cross-shop directory, tagged with its owning shop."""

    id: uuid.UUID
    name: str
    phone: str | None
    shop_id: uuid.UUID
    shop_name: str
    created_at: dt.datetime


class AdminCustomerList(BaseModel):
    items: list[AdminCustomerRow]
    total: int
    limit: int
    offset: int
