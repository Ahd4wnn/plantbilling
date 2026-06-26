"""Let every user read their own users row (fixes owner /auth/me).

The owner role has shop_id = NULL and is not tied to a single shop, so the
users_isolation policy's branches (admin / shop_id = current_shop_id / owner
sees staff whose shop_id is in owned shops) never match the owner's OWN row.
get_current_user then can't load the owner under RLS and returns
"Could not validate credentials" on /auth/me.

Add an explicit self-row branch — `id = app.current_user_id` — so any
authenticated user can always see (and update) their own row, regardless of
role. Managers/salespeople already matched via shop_id; this is what the owner
was missing.
"""
from __future__ import annotations

from typing import Sequence, Union

from alembic import op

# revision identifiers, used by Alembic.
revision: str = "b2c3d4e5f6a7"
down_revision: Union[str, None] = "a1b2c3d4e5f6"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None

_ADMIN = "current_setting('app.user_role', true) = 'admin'"
_SHOP = "shop_id = NULLIF(current_setting('app.current_shop_id', true), '')::uuid"
_SELF = "id = NULLIF(current_setting('app.current_user_id', true), '')::uuid"
_OWNER_UID = "NULLIF(current_setting('app.current_user_id', true), '')::uuid"
_IS_OWNER = "current_setting('app.user_role', true) = 'owner'"
_OWNER_STAFF = f"({_IS_OWNER} AND shop_id IN (SELECT s.id FROM shops s WHERE s.owner_id = {_OWNER_UID}))"

# With the self-row branch.
_WITH_SELF = f"({_ADMIN} OR {_SELF} OR {_SHOP} OR {_OWNER_STAFF})"
# Prior policy (no self branch) — used on downgrade.
_WITHOUT_SELF = f"({_ADMIN} OR {_SHOP} OR {_OWNER_STAFF})"


def _set_users_policy(expr: str) -> None:
    op.execute("DROP POLICY IF EXISTS users_isolation ON users;")
    op.execute(
        f"""
        CREATE POLICY users_isolation ON users
            FOR ALL
            USING {expr}
            WITH CHECK {expr};
        """
    )


def upgrade() -> None:
    _set_users_policy(_WITH_SELF)


def downgrade() -> None:
    _set_users_policy(_WITHOUT_SELF)
