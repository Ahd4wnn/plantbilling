"""Shop-facing notifications feed.

The shop app (managers + salespersons) fetches admin-authored notifications it is
allowed to see (broadcasts + those targeted at its shop — enforced by RLS) and
marks them read per user. Visibility is entirely RLS-driven, so there is no manual
shop_id filtering here.
"""
from __future__ import annotations

import uuid

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import func, select
from sqlalchemy.dialects.postgresql import insert as pg_insert
from sqlalchemy.orm import Session

from app.auth.dependencies import get_db, require_shop_staff
from app.models.notification import Notification, NotificationRead
from app.models.user import User
from app.schemas.notification import NotificationFeedOut, NotificationOut

router = APIRouter(prefix="/notifications", tags=["notifications"])


@router.get("", response_model=NotificationFeedOut)
def list_feed(
    db: Session = Depends(get_db),
    current: User = Depends(require_shop_staff),
    limit: int = Query(default=100, ge=1, le=200),
) -> NotificationFeedOut:
    """Notifications visible to this user, newest first, with per-user read state."""
    # LEFT JOIN this user's read receipts (RLS already scopes reads to this user).
    rows = db.execute(
        select(Notification, NotificationRead.notification_id)
        .outerjoin(
            NotificationRead,
            (NotificationRead.notification_id == Notification.id)
            & (NotificationRead.user_id == current.id),
        )
        .order_by(Notification.created_at.desc())
        .limit(limit)
    ).all()

    items = [
        NotificationOut(
            id=n.id,
            title=n.title,
            body=n.body,
            action_url=n.action_url,
            read=read_id is not None,
            created_at=n.created_at,
        )
        for n, read_id in rows
    ]

    # Unread across ALL visible notifications (not just this page).
    unread_count = db.execute(
        select(func.count())
        .select_from(Notification)
        .outerjoin(
            NotificationRead,
            (NotificationRead.notification_id == Notification.id)
            & (NotificationRead.user_id == current.id),
        )
        .where(NotificationRead.notification_id.is_(None))
    ).scalar_one()

    return NotificationFeedOut(items=items, unread_count=int(unread_count))


@router.post("/{notification_id}/read", status_code=status.HTTP_200_OK)
def mark_read(
    notification_id: uuid.UUID,
    db: Session = Depends(get_db),
    current: User = Depends(require_shop_staff),
) -> dict[str, str]:
    """Mark a notification read for the current user (idempotent)."""
    # RLS ensures only a visible notification is returned here.
    visible = db.execute(
        select(Notification.id).where(Notification.id == notification_id)
    ).scalar_one_or_none()
    if visible is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Notification not found")

    db.execute(
        pg_insert(NotificationRead)
        .values(notification_id=notification_id, user_id=current.id)
        .on_conflict_do_nothing(index_elements=["notification_id", "user_id"])
    )
    db.flush()
    return {"status": "read"}
