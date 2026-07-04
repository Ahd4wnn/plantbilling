"""performance indexes for hot filter/join columns

Revision ID: c3d4e5f6a7b8
Revises: b2c3d4e5f6a7
Create Date: 2026-07-05

Adds indexes that back the query patterns that grow with data volume:

  * bills.created_by      -> per-salesperson summaries, reports, owner leaderboard
  * bills.customer_id     -> the customer join in the bill history list
  * bill_items.product_id -> the category grouping join to products in reports
  * bills (partial, due>0) -> the Dues screen ("only bills that still owe money")

Existing coverage (unchanged): bills(shop_id, created_at), bill_items(bill_id),
products(shop_id), customers(shop_id), expenses(shop_id, created_at).

All created CONCURRENTLY inside an autocommit block so they take no write lock on
the live table — safe to run against production with shops actively billing.
"""
from typing import Sequence, Union

from alembic import op

# revision identifiers, used by Alembic.
revision: str = "c3d4e5f6a7b8"
down_revision: Union[str, None] = "b2c3d4e5f6a7"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


_INDEXES = (
    ("ix_bills_created_by", "bills", "(created_by)", ""),
    ("ix_bills_customer_id", "bills", "(customer_id)", ""),
    ("ix_bill_items_product_id", "bill_items", "(product_id)", ""),
    # Dues screen filters shop's bills to those still owing; a partial index keeps
    # it tiny (only unpaid bills) and matches WHERE due_amount > 0.
    ("ix_bills_shop_due", "bills", "(shop_id)", "WHERE due_amount > 0"),
)


def upgrade() -> None:
    with op.get_context().autocommit_block():
        for name, table, cols, where in _INDEXES:
            op.execute(
                f"CREATE INDEX CONCURRENTLY IF NOT EXISTS {name} ON {table} {cols} {where};".strip()
            )


def downgrade() -> None:
    with op.get_context().autocommit_block():
        for name, _table, _cols, _where in _INDEXES:
            op.execute(f"DROP INDEX CONCURRENTLY IF EXISTS {name};")
