"""labour: labourers + labour payments

Revision ID: b9c3d4e5f6a7
Revises: a8b2c3d4e5f6
Create Date: 2026-07-12 02:00:00.000000+00:00

A shop's labour ledger:

- `labourers` — the worker profiles a manager sets up: name, optional phone,
  gender, a default daily wage and an overtime rate per hour. All editable.
- `labour_payments` — a record every time a worker is paid (by a manager OR a
  salesperson). The wage is pre-filled from the default but editable per payment;
  overtime = hours × the labourer's rate. The labourer's name + gender are
  denormalized onto each payment (like bill line items) so history never changes
  and survives the labourer being deleted. `created_at` is the time it was paid.

RLS mirrors the other tenant tables (admin all; shop members their shop; owners
their owned shops via the shop_owners join).
"""
from typing import Sequence, Union

from alembic import op
from app.config import get_settings

# revision identifiers, used by Alembic.
revision: str = 'b9c3d4e5f6a7'
down_revision: Union[str, None] = 'a8b2c3d4e5f6'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None

APP_DB_ROLE = get_settings().APP_DB_ROLE

_ROLE = "current_setting('app.user_role', true) = 'admin'"
_SHOP = "NULLIF(current_setting('app.current_shop_id', true), '')::uuid"
_OWNER_UID = "NULLIF(current_setting('app.current_user_id', true), '')::uuid"
_IS_OWNER = "current_setting('app.user_role', true) = 'owner'"
_OWNED_JOIN = f"(SELECT so.shop_id FROM shop_owners so WHERE so.owner_id = {_OWNER_UID})"
_OWNER_BRANCH = f" OR ({_IS_OWNER} AND shop_id IN {_OWNED_JOIN})"


def _enable_rls(table: str) -> None:
    op.execute(f"ALTER TABLE {table} ENABLE ROW LEVEL SECURITY;")
    op.execute(f"ALTER TABLE {table} FORCE ROW LEVEL SECURITY;")
    op.execute(
        f"""
        CREATE POLICY {table}_isolation ON {table}
            FOR ALL
            USING ({_ROLE} OR shop_id = {_SHOP}{_OWNER_BRANCH})
            WITH CHECK ({_ROLE} OR shop_id = {_SHOP}{_OWNER_BRANCH});
        """
    )
    role = f'"{APP_DB_ROLE}"'
    op.execute(f"GRANT SELECT, INSERT, UPDATE, DELETE ON {table} TO {role};")


def upgrade() -> None:
    op.execute(
        """
        CREATE TABLE labourers (
            id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            shop_id       UUID NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
            name          TEXT NOT NULL,
            phone         TEXT,
            gender        TEXT NOT NULL,
            default_wage  NUMERIC(12,2) NOT NULL DEFAULT 0,
            overtime_rate NUMERIC(12,2) NOT NULL DEFAULT 0,
            is_active     BOOLEAN NOT NULL DEFAULT true,
            created_by    UUID REFERENCES users(id) ON DELETE SET NULL,
            created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
            CONSTRAINT labourers_gender_check CHECK (gender IN ('male', 'female')),
            CONSTRAINT labourers_amounts_nonneg CHECK (default_wage >= 0 AND overtime_rate >= 0)
        );
        """
    )
    op.execute("CREATE INDEX ix_labourers_shop_id ON labourers(shop_id);")

    op.execute(
        """
        CREATE TABLE labour_payments (
            id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            shop_id         UUID NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
            labourer_id     UUID REFERENCES labourers(id) ON DELETE SET NULL,
            labourer_name   TEXT NOT NULL,
            gender          TEXT NOT NULL,
            wage_amount     NUMERIC(12,2) NOT NULL DEFAULT 0,
            overtime_hours  NUMERIC(12,2) NOT NULL DEFAULT 0,
            overtime_rate   NUMERIC(12,2) NOT NULL DEFAULT 0,
            overtime_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
            total_amount    NUMERIC(12,2) NOT NULL DEFAULT 0,
            note            TEXT,
            created_by      UUID REFERENCES users(id) ON DELETE SET NULL,
            created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
            CONSTRAINT labour_payments_gender_check CHECK (gender IN ('male', 'female')),
            CONSTRAINT labour_payments_amounts_nonneg
                CHECK (wage_amount >= 0 AND overtime_hours >= 0 AND overtime_amount >= 0 AND total_amount >= 0)
        );
        """
    )
    op.execute(
        "CREATE INDEX ix_labour_payments_shop_id_created_at ON labour_payments(shop_id, created_at);"
    )

    _enable_rls("labourers")
    _enable_rls("labour_payments")


def downgrade() -> None:
    role = f'"{APP_DB_ROLE}"'
    for table in ("labour_payments", "labourers"):
        op.execute(f"REVOKE ALL ON {table} FROM {role};")
        op.execute(f"DROP TABLE IF EXISTS {table} CASCADE;")
