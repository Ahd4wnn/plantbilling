"""labour payment methods (cash/upi/due) + kind + attendance

Revision ID: c1d2e3f4a5b6
Revises: b9c3d4e5f6a7
Create Date: 2026-07-12 03:00:00.000000+00:00

- labour_payments gains a cash/UPI/due split (like bills) so a worker can be paid
  by cash, UPI, split, or left as due (owed). `kind` distinguishes a normal wage
  payment from a 'due_clear' (paying off what's owed). Existing rows were all cash,
  so cash_amount is backfilled to total_amount.
- labour_attendance: one row per worker per day — present/absent/half-day plus
  overtime hours — markable by any shop staff.
"""
from typing import Sequence, Union

from alembic import op
from app.config import get_settings

revision: str = 'c1d2e3f4a5b6'
down_revision: Union[str, None] = 'b9c3d4e5f6a7'
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
    # 1. Payment split + kind.
    op.execute(
        """
        ALTER TABLE labour_payments
            ADD COLUMN cash_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
            ADD COLUMN upi_amount  NUMERIC(12,2) NOT NULL DEFAULT 0,
            ADD COLUMN due_amount  NUMERIC(12,2) NOT NULL DEFAULT 0,
            ADD COLUMN kind        TEXT NOT NULL DEFAULT 'wage';
        """
    )
    # Existing payments were paid entirely in cash.
    op.execute("UPDATE labour_payments SET cash_amount = total_amount WHERE kind = 'wage';")
    op.execute(
        "ALTER TABLE labour_payments ADD CONSTRAINT labour_payments_kind_check "
        "CHECK (kind IN ('wage', 'due_clear'));"
    )

    # 2. Attendance.
    op.execute(
        """
        CREATE TABLE labour_attendance (
            id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            shop_id        UUID NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
            labourer_id    UUID NOT NULL REFERENCES labourers(id) ON DELETE CASCADE,
            day            DATE NOT NULL,
            status         TEXT NOT NULL,
            overtime_hours NUMERIC(12,2) NOT NULL DEFAULT 0,
            created_by     UUID REFERENCES users(id) ON DELETE SET NULL,
            created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
            updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
            CONSTRAINT labour_attendance_status_check CHECK (status IN ('present', 'absent', 'half_day')),
            CONSTRAINT labour_attendance_unique UNIQUE (labourer_id, day)
        );
        """
    )
    op.execute("CREATE INDEX ix_labour_attendance_shop_day ON labour_attendance(shop_id, day);")

    op.execute("ALTER TABLE labour_attendance ENABLE ROW LEVEL SECURITY;")
    op.execute("ALTER TABLE labour_attendance FORCE ROW LEVEL SECURITY;")
    op.execute(
        f"""
        CREATE POLICY labour_attendance_isolation ON labour_attendance
            FOR ALL
            USING ({_ROLE} OR shop_id = {_SHOP}{_OWNER_BRANCH})
            WITH CHECK ({_ROLE} OR shop_id = {_SHOP}{_OWNER_BRANCH});
        """
    )
    role = f'"{APP_DB_ROLE}"'
    op.execute(f"GRANT SELECT, INSERT, UPDATE, DELETE ON labour_attendance TO {role};")


def downgrade() -> None:
    role = f'"{APP_DB_ROLE}"'
    op.execute(f"REVOKE ALL ON labour_attendance FROM {role};")
    op.execute("DROP TABLE IF EXISTS labour_attendance CASCADE;")
    op.execute("ALTER TABLE labour_payments DROP CONSTRAINT IF EXISTS labour_payments_kind_check;")
    op.execute(
        """
        ALTER TABLE labour_payments
            DROP COLUMN IF EXISTS cash_amount,
            DROP COLUMN IF EXISTS upi_amount,
            DROP COLUMN IF EXISTS due_amount,
            DROP COLUMN IF EXISTS kind;
        """
    )
