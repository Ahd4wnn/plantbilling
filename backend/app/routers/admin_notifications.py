"""Admin-only notification composer.

The platform admin composes notifications and targets all shops or specific
shops; the shop app fetches them via /notifications (see routers/notifications.py).
Every route here requires the admin (admin RLS context), so writes to the global
notifications tables are permitted by their admin policy.
"""
from __future__ import annotations

import uuid

from fastapi import APIRouter, Depends, HTTPException, Query, Response, status
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.auth.dependencies import get_db, require_admin
from app.models.notification import (
    Notification,
    NotificationRead,
    NotificationTarget,
)
from app.models.shop import Shop
from app.models.user import User
from app.schemas.notification import (
    AdminNotificationListOut,
    AdminNotificationOut,
    NotificationCreate,
)

router = APIRouter(
    prefix="/admin/notifications",
    tags=["admin-notifications"],
    dependencies=[Depends(require_admin)],
)


@router.post("", response_model=AdminNotificationOut, status_code=status.HTTP_201_CREATED)
def create_notification(
    payload: NotificationCreate,
    db: Session = Depends(get_db),
    admin: User = Depends(require_admin),
) -> AdminNotificationOut:
    """Compose and send a notification to all shops or specific shops."""
    if payload.target == "shops":
        # Validate every target shop exists (admin sees all shops via RLS).
        found = set(
            db.execute(
                select(Shop.id).where(Shop.id.in_(payload.shop_ids))
            ).scalars().all()
        )
        missing = [str(s) for s in payload.shop_ids if s not in found]
        if missing:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail=f"Unknown shop(s): {', '.join(missing)}",
            )

    notif = Notification(
        title=payload.title,
        body=payload.body,
        action_url=payload.action_url,
        target=payload.target,
        created_by=admin.id,
    )
    db.add(notif)
    db.flush()  # assign notif.id

    if payload.target == "shops":
        for sid in payload.shop_ids:
            db.add(NotificationTarget(notification_id=notif.id, shop_id=sid))
        db.flush()

    db.refresh(notif)
    return AdminNotificationOut(
        id=notif.id,
        title=notif.title,
        body=notif.body,
        action_url=notif.action_url,
        target=notif.target,
        shop_count=len(payload.shop_ids),
        read_count=0,
        created_at=notif.created_at,
    )


@router.get("", response_model=AdminNotificationListOut)
def list_notifications(
    db: Session = Depends(get_db),
    limit: int = Query(default=50, ge=1, le=200),
    offset: int = Query(default=0, ge=0),
) -> AdminNotificationListOut:
    """Sent history, newest first, with per-notification shop + read counts."""
    rows = db.execute(
        select(Notification)
        .order_by(Notification.created_at.desc())
        .limit(limit + 1)
        .offset(offset)
    ).scalars().all()
    has_more = len(rows) > limit
    page = rows[:limit]
    ids = [n.id for n in page]

    read_counts: dict[uuid.UUID, int] = {}
    shop_counts: dict[uuid.UUID, int] = {}
    if ids:
        read_counts = dict(
            db.execute(
                select(NotificationRead.notification_id, func.count())
                .where(NotificationRead.notification_id.in_(ids))
                .group_by(NotificationRead.notification_id)
            ).all()
        )
        shop_counts = dict(
            db.execute(
                select(NotificationTarget.notification_id, func.count())
                .where(NotificationTarget.notification_id.in_(ids))
                .group_by(NotificationTarget.notification_id)
            ).all()
        )

    items = [
        AdminNotificationOut(
            id=n.id,
            title=n.title,
            body=n.body,
            action_url=n.action_url,
            target=n.target,
            shop_count=shop_counts.get(n.id, 0),
            read_count=read_counts.get(n.id, 0),
            created_at=n.created_at,
        )
        for n in page
    ]
    return AdminNotificationListOut(items=items, limit=limit, offset=offset, has_more=has_more)


@router.delete("/{notification_id}", status_code=status.HTTP_204_NO_CONTENT, response_class=Response)
def delete_notification(
    notification_id: uuid.UUID,
    db: Session = Depends(get_db),
):
    """Revoke a notification (removes it for everyone; CASCADE clears targets + reads)."""
    notif = db.execute(
        select(Notification).where(Notification.id == notification_id)
    ).scalar_one_or_none()
    if notif is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Notification not found")
    db.delete(notif)
    db.flush()
