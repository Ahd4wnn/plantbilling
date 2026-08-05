from __future__ import annotations

import datetime as dt
import uuid

from sqlalchemy import ForeignKey, Text
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base, created_at_col, uuid_pk


class ExpenseCategory(Base):
    """A reusable, shop-scoped expense category (e.g. "Petrol", "Electricity").

    Managers curate the list; counter staff pick one when logging an expense so
    reports can total spend per category (all "Petrol" together) rather than per
    day. Tenant table — RLS scopes every row to the owning shop.
    """

    __tablename__ = "expense_categories"

    id: Mapped[uuid.UUID] = uuid_pk()
    shop_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("shops.id", ondelete="CASCADE"),
        nullable=False,
    )
    name: Mapped[str] = mapped_column(Text, nullable=False)
    created_at: Mapped[dt.datetime] = created_at_col()
