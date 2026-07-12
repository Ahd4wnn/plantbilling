from __future__ import annotations

import datetime as dt
import decimal
import uuid

from sqlalchemy import Boolean, ForeignKey, Numeric, Text, text
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base, created_at_col, uuid_pk


class Labourer(Base):
    """A worker on the shop's labour roster (managed by the manager)."""

    __tablename__ = "labourers"

    id: Mapped[uuid.UUID] = uuid_pk()
    shop_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("shops.id", ondelete="CASCADE"), nullable=False
    )
    name: Mapped[str] = mapped_column(Text, nullable=False)
    phone: Mapped[str | None] = mapped_column(Text, nullable=True)
    gender: Mapped[str] = mapped_column(Text, nullable=False)  # 'male' | 'female'
    default_wage: Mapped[decimal.Decimal] = mapped_column(
        Numeric(12, 2), nullable=False, server_default=text("0")
    )
    overtime_rate: Mapped[decimal.Decimal] = mapped_column(
        Numeric(12, 2), nullable=False, server_default=text("0")
    )  # per hour
    is_active: Mapped[bool] = mapped_column(Boolean, nullable=False, server_default=text("true"))
    created_by: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True), ForeignKey("users.id", ondelete="SET NULL"), nullable=True
    )
    created_at: Mapped[dt.datetime] = created_at_col()


class LabourPayment(Base):
    """A single payment made to a worker. Name + gender are denormalized so the
    record (and the report) never change if the labourer is edited or deleted."""

    __tablename__ = "labour_payments"

    id: Mapped[uuid.UUID] = uuid_pk()
    shop_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("shops.id", ondelete="CASCADE"), nullable=False
    )
    labourer_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True), ForeignKey("labourers.id", ondelete="SET NULL"), nullable=True
    )
    labourer_name: Mapped[str] = mapped_column(Text, nullable=False)
    gender: Mapped[str] = mapped_column(Text, nullable=False)
    wage_amount: Mapped[decimal.Decimal] = mapped_column(
        Numeric(12, 2), nullable=False, server_default=text("0")
    )
    overtime_hours: Mapped[decimal.Decimal] = mapped_column(
        Numeric(12, 2), nullable=False, server_default=text("0")
    )
    # The per-hour rate applied (denormalized from the labourer at payment time),
    # so editing a payment recomputes overtime without needing the labourer row.
    overtime_rate: Mapped[decimal.Decimal] = mapped_column(
        Numeric(12, 2), nullable=False, server_default=text("0")
    )
    overtime_amount: Mapped[decimal.Decimal] = mapped_column(
        Numeric(12, 2), nullable=False, server_default=text("0")
    )
    total_amount: Mapped[decimal.Decimal] = mapped_column(
        Numeric(12, 2), nullable=False, server_default=text("0")
    )
    note: Mapped[str | None] = mapped_column(Text, nullable=True)
    created_by: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True), ForeignKey("users.id", ondelete="SET NULL"), nullable=True
    )
    created_at: Mapped[dt.datetime] = created_at_col()
