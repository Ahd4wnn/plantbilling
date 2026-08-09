from __future__ import annotations

import datetime as dt
import decimal
import uuid

from sqlalchemy import Boolean, Integer, Numeric, Text, text
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base, created_at_col, uuid_pk


class Shop(Base):
    __tablename__ = "shops"

    id: Mapped[uuid.UUID] = uuid_pk()
    name: Mapped[str] = mapped_column(Text, nullable=False)
    # Multi-shop business owners are linked via the shop_owners join table (a shop
    # may have several owners, or none). See app.models.shop_owner.ShopOwner.
    owner_name: Mapped[str | None] = mapped_column(Text, nullable=True)
    owner_phone: Mapped[str | None] = mapped_column(Text, nullable=True)
    is_active: Mapped[bool] = mapped_column(Boolean, nullable=False, server_default=text("true"))
    
    business_name: Mapped[str | None] = mapped_column(Text, nullable=True)
    business_address: Mapped[str | None] = mapped_column(Text, nullable=True)
    business_phone: Mapped[str | None] = mapped_column(Text, nullable=True)
    business_email: Mapped[str | None] = mapped_column(Text, nullable=True)
    business_upi: Mapped[str | None] = mapped_column(Text, nullable=True)

    # Offset for the running (cumulative) cash-in-hand display. Running total =
    # base + all cash flows (cash sales − cash expenses) through the day. See the
    # a8b2c3d4e5f6 migration.
    cash_in_hand_base: Mapped[decimal.Decimal] = mapped_column(
        Numeric(12, 2), nullable=False, server_default=text("0")
    )

    # Per-shop counter for human-facing bill numbers. Allocated atomically at
    # checkout (UPDATE … RETURNING) so concurrent bills never share a number.
    next_bill_seq: Mapped[int] = mapped_column(
        Integer, nullable=False, server_default=text("1")
    )

    settings: Mapped[dict] = mapped_column(JSONB, nullable=False, server_default=text("'{}'::jsonb"))
    created_at: Mapped[dt.datetime] = created_at_col()

    @property
    def whatsapp_auto_send(self) -> bool:
        return self.settings.get("whatsapp_auto_send", False)

    @whatsapp_auto_send.setter
    def whatsapp_auto_send(self, value: bool) -> None:
        if self.settings is None:
            self.settings = {}
        new_settings = dict(self.settings)
        new_settings["whatsapp_auto_send"] = value
        self.settings = new_settings

    @property
    def whatsapp_message_template(self) -> str | None:
        return self.settings.get("whatsapp_message_template", None)

    @whatsapp_message_template.setter
    def whatsapp_message_template(self, value: str | None) -> None:
        if self.settings is None:
            self.settings = {}
        new_settings = dict(self.settings)
        if value is None:
            new_settings.pop("whatsapp_message_template", None)
        else:
            new_settings["whatsapp_message_template"] = value.strip()
        self.settings = new_settings

    @property
    def whatsapp_enable_pdf(self) -> bool:
        return self.settings.get("whatsapp_enable_pdf", True)

    @whatsapp_enable_pdf.setter
    def whatsapp_enable_pdf(self, value: bool) -> None:
        if self.settings is None:
            self.settings = {}
        new_settings = dict(self.settings)
        new_settings["whatsapp_enable_pdf"] = value
        self.settings = new_settings

    @property
    def whatsapp_footer_message(self) -> str | None:
        return self.settings.get("whatsapp_footer_message", None)

    @whatsapp_footer_message.setter
    def whatsapp_footer_message(self, value: str | None) -> None:
        if self.settings is None:
            self.settings = {}
        new_settings = dict(self.settings)
        if value is None:
            new_settings.pop("whatsapp_footer_message", None)
        else:
            new_settings["whatsapp_footer_message"] = value.strip()
        self.settings = new_settings

    @property
    def whatsapp_language(self) -> str:
        return self.settings.get("whatsapp_language", "en")

    @whatsapp_language.setter
    def whatsapp_language(self, value: str | None) -> None:
        if self.settings is None:
            self.settings = {}
        new_settings = dict(self.settings)
        if value is None:
            new_settings.pop("whatsapp_language", None)
        else:
            new_settings["whatsapp_language"] = value.strip()
        self.settings = new_settings

