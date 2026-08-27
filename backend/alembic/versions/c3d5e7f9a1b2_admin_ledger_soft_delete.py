"""admin ledger soft delete — deleted_at / deleted_by on admin_sales & admin_expenses

Revision ID: c3d5e7f9a1b2
Revises: b2c4d6e8f0a1
Create Date: 2026-08-27 00:00:00.000000+00:00

An admin reported that the Sales & Expenses page was "deleting" entries. The
entries turned out to be hidden by the UI's 30-day window rather than removed —
but the investigation showed that if anything HAD been deleted there would be no
way to know: `DELETE /admin/ledger/...` was a hard `DELETE FROM` behind a single
browser confirm, with no record of who removed what.

These are the platform's own books. A mis-tap on a trash icon should not
permanently destroy a money record, so deletes become reversible: the row stays,
`deleted_at` hides it from every read, and an admin can restore it.

Purely additive — the running app ignores the new columns, so the deploy is safe
in either order.
"""
from typing import Sequence, Union

from alembic import op

revision: str = "c3d5e7f9a1b2"
down_revision: Union[str, None] = "b2c4d6e8f0a1"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    # Both tables are under FORCE ROW LEVEL SECURITY, which applies to the
    # migration role too. The DDL below bypasses RLS so this isn't strictly
    # needed today, but any DML added here later would silently match zero rows
    # without it — see b2c4d6e8f0a1 and a1b2c3d4e5f6 for that trap biting.
    op.execute("SELECT set_config('app.user_role', 'admin', true);")

    for table in ("admin_sales", "admin_expenses"):
        op.execute(
            f"""
            ALTER TABLE {table}
                ADD COLUMN deleted_at TIMESTAMPTZ,
                ADD COLUMN deleted_by UUID REFERENCES users(id) ON DELETE SET NULL;
            """
        )
        # Partial index: every list and every aggregate filters on
        # `deleted_at IS NULL`, and deleted rows are a rounding error by count,
        # so the live-rows-only index is what the planner actually wants.
        op.execute(
            f"CREATE INDEX ix_{table}_live ON {table}(occurred_on) "
            "WHERE deleted_at IS NULL;"
        )


def downgrade() -> None:
    for table in ("admin_sales", "admin_expenses"):
        op.execute(f"DROP INDEX IF EXISTS ix_{table}_live;")
        op.execute(
            f"ALTER TABLE {table} "
            "DROP COLUMN IF EXISTS deleted_by, "
            "DROP COLUMN IF EXISTS deleted_at;"
        )
