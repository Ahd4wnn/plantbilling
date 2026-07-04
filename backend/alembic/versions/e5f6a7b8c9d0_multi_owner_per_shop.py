"""multiple owners per shop: shop_owners join table + RLS via join

Revision ID: e5f6a7b8c9d0
Revises: d4e5f6a7b8c9
Create Date: 2026-07-05

Replaces the single `shops.owner_id` (one owner per shop) with a many-to-many
`shop_owners` join table, so a shop can have several multi-shop owners.

RLS change: the owner branch previously read
    <shop col> IN (SELECT id FROM shops WHERE owner_id = current_user_id)
and now reads
    <shop col> IN (SELECT shop_id FROM shop_owners WHERE owner_id = current_user_id)

`shops.owner_id` is DROPPED — `shop_owners` is the single source of truth. The
existing owner_id links are backfilled into the join table first.

Safe subquery note: the shops policy's owner branch queries `shop_owners` (NOT
`shops`), so there's no RLS self-recursion on shops. `shop_owners` itself gets a
policy that lets an owner read their own membership rows, which the subqueries in
the other tables' policies rely on (they run under the owner's role).
"""
from __future__ import annotations

from typing import Sequence, Union

from alembic import op

from app.config import get_settings

revision: str = "e5f6a7b8c9d0"
down_revision: Union[str, None] = "d4e5f6a7b8c9"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None

APP_DB_ROLE = get_settings().APP_DB_ROLE

# ── Shared policy fragments ───────────────────────────────────────────────────
_ROLE = "current_setting('app.user_role', true) = 'admin'"
_SHOP = "NULLIF(current_setting('app.current_shop_id', true), '')::uuid"
_OWNER_UID = "NULLIF(current_setting('app.current_user_id', true), '')::uuid"
_IS_OWNER = "current_setting('app.user_role', true) = 'owner'"

# The set of shop ids the current owner owns — now via the join table.
_OWNED_JOIN = f"(SELECT so.shop_id FROM shop_owners so WHERE so.owner_id = {_OWNER_UID})"
_OWNED_OLD = f"(SELECT s.id FROM shops s WHERE s.owner_id = {_OWNER_UID})"


def _recreate_policies(*, owned_expr: str, shops_owner_branch: str) -> None:
    """(Re)create every tenant policy. `owned_expr` is the sub-select yielding the
    owner's shop ids; `shops_owner_branch` is the owner clause for the shops table
    (which can't reference the owned-set the same way without recursion)."""
    owner_by_shop = f" OR ({_IS_OWNER} AND shop_id IN {owned_expr})"

    op.execute("DROP POLICY IF EXISTS shops_isolation ON shops;")
    op.execute(
        f"""
        CREATE POLICY shops_isolation ON shops
            FOR ALL
            USING ({_ROLE} OR id = {_SHOP}{shops_owner_branch})
            WITH CHECK ({_ROLE} OR id = {_SHOP}{shops_owner_branch});
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

    owner_bi = f" OR ({_IS_OWNER} AND b.shop_id IN {owned_expr})"
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
    # DML below (backfill) must see all rows under FORCE RLS.
    op.execute("SELECT set_config('app.user_role', 'admin', true);")

    # 1. Join table.
    op.execute(
        """
        CREATE TABLE shop_owners (
            shop_id  UUID NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
            owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
            created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
            PRIMARY KEY (shop_id, owner_id)
        );
        """
    )
    op.execute("CREATE INDEX ix_shop_owners_owner_id ON shop_owners(owner_id);")

    # 2. Backfill existing single-owner links.
    op.execute(
        "INSERT INTO shop_owners (shop_id, owner_id) "
        "SELECT id, owner_id FROM shops WHERE owner_id IS NOT NULL "
        "ON CONFLICT DO NOTHING;"
    )

    # 3. RLS on the join table: admin manages; an owner may READ their own rows
    #    (the tenant policies' subqueries depend on this under the owner's role).
    op.execute("ALTER TABLE shop_owners ENABLE ROW LEVEL SECURITY;")
    op.execute("ALTER TABLE shop_owners FORCE ROW LEVEL SECURITY;")
    op.execute(
        f"""
        CREATE POLICY shop_owners_isolation ON shop_owners
            FOR ALL
            USING ({_ROLE} OR ({_IS_OWNER} AND owner_id = {_OWNER_UID}))
            WITH CHECK ({_ROLE});
        """
    )
    role = f'"{APP_DB_ROLE}"'
    op.execute(f"GRANT SELECT, INSERT, UPDATE, DELETE ON shop_owners TO {role};")

    # 4. Rebuild every tenant policy to route the owner branch through the join.
    _recreate_policies(
        owned_expr=_OWNED_JOIN,
        shops_owner_branch=f" OR ({_IS_OWNER} AND id IN {_OWNED_JOIN})",
    )

    # 5. Drop the now-redundant single-owner column.
    op.execute("DROP INDEX IF EXISTS ix_shops_owner_id;")
    op.execute("ALTER TABLE shops DROP COLUMN IF EXISTS owner_id;")


def downgrade() -> None:
    op.execute("SELECT set_config('app.user_role', 'admin', true);")

    # Restore shops.owner_id (single owner = an arbitrary one of the owners).
    op.execute(
        "ALTER TABLE shops ADD COLUMN owner_id UUID REFERENCES users(id) ON DELETE SET NULL;"
    )
    op.execute("CREATE INDEX ix_shops_owner_id ON shops(owner_id);")
    op.execute(
        """
        UPDATE shops s
           SET owner_id = (
               SELECT so.owner_id FROM shop_owners so
                WHERE so.shop_id = s.id
                ORDER BY so.created_at ASC
                LIMIT 1
           );
        """
    )

    # Restore the prior owner-by-shops-column policies.
    _recreate_policies(
        owned_expr=_OWNED_OLD,
        shops_owner_branch=f" OR ({_IS_OWNER} AND owner_id = {_OWNER_UID})",
    )

    role = f'"{APP_DB_ROLE}"'
    op.execute(f"REVOKE ALL ON shop_owners FROM {role};")
    op.execute("DROP TABLE IF EXISTS shop_owners CASCADE;")
