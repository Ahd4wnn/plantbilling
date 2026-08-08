"""expense_categories RLS: add the multi-shop owner branch

The original expense_categories policy (in a1c2e3f4b5d6) only allowed the admin
role and the current-shop manager/salesperson (shop_id = app.current_shop_id). It
MISSED the owner branch that every other tenant table got in e5f6a7b8c9d0 (owner
reaches shops via the shop_owners join).

Consequence: when a multi-shop owner opened a shop's report/overview, the report
generator loaded a category-backed expense's `category_name` — a join into
expense_categories — which RLS hid from the owner, yielding NULL. The per-category
total then failed validation (category is a non-null str), 500-ing the whole
overview whenever a category expense existed. This aligns the policy with
`expenses` so the owner can read categories for shops they own.

Revision ID: c3e5a7b9d1f0
Revises: a1c2e3f4b5d6
Create Date: 2026-08-08 00:00:00.000000+00:00
"""
from typing import Sequence, Union

from alembic import op
from app.config import get_settings

# revision identifiers, used by Alembic.
revision: str = "c3e5a7b9d1f0"
down_revision: Union[str, None] = "a1c2e3f4b5d6"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None

# Mirror the predicate shape used for the other tenant tables (e5f6a7b8c9d0).
_ROLE = "current_setting('app.user_role', true) = 'admin'"
_SHOP = "NULLIF(current_setting('app.current_shop_id', true), '')::uuid"
_OWNER_UID = "NULLIF(current_setting('app.current_user_id', true), '')::uuid"
_IS_OWNER = "current_setting('app.user_role', true) = 'owner'"
_OWNED = f"(SELECT so.shop_id FROM shop_owners so WHERE so.owner_id = {_OWNER_UID})"
_OWNER_BRANCH = f" OR ({_IS_OWNER} AND shop_id IN {_OWNED})"


def upgrade() -> None:
    op.execute("DROP POLICY IF EXISTS expense_categories_isolation ON expense_categories;")
    op.execute(
        f"""
        CREATE POLICY expense_categories_isolation ON expense_categories
            FOR ALL
            USING ({_ROLE} OR shop_id = {_SHOP}{_OWNER_BRANCH})
            WITH CHECK ({_ROLE} OR shop_id = {_SHOP}{_OWNER_BRANCH});
        """
    )


def downgrade() -> None:
    op.execute("DROP POLICY IF EXISTS expense_categories_isolation ON expense_categories;")
    op.execute(
        f"""
        CREATE POLICY expense_categories_isolation ON expense_categories
            FOR ALL
            USING ({_ROLE} OR shop_id = {_SHOP})
            WITH CHECK ({_ROLE} OR shop_id = {_SHOP});
        """
    )
