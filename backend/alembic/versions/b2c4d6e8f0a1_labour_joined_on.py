"""labourers.joined_on — the date a worker joined the shop

Revision ID: b2c4d6e8f0a1
Revises: a1b2c3d4e5f7
Create Date: 2026-08-26 00:00:00.000000+00:00

Managers want to see how long a worker has been with the shop, and the report's
attendance register needs it to explain why someone has no marks before a
mid-month date.

It defaults to the day the worker is added, but is a real editable column rather
than a read of created_at, because workers are routinely entered into the app
days or weeks after they actually started.

Existing rows are backfilled from created_at in Asia/Kolkata — the app's day
boundary everywhere else — so a worker added at 1am IST doesn't get yesterday's
date from the UTC timestamp.
"""
from typing import Sequence, Union

from alembic import op

revision: str = "b2c4d6e8f0a1"
down_revision: Union[str, None] = "a1b2c3d4e5f7"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    # `labourers` is under FORCE ROW LEVEL SECURITY, which applies to the
    # privileged migration role too. The DDL below bypasses RLS, but the backfill
    # is DML: without an admin GUC the policy matches zero rows, the UPDATE
    # silently does nothing, and SET NOT NULL then fails on the nulls it left.
    # Same trap (and same fix) as a1b2c3d4e5f6's role-rename UPDATE.
    op.execute("SELECT set_config('app.user_role', 'admin', true);")

    op.execute("ALTER TABLE labourers ADD COLUMN joined_on DATE;")
    op.execute(
        "UPDATE labourers SET joined_on = "
        "((created_at AT TIME ZONE 'UTC') AT TIME ZONE 'Asia/Kolkata')::date "
        "WHERE joined_on IS NULL;"
    )
    op.execute("ALTER TABLE labourers ALTER COLUMN joined_on SET DEFAULT CURRENT_DATE;")
    op.execute("ALTER TABLE labourers ALTER COLUMN joined_on SET NOT NULL;")


def downgrade() -> None:
    op.execute("ALTER TABLE labourers DROP COLUMN IF EXISTS joined_on;")
