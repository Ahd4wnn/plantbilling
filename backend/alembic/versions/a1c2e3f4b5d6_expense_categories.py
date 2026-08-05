"""expense_categories + expenses.category_id/note

Adds a shop-scoped, manager-curated expense category list, and links expenses to
it (plus an optional free-text note). Purely additive: existing expenses keep
their free-text `reason` and a NULL `category_id`.

Revision ID: a1c2e3f4b5d6
Revises: b7d3f1a9c2e4
Create Date: 2026-08-05 00:00:00.000000+00:00
"""
from typing import Sequence, Union

from alembic import op
from app.config import get_settings

# revision identifiers, used by Alembic.
revision: str = "a1c2e3f4b5d6"
down_revision: Union[str, None] = "b7d3f1a9c2e4"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None

APP_DB_ROLE = get_settings().APP_DB_ROLE
_SHOP = "NULLIF(current_setting('app.current_shop_id', true), '')::uuid"
_ROLE = "current_setting('app.user_role', true) = 'admin'"


def upgrade() -> None:
    role = f'"{APP_DB_ROLE}"'

    # 1. expense_categories table (tenant table, RLS by shop_id)
    op.execute(
        """
        CREATE TABLE expense_categories (
            id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            shop_id    UUID NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
            name       TEXT NOT NULL,
            created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
            CONSTRAINT expense_categories_name_not_blank CHECK (length(btrim(name)) > 0)
        );
        """
    )
    # Case-insensitive uniqueness per shop, so "Petrol" and "petrol" don't split.
    op.execute(
        "CREATE UNIQUE INDEX ux_expense_categories_shop_name "
        "ON expense_categories (shop_id, lower(name));"
    )

    op.execute("ALTER TABLE expense_categories ENABLE ROW LEVEL SECURITY;")
    op.execute("ALTER TABLE expense_categories FORCE ROW LEVEL SECURITY;")
    op.execute(
        f"""
        CREATE POLICY expense_categories_isolation ON expense_categories
            FOR ALL
            USING ({_ROLE} OR shop_id = {_SHOP})
            WITH CHECK ({_ROLE} OR shop_id = {_SHOP});
        """
    )
    op.execute(
        f"GRANT SELECT, INSERT, UPDATE, DELETE ON expense_categories TO {role};"
    )

    # 2. Link expenses to a category + optional note. ON DELETE SET NULL so
    #    deleting a category never deletes its historical expenses.
    op.execute(
        """
        ALTER TABLE expenses
            ADD COLUMN category_id UUID
                REFERENCES expense_categories(id) ON DELETE SET NULL,
            ADD COLUMN note TEXT;
        """
    )
    op.execute(
        "CREATE INDEX ix_expenses_category_id ON expenses(category_id);"
    )


def downgrade() -> None:
    role = f'"{APP_DB_ROLE}"'
    op.execute("DROP INDEX IF EXISTS ix_expenses_category_id;")
    op.execute("ALTER TABLE expenses DROP COLUMN IF EXISTS note;")
    op.execute("ALTER TABLE expenses DROP COLUMN IF EXISTS category_id;")
    op.execute(f"REVOKE ALL ON expense_categories FROM {role};")
    op.execute("DROP TABLE IF EXISTS expense_categories CASCADE;")
