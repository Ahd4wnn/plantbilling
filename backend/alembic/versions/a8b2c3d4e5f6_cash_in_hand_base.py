"""cumulative cash-in-hand baseline per shop

Revision ID: a8b2c3d4e5f6
Revises: f7a1c2b3d4e5
Create Date: 2026-07-12 01:00:00.000000+00:00

Adds `shops.cash_in_hand_base` — a single offset so the app can show a *running*
cash-in-hand that carries over day to day (opt-in per device).

Running cash in hand as of day D = cash_in_hand_base
    + Σ(bill.cash_amount) − Σ(cash expense.amount), over everything up to end of D.

- Admin sets the opening amount when creating a shop → stored straight into base
  (there are no flows yet, so running == opening on day one).
- A manager "setting the running total to X now" stores
  base := X − (all cash flows through today), so the number reads X immediately
  and keeps accumulating afterwards.

Default 0 preserves today's behaviour for existing shops (running == all cash
recorded since they started billing).
"""
from typing import Sequence, Union

from alembic import op

# revision identifiers, used by Alembic.
revision: str = 'a8b2c3d4e5f6'
down_revision: Union[str, None] = 'f7a1c2b3d4e5'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.execute(
        "ALTER TABLE shops ADD COLUMN cash_in_hand_base NUMERIC(12,2) NOT NULL DEFAULT 0;"
    )


def downgrade() -> None:
    op.execute("ALTER TABLE shops DROP COLUMN IF EXISTS cash_in_hand_base;")
