"""Application configuration loaded from environment variables.

All configuration is sourced from the environment (or a local `.env` file during
development). Nothing is hardcoded. See `.env.example` for the full list.
"""
from __future__ import annotations

from functools import lru_cache

from pydantic import EmailStr, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
        case_sensitive=True,
    )

    # Database connections (two distinct Postgres roles — see README).
    DATABASE_URL_ADMIN: str
    DATABASE_URL_APP: str

    # Name of the limited app role; the initial migration grants it privileges.
    APP_DB_ROLE: str = "plantora_app"

    # ── Database connection pool (per worker process) ─────────────────────────
    # Each request holds one connection for its whole transaction (RLS context is
    # transaction-scoped), so the pool caps how many requests a worker serves at
    # once. Total DB connections = (gunicorn workers) x (POOL_SIZE + MAX_OVERFLOW);
    # keep that under Postgres max_connections, or front the DB with PgBouncer.
    DB_POOL_SIZE: int = 10
    DB_MAX_OVERFLOW: int = 20
    # Seconds a request waits for a free connection before erroring (instead of
    # hanging forever — a fast, visible failure beats a silent freeze).
    DB_POOL_TIMEOUT: int = 10
    # Recycle connections older than this (seconds) to survive DB restarts / idle
    # server-side connection drops (e.g. behind a proxy or PgBouncer).
    DB_POOL_RECYCLE: int = 1800
    # Server-side guards so no single request can pin a connection indefinitely.
    # statement_timeout caps any one query; idle_in_transaction caps a stalled
    # open transaction. Both in milliseconds; 0 disables.
    DB_STATEMENT_TIMEOUT_MS: int = 15000
    DB_IDLE_TX_TIMEOUT_MS: int = 30000

    # JWT / auth.
    JWT_SECRET: str
    JWT_ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 720

    # Bootstrap admin (used only by scripts/create_admin.py).
    BOOTSTRAP_ADMIN_EMAIL: EmailStr
    BOOTSTRAP_ADMIN_PASSWORD: str

    # CORS — comma-separated list of allowed origins.
    CORS_ORIGINS: str = "http://localhost:5173,http://localhost:3000"

    # Media / image storage.
    # MEDIA_ROOT: directory where uploaded product images are stored on disk.
    #   Relative paths are resolved from the process working directory (backend/).
    #   On the VPS set this to an absolute path.
    # MEDIA_URL_PREFIX: URL prefix images are served under (locally a FastAPI
    #   static mount; on the VPS, Nginx serves MEDIA_ROOT at this prefix).
    MEDIA_ROOT: str = "./media"
    MEDIA_URL_PREFIX: str = "/media"

    # ── WhatsApp delivery ────────────────────────────────────────────────────
    # Global kill switch. When false, sending always degrades to a wa.me link.
    WHATSAPP_ENABLED: bool = False
    # Meta WhatsApp Cloud API settings
    WHATSAPP_PHONE_NUMBER_ID: str = ""
    WHATSAPP_ACCESS_TOKEN: str = ""
    WHATSAPP_BUSINESS_ACCOUNT_ID: str = ""
    WHATSAPP_VERIFY_TOKEN: str = ""
    WHATSAPP_API_VERSION: str = "v20.0"
    WHATSAPP_TEMPLATE_NAME: str = "plantora_invoice"
    # Default country code used to normalize bare national numbers (India = 91).
    WHATSAPP_COUNTRY_CODE: str = "91"
    # Per-request timeout (seconds) for HTTP calls.
    WHATSAPP_TIMEOUT_SECONDS: float = 8.0

    # ── Frontend Link ────────────────────────────────────────────────────────
    # Base URL of the frontend app, used to construct sharing URLs (e.g. wa.me and print links).
    FRONTEND_BASE_URL: str = "http://localhost:5173"

    @property
    def cors_origins_list(self) -> list[str]:
        return [o.strip() for o in self.CORS_ORIGINS.split(",") if o.strip()]

    @field_validator("JWT_SECRET")
    @classmethod
    def _secret_not_placeholder(cls, v: str) -> str:
        if not v or "CHANGE_ME" in v:
            raise ValueError("JWT_SECRET must be set to a real secret value")
        return v


@lru_cache
def get_settings() -> Settings:
    """Return a cached Settings instance."""
    return Settings()  # type: ignore[call-arg]
