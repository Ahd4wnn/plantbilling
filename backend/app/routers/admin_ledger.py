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
"""
from __future__ import annotations

import datetime as dt
import uuid
from decimal import ROUND_HALF_UP, Decimal
from zoneinfo import ZoneInfo

from fastapi import APIRouter, Depends, HTTPException, Query, Response, status
from sqlalchemy import delete, func, select
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
)

router = APIRouter(
    prefix="/admin/ledger",
    tags=["admin-ledger"],
    dependencies=[Depends(require_admin)],
)

SHOP_TZ = ZoneInfo("Asia/Kolkata")
CENT = Decimal("0.01")
ZERO = Decimal("0.00")
MAX_PAGE = 100


def q2(value: Decimal | int) -> Decimal:
    """Quantize to 2 decimals, ROUND_HALF_UP — the project-wide money rule."""
    return Decimal(value).quantize(CENT, rounding=ROUND_HALF_UP)


def _today_ist() -> dt.date:
    return dt.datetime.now(tz=SHOP_TZ).date()


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
    stmt = select(AdminSale)
    if date_from is not None:
        stmt = stmt.where(AdminSale.occurred_on >= date_from)
    if date_to is not None:
        stmt = stmt.where(AdminSale.occurred_on <= date_to)
    if due_only:
        stmt = stmt.where(AdminSale.due_amount > ZERO)
    stmt = stmt.order_by(AdminSale.occurred_on.desc(), AdminSale.created_at.desc())
    rows = db.execute(stmt.offset(offset).limit(limit + 1)).scalars().all()
    has_more = len(rows) > limit
    return AdminSaleList(items=rows[:limit], has_more=has_more)


@router.patch("/sales/{sale_id}", response_model=AdminSaleOut)
def update_sale(
    sale_id: uuid.UUID,
    payload: AdminSaleUpdate,
    db: Session = Depends(get_db),
) -> AdminSale:
    sale = db.get(AdminSale, sale_id)
    if sale is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Sale not found")

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
    sale = db.get(AdminSale, sale_id)
    if sale is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Sale not found")

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
def delete_sale(sale_id: uuid.UUID, db: Session = Depends(get_db)) -> Response:
    result = db.execute(delete(AdminSale).where(AdminSale.id == sale_id))
    if result.rowcount == 0:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Sale not found")
    return Response(status_code=status.HTTP_204_NO_CONTENT)


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
    stmt = select(AdminExpense)
    if date_from is not None:
        stmt = stmt.where(AdminExpense.occurred_on >= date_from)
    if date_to is not None:
        stmt = stmt.where(AdminExpense.occurred_on <= date_to)
    stmt = stmt.order_by(AdminExpense.occurred_on.desc(), AdminExpense.created_at.desc())
    rows = db.execute(stmt.offset(offset).limit(limit + 1)).scalars().all()
    has_more = len(rows) > limit
    return AdminExpenseList(items=rows[:limit], has_more=has_more)


@router.patch("/expenses/{expense_id}", response_model=AdminExpenseOut)
def update_expense(
    expense_id: uuid.UUID,
    payload: AdminExpenseUpdate,
    db: Session = Depends(get_db),
) -> AdminExpense:
    exp = db.get(AdminExpense, expense_id)
    if exp is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Expense not found")
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
def delete_expense(expense_id: uuid.UUID, db: Session = Depends(get_db)) -> Response:
    result = db.execute(delete(AdminExpense).where(AdminExpense.id == expense_id))
    if result.rowcount == 0:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Expense not found")
    return Response(status_code=status.HTTP_204_NO_CONTENT)


# ── Dashboard summary ──────────────────────────────────────────────────────
@router.get("/summary", response_model=LedgerSummary)
def summary(
    date_from: dt.date | None = None,
    date_to: dt.date | None = None,
    db: Session = Depends(get_db),
) -> LedgerSummary:
    """Totals + a per-day trend for the mini dashboard, over [date_from, date_to]."""
    to_day = date_to or _today_ist()
    from_day = date_from or (to_day - dt.timedelta(days=29))

    sale_totals = db.execute(
        select(
            func.coalesce(func.sum(AdminSale.amount), 0),
            func.coalesce(func.sum(AdminSale.cash_amount), 0),
            func.coalesce(func.sum(AdminSale.upi_amount), 0),
            func.coalesce(func.sum(AdminSale.due_amount), 0),
            func.count(AdminSale.id),
        ).where(
            AdminSale.occurred_on >= from_day,
            AdminSale.occurred_on <= to_day,
        )
    ).one()
    total_sales, cash_collected, upi_collected, outstanding_due, sales_count = sale_totals

    exp_totals = db.execute(
        select(
            func.coalesce(func.sum(AdminExpense.amount), 0),
            func.count(AdminExpense.id),
        ).where(
            AdminExpense.occurred_on >= from_day,
            AdminExpense.occurred_on <= to_day,
        )
    ).one()
    total_expenses, expenses_count = exp_totals

    # Per-day trend (only days that have activity; the client fills gaps).
    sales_by_day = dict(
        db.execute(
            select(AdminSale.occurred_on, func.coalesce(func.sum(AdminSale.amount), 0))
            .where(AdminSale.occurred_on >= from_day, AdminSale.occurred_on <= to_day)
            .group_by(AdminSale.occurred_on)
        ).all()
    )
    exp_by_day = dict(
        db.execute(
            select(AdminExpense.occurred_on, func.coalesce(func.sum(AdminExpense.amount), 0))
            .where(AdminExpense.occurred_on >= from_day, AdminExpense.occurred_on <= to_day)
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
        total_expenses=q2(total_expenses),
        expenses_count=expenses_count,
        net_collected=net_collected,
        trend=trend,
    )
