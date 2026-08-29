"""Filesystem storage helpers for uploaded images.

Images are stored under ``MEDIA_ROOT/<subdir>/`` with server-generated unique
filenames — ``products/`` for product photos, ``logos/`` for shop logos. The DB
stores only the RELATIVE path (e.g. ``products/<uuid>.jpg``); outward-facing URLs
are built from ``MEDIA_URL_PREFIX``.
"""
from __future__ import annotations

import uuid
from pathlib import Path

from app.config import get_settings

settings = get_settings()

# Allowed upload content types mapped to the extension we store them under.
IMAGE_CONTENT_TYPES: dict[str, str] = {
    "image/jpeg": ".jpg",
    "image/png": ".png",
    "image/webp": ".webp",
}

MAX_IMAGE_BYTES = 5 * 1024 * 1024  # 5 MB

_PRODUCTS_SUBDIR = "products"
_INVOICES_SUBDIR = "invoices"
_LOGOS_SUBDIR = "logos"


def media_root() -> Path:
    return Path(settings.MEDIA_ROOT).resolve()


def products_dir() -> Path:
    return media_root() / _PRODUCTS_SUBDIR


def invoices_dir() -> Path:
    return media_root() / _INVOICES_SUBDIR


def logos_dir() -> Path:
    return media_root() / _LOGOS_SUBDIR


def ensure_media_dirs() -> None:
    """Create MEDIA_ROOT and the products, invoices and logos subdirectories."""
    products_dir().mkdir(parents=True, exist_ok=True)
    invoices_dir().mkdir(parents=True, exist_ok=True)
    logos_dir().mkdir(parents=True, exist_ok=True)


def _save_image_bytes(data: bytes, ext: str, subdir: str) -> str:
    """Persist image bytes under a media subdirectory and return the relative path.

    The filename is server-generated, never taken from the upload — a
    client-supplied name is an easy path-traversal foothold and a collision risk.
    """
    ensure_media_dirs()
    filename = f"{uuid.uuid4().hex}{ext}"
    (media_root() / subdir / filename).write_bytes(data)
    return f"{subdir}/{filename}"


def save_product_image_bytes(data: bytes, ext: str) -> str:
    """Persist a product photo and return the relative path stored in the DB."""
    return _save_image_bytes(data, ext, _PRODUCTS_SUBDIR)


def save_shop_logo_bytes(data: bytes, ext: str) -> str:
    """Persist a shop logo and return the relative path stored in the DB."""
    return _save_image_bytes(data, ext, _LOGOS_SUBDIR)


def delete_relative(rel_path: str | None) -> None:
    """Best-effort delete of a stored media file. Safe if missing/None.

    Guards against path traversal by ensuring the resolved file stays within
    MEDIA_ROOT before unlinking.
    """
    if not rel_path:
        return
    target = (media_root() / rel_path).resolve()
    try:
        target.relative_to(media_root())
    except ValueError:
        return  # outside MEDIA_ROOT — refuse to touch it
    target.unlink(missing_ok=True)


def build_media_url(rel_path: str | None) -> str | None:
    """Turn a stored relative path into a URL the frontend can use directly.

    e.g. ('products/abc.jpg') -> '/media/products/abc.jpg'. None -> None.
    """
    if not rel_path:
        return None
    prefix = settings.MEDIA_URL_PREFIX.rstrip("/")
    return f"{prefix}/{rel_path.lstrip('/')}"
