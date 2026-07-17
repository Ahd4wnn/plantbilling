"""denormalize the biller email onto bills (survives salesperson deletion)

Revision ID: c9d1e2f3a4b5
Revises: f8b1c2d3e4f5
Create Date: 2026-07-17

`bills.created_by` is a FK to users with ON DELETE SET NULL, so hard-deleting a
salesperson nulled `created_by` on every bill they ever made -> the "who billed"
line showed "Unknown" everywhere (owner/admin views, receipts, reports).

Following the same denormalization the codebase already uses for bill line items
and labour payments ("history never changes and survives the record being
deleted"), we snapshot the biller's email onto each bill at sale time. This new
column is the source of truth for display; a live user lookup is only a fallback.

Backfill copies the current email for every bill whose creator still exists.
Bills whose salesperson was already deleted stay NULL (that email is gone).
"""
from __future__ import annotations

from typing import Sequence, Union

from alembic import op

revision: str = "c9d1e2f3a4b5"
down_revision: Union[str, None] = "f8b1c2d3e4f5"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.execute("ALTER TABLE bills ADD COLUMN created_by_email TEXT NULL;")
    # Backfill under the admin RLS context so every shop's rows are visible.
    op.execute("SELECT set_config('app.user_role', 'admin', true);")
    op.execute(
        """
        UPDATE bills b
           SET created_by_email = u.email
          FROM users u
         WHERE u.id = b.created_by
           AND b.created_by_email IS NULL;
        """
    )


def downgrade() -> None:
    op.execute("ALTER TABLE bills DROP COLUMN IF EXISTS created_by_email;")
