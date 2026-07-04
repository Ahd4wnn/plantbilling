"""restore the users self-row RLS branch (regressed by e5f6a7b8c9d0)

Revision ID: f6a7b8c9d0e1
Revises: e5f6a7b8c9d0
Create Date: 2026-07-05

The multi-owner migration (e5f6a7b8c9d0) recreated users_isolation but dropped the
self-row branch that b2c3d4e5f6a7 had added, so an owner (shop_id NULL) could no
longer read their OWN users row -> get_current_user failed -> /auth/me returned
401 for owners. Restore `id = current_user_id`, keeping the multi-owner branch
(owner sees staff whose shop is in shop_owners).
"""
from __future__ import annotations

from typing import Sequence, Union

from alembic import op

revision: str = "f6a7b8c9d0e1"
down_revision: Union[str, None] = "e5f6a7b8c9d0"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None

_ADMIN = "current_setting('app.user_role', true) = 'admin'"
_SHOP = "shop_id = NULLIF(current_setting('app.current_shop_id', true), '')::uuid"
_SELF = "id = NULLIF(current_setting('app.current_user_id', true), '')::uuid"
_OWNER_UID = "NULLIF(current_setting('app.current_user_id', true), '')::uuid"
_IS_OWNER = "current_setting('app.user_role', true) = 'owner'"
_OWNER_STAFF = (
    f"({_IS_OWNER} AND shop_id IN "
    f"(SELECT so.shop_id FROM shop_owners so WHERE so.owner_id = {_OWNER_UID}))"
)

_WITH_SELF = f"({_ADMIN} OR {_SELF} OR {_SHOP} OR {_OWNER_STAFF})"
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
