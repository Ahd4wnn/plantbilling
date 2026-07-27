"""admin -> shop notifications: notifications, targets, reads + RLS

Revision ID: b7d3f1a9c2e4
Revises: f9c1d2e3a4b5
Create Date: 2026-07-27

The platform admin composes notifications and targets all shops or specific
shops; the shop app shows them in-app with per-user read tracking. Three tables:

- notifications        : global, admin-authored (title/body/action_url/target).
- notification_targets : (notification_id, shop_id) rows for target='shops'.
- notification_reads   : (notification_id, user_id) per-user read receipts.

RLS: the admin manages everything. A shop (manager/salesperson via current_shop_id,
or an owner via the shop_owners join) may READ a notification that is a broadcast
('all') or that names its shop in notification_targets. A user may read/write only
their own read receipts; the admin reads all receipts to show read counts. The
targets table is shop-readable for the rows naming the shop so the notifications
visibility EXISTS subquery resolves under the shop/owner role.
"""
from typing import Sequence, Union

from alembic import op

from app.config import get_settings

revision: str = "b7d3f1a9c2e4"
down_revision: Union[str, None] = "f9c1d2e3a4b5"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None

APP_DB_ROLE = get_settings().APP_DB_ROLE

# ── Shared policy fragments (mirror the multi-owner migration) ─────────────────
_ROLE = "current_setting('app.user_role', true) = 'admin'"
_SHOP = "NULLIF(current_setting('app.current_shop_id', true), '')::uuid"
_UID = "NULLIF(current_setting('app.current_user_id', true), '')::uuid"
_IS_OWNER = "current_setting('app.user_role', true) = 'owner'"
_OWNED_JOIN = f"(SELECT so.shop_id FROM shop_owners so WHERE so.owner_id = {_UID})"


def _shop_sees(col: str) -> str:
    """Predicate: the current shop/owner may see a row whose shop is `col`
    (its current shop, or — for an owner — one of the shops it owns)."""
    return f"({col} = {_SHOP} OR ({_IS_OWNER} AND {col} IN {_OWNED_JOIN}))"


def upgrade() -> None:
    role = f'"{APP_DB_ROLE}"'

    # ── notifications ──────────────────────────────────────────────────────────
    op.execute(
        """
        CREATE TABLE notifications (
            id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            title      TEXT NOT NULL,
            body       TEXT NOT NULL,
            action_url TEXT,
            target     TEXT NOT NULL DEFAULT 'all',
            created_by UUID REFERENCES users(id) ON DELETE SET NULL,
            created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
            CONSTRAINT notifications_target_valid CHECK (target IN ('all', 'shops'))
        );
        """
    )
    op.execute("CREATE INDEX ix_notifications_created_at ON notifications(created_at DESC);")

    # ── notification_targets ───────────────────────────────────────────────────
    op.execute(
        """
        CREATE TABLE notification_targets (
            notification_id UUID NOT NULL REFERENCES notifications(id) ON DELETE CASCADE,
            shop_id         UUID NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
            PRIMARY KEY (notification_id, shop_id)
        );
        """
    )
    op.execute("CREATE INDEX ix_notification_targets_shop_id ON notification_targets(shop_id);")

    # ── notification_reads ─────────────────────────────────────────────────────
    op.execute(
        """
        CREATE TABLE notification_reads (
            notification_id UUID NOT NULL REFERENCES notifications(id) ON DELETE CASCADE,
            user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
            read_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
            PRIMARY KEY (notification_id, user_id)
        );
        """
    )

    # ── Enable + FORCE RLS on all three ────────────────────────────────────────
    for tbl in ("notifications", "notification_targets", "notification_reads"):
        op.execute(f"ALTER TABLE {tbl} ENABLE ROW LEVEL SECURITY;")
        op.execute(f"ALTER TABLE {tbl} FORCE ROW LEVEL SECURITY;")

    # notifications: admin manages; shops read broadcasts + those naming their shop.
    op.execute(
        f"""
        CREATE POLICY notifications_admin ON notifications
            FOR ALL
            USING ({_ROLE})
            WITH CHECK ({_ROLE});
        """
    )
    op.execute(
        f"""
        CREATE POLICY notifications_shop_read ON notifications
            FOR SELECT
            USING (
                target = 'all'
                OR EXISTS (
                    SELECT 1 FROM notification_targets nt
                    WHERE nt.notification_id = notifications.id
                      AND {_shop_sees('nt.shop_id')}
                )
            );
        """
    )

    # notification_targets: admin manages; a shop reads rows naming it (so the
    # notifications EXISTS subquery resolves under the shop/owner role).
    op.execute(
        f"""
        CREATE POLICY notification_targets_admin ON notification_targets
            FOR ALL
            USING ({_ROLE})
            WITH CHECK ({_ROLE});
        """
    )
    op.execute(
        f"""
        CREATE POLICY notification_targets_shop_read ON notification_targets
            FOR SELECT
            USING ({_shop_sees('shop_id')});
        """
    )

    # notification_reads: a user reads/writes only their own receipts; admin all.
    op.execute(
        f"""
        CREATE POLICY notification_reads_isolation ON notification_reads
            FOR ALL
            USING ({_ROLE} OR user_id = {_UID})
            WITH CHECK ({_ROLE} OR user_id = {_UID});
        """
    )

    # ── Grants ─────────────────────────────────────────────────────────────────
    for tbl in ("notifications", "notification_targets", "notification_reads"):
        op.execute(f"GRANT SELECT, INSERT, UPDATE, DELETE ON {tbl} TO {role};")


def downgrade() -> None:
    role = f'"{APP_DB_ROLE}"'
    for tbl in ("notification_reads", "notification_targets", "notifications"):
        op.execute(f"REVOKE ALL ON {tbl} FROM {role};")
        op.execute(f"DROP TABLE IF EXISTS {tbl} CASCADE;")
