"""Multi-shop owner role: shops.owner_id, role rename, RLS owner branch.

Introduces a new top-of-business `owner` role that may own MANY shops, and
renames the per-shop operator `shop_owner` -> `manager`.

Roles after this migration: admin, owner, manager, salesperson.

  * admin       — platform; shop_id NULL; RLS sees everything.
  * owner       — multi-shop; shop_id NULL; owns shops via shops.owner_id; RLS
                  sees every row whose shop is owned by them.
  * manager     — one shop (the old shop_owner); shop_id set.
  * salesperson — one shop; shop_id set.

RLS gains a third branch driven by a new transaction-local GUC
`app.current_user_id` (the caller's user id). An owner row is visible when its
shop belongs to the owner:

    OR (current_setting('app.user_role',true) = 'owner'
        AND <shop col> IN (SELECT id FROM shops WHERE owner_id = <current_user_id>))
"""
from __future__ import annotations

from typing import Sequence, Union

from alembic import op

# revision identifiers, used by Alembic.
revision: str = "a1b2c3d4e5f6"
down_revision: Union[str, None] = "d436fc7b005a"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None

# ── Shared policy fragments ───────────────────────────────────────────────────
_ROLE = "current_setting('app.user_role', true) = 'admin'"
_SHOP = "NULLIF(current_setting('app.current_shop_id', true), '')::uuid"
_OWNER_UID = "NULLIF(current_setting('app.current_user_id', true), '')::uuid"
_IS_OWNER = "current_setting('app.user_role', true) = 'owner'"
_OWNED = f"(SELECT s.id FROM shops s WHERE s.owner_id = {_OWNER_UID})"

# Owner branch for tables that carry shop_id directly.
_OWNER_BY_SHOP = f"({_IS_OWNER} AND shop_id IN {_OWNED})"


def _recreate_policies(*, with_owner: bool) -> None:
    """(Re)create the isolation policies on every tenant table.

    with_owner=True adds the owner branch; False restores the prior
    admin/shop-only policies (used on downgrade).
    """
    # The shops policy compares owner_id DIRECTLY (not a subquery on shops) — a
    # subquery on shops inside the shops policy causes infinite RLS recursion.
    owner_shops = f" OR ({_IS_OWNER} AND owner_id = {_OWNER_UID})" if with_owner else ""
    owner_by_shop = f" OR {_OWNER_BY_SHOP}" if with_owner else ""

    op.execute("DROP POLICY IF EXISTS shops_isolation ON shops;")
    op.execute(
        f"""
        CREATE POLICY shops_isolation ON shops
            FOR ALL
            USING ({_ROLE} OR id = {_SHOP}{owner_shops})
            WITH CHECK ({_ROLE} OR id = {_SHOP}{owner_shops});
        """
    )

    op.execute("DROP POLICY IF EXISTS users_isolation ON users;")
    op.execute(
        f"""
        CREATE POLICY users_isolation ON users
            FOR ALL
            USING ({_ROLE} OR shop_id = {_SHOP}{owner_by_shop})
            WITH CHECK ({_ROLE} OR shop_id = {_SHOP}{owner_by_shop});
        """
    )

    for tbl in ("products", "customers", "bills", "expenses"):
        op.execute(f"DROP POLICY IF EXISTS {tbl}_isolation ON {tbl};")
        op.execute(
            f"""
            CREATE POLICY {tbl}_isolation ON {tbl}
                FOR ALL
                USING ({_ROLE} OR shop_id = {_SHOP}{owner_by_shop})
                WITH CHECK ({_ROLE} OR shop_id = {_SHOP}{owner_by_shop});
            """
        )

    owner_bi = (
        f" OR ({_IS_OWNER} AND b.shop_id IN {_OWNED})" if with_owner else ""
    )
    op.execute("DROP POLICY IF EXISTS bill_items_isolation ON bill_items;")
    op.execute(
        f"""
        CREATE POLICY bill_items_isolation ON bill_items
            FOR ALL
            USING (
                {_ROLE}
                OR EXISTS (
                    SELECT 1 FROM bills b
                    WHERE b.id = bill_items.bill_id
                      AND (b.shop_id = {_SHOP}{owner_bi})
                )
            )
            WITH CHECK (
                {_ROLE}
                OR EXISTS (
                    SELECT 1 FROM bills b
                    WHERE b.id = bill_items.bill_id
                      AND (b.shop_id = {_SHOP}{owner_bi})
                )
            );
        """
    )


def upgrade() -> None:
    # 1. shops.owner_id — links a shop to its multi-shop owner (optional).
    op.execute(
        "ALTER TABLE shops ADD COLUMN owner_id UUID REFERENCES users(id) ON DELETE SET NULL;"
    )
    op.execute("CREATE INDEX ix_shops_owner_id ON shops(owner_id);")

    # 2. Role rename + new role set. Drop the old checks, backfill, re-add.
    op.execute("ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_shop_consistency;")
    op.execute("ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;")

    op.execute("UPDATE users SET role = 'manager' WHERE role = 'shop_owner';")

    op.execute(
        "ALTER TABLE users ADD CONSTRAINT users_role_check "
        "CHECK (role IN ('admin', 'owner', 'manager', 'salesperson'));"
    )
    op.execute(
        """
        ALTER TABLE users ADD CONSTRAINT users_role_shop_consistency CHECK (
            (role IN ('admin', 'owner') AND shop_id IS NULL) OR
            (role IN ('manager', 'salesperson') AND shop_id IS NOT NULL)
        );
        """
    )

    # 3. Rebuild RLS with the owner branch.
    _recreate_policies(with_owner=True)


def downgrade() -> None:
    # Restore admin/shop-only policies.
    _recreate_policies(with_owner=False)

    op.execute("ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_shop_consistency;")
    op.execute("ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;")

    # Owners cannot exist under the old model; collapse them back to managers is
    # not meaningful (no shop), so this assumes a dev DB without live owners.
    op.execute("UPDATE users SET role = 'shop_owner' WHERE role = 'manager';")

    op.execute(
        "ALTER TABLE users ADD CONSTRAINT users_role_check "
        "CHECK (role IN ('admin', 'shop_owner', 'salesperson'));"
    )
    op.execute(
        """
        ALTER TABLE users ADD CONSTRAINT users_role_shop_consistency CHECK (
            (role = 'admin' AND shop_id IS NULL) OR
            (role IN ('shop_owner', 'salesperson') AND shop_id IS NOT NULL)
        );
        """
    )

    op.execute("DROP INDEX IF EXISTS ix_shops_owner_id;")
    op.execute("ALTER TABLE shops DROP COLUMN IF EXISTS owner_id;")
