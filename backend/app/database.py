"""Database engine, session factory, and RLS-aware session dependencies.

Tenant isolation is enforced by PostgreSQL Row Level Security (RLS). The
application connects as a NON-superuser, NON-BYPASSRLS role (DATABASE_URL_APP),
so the policies defined in the initial migration are always enforced.

Each request runs inside a single transaction. Before any tenant query runs we
set two transaction-local variables that the RLS policies read:

    app.user_role        -> 'admin' | 'shop_owner'
    app.current_shop_id  -> the owner's shop UUID (omitted for admin)

We use `set_config(name, value, is_local => true)` instead of `SET LOCAL ...`
because set_config accepts bound parameters, avoiding any SQL string building.
`is_local => true` scopes the value to the current transaction and resets it
automatically at COMMIT/ROLLBACK, so pooled connections never leak context.
"""
from __future__ import annotations

from contextlib import contextmanager
from typing import Iterator

from sqlalchemy import create_engine, text
from sqlalchemy.orm import Session, sessionmaker

from app.config import get_settings

settings = get_settings()

# Per-connection server-side guards: no single query or stalled transaction may
# pin a pooled connection forever (that's how a busy pool permanently wedges).
# Passed as libpq "options"; applies to every new connection in the pool.
_pg_options_parts = []
if settings.DB_STATEMENT_TIMEOUT_MS > 0:
    _pg_options_parts.append(f"-c statement_timeout={settings.DB_STATEMENT_TIMEOUT_MS}")
if settings.DB_IDLE_TX_TIMEOUT_MS > 0:
    _pg_options_parts.append(
        f"-c idle_in_transaction_session_timeout={settings.DB_IDLE_TX_TIMEOUT_MS}"
    )
_connect_args = {"application_name": "plantora-api"}
if _pg_options_parts:
    _connect_args["options"] = " ".join(_pg_options_parts)

# Runtime engine: connects as the limited app role (RLS is enforced).
#
# Pool sizing matters a lot: every in-flight request holds one connection for its
# entire transaction (RLS context lives for the transaction), so the pool is the
# real concurrency limit per worker. Defaults here are tuned for many small shops
# billing concurrently; see config for the total-connections math and PgBouncer.
engine = create_engine(
    settings.DATABASE_URL_APP,
    pool_pre_ping=True,
    pool_size=settings.DB_POOL_SIZE,
    max_overflow=settings.DB_MAX_OVERFLOW,
    pool_timeout=settings.DB_POOL_TIMEOUT,
    pool_recycle=settings.DB_POOL_RECYCLE,
    connect_args=_connect_args,
    future=True,
)

SessionLocal = sessionmaker(bind=engine, autoflush=False, expire_on_commit=False, future=True)


def _apply_rls_context(
    session: Session, *, role: str, shop_id: str | None, user_id: str | None = None
) -> None:
    """Set the transaction-local RLS variables for this session.

    Must be called as the first statement(s) of the transaction so every
    subsequent query is filtered by the policies.
    """
    session.execute(
        text("SELECT set_config('app.user_role', :role, true)"),
        {"role": role},
    )
    # The caller's user id powers the multi-shop owner RLS branch (an owner sees
    # rows whose shop_id is in SELECT shop_id FROM shop_owners WHERE owner_id = this id).
    if user_id is not None:
        session.execute(
            text("SELECT set_config('app.current_user_id', :uid, true)"),
            {"uid": str(user_id)},
        )
    # Only set the shop id when present. For admin/owner we leave it unset (NULL);
    # the policies short-circuit before evaluating the shop_id comparison, so no
    # cast of NULL/'' is attempted.
    if shop_id is not None:
        session.execute(
            text("SELECT set_config('app.current_shop_id', :sid, true)"),
            {"sid": str(shop_id)},
        )


def get_rls_session(role: str, shop_id: str | None, user_id: str | None = None) -> Iterator[Session]:
    """Generator yielding a session whose transaction has RLS context applied.

    This is the standard way authenticated routes obtain a DB session. The
    concrete FastAPI dependency that calls this lives in app.auth.dependencies,
    where the (role, shop_id) come from the verified JWT claims.

    The transaction stays open across the request and is committed on success
    or rolled back on error.
    """
    session = SessionLocal()
    try:
        _apply_rls_context(session, role=role, shop_id=shop_id, user_id=user_id)
        yield session
        session.commit()
    except Exception:
        session.rollback()
        raise
    finally:
        session.close()


@contextmanager
def privileged_session() -> Iterator[Session]:
    """A session that runs under the 'admin' RLS context.

    Used ONLY for trusted, JWT-less server operations that must read/write
    across the users table:
      * login (look up a user by email to verify credentials), and
      * bootstrap admin creation (scripts/create_admin.py).

    This is not an escape hatch out of RLS at the database level — the app role
    still has no BYPASSRLS. It simply asserts the privileged application context
    for these two well-defined code paths.
    """
    session = SessionLocal()
    try:
        session.execute(text("SELECT set_config('app.user_role', 'admin', true)"))
        yield session
        session.commit()
    except Exception:
        session.rollback()
        raise
    finally:
        session.close()
