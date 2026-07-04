from __future__ import annotations

import datetime as dt
import uuid

from sqlalchemy import Text
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base, created_at_col, uuid_pk


class CrashReport(Base):
    """A crash report POSTed by the Android app (ACRA) to /crash-reports.

    Self-hosted crash reporting: nothing leaves the VPS. The full ACRA payload is
    kept in `report` (JSONB); the columns are the few fields we filter/sort on so
    the admin crash list stays fast without parsing JSON per row.
    """

    __tablename__ = "crash_reports"

    id: Mapped[uuid.UUID] = uuid_pk()
    # A few denormalized fields for listing/filtering; full payload in `report`.
    app_version: Mapped[str | None] = mapped_column(Text, nullable=True)
    android_version: Mapped[str | None] = mapped_column(Text, nullable=True)
    device: Mapped[str | None] = mapped_column(Text, nullable=True)
    installation_id: Mapped[str | None] = mapped_column(Text, nullable=True)
    stack_trace: Mapped[str | None] = mapped_column(Text, nullable=True)
    user_comment: Mapped[str | None] = mapped_column(Text, nullable=True)
    report: Mapped[dict] = mapped_column(JSONB, nullable=False, default=dict)
    created_at: Mapped[dt.datetime] = created_at_col()
