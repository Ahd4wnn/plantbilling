from __future__ import annotations

import datetime as dt
import decimal
import uuid

from sqlalchemy import Boolean, DateTime, ForeignKey, Numeric, Text, text
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base, created_at_col, uuid_pk


class Borrowing(Base):
    """Money the shop borrowed from a person (a lender).

    A plain debt note: it records who lent the money, how much, how it was
    received (cash/UPI split — informational only), and once repaid, how it was
    paid back and when. It deliberately does NOT affect Cash in Hand or the cash
    book so it never entangles with daily reconciliation.
    """

    __tablename__ = "borrowings"

    id: Mapped[uuid.UUID] = uuid_pk()
    shop_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("shops.id", ondelete="CASCADE"), nullable=False
    )
    lender_name: Mapped[str] = mapped_column(Text, nullable=False)
    lender_phone: Mapped[str | None] = mapped_column(Text, nullable=True)
    # Total borrowed, and how it was received (cash + upi == amount).
    amount: Mapped[decimal.Decimal] = mapped_column(
        Numeric(12, 2), nullable=False, server_default=text("0")
    )
    cash_amount: Mapped[decimal.Decimal] = mapped_column(
        Numeric(12, 2), nullable=False, server_default=text("0")
    )
    upi_amount: Mapped[decimal.Decimal] = mapped_column(
        Numeric(12, 2), nullable=False, server_default=text("0")
    )
    remarks: Mapped[str | None] = mapped_column(Text, nullable=True)
    # Repayment. is_paid flips when the full amount is repaid; the split records how.
    is_paid: Mapped[bool] = mapped_column(Boolean, nullable=False, server_default=text("false"))
    paid_cash_amount: Mapped[decimal.Decimal] = mapped_column(
        Numeric(12, 2), nullable=False, server_default=text("0")
    )
    paid_upi_amount: Mapped[decimal.Decimal] = mapped_column(
        Numeric(12, 2), nullable=False, server_default=text("0")
    )
    paid_at: Mapped[dt.datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    created_by: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True), ForeignKey("users.id", ondelete="SET NULL"), nullable=True
    )
    created_at: Mapped[dt.datetime] = created_at_col()
