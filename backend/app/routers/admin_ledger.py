"""Platform (Dofida) own books — admin-only sales & expenses.

The platform operator's own income and expenses, kept separate from any shop's
billing. Every route requires the admin (require_admin) and runs under the
'admin' RLS context, and the underlying tables are admin-only at the database
level too (see the Alembic migration).

Money rules match the rest of the app: all NUMERIC(12,2), quantized 2dp
ROUND_HALF_UP, server-authoritative. For a sale the split
    cash_amount + upi_amount + due_amount == amount
is enforced on every write. Collecting a due later moves money out of
due_amount into cash/upi, preserving the invariant.

Deletes are SOFT. These are the platform's own money records, so removing one
sets `deleted_at`/`deleted_by` rather than destroying the row; every read below
filters `deleted_at IS NULL`, and an admin can list and restore what was
removed. If you add a query here, add that filter — a missing one silently
resurrects deleted money into a total.
"""
from __future__ import annotations

import datetime as dt
import uuid
from decimal import ROUND_HALF_UP, Decimal
from zoneinfo import ZoneInfo

from fastapi import APIRouter, Depends, HTTPException, Query, Response, status
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.auth.dependencies import get_db, require_admin
from app.models.admin_ledger import AdminExpense, AdminSale
from app.models.user import User
from app.schemas.admin_ledger import (
    AdminExpenseCreate,
    AdminExpenseList,
    AdminExpenseOut,
    AdminExpenseUpdate,
    AdminSaleCreate,
    AdminSaleList,
    AdminSaleOut,
    AdminSaleUpdate,
    CollectDueRequest,
    LedgerSummary,
    LedgerTrendPoint,
    TrashedEntry,
    TrashList,
)

router = APIRouter(
    prefix="/admin/ledger",
    tags=["admin-ledger"],
    dependencies=[Depends(require_admin)],
)

SHOP_TZ = ZoneInfo("Asia/Kolkata")
CENT = Decimal("0.01")
ZERO = Decimal("0.00")
MAX_PAGE = 200


def q2(value: Decimal | int) -> Decimal:
    """Quantize to 2 decimals, ROUND_HALF_UP — the project-wide money rule."""
    return Decimal(value).quantize(CENT, rounding=ROUND_HALF_UP)


def _today_ist() -> dt.date:
    return dt.datetime.now(tz=SHOP_TZ).date()


def _live(model):
    """The 'not deleted' predicate. Every read of either ledger table needs it."""
    return model.deleted_at.is_(None)


def _get_live(db: Session, model, row_id: uuid.UUID, what: str):
    """Load a row that exists and hasn't been deleted, or 404.

    A soft-deleted entry must behave as if it were gone: it can't be edited,
    collected against, or deleted twice. Restoring it is the only way back.
    """
    row = db.execute(
        select(model).where(model.id == row_id, _live(model))
    ).scalar_one_or_none()
    if row is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND, detail=f"{what} not found"
        )
    return row


# ── Sales ──────────────────────────────────────────────────────────────────
@router.post("/sales", response_model=AdminSaleOut, status_code=status.HTTP_201_CREATED)
def create_sale(
    payload: AdminSaleCreate,
    db: Session = Depends(get_db),
    admin: User = Depends(require_admin),
) -> AdminSale:
    amount = q2(payload.amount)
    cash = q2(payload.cash_amount)
    upi = q2(payload.upi_amount)
    due = q2(payload.due_amount)
    if cash + upi + due != amount:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail=(
                f"Cash + UPI + Due (₹{cash + upi + due:.2f}) must equal "
                f"the total (₹{amount:.2f})."
            ),
        )
    sale = AdminSale(
        title=payload.title,
        amount=amount,
        cash_amount=cash,
        upi_amount=upi,
        due_amount=due,
        customer_name=payload.customer_name or None,
        customer_phone=payload.customer_phone or None,
        note=payload.note or None,
        occurred_on=payload.occurred_on or _today_ist(),
        created_by=admin.id,
    )
    db.add(sale)
    db.flush()
    db.refresh(sale)
    return sale


