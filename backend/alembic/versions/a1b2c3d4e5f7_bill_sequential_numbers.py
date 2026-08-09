"""per-shop sequential bill numbers

Adds a human-facing, per-shop bill number. `bills.bill_seq` holds the number
(1, 2, 3 …) shown to shopkeepers as 0001, 0002 …; `shops.next_bill_seq` is the
per-shop counter allocated atomically at checkout. Existing bills are left NULL
(not backfilled) — they keep displaying their UUID fragment; every shop's next
new bill starts at 0001.

Revision ID: a1b2c3d4e5f7
Revises: c3e5a7b9d1f0
Create Date: 2026-08-10 00:00:00.000000+00:00
"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = "a1b2c3d4e5f7"
down_revision: Union[str, None] = "c3e5a7b9d1f0"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    # Nullable: existing bills stay NULL (no backfill) and fall back to the old
    # UUID-fragment display; only bills created after this ships get a number.
    op.add_column("bills", sa.Column("bill_seq", sa.Integer(), nullable=True))

    # Per-shop counter. DEFAULT 1 means every existing shop's next bill is 0001.
    op.add_column(
        "shops",
        sa.Column("next_bill_seq", sa.Integer(), nullable=False, server_default=sa.text("1")),
    )

    # Safety belt: a shop can never repeat a number. Partial, so the many NULL
    # legacy rows don't collide (each NULL is distinct anyway, but be explicit).
    op.create_index(
        "ux_bills_shop_seq",
        "bills",
        ["shop_id", "bill_seq"],
        unique=True,
        postgresql_where=sa.text("bill_seq IS NOT NULL"),
    )


def downgrade() -> None:
    op.drop_index("ux_bills_shop_seq", table_name="bills")
    op.drop_column("shops", "next_bill_seq")
    op.drop_column("bills", "bill_seq")
