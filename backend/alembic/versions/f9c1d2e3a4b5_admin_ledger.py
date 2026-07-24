"""admin_ledger — platform (Dofida) own sales & expenses (admin-only)

Two standalone, non-tenant tables for the platform operator's own books. No
shop_id; RLS restricts every row to the 'admin' role so no shop user can read
or write them.

Revision ID: f9c1d2e3a4b5
Revises: e2f3a4b5c6d7
Create Date: 2026-07-24 00:00:00.000000+00:00
"""
from typing import Sequence, Union

from alembic import op
from app.config import get_settings

# revision identifiers, used by Alembic.
revision: str = "f9c1d2e3a4b5"
down_revision: Union[str, None] = "e2f3a4b5c6d7"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None

APP_DB_ROLE = get_settings().APP_DB_ROLE
_ADMIN = "current_setting('app.user_role', true) = 'admin'"


def upgrade() -> None:
    role = f'"{APP_DB_ROLE}"'

    # ── admin_sales ─────────────────────────────────────────────────────────
    op.execute(
        """
        CREATE TABLE admin_sales (
            id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            title          TEXT NOT NULL,
            amount         NUMERIC(12,2) NOT NULL,
            cash_amount    NUMERIC(12,2) NOT NULL DEFAULT 0,
            upi_amount     NUMERIC(12,2) NOT NULL DEFAULT 0,
            due_amount     NUMERIC(12,2) NOT NULL DEFAULT 0,
            customer_name  TEXT,
            customer_phone TEXT,
            note           TEXT,
            occurred_on    DATE NOT NULL,
            created_by     UUID REFERENCES users(id) ON DELETE SET NULL,
            created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
            CONSTRAINT admin_sales_amount_positive CHECK (amount > 0),
            CONSTRAINT admin_sales_split_nonneg CHECK (
                cash_amount >= 0 AND upi_amount >= 0 AND due_amount >= 0
            ),
            CONSTRAINT admin_sales_split_sums CHECK (
                cash_amount + upi_amount + due_amount = amount
            )
        );
        """
    )
    op.execute("CREATE INDEX ix_admin_sales_occurred_on ON admin_sales(occurred_on);")
    op.execute(
        "CREATE INDEX ix_admin_sales_due ON admin_sales(occurred_on) WHERE due_amount > 0;"
    )
    op.execute("ALTER TABLE admin_sales ENABLE ROW LEVEL SECURITY;")
    op.execute("ALTER TABLE admin_sales FORCE ROW LEVEL SECURITY;")
    op.execute(
        f"""
        CREATE POLICY admin_sales_admin_only ON admin_sales
            FOR ALL
            USING ({_ADMIN})
            WITH CHECK ({_ADMIN});
        """
    )
    op.execute(f"GRANT SELECT, INSERT, UPDATE, DELETE ON admin_sales TO {role};")

    # ── admin_expenses ──────────────────────────────────────────────────────
    op.execute(
        """
        CREATE TABLE admin_expenses (
            id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            reason         TEXT NOT NULL,
            amount         NUMERIC(12,2) NOT NULL,
            payment_method TEXT NOT NULL DEFAULT 'cash',
            note           TEXT,
            occurred_on    DATE NOT NULL,
            created_by     UUID REFERENCES users(id) ON DELETE SET NULL,
            created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
            CONSTRAINT admin_expenses_amount_positive CHECK (amount > 0),
            CONSTRAINT admin_expenses_method CHECK (payment_method IN ('cash','upi'))
        );
        """
    )
    op.execute("CREATE INDEX ix_admin_expenses_occurred_on ON admin_expenses(occurred_on);")
    op.execute("ALTER TABLE admin_expenses ENABLE ROW LEVEL SECURITY;")
    op.execute("ALTER TABLE admin_expenses FORCE ROW LEVEL SECURITY;")
    op.execute(
        f"""
        CREATE POLICY admin_expenses_admin_only ON admin_expenses
            FOR ALL
            USING ({_ADMIN})
            WITH CHECK ({_ADMIN});
        """
    )
    op.execute(f"GRANT SELECT, INSERT, UPDATE, DELETE ON admin_expenses TO {role};")


def downgrade() -> None:
    role = f'"{APP_DB_ROLE}"'
    op.execute(f"REVOKE ALL ON admin_expenses FROM {role};")
    op.execute("DROP TABLE IF EXISTS admin_expenses CASCADE;")
    op.execute(f"REVOKE ALL ON admin_sales FROM {role};")
    op.execute("DROP TABLE IF EXISTS admin_sales CASCADE;")