@router.get("/sales", response_model=AdminSaleList)
def list_sales(
    date_from: dt.date | None = None,
    date_to: dt.date | None = None,
    due_only: bool = False,
    limit: int = Query(default=20, ge=1, le=MAX_PAGE),
    offset: int = Query(default=0, ge=0),
    db: Session = Depends(get_db),
) -> AdminSaleList:
    stmt = select(AdminSale).where(_live(AdminSale))
    if date_from is not None:
        stmt = stmt.where(AdminSale.occurred_on >= date_from)
    if date_to is not None:
        stmt = stmt.where(AdminSale.occurred_on <= date_to)
    if due_only:
        stmt = stmt.where(AdminSale.due_amount > ZERO)
    # `id` is the tiebreaker: without a unique final sort key, two rows sharing
    # an occurred_on and created_at can swap places between pages, so paging
    # would skip one and repeat the other.
    stmt = stmt.order_by(
        AdminSale.occurred_on.desc(), AdminSale.created_at.desc(), AdminSale.id.desc()
    )
    rows = db.execute(stmt.offset(offset).limit(limit + 1)).scalars().all()
    has_more = len(rows) > limit
    return AdminSaleList(items=rows[:limit], has_more=has_more)


@router.patch("/sales/{sale_id}", response_model=AdminSaleOut)
def update_sale(
    sale_id: uuid.UUID,
    payload: AdminSaleUpdate,
    db: Session = Depends(get_db),
) -> AdminSale:
    sale = _get_live(db, AdminSale, sale_id, "Sale")

    if payload.title is not None:
        sale.title = payload.title
    if payload.customer_name is not None:
        sale.customer_name = payload.customer_name or None
    if payload.customer_phone is not None:
        sale.customer_phone = payload.customer_phone or None
    if payload.note is not None:
        sale.note = payload.note or None
    if payload.occurred_on is not None:
        sale.occurred_on = payload.occurred_on

    # Money fields: recompute against the resulting split and re-check the invariant.
    amount = q2(payload.amount) if payload.amount is not None else sale.amount
    cash = q2(payload.cash_amount) if payload.cash_amount is not None else sale.cash_amount
    upi = q2(payload.upi_amount) if payload.upi_amount is not None else sale.upi_amount
    due = q2(payload.due_amount) if payload.due_amount is not None else sale.due_amount
    if cash + upi + due != amount:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail=(
                f"Cash + UPI + Due (₹{cash + upi + due:.2f}) must equal "
                f"the total (₹{amount:.2f})."
            ),
        )
    sale.amount, sale.cash_amount, sale.upi_amount, sale.due_amount = amount, cash, upi, due

    db.flush()
    db.refresh(sale)
    return sale


@router.post("/sales/{sale_id}/collect", response_model=AdminSaleOut)
def collect_due(
    sale_id: uuid.UUID,
    payload: CollectDueRequest,
    db: Session = Depends(get_db),
) -> AdminSale:
    """Collect part/all of an outstanding due: move it from due into cash/upi."""
    sale = _get_live(db, AdminSale, sale_id, "Sale")

    take = q2(payload.amount)
    if take > sale.due_amount:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail=f"Can't collect more than the ₹{sale.due_amount:.2f} still owed.",
        )
    sale.due_amount = q2(sale.due_amount - take)
    if payload.method == "upi":
        sale.upi_amount = q2(sale.upi_amount + take)
    else:
        sale.cash_amount = q2(sale.cash_amount + take)

    db.flush()
    db.refresh(sale)
    return sale


@router.delete("/sales/{sale_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_sale(
    sale_id: uuid.UUID,
    db: Session = Depends(get_db),
    admin: User = Depends(require_admin),
) -> Response:
    """Soft-delete a sale: hide it everywhere, but keep the row so it can be
    restored and so there's a record of who removed it."""
    sale = _get_live(db, AdminSale, sale_id, "Sale")
    sale.deleted_at = dt.datetime.now(tz=dt.timezone.utc)
    sale.deleted_by = admin.id
    db.flush()
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.post("/sales/{sale_id}/restore", response_model=AdminSaleOut)
def restore_sale(
    sale_id: uuid.UUID,
    db: Session = Depends(get_db),
) -> AdminSale:
    """Put a deleted sale back. No-op if it was never deleted."""
    sale = db.get(AdminSale, sale_id)
    if sale is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Sale not found")
    sale.deleted_at = None
    sale.deleted_by = None
    db.flush()
    db.refresh(sale)
    return sale


