from __future__ import annotations

import datetime as dt
import uuid

from sqlalchemy import Boolean, ForeignKey, Text, text
from sqlalchemy.dialects.postgresql import JSONB, UUID
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base, created_at_col, uuid_pk


class Shop(Base):
    __tablename__ = "shops"

    id: Mapped[uuid.UUID] = uuid_pk()
    name: Mapped[str] = mapped_column(Text, nullable=False)
    # The multi-shop business owner (a users.id with role 'owner'). Optional —
    # a shop may run with just a manager and no owner.
    owner_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("users.id", ondelete="SET NULL"),
        nullable=True,
    )
    owner_name: Mapped[str | None] = mapped_column(Text, nullable=True)
    owner_phone: Mapped[str | None] = mapped_column(Text, nullable=True)
    is_active: Mapped[bool] = mapped_column(Boolean, nullable=False, server_default=text("true"))
    
    business_name: Mapped[str | None] = mapped_column(Text, nullable=True)
    business_address: Mapped[str | None] = mapped_column(Text, nullable=True)
    business_phone: Mapped[str | None] = mapped_column(Text, nullable=True)
    business_email: Mapped[str | None] = mapped_column(Text, nullable=True)
    business_upi: Mapped[str | None] = mapped_column(Text, nullable=True)

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

