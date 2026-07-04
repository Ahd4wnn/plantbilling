from __future__ import annotations

import datetime as dt
import uuid

from sqlalchemy import ForeignKey
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base, created_at_col


class ShopOwner(Base):
    """Join table linking shops to their multi-shop owners (many-to-many).

    Replaces the old single `shops.owner_id`. RLS uses this table to decide which
    shops an owner may read: a row (shop_id, owner_id) grants the owner access to
    that shop's data. Managed by the admin only.
    """

    __tablename__ = "shop_owners"

    shop_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("shops.id", ondelete="CASCADE"),
        primary_key=True,
    )
    owner_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("users.id", ondelete="CASCADE"),
        primary_key=True,
    )
    created_at: Mapped[dt.datetime] = created_at_col()
