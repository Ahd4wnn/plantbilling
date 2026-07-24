"""Platform (Dofida) own books — admin-only sales & expenses.

These tables track the PLATFORM operator's own money (e.g. selling PlantBill
subscriptions or printers to nurseries, and the business's own running costs).
They are deliberately NOT tenant tables: there is no ``shop_id``. RLS restricts
every row to the ``admin`` role, so no shop user can ever read or write them.

Money mirrors the rest of the app: NUMERIC(12,2), quantized 2dp, and for a sale
the invariant ``cash_amount + upi_amount + due_amount == amount`` always holds.
Collecting an outstanding due later simply moves money out of ``due_amount`` into
``cash_amount`` / ``upi_amount``, preserving that invariant.
"""
from __future__ import annotations

import datetime as dt
import decimal
import uuid

from sqlalchemy import Date, ForeignKey, Numeric, Text, text
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base, created_at_col, uuid_pk


class AdminSale(Base):
    """A single income entry in the platform's own books."""

    __tablename__ = "admin_sales"

    id: Mapped[uuid.UUID] = uuid_pk()
    title: Mapped[str] = mapped_column(Text, nullable=False)
    amount: Mapped[decimal.Decimal] = mapped_column(Numeric(12, 2), nullable=False)
    # Split of how the total was received. Sums to `amount`.
    cash_amount: Mapped[decimal.Decimal] = mapped_column(
        Numeric(12, 2), nullable=False, server_default=text("0")
    )
    upi_amount: Mapped[decimal.Decimal] = mapped_column(
        Numeric(12, 2), nullable=False, server_default=text("0")
    )
    due_amount: Mapped[decimal.Decimal] = mapped_column(
        Numeric(12, 2), nullable=False, server_default=text("0")
    )
    customer_name: Mapped[str | None] = mapped_column(Text, nullable=True)
    customer_phone: Mapped[str | None] = mapped_column(Text, nullable=True)
    note: Mapped[str | None] = mapped_column(Text, nullable=True)
    # The business date the sale belongs to (admin-chosen, IST). Distinct from the
    # created_at audit timestamp.
    occurred_on: Mapped[dt.date] = mapped_column(Date, nullable=False)
    created_by: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("users.id", ondelete="SET NULL"),
        nullable=True,
    )
    created_at: Mapped[dt.datetime] = created_at_col()


class AdminExpense(Base):
    """A single business expense in the platform's own books."""

    __tablename__ = "admin_expenses"

    id: Mapped[uuid.UUID] = uuid_pk()
    reason: Mapped[str] = mapped_column(Text, nullable=False)
    amount: Mapped[decimal.Decimal] = mapped_column(Numeric(12, 2), nullable=False)
    # 'cash' | 'upi' — how the expense was paid.
    payment_method: Mapped[str] = mapped_column(
        Text, nullable=False, server_default=text("'cash'")
    )
    note: Mapped[str | None] = mapped_column(Text, nullable=True)
    occurred_on: Mapped[dt.date] = mapped_column(Date, nullable=False)
    created_by: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("users.id", ondelete="SET NULL"),
        nullable=True,
    )
    created_at: Mapped[dt.datetime] = created_at_col()
