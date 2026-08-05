from __future__ import annotations

import datetime as dt
import uuid
from typing import Annotated

from pydantic import BaseModel, StringConstraints, field_validator

NonEmptyStr = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1)]


def _validate_optional_phone(v: str | None) -> str | None:
    """A phone, if given, must be exactly 10 digits. Blank → None (optional)."""
    if v is None:
        return None
    digits = "".join(ch for ch in v if ch.isdigit())
    if digits == "":
        return None
    if len(digits) != 10:
        raise ValueError("Phone number must be exactly 10 digits")
    return digits


class CustomerCreate(BaseModel):
    name: NonEmptyStr
    phone: str | None = None

    @field_validator("phone")
    @classmethod
    def _check_phone(cls, v: str | None) -> str | None:
        return _validate_optional_phone(v)


class CustomerLookupOut(BaseModel):
    """Returning-customer hint for the billing screen. RLS ensures the count only
    reflects THIS shop's bills, so a number from another shop reads as not found."""

    found: bool = False
    name: str | None = None
    visit_count: int = 0


class CustomerOut(BaseModel):
    id: uuid.UUID
    name: str
    phone: str | None
    # WhatsApp consent state so the frontend can reflect eligibility (10B).
    whatsapp_consent: bool = False
    whatsapp_opted_out: bool = False
    # True iff phone present AND consent AND not opted out (mirrors the helper).
    whatsapp_eligible: bool = False
    created_at: dt.datetime

    model_config = {"from_attributes": True}
