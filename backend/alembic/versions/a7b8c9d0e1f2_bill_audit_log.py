"""bill_audit_log: record every bill edit and delete (for the sales report)

Revision ID: a7b8c9d0e1f2
Revises: f6a7b8c9d0e1
Create Date: 2026-07-05

Bills were previously mutable/deletable with no history — `is_edited` was a bare
boolean and a deleted bill vanished. The detailed sales report needs an edit &
delete log, so this table records each change going forward: who, when, what
(before/after for edits; a snapshot for deletes). `bill_id` has no FK so a delete
row survives after the bill is gone.

RLS mirrors the other tenant tables (admin / own shop / owner-via-shop_owners).
"""
from __future__ import annotations

from typing import Sequence, Union

from alembic import op

from app.config import get_settings

revision: str = "a7b8c9d0e1f2"
down_revision: Union[str, None] = "f6a7b8c9d0e1"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None

APP_DB_ROLE = get_settings().APP_DB_ROLE

_ROLE = "current_setting('app.user_role', true) = 'admin'"
_SHOP = "NULLIF(current_setting('app.current_shop_id', true), '')::uuid"
_OWNER_UID = "NULLIF(current_setting('app.current_user_id', true), '')::uuid"
_IS_OWNER = "current_setting('app.user_role', true) = 'owner'"
_OWNER_BY_SHOP = (
    f"({_IS_OWNER} AND shop_id IN "
    f"(SELECT so.shop_id FROM shop_owners so WHERE so.owner_id = {_OWNER_UID}))"
)
_POLICY = f"({_ROLE} OR shop_id = {_SHOP} OR {_OWNER_BY_SHOP})"


def upgrade() -> None:
    op.execute(
        """
        CREATE TABLE bill_audit_log (
            id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            shop_id          UUID NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
            bill_id          UUID,
            action           TEXT NOT NULL CHECK (action IN ('edit','delete')),
            changed_by       UUID REFERENCES users(id) ON DELETE SET NULL,
            changed_by_email TEXT,
            summary          TEXT,
            details          JSONB NOT NULL DEFAULT '{}'::jsonb,
            created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
        );
        """
    )
    op.execute("CREATE INDEX ix_bill_audit_log_shop_created ON bill_audit_log(shop_id, created_at);")

    op.execute("ALTER TABLE bill_audit_log ENABLE ROW LEVEL SECURITY;")
    op.execute("ALTER TABLE bill_audit_log FORCE ROW LEVEL SECURITY;")
    op.execute(
        f"""
        CREATE POLICY bill_audit_log_isolation ON bill_audit_log
            FOR ALL
            USING {_POLICY}
            WITH CHECK {_POLICY};
        """
    )

    role = f'"{APP_DB_ROLE}"'
    op.execute(f"GRANT SELECT, INSERT, UPDATE, DELETE ON bill_audit_log TO {role};")


def downgrade() -> None:
    role = f'"{APP_DB_ROLE}"'
    op.execute(f"REVOKE ALL ON bill_audit_log FROM {role};")
    op.execute("DROP TABLE IF EXISTS bill_audit_log CASCADE;")
