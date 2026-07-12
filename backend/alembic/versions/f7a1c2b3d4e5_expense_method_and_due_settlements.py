"""expense payment method + due settlement approval workflow

Revision ID: f7a1c2b3d4e5
Revises: a7b8c9d0e1f2
Create Date: 2026-07-12 00:00:00.000000+00:00

Two additions:

1. `expenses.payment_method` ('cash' | 'upi', default 'cash'). Existing rows
   were implicitly paid from cash (that's how "Cash in Hand" was computed), so
   the backfill/default of 'cash' preserves today's numbers exactly.

2. `due_settlements` — the manager-approval queue for collecting a due. A
   salesperson's collection creates a PENDING row (money is NOT moved onto the
   bill yet); a manager/admin approving it applies the cash/UPI split to the
   bill and closes the due. A manager/admin collecting a due directly writes an
   already-'approved' row for the audit trail. At most one pending settlement
   may exist per bill (partial unique index), so a due can't be double-collected.

RLS mirrors the other tenant tables' owner-aware policy (admin sees all; a
member of the shop sees their shop; an owner sees shops they own via the
`shop_owners` join table).
"""
from typing import Sequence, Union

from alembic import op
from app.config import get_settings

# revision identifiers, used by Alembic.
revision: str = 'f7a1c2b3d4e5'
down_revision: Union[str, None] = 'a7b8c9d0e1f2'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None

APP_DB_ROLE = get_settings().APP_DB_ROLE

# RLS fragments — identical style to the multi-owner migration so the owner
# branch keeps working through the shop_owners join table.
_ROLE = "current_setting('app.user_role', true) = 'admin'"
_SHOP = "NULLIF(current_setting('app.current_shop_id', true), '')::uuid"
_OWNER_UID = "NULLIF(current_setting('app.current_user_id', true), '')::uuid"
_IS_OWNER = "current_setting('app.user_role', true) = 'owner'"
_OWNED_JOIN = f"(SELECT so.shop_id FROM shop_owners so WHERE so.owner_id = {_OWNER_UID})"
_OWNER_BRANCH = f" OR ({_IS_OWNER} AND shop_id IN {_OWNED_JOIN})"


def upgrade() -> None:
    # 1. Expense payment method.
    op.execute(
        "ALTER TABLE expenses ADD COLUMN payment_method TEXT NOT NULL DEFAULT 'cash';"
    )
    op.execute(
        "ALTER TABLE expenses ADD CONSTRAINT expenses_payment_method_check "
        "CHECK (payment_method IN ('cash', 'upi'));"
    )

    # 2. Due settlement queue.
    op.execute(
        """
        CREATE TABLE due_settlements (
            id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            shop_id      UUID NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
            bill_id      UUID NOT NULL REFERENCES bills(id) ON DELETE CASCADE,
            cash_amount  NUMERIC(12,2) NOT NULL DEFAULT 0,
            upi_amount   NUMERIC(12,2) NOT NULL DEFAULT 0,
            status       TEXT NOT NULL DEFAULT 'pending',
            requested_by UUID REFERENCES users(id) ON DELETE SET NULL,
            reviewed_by  UUID REFERENCES users(id) ON DELETE SET NULL,
            created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
            reviewed_at  TIMESTAMPTZ,
            CONSTRAINT due_settlements_status_check
                CHECK (status IN ('pending', 'approved', 'rejected')),
            CONSTRAINT due_settlements_amounts_nonneg
                CHECK (cash_amount >= 0 AND upi_amount >= 0)
        );
        """
    )
    op.execute(
        "CREATE INDEX ix_due_settlements_shop_status ON due_settlements(shop_id, status);"
    )
    op.execute("CREATE INDEX ix_due_settlements_bill_id ON due_settlements(bill_id);")
    # At most one PENDING request per bill — stops a due being double-collected.
    op.execute(
        "CREATE UNIQUE INDEX ux_due_settlements_one_pending_per_bill "
        "ON due_settlements(bill_id) WHERE status = 'pending';"
    )

    op.execute("ALTER TABLE due_settlements ENABLE ROW LEVEL SECURITY;")
    op.execute("ALTER TABLE due_settlements FORCE ROW LEVEL SECURITY;")
    op.execute(
        f"""
        CREATE POLICY due_settlements_isolation ON due_settlements
            FOR ALL
            USING ({_ROLE} OR shop_id = {_SHOP}{_OWNER_BRANCH})
            WITH CHECK ({_ROLE} OR shop_id = {_SHOP}{_OWNER_BRANCH});
        """
    )

    role = f'"{APP_DB_ROLE}"'
    op.execute(f"GRANT SELECT, INSERT, UPDATE, DELETE ON due_settlements TO {role};")


def downgrade() -> None:
    role = f'"{APP_DB_ROLE}"'
    op.execute(f"REVOKE ALL ON due_settlements FROM {role};")
    op.execute("DROP TABLE IF EXISTS due_settlements CASCADE;")
    op.execute(
        "ALTER TABLE expenses DROP CONSTRAINT IF EXISTS expenses_payment_method_check;"
    )
    op.execute("ALTER TABLE expenses DROP COLUMN IF EXISTS payment_method;")
