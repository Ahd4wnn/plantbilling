"""remember the bill NUMBER on each audit-log entry

The sales report's edit & delete log showed a UUID fragment ("3F9A2B1C") in its
bill column, because that is all the audit row ever held. Every other tab shows
the shop's own bill number (0042) since a1b2c3d4e5f7, so the log read as random
noise that nobody could match against a bill.

The number cannot simply be joined from `bills` at report time: `bill_id` has no
foreign key precisely so a DELETE entry outlives the bill it describes, and a
deleted bill's number would be unrecoverable. So it is copied onto the audit row
when the entry is written, and backfilled here for entries whose bill still
exists.

Entries for bills that were already deleted keep falling back to the UUID
fragment — the number is genuinely gone for those, and inventing one would be
worse than showing the fragment.

Revision ID: e5f7a9b1c3d4
Revises: d4e6f8a0b2c3
Create Date: 2026-09-04 00:00:00.000000+00:00
"""
from typing import Sequence, Union

from alembic import op


# revision identifiers, used by Alembic.
revision: str = "e5f7a9b1c3d4"
down_revision: Union[str, None] = "d4e6f8a0b2c3"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    # bill_audit_log and bills are both under FORCE ROW LEVEL SECURITY, which
    # applies to the table owner too — without this the UPDATE below silently
    # matches zero rows instead of failing.
    op.execute("SELECT set_config('app.user_role', 'admin', true);")

    op.execute("ALTER TABLE bill_audit_log ADD COLUMN bill_no INTEGER;")

    # Historical entries: recover the number wherever the bill is still there.
    # Account-deletion entries have a NULL bill_id and stay NULL, as do entries
    # for bills that were deleted or predate per-shop numbering.
    op.execute(
        """
        UPDATE bill_audit_log a
           SET bill_no = b.bill_seq
          FROM bills b
         WHERE b.id = a.bill_id
           AND b.bill_seq IS NOT NULL;
        """
    )


def downgrade() -> None:
    op.execute("ALTER TABLE bill_audit_log DROP COLUMN bill_no;")
