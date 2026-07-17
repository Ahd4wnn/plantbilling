"""borrowings: money the shop borrowed from people (lenders ledger)

Revision ID: d1e2f3a4b5c6
Revises: c9d1e2f3a4b5
Create Date: 2026-07-17

A standalone ledger of money the shop has borrowed from other people (informal
lenders, friends, suppliers). Each entry records who it was borrowed from, how
much, how it was received (cash / UPI / split — informational), an optional
remark, and — once repaid — how it was paid back and when.

This is deliberately a plain debt note: it does NOT touch Cash in Hand or the
cash book, so it never entangles with daily reconciliation.

RLS mirrors the other tenant tables (admin all; shop members their shop; owners
their owned shops via the shop_owners join).
"""
from typing import Sequence, Union

from alembic import op
from app.config import get_settings

revision: str = "d1e2f3a4b5c6"
down_revision: Union[str, None] = "c9d1e2f3a4b5"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None

APP_DB_ROLE = get_settings().APP_DB_ROLE

_ROLE = "current_setting('app.user_role', true) = 'admin'"
_SHOP = "NULLIF(current_setting('app.current_shop_id', true), '')::uuid"
_OWNER_UID = "NULLIF(current_setting('app.current_user_id', true), '')::uuid"
_IS_OWNER = "current_setting('app.user_role', true) = 'owner'"
_OWNED_JOIN = f"(SELECT so.shop_id FROM shop_owners so WHERE so.owner_id = {_OWNER_UID})"
_OWNER_BRANCH = f" OR ({_IS_OWNER} AND shop_id IN {_OWNED_JOIN})"


def upgrade() -> None:
    op.execute(
        """
        CREATE TABLE borrowings (
            id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            shop_id          UUID NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
            lender_name      TEXT NOT NULL,
            lender_phone     TEXT,
            amount           NUMERIC(12,2) NOT NULL DEFAULT 0,
            cash_amount      NUMERIC(12,2) NOT NULL DEFAULT 0,
            upi_amount       NUMERIC(12,2) NOT NULL DEFAULT 0,
            remarks          TEXT,
            is_paid          BOOLEAN NOT NULL DEFAULT false,
            paid_cash_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
            paid_upi_amount  NUMERIC(12,2) NOT NULL DEFAULT 0,
            paid_at          TIMESTAMPTZ,
            created_by       UUID REFERENCES users(id) ON DELETE SET NULL,
            created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
            CONSTRAINT borrowings_amounts_nonneg CHECK (
                amount >= 0 AND cash_amount >= 0 AND upi_amount >= 0
                AND paid_cash_amount >= 0 AND paid_upi_amount >= 0
            )
        );
        """
    )
    op.execute("CREATE INDEX ix_borrowings_shop_id_is_paid ON borrowings(shop_id, is_paid);")

    op.execute("ALTER TABLE borrowings ENABLE ROW LEVEL SECURITY;")
    op.execute("ALTER TABLE borrowings FORCE ROW LEVEL SECURITY;")
    op.execute(
        f"""
        CREATE POLICY borrowings_isolation ON borrowings
            FOR ALL
            USING ({_ROLE} OR shop_id = {_SHOP}{_OWNER_BRANCH})
            WITH CHECK ({_ROLE} OR shop_id = {_SHOP}{_OWNER_BRANCH});
        """
    )
    role = f'"{APP_DB_ROLE}"'
    op.execute(f"GRANT SELECT, INSERT, UPDATE, DELETE ON borrowings TO {role};")


def downgrade() -> None:
    role = f'"{APP_DB_ROLE}"'
    op.execute(f"REVOKE ALL ON borrowings FROM {role};")
    op.execute("DROP TABLE IF EXISTS borrowings CASCADE;")
