"""crash_reports table for self-hosted ACRA crash reporting

Revision ID: d4e5f6a7b8c9
Revises: c3d4e5f6a7b8
Create Date: 2026-07-05

The Android app (ACRA) POSTs uncaught-exception reports to /crash-reports. They
land here so all crash data stays on our own VPS (no Google/Sentry). Ingestion is
unauthenticated (crashes happen before/without login) and runs under the admin
RLS context, so the policy only needs to admit admin. Reads are admin-only.
"""
from typing import Sequence, Union

from alembic import op

from app.config import get_settings

revision: str = "d4e5f6a7b8c9"
down_revision: Union[str, None] = "c3d4e5f6a7b8"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None

APP_DB_ROLE = get_settings().APP_DB_ROLE
_ROLE = "current_setting('app.user_role', true) = 'admin'"


def upgrade() -> None:
    op.execute(
        """
        CREATE TABLE crash_reports (
            id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            app_version     TEXT,
            android_version TEXT,
            device          TEXT,
            installation_id TEXT,
            stack_trace     TEXT,
            user_comment    TEXT,
            report          JSONB NOT NULL DEFAULT '{}'::jsonb,
            created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
        );
        """
    )
    op.execute("CREATE INDEX ix_crash_reports_created_at ON crash_reports(created_at DESC);")

    op.execute("ALTER TABLE crash_reports ENABLE ROW LEVEL SECURITY;")
    op.execute("ALTER TABLE crash_reports FORCE ROW LEVEL SECURITY;")

    # Ingestion runs under the admin context (privileged_session); reads are admin
    # only. No shop scoping — crash reports are platform-level, not tenant data.
    op.execute(
        f"""
        CREATE POLICY crash_reports_admin ON crash_reports
            FOR ALL
            USING ({_ROLE})
            WITH CHECK ({_ROLE});
        """
    )

    role = f'"{APP_DB_ROLE}"'
    op.execute(f"GRANT SELECT, INSERT, UPDATE, DELETE ON crash_reports TO {role};")


def downgrade() -> None:
    role = f'"{APP_DB_ROLE}"'
    op.execute(f"REVOKE ALL ON crash_reports FROM {role};")
    op.execute("DROP TABLE IF EXISTS crash_reports CASCADE;")
