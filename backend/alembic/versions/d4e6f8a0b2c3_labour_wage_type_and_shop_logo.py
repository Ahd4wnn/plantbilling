"""Monthly-wage workers, and a per-shop logo for printed bills

Revision ID: d4e6f8a0b2c3
Revises: c3d5e7f9a1b2
Create Date: 2026-08-29 00:00:00.000000+00:00

Two additive changes that happen to land together.

**labourers.wage_type / monthly_wage / paid_leaves_per_month.** Until now every
worker was paid by the day: `default_wage` x days marked present. Real shops also
keep workers on a monthly salary, where attendance drives *deductions* rather than
earnings — a month is paid in full unless leaves exceed the worker's paid-leave
allowance. Rather than overload `default_wage`, monthly pay gets its own column so
each mode reads exactly what it means and neither can be silently misread as the
other. `default_wage` keeps its meaning: rupees per day, used only when
wage_type = 'daily'.

Everything existing defaults to 'daily' with a zero monthly wage, so no current
worker's pay changes by a rupee when this runs.

**shops.logo_path.** Mirrors `products.photo_path` — the relative path under
MEDIA_ROOT, never a URL, so moving the media directory or changing the public
hostname doesn't rewrite rows. Only the platform admin can set it. Whether the
logo actually prints is a separate on/off switch that lives in `shops.settings`
JSONB alongside the whatsapp_* flags, so a shop can be turned off without losing
the uploaded file.

All DDL, no DML. The set_config below is therefore not strictly required — DDL
runs as the table owner and bypasses the policy — but it stays as the standing
convention for this codebase: both tables are under FORCE ROW LEVEL SECURITY, and
any UPDATE added to this file later would otherwise match zero rows and fail
silently. See b2c4d6e8f0a1 and a1b2c3d4e5f6 for that trap actually biting.
"""
from typing import Sequence, Union

from alembic import op

revision: str = "d4e6f8a0b2c3"
down_revision: Union[str, None] = "c3d5e7f9a1b2"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.execute("SELECT set_config('app.user_role', 'admin', true);")

    op.execute(
        """
        ALTER TABLE labourers
            ADD COLUMN wage_type TEXT NOT NULL DEFAULT 'daily',
            ADD COLUMN monthly_wage NUMERIC(12,2) NOT NULL DEFAULT 0,
            ADD COLUMN paid_leaves_per_month INTEGER NOT NULL DEFAULT 0;
        """
    )
    op.execute(
        "ALTER TABLE labourers ADD CONSTRAINT labourers_wage_type_check "
        "CHECK (wage_type IN ('daily', 'monthly'));"
    )
    # b9c3d4e5f6a7 created labourers_amounts_nonneg over (default_wage, overtime_rate);
    # Postgres dropped that constraint outright when d2e3f4a5b6c7 removed
    # overtime_rate, so default_wage has been unguarded since. Cover all three here.
    op.execute(
        """
        ALTER TABLE labourers ADD CONSTRAINT labourers_wage_nonneg
            CHECK (
                default_wage >= 0
                AND monthly_wage >= 0
                AND paid_leaves_per_month >= 0
                AND paid_leaves_per_month <= 31
            );
        """
    )

    op.execute("ALTER TABLE shops ADD COLUMN logo_path TEXT;")


def downgrade() -> None:
    op.execute("ALTER TABLE shops DROP COLUMN IF EXISTS logo_path;")
    op.execute("ALTER TABLE labourers DROP CONSTRAINT IF EXISTS labourers_wage_nonneg;")
    op.execute("ALTER TABLE labourers DROP CONSTRAINT IF EXISTS labourers_wage_type_check;")
    op.execute(
        """
        ALTER TABLE labourers
            DROP COLUMN IF EXISTS paid_leaves_per_month,
            DROP COLUMN IF EXISTS monthly_wage,
            DROP COLUMN IF EXISTS wage_type;
        """
    )
