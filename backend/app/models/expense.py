from __future__ import annotations

import datetime as dt
import decimal
import uuid

from typing import TYPE_CHECKING

from sqlalchemy import ForeignKey, Numeric, Text, text
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.models.base import Base, created_at_col, uuid_pk

if TYPE_CHECKING:
    from app.models.expense_category import ExpenseCategory


class Expense(Base):
    __tablename__ = "expenses"

    id: Mapped[uuid.UUID] = uuid_pk()
    shop_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("shops.id", ondelete="CASCADE"),
        nullable=False,
    )
    amount: Mapped[decimal.Decimal] = mapped_column(Numeric(12, 2), nullable=False)
    # Human-readable label. For category-backed expenses this is a snapshot of the
    # category name at create time (so a later rename never rewrites history); for
    # legacy rows it's the free text that was typed before categories existed.
    reason: Mapped[str] = mapped_column(Text, nullable=False)
    # Optional link to a reusable expense category. Nullable so legacy expenses and
    # deleted categories (ON DELETE SET NULL) remain valid. Reports group by this.
    category_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("expense_categories.id", ondelete="SET NULL"),
        nullable=True,
    )
    # Optional free-text remark with the specifics ("scooter fill", "meter #3").
    note: Mapped[str | None] = mapped_column(Text, nullable=True)
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

    # Eager-loaded so ExpenseOut.category_name resolves in one query (no N+1) and
    # stays valid within the request transaction (RLS context alive until commit).
    category: Mapped["ExpenseCategory | None"] = relationship(lazy="joined")

    @property
    def category_name(self) -> str | None:
        return self.category.name if self.category is not None else None

