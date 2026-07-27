from __future__ import annotations

import datetime as dt
import uuid

from sqlalchemy import ForeignKey, Text
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base, created_at_col, uuid_pk


class Notification(Base):
    """A platform announcement authored by the admin and shown to shops in-app.

    Global (not tenant) data — there is no `shop_id`. Which shops see it is
    decided by `target`: 'all' means every shop (no target rows), 'shops' means
    only the shops listed in `notification_targets`. Read/unread is tracked per
    user in `notification_reads`. Delivery is in-app (the shop app polls); the
    schema is push-ready (FCM can be layered on later without changes here).
    """

    __tablename__ = "notifications"

    id: Mapped[uuid.UUID] = uuid_pk()
    title: Mapped[str] = mapped_column(Text, nullable=False)
    body: Mapped[str] = mapped_column(Text, nullable=False)
    # Optional deep link / URL a shop can tap from the notification.
    action_url: Mapped[str | None] = mapped_column(Text, nullable=True)
    # 'all' = every shop; 'shops' = only rows in notification_targets.
    target: Mapped[str] = mapped_column(Text, nullable=False, default="all")
    created_by: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("users.id", ondelete="SET NULL"),
        nullable=True,
    )
    created_at: Mapped[dt.datetime] = created_at_col()


class NotificationTarget(Base):
    """Which shops a targeted ('shops') notification is addressed to.

    Composite-PK join like shop_owners. A broadcast ('all') notification has no
    rows here. RLS lets the admin manage rows and lets a shop read the rows that
    name it (so the notifications visibility policy's EXISTS subquery resolves).
    """

    __tablename__ = "notification_targets"

    notification_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("notifications.id", ondelete="CASCADE"),
        primary_key=True,
    )
    shop_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("shops.id", ondelete="CASCADE"),
        primary_key=True,
    )


class NotificationRead(Base):
    """Per-user read receipt for a notification (composite PK like shop_owners).

    A row (notification_id, user_id) means that user has read it. RLS scopes a
    user to their own rows; the admin reads all rows to show read counts.
    """

    __tablename__ = "notification_reads"

    notification_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("notifications.id", ondelete="CASCADE"),
        primary_key=True,
    )
    user_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("users.id", ondelete="CASCADE"),
        primary_key=True,
    )
    read_at: Mapped[dt.datetime] = created_at_col()
