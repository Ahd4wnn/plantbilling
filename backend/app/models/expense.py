from __future__ import annotations

import datetime as dt
import decimal
import uuid

from sqlalchemy import ForeignKey, Numeric, Text, text
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base, created_at_col, uuid_pk


class Expense(Base):
    __tablename__ = "expenses"

    id: Mapped[uuid.UUID] = uuid_pk()
    shop_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("shops.id", ondelete="CASCADE"),
        nullable=False,
    )
    amount: Mapped[decimal.Decimal] = mapped_column(Numeric(12, 2), nullable=False)
    reason: Mapped[str] = mapped_column(Text, nullable=False)
    # How the expense was paid: 'cash' comes out of the drawer, 'upi' out of the
    # UPI takings. Drives the day's Cash in Hand (cash sales − cash expenses).
    payment_method: Mapped[str] = mapped_column(
        Text, nullable=False, server_default=text("'cash'")
    )
    created_by: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("users.id", ondelete="SET NULL"),
        nullable=True,
    )
    created_at: Mapped[dt.datetime] = created_at_col()

