"""labour: add optional aadhaar, drop overtime everywhere

Revision ID: d2e3f4a5b6c7
Revises: c1d2e3f4a5b6
Create Date: 2026-07-13 00:00:00.000000+00:00

- labourers gain an optional `aadhaar` (Aadhaar number, free text — optional).
- Overtime is removed from the whole labour feature:
    * labourers.overtime_rate
    * labour_payments.overtime_hours / overtime_rate / overtime_amount
    * labour_attendance.overtime_hours
  Existing payments keep their stored `total_amount` (which already baked in any
  past overtime), so historical records are unchanged — only the columns go away.
"""
from typing import Sequence, Union

from alembic import op

revision: str = 'd2e3f4a5b6c7'
down_revision: Union[str, None] = 'c1d2e3f4a5b6'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.execute("ALTER TABLE labourers ADD COLUMN aadhaar TEXT;")
    op.execute("ALTER TABLE labourers DROP COLUMN IF EXISTS overtime_rate;")
    op.execute(
        """
        ALTER TABLE labour_payments
            DROP COLUMN IF EXISTS overtime_hours,
            DROP COLUMN IF EXISTS overtime_rate,
            DROP COLUMN IF EXISTS overtime_amount;
        """
    )
    op.execute("ALTER TABLE labour_attendance DROP COLUMN IF EXISTS overtime_hours;")


def downgrade() -> None:
    op.execute(
        "ALTER TABLE labour_attendance "
        "ADD COLUMN overtime_hours NUMERIC(12,2) NOT NULL DEFAULT 0;"
    )
    op.execute(
        """
        ALTER TABLE labour_payments
            ADD COLUMN overtime_hours  NUMERIC(12,2) NOT NULL DEFAULT 0,
            ADD COLUMN overtime_rate   NUMERIC(12,2) NOT NULL DEFAULT 0,
            ADD COLUMN overtime_amount NUMERIC(12,2) NOT NULL DEFAULT 0;
        """
    )
    op.execute(
        "ALTER TABLE labourers "
        "ADD COLUMN overtime_rate NUMERIC(12,2) NOT NULL DEFAULT 0;"
    )
    op.execute("ALTER TABLE labourers DROP COLUMN IF EXISTS aadhaar;")
