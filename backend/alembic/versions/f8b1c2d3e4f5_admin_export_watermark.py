"""admin customer-export watermark

Revision ID: f8b1c2d3e4f5
Revises: e3f4a5b6c7d8
Create Date: 2026-07-15 00:00:00.000000+00:00

Adds `users.customers_exported_at` — a per-admin timestamp remembering when the
admin last downloaded the cross-shop customer directory. The export defaults to
"only customers created since this watermark", so repeated downloads never
re-emit rows already exported. Advanced to `now()` on each successful export.

NULL means "never exported" → the first export includes everything.
"""
from typing import Sequence, Union

from alembic import op

# revision identifiers, used by Alembic.
revision: str = 'f8b1c2d3e4f5'
down_revision: Union[str, None] = 'e3f4a5b6c7d8'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.execute(
        "ALTER TABLE users ADD COLUMN customers_exported_at TIMESTAMPTZ NULL;"
    )


def downgrade() -> None:
    op.execute("ALTER TABLE users DROP COLUMN IF EXISTS customers_exported_at;")
