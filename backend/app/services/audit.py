"""Shared audit-log writes that are not bill-specific.

Kept separate from app.routers.bills (which pulls in reportlab) so lightweight
routers like shop_users can record audit entries without that import weight.
Account deletions land in bill_audit_log with action='account_delete' and a NULL
bill_id, so they appear in the sales report's edit/delete log alongside bill edits.
"""
from __future__ import annotations

import uuid

from sqlalchemy.orm import Session

from app.models.bill_audit import BillAuditLog
from app.models.user import User


def record_account_deletion(
    db: Session,
    *,
    shop_id: uuid.UUID,
    actor: User,
    target_email: str | None,
    target_role: str,
) -> None:
    """Append an account-deletion entry to the audit log for `shop_id`.

    Written inside the request transaction, so it commits atomically with the
    delete. RLS on bill_audit_log allows admin / the row's shop members / owners
    of the shop, matching who is allowed to perform the deletion.
    """
    label = target_email or "(unknown)"
    db.add(
        BillAuditLog(
            shop_id=shop_id,
            bill_id=None,
            action="account_delete",
            changed_by=actor.id,
            changed_by_email=actor.email,
            summary=f"Deleted {target_role} account {label}",
            details={"target_email": target_email, "target_role": target_role},
        )
    )