# ── Expenses ───────────────────────────────────────────────────────────────
@router.post("/expenses", response_model=AdminExpenseOut, status_code=status.HTTP_201_CREATED)
def create_expense(
    payload: AdminExpenseCreate,
    db: Session = Depends(get_db),
    admin: User = Depends(require_admin),
) -> AdminExpense:
    exp = AdminExpense(
        reason=payload.reason,
        amount=q2(payload.amount),
        payment_method=payload.payment_method,
        note=payload.note or None,
        occurred_on=payload.occurred_on or _today_ist(),
        created_by=admin.id,
    )
    db.add(exp)
    db.flush()
    db.refresh(exp)
    return exp


@router.get("/expenses", response_model=AdminExpenseList)
def list_expenses(
    date_from: dt.date | None = None,
    date_to: dt.date | None = None,
    limit: int = Query(default=20, ge=1, le=MAX_PAGE),
    offset: int = Query(default=0, ge=0),
    db: Session = Depends(get_db),
) -> AdminExpenseList:
    stmt = select(AdminExpense).where(_live(AdminExpense))
    if date_from is not None:
        stmt = stmt.where(AdminExpense.occurred_on >= date_from)
    if date_to is not None:
        stmt = stmt.where(AdminExpense.occurred_on <= date_to)
    stmt = stmt.order_by(
        AdminExpense.occurred_on.desc(),
        AdminExpense.created_at.desc(),
        AdminExpense.id.desc(),
    )
    rows = db.execute(stmt.offset(offset).limit(limit + 1)).scalars().all()
    has_more = len(rows) > limit
    return AdminExpenseList(items=rows[:limit], has_more=has_more)


@router.patch("/expenses/{expense_id}", response_model=AdminExpenseOut)
def update_expense(
    expense_id: uuid.UUID,
    payload: AdminExpenseUpdate,
    db: Session = Depends(get_db),
) -> AdminExpense:
    exp = _get_live(db, AdminExpense, expense_id, "Expense")
    if payload.reason is not None:
        exp.reason = payload.reason
    if payload.amount is not None:
        exp.amount = q2(payload.amount)
    if payload.payment_method is not None:
        exp.payment_method = payload.payment_method
    if payload.note is not None:
        exp.note = payload.note or None
    if payload.occurred_on is not None:
        exp.occurred_on = payload.occurred_on
    db.flush()
    db.refresh(exp)
    return exp


@router.delete("/expenses/{expense_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_expense(
    expense_id: uuid.UUID,
    db: Session = Depends(get_db),
    admin: User = Depends(require_admin),
) -> Response:
    """Soft-delete an expense — see delete_sale."""
    exp = _get_live(db, AdminExpense, expense_id, "Expense")
    exp.deleted_at = dt.datetime.now(tz=dt.timezone.utc)
    exp.deleted_by = admin.id
    db.flush()
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.post("/expenses/{expense_id}/restore", response_model=AdminExpenseOut)
def restore_expense(
    expense_id: uuid.UUID,
    db: Session = Depends(get_db),
) -> AdminExpense:
    """Put a deleted expense back. No-op if it was never deleted."""
    exp = db.get(AdminExpense, expense_id)
    if exp is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Expense not found")
    exp.deleted_at = None
    exp.deleted_by = None
    db.flush()
    db.refresh(exp)
    return exp


# ── Recently deleted ───────────────────────────────────────────────────────
@router.get("/trash", response_model=TrashList)
def list_trash(
    limit: int = Query(default=50, ge=1, le=MAX_PAGE),
    offset: int = Query(default=0, ge=0),
    db: Session = Depends(get_db),
) -> TrashList:
    """Everything an admin has removed, newest first, with who removed it.

    Sales and expenses are merged into one list and sorted together in Python:
    both sides are small (deleted rows are rare), and a SQL UNION of two
    different shapes would cost more in complexity than it saves.
    """
    def _rows(model, label_col, kind: str):
        stmt = (
            select(model, User.email)
            .outerjoin(User, User.id == model.deleted_by)
            .where(model.deleted_at.is_not(None))
            .order_by(model.deleted_at.desc())
            .limit(offset + limit + 1)
        )
        return [
            TrashedEntry(
                kind=kind,
                id=row.id,
                label=getattr(row, label_col),
                amount=row.amount,
                occurred_on=row.occurred_on,
                deleted_at=row.deleted_at,
                deleted_by_email=email,
            )
            for row, email in db.execute(stmt).all()
        ]

    merged = _rows(AdminSale, "title", "sale") + _rows(AdminExpense, "reason", "expense")
    merged.sort(key=lambda e: e.deleted_at, reverse=True)
    page = merged[offset : offset + limit + 1]
    return TrashList(items=page[:limit], has_more=len(page) > limit)


