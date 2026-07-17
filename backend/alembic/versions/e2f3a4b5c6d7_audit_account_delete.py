"""allow bill_audit_log.action = 'account_delete' (log staff-account removals)

Revision ID: e2f3a4b5c6d7
Revises: d1e2f3a4b5c6
Create Date: 2026-07-17

The bill_audit_log powers the sales report's edit/delete log. We now also record
staff-account deletions (a manager removing a salesperson, or an owner removing
staff) so they show up in the report. That needs the action CHECK to accept a
third value. The table is otherwise a perfect fit (nullable bill_id, shop_id,
actor, summary/details), so no columns change.
"""
from __future__ import annotations

from typing import Sequence, Union

from alembic import op

revision: str = "e2f3a4b5c6d7"
down_revision: Union[str, None] = "d1e2f3a4b5c6"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.execute("ALTER TABLE bill_audit_log DROP CONSTRAINT IF EXISTS bill_audit_log_action_check;")
    op.execute(
        "ALTER TABLE bill_audit_log ADD CONSTRAINT bill_audit_log_action_check "
        "CHECK (action IN ('edit','delete','account_delete'));"
    )


def downgrade() -> None:
    # Drop any account_delete rows first so the stricter constraint can be re-added.
    op.execute("DELETE FROM bill_audit_log WHERE action = 'account_delete';")
    op.execute("ALTER TABLE bill_audit_log DROP CONSTRAINT IF EXISTS bill_audit_log_action_check;")
    op.execute(
        "ALTER TABLE bill_audit_log ADD CONSTRAINT bill_audit_log_action_check "
        "CHECK (action IN ('edit','delete'));"
    )
