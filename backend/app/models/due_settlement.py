from __future__ import annotations

import datetime as dt
import decimal
import uuid

from sqlalchemy import DateTime, ForeignKey, Numeric, Text, text
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base, created_at_col, uuid_pk


class DueSettlement(Base):
    """A request to collect (close) an outstanding due on a bill.

    A salesperson's collection is recorded here as 'pending' — the money is NOT
    yet applied to the bill; a manager/admin must approve it (verifying the cash
    actually arrived). A manager/admin collecting a due directly is written as
    'approved' immediately and the split is applied to the bill in the same
    transaction. At most one 'pending' row may exist per bill (partial unique
    index in the migration), so a due can't be collected twice.
    """

    __tablename__ = "due_settlements"

    id: Mapped[uuid.UUID] = uuid_pk()
    shop_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("shops.id", ondelete="CASCADE"),
        nullable=False,
    )
    bill_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("bills.id", ondelete="CASCADE"),
        nullable=False,
    )
    cash_amount: Mapped[decimal.Decimal] = mapped_column(
        Numeric(12, 2), nullable=False, server_default=text("0")
    )
    upi_amount: Mapped[decimal.Decimal] = mapped_column(
        Numeric(12, 2), nullable=False, server_default=text("0")
    )
    status: Mapped[str] = mapped_column(
        Text, nullable=False, server_default=text("'pending'")
    )  # 'pending' | 'approved' | 'rejected'
    requested_by: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("users.id", ondelete="SET NULL"),
        nullable=True,
    )
    reviewed_by: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("users.id", ondelete="SET NULL"),
        nullable=True,
    )
    created_at: Mapped[dt.datetime] = created_at_col()
    reviewed_at: Mapped[dt.datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True
    )
