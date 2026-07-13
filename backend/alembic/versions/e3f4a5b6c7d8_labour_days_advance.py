"""labour: payment days + advance kind

Revision ID: e3f4a5b6c7d8
Revises: d2e3f4a5b6c7
Create Date: 2026-07-13 02:00:00.000000+00:00

- labour_payments.days: how many days' wage a payment covers (informational; a
  wage payment is usually wage_per_day × days, but the amount stays editable).
- kind gains 'advance' (money paid ahead of work). Balance to pay is now driven
  by attendance (wage_per_day × days worked) minus everything paid, so an advance
  simply counts as paid and pushes the balance negative.
"""
from typing import Sequence, Union

from alembic import op

revision: str = 'e3f4a5b6c7d8'
down_revision: Union[str, None] = 'd2e3f4a5b6c7'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.execute("ALTER TABLE labour_payments ADD COLUMN days NUMERIC(12,2);")
    op.execute("ALTER TABLE labour_payments DROP CONSTRAINT IF EXISTS labour_payments_kind_check;")
    op.execute(
        "ALTER TABLE labour_payments ADD CONSTRAINT labour_payments_kind_check "
        "CHECK (kind IN ('wage', 'advance', 'due_clear'));"
    )


def downgrade() -> None:
    op.execute("ALTER TABLE labour_payments DROP CONSTRAINT IF EXISTS labour_payments_kind_check;")
    op.execute("UPDATE labour_payments SET kind = 'wage' WHERE kind = 'advance';")
    op.execute(
        "ALTER TABLE labour_payments ADD CONSTRAINT labour_payments_kind_check "
        "CHECK (kind IN ('wage', 'due_clear'));"
    )
    op.execute("ALTER TABLE labour_payments DROP COLUMN IF EXISTS days;")
