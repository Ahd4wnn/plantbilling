from __future__ import annotations

import datetime as dt
import decimal
import uuid

from sqlalchemy import Boolean, DateTime, ForeignKey, Integer, Numeric, Text, text
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base, created_at_col, uuid_pk


class Bill(Base):
    __tablename__ = "bills"

    id: Mapped[uuid.UUID] = uuid_pk()
    shop_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("shops.id", ondelete="CASCADE"),
        nullable=False,
    )
    customer_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("customers.id", ondelete="SET NULL"),
        nullable=True,
    )
    # Human-facing, per-shop bill number (1, 2, 3 …), shown as 0001, 0002 …
    # Allocated at checkout from shops.next_bill_seq. NULL for legacy bills created
    # before sequential numbering shipped (they fall back to the UUID fragment).
    # Unique per shop (partial unique index ux_bills_shop_seq).
    bill_seq: Mapped[int | None] = mapped_column(Integer, nullable=True)
    bill_type: Mapped[str] = mapped_column(Text, nullable=False)  # 'retail' | 'wholesale'
    subtotal: Mapped[decimal.Decimal] = mapped_column(Numeric(12, 2), nullable=False)
    discount_type: Mapped[str] = mapped_column(
        Text, nullable=False, server_default=text("'flat'")
    )  # 'flat' | 'percent'
    discount_value: Mapped[decimal.Decimal] = mapped_column(
        Numeric(12, 2), nullable=False, server_default=text("0")
    )
    discount_amount: Mapped[decimal.Decimal] = mapped_column(
        Numeric(12, 2), nullable=False, server_default=text("0")
    )
    total: Mapped[decimal.Decimal] = mapped_column(Numeric(12, 2), nullable=False)
    cash_amount: Mapped[decimal.Decimal] = mapped_column(
        Numeric(12, 2), nullable=False, server_default=text("0")
    )
    upi_amount: Mapped[decimal.Decimal] = mapped_column(
        Numeric(12, 2), nullable=False, server_default=text("0")
    )
    due_amount: Mapped[decimal.Decimal] = mapped_column(
        Numeric(12, 2), nullable=False, server_default=text("0")
    )
    created_by: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("users.id", ondelete="SET NULL"),
        nullable=True,
    )
    # The biller's email, denormalized (snapshotted) at sale time so "who billed"
    # survives the salesperson being deleted (created_by is SET NULL on delete).
    # Source of truth for display; a live user lookup is only a fallback.
    created_by_email: Mapped[str | None] = mapped_column(Text, nullable=True)
    # Client-generated key making POST /bills idempotent: a retry/double-tap with
    # the same key returns the original bill instead of creating a duplicate.
    # Unique per shop (partial unique index in migration 0002).
    idempotency_key: Mapped[str | None] = mapped_column(Text, nullable=True)
    # Last WhatsApp send outcome for this bill (debugging/admin only; never gates
    # the saved sale). One of the SendWhatsAppResult statuses, or NULL if never tried.
    whatsapp_last_status: Mapped[str | None] = mapped_column(Text, nullable=True)
    whatsapp_sent_at: Mapped[dt.datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True
    )
    invoice_url: Mapped[str | None] = mapped_column(Text, nullable=True)
    whatsapp_status: Mapped[str] = mapped_column(Text, nullable=False, server_default=text("'none'"))
    whatsapp_message_id: Mapped[str | None] = mapped_column(Text, nullable=True)
    whatsapp_error: Mapped[str | None] = mapped_column(Text, nullable=True)
    retry_count: Mapped[int] = mapped_column(Integer, nullable=False, server_default=text("0"))
    last_retry_at: Mapped[dt.datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True
    )
    remarks: Mapped[str | None] = mapped_column(Text, nullable=True)
    is_edited: Mapped[bool] = mapped_column(
        Boolean, nullable=False, server_default=text("false")
    )
    created_at: Mapped[dt.datetime] = created_at_col()


class BillItem(Base):
    __tablename__ = "bill_items"

    id: Mapped[uuid.UUID] = uuid_pk()
    bill_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("bills.id", ondelete="CASCADE"),
        nullable=False,
    )
    # Nullable so historical bills survive product deletion; name/price are
    # denormalized snapshots captured at the time of sale.
    product_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("products.id", ondelete="SET NULL"),
        nullable=True,
    )
    product_name: Mapped[str] = mapped_column(Text, nullable=False)
    unit_price: Mapped[decimal.Decimal] = mapped_column(Numeric(12, 2), nullable=False)
    quantity: Mapped[int] = mapped_column(Integer, nullable=False)
    line_total: Mapped[decimal.Decimal] = mapped_column(Numeric(12, 2), nullable=False)
