"""Self-hosted crash reporting ingest + admin viewer.

The Android app (ACRA) POSTs uncaught-exception reports as JSON to /crash-reports.
Ingestion is intentionally unauthenticated — a crash can happen before login, and
we never want a failed auth to lose a crash report. It writes under the admin RLS
context via privileged_session (the endpoint only ever inserts one crash row, so
this fixed code path is a safe use of that context). Reads are admin-only.
"""
from __future__ import annotations

import json
import uuid
from typing import Any

from fastapi import APIRouter, Body, Depends, HTTPException, Query, status
from sqlalchemy import select

from app.auth.dependencies import get_db, require_admin
from app.database import privileged_session
from app.models.crash_report import CrashReport
from app.models.user import User
from app.schemas.crash import CrashReportListOut, CrashReportOut

router = APIRouter(tags=["crash-reports"])

# Guardrails against abuse of the public endpoint.
_MAX_PAYLOAD_BYTES = 256 * 1024   # reject anything larger than a fat crash report
_MAX_TEXT = 20_000                # cap what we copy into denormalized text columns


def _clip(value: Any) -> str | None:
    if value is None:
        return None
    s = value if isinstance(value, str) else json.dumps(value, default=str)
    return s[:_MAX_TEXT]


@router.post("/crash-reports", status_code=status.HTTP_202_ACCEPTED)
def ingest_crash_report(payload: dict[str, Any] = Body(...)) -> dict[str, str]:
    """Receive one ACRA crash report (public). Always 202 so the app never retries
    a well-formed report; malformed/oversized ones are dropped quietly."""
    try:
        if len(json.dumps(payload, default=str).encode("utf-8")) > _MAX_PAYLOAD_BYTES:
            # Too big to be a real report we care to store — drop it, don't error.
            return {"status": "ignored"}
    except (TypeError, ValueError):
        return {"status": "ignored"}

    # ACRA StringFormat.JSON keys the report by ReportField names.
    brand = payload.get("BRAND")
    model = payload.get("PHONE_MODEL")
    device = " ".join(p for p in (brand, model) if p) or None

    row = CrashReport(
        app_version=_clip(payload.get("APP_VERSION_NAME")),
        android_version=_clip(payload.get("ANDROID_VERSION")),
        device=_clip(device),
        installation_id=_clip(payload.get("INSTALLATION_ID")),
        stack_trace=_clip(payload.get("STACK_TRACE")),
        user_comment=_clip(payload.get("USER_COMMENT")),
        report=payload,
    )
    with privileged_session() as db:
        db.add(row)
    return {"status": "accepted"}


@router.get("/admin/crash-reports", response_model=CrashReportListOut)
def list_crash_reports(
    db=Depends(get_db),
    _admin: User = Depends(require_admin),
    limit: int = Query(default=50, ge=1, le=200),
    offset: int = Query(default=0, ge=0),
) -> CrashReportListOut:
    rows = db.execute(
        select(CrashReport)
        .order_by(CrashReport.created_at.desc())
        .limit(limit + 1)
        .offset(offset)
    ).scalars().all()
    has_more = len(rows) > limit
    page = rows[:limit]
    return CrashReportListOut(
        items=[CrashReportOut.model_validate(r) for r in page],
        limit=limit,
        offset=offset,
        has_more=has_more,
    )


@router.get("/admin/crash-reports/{crash_id}", response_model=CrashReportOut)
def get_crash_report(
    crash_id: uuid.UUID,
    db=Depends(get_db),
    _admin: User = Depends(require_admin),
) -> CrashReport:
    row = db.execute(select(CrashReport).where(CrashReport.id == crash_id)).scalar_one_or_none()
    if row is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Crash report not found")
    return row