# ── Dashboard summary ──────────────────────────────────────────────────────
@router.get("/summary", response_model=LedgerSummary)
def summary(
    date_from: dt.date | None = None,
    date_to: dt.date | None = None,
    all_time: bool = Query(
        default=False,
        description="Ignore the dates and cover every entry ever recorded.",
    ),
    db: Session = Depends(get_db),
) -> LedgerSummary:
    """Totals + a per-day trend for the mini dashboard, over [date_from, date_to].

    With `all_time`, the window is widened to the earliest entry on record. The
    response always reports the window it actually used in date_from/date_to, so
    the UI can label the figures honestly instead of implying they're lifetime
    totals when they cover 30 days.
    """
    to_day = date_to or _today_ist()
    from_day = date_from or (to_day - dt.timedelta(days=29))

    if all_time:
        earliest = db.execute(
            select(
                func.least(
                    select(func.min(AdminSale.occurred_on))
                    .where(_live(AdminSale))
                    .scalar_subquery(),
                    select(func.min(AdminExpense.occurred_on))
                    .where(_live(AdminExpense))
                    .scalar_subquery(),
                )
            )
        ).scalar()
        # No entries at all yet — leave the default window so the empty state
        # doesn't render a nonsensical date range.
        from_day = earliest or from_day
        to_day = max(to_day, from_day)

    sale_totals = db.execute(
        select(
            func.coalesce(func.sum(AdminSale.amount), 0),
            func.coalesce(func.sum(AdminSale.cash_amount), 0),
            func.coalesce(func.sum(AdminSale.upi_amount), 0),
            func.coalesce(func.sum(AdminSale.due_amount), 0),
            func.count(AdminSale.id),
        ).where(
            _live(AdminSale),
            AdminSale.occurred_on >= from_day,
            AdminSale.occurred_on <= to_day,
        )
    ).one()
    total_sales, cash_collected, upi_collected, outstanding_due, sales_count = sale_totals

    # Money still owed, across ALL time. The windowed `outstanding_due` above
    # silently drops a sale from three months ago that was never paid, which is
    # why this tile used to disagree with the Outstanding dues tab beside it.
    due_all_time, dues_count = db.execute(
        select(
            func.coalesce(func.sum(AdminSale.due_amount), 0),
            func.count(AdminSale.id),
        ).where(_live(AdminSale), AdminSale.due_amount > ZERO)
    ).one()

    exp_totals = db.execute(
        select(
            func.coalesce(func.sum(AdminExpense.amount), 0),
            func.count(AdminExpense.id),
        ).where(
            _live(AdminExpense),
            AdminExpense.occurred_on >= from_day,
            AdminExpense.occurred_on <= to_day,
        )
    ).one()
    total_expenses, expenses_count = exp_totals

    # Per-day trend (only days that have activity; the client fills gaps).
    sales_by_day = dict(
        db.execute(
            select(AdminSale.occurred_on, func.coalesce(func.sum(AdminSale.amount), 0))
            .where(
                _live(AdminSale),
                AdminSale.occurred_on >= from_day,
                AdminSale.occurred_on <= to_day,
            )
            .group_by(AdminSale.occurred_on)
        ).all()
    )
    exp_by_day = dict(
        db.execute(
            select(AdminExpense.occurred_on, func.coalesce(func.sum(AdminExpense.amount), 0))
            .where(
                _live(AdminExpense),
                AdminExpense.occurred_on >= from_day,
                AdminExpense.occurred_on <= to_day,
            )
            .group_by(AdminExpense.occurred_on)
        ).all()
    )
    days = sorted(set(sales_by_day) | set(exp_by_day))
    trend = [
        LedgerTrendPoint(
            date=d,
            sales=q2(sales_by_day.get(d, 0)),
            expenses=q2(exp_by_day.get(d, 0)),
        )
        for d in days
    ]

    net_collected = q2(Decimal(cash_collected) + Decimal(upi_collected) - Decimal(total_expenses))

    return LedgerSummary(
        date_from=from_day,
        date_to=to_day,
        total_sales=q2(total_sales),
        sales_count=sales_count,
        cash_collected=q2(cash_collected),
        upi_collected=q2(upi_collected),
        outstanding_due=q2(outstanding_due),
        outstanding_due_all_time=q2(due_all_time),
        dues_count_all_time=dues_count,
        total_expenses=q2(total_expenses),
        expenses_count=expenses_count,
        net_collected=net_collected,
        trend=trend,
    )
