"""Multi-shop owner area: oversight analytics + business details + staff.

The owner role has shop_id NULL and owns shops via shops.owner_id. RLS lets an
owner read/write rows for every shop they own, so — unlike the single-shop
endpoints — every query here is explicit about shop_id (otherwise an owner with
several shops would see them mixed together).

Owners do NOT bill or manage products; those live in the manager/salesperson
endpoints. Owners cannot create or delete shops (admin only).
"""
from __future__ import annotations

import datetime as dt
import uuid
from decimal import Decimal

from fastapi import APIRouter, Depends, HTTPException, Query, Response, status
from sqlalchemy import func, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.auth.dependencies import get_db, require_owner
from app.auth.security import hash_password
from app.models.bill import Bill
from app.models.expense import Expense
from app.models.shop import Shop
from app.models.user import ROLE_MANAGER, ROLE_SALESPERSON, User
from app.routers.bills import (
    SHOP_TZ,
    _generate_report_data,
    _ist_day_bounds_utc,
    _today_ist,
    q2,
)
from app.schemas.report import DetailedReportResponse
from app.schemas.owner import (
    OwnerOverview,
    OwnerShop,
    OwnerShopUpdate,
    OwnerStaffActivate,
    OwnerStaffCreate,
    OwnerStaffOut,
    OwnerStaffResetPassword,
    ShopOverviewRow,
    StaffPerformance,
)

router = APIRouter(prefix="/owner", tags=["owner"], dependencies=[Depends(require_owner)])

ZERO = Decimal("0.00")


def _owned_shop_or_404(db: Session, owner: User, shop_id: uuid.UUID) -> Shop:
    """Fetch a shop the owner owns, or 404. RLS already hides non-owned shops."""
    shop = db.execute(
        select(Shop).where(Shop.id == shop_id, Shop.owner_id == owner.id)
    ).scalar_one_or_none()
    if shop is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Shop not found")
    return shop


# ── Shops + business details ──────────────────────────────────────────────────
@router.get("/shops", response_model=list[OwnerShop])
def list_owned_shops(db: Session = Depends(get_db), owner: User = Depends(require_owner)) -> list[Shop]:
    rows = db.execute(
        select(Shop).where(Shop.owner_id == owner.id).order_by(Shop.name.asc())
    ).scalars().all()
    return list(rows)


@router.patch("/shops/{shop_id}", response_model=OwnerShop)
def update_shop_details(
    shop_id: uuid.UUID,
    payload: OwnerShopUpdate,
    db: Session = Depends(get_db),
    owner: User = Depends(require_owner),
) -> Shop:
    shop = _owned_shop_or_404(db, owner, shop_id)
    if payload.business_name is not None:
        shop.business_name = payload.business_name
    if payload.business_address is not None:
        shop.business_address = payload.business_address
    if payload.business_phone is not None:
        shop.business_phone = payload.business_phone
    if payload.business_email is not None:
        shop.business_email = payload.business_email
    if payload.business_upi is not None:
        shop.business_upi = payload.business_upi
    if payload.whatsapp_auto_send is not None:
        shop.whatsapp_auto_send = payload.whatsapp_auto_send
    if payload.whatsapp_message_template is not None:
        shop.whatsapp_message_template = payload.whatsapp_message_template
    db.flush()
    db.refresh(shop)
    return shop


# ── Per-shop detailed report (reuses the shop report generator) ───────────────
@router.get("/shops/{shop_id}/report", response_model=DetailedReportResponse)
def shop_report(
    shop_id: uuid.UUID,
    date_from: dt.date | None = Query(default=None),
    date_to: dt.date | None = Query(default=None),
    created_by: uuid.UUID | None = Query(default=None),
    db: Session = Depends(get_db),
    owner: User = Depends(require_owner),
) -> DetailedReportResponse:
    _owned_shop_or_404(db, owner, shop_id)
    today = _today_ist()
    return _generate_report_data(db, shop_id, date_from or today, date_to or today, created_by)


# ── Aggregate overview across all owned shops ─────────────────────────────────
@router.get("/overview", response_model=OwnerOverview)
def overview(
    date_from: dt.date | None = Query(default=None),
    date_to: dt.date | None = Query(default=None),
    db: Session = Depends(get_db),
    owner: User = Depends(require_owner),
) -> OwnerOverview:
    today = _today_ist()
    d_from = date_from or today
    d_to = date_to or today
    start, end = _ist_day_bounds_utc(d_from)[0], _ist_day_bounds_utc(d_to)[1]

    shops = db.execute(
        select(Shop).where(Shop.owner_id == owner.id).order_by(Shop.name.asc())
    ).scalars().all()
    shop_name = {s.id: s.name for s in shops}
    shop_ids = list(shop_name.keys())

    rows: list[ShopOverviewRow] = []
    agg_sales = agg_cash = agg_upi = agg_due = agg_exp = ZERO
    agg_bills = 0

    if shop_ids:
        sales_rows = db.execute(
            select(
                Bill.shop_id,
                func.coalesce(func.sum(Bill.total), 0),
                func.count(Bill.id),
                func.coalesce(func.sum(Bill.cash_amount), 0),
                func.coalesce(func.sum(Bill.upi_amount), 0),
                func.coalesce(func.sum(Bill.due_amount), 0),
            )
            .where(Bill.shop_id.in_(shop_ids), Bill.created_at >= start, Bill.created_at < end)
            .group_by(Bill.shop_id)
        ).all()
        sales_by_shop = {r[0]: r for r in sales_rows}

        exp_rows = db.execute(
            select(Expense.shop_id, func.coalesce(func.sum(Expense.amount), 0))
            .where(Expense.shop_id.in_(shop_ids), Expense.created_at >= start, Expense.created_at < end)
            .group_by(Expense.shop_id)
        ).all()
        exp_by_shop = {r[0]: r[1] for r in exp_rows}

        for sid in shop_ids:
            sr = sales_by_shop.get(sid)
            total = q2(sr[1]) if sr else ZERO
            count = int(sr[2]) if sr else 0
            cash = q2(sr[3]) if sr else ZERO
            upi = q2(sr[4]) if sr else ZERO
            due = q2(sr[5]) if sr else ZERO
            exp = q2(exp_by_shop.get(sid, 0))
            rows.append(ShopOverviewRow(
                shop_id=sid, shop_name=shop_name[sid], total_sales=total, bill_count=count,
                cash_total=cash, upi_total=upi, due_total=due, total_expenses=exp,
                net_sales=q2(total - exp),
            ))
            agg_sales += total; agg_cash += cash; agg_upi += upi; agg_due += due
            agg_exp += exp; agg_bills += count

    # Staff leaderboard: sales grouped by who created the bill, across owned shops.
    staff: list[StaffPerformance] = []
    if shop_ids:
        perf_rows = db.execute(
            select(
                Bill.created_by,
                Bill.shop_id,
                User.email,
                User.role,
                func.coalesce(func.sum(Bill.total), 0),
                func.count(Bill.id),
            )
            .outerjoin(User, User.id == Bill.created_by)
            .where(Bill.shop_id.in_(shop_ids), Bill.created_at >= start, Bill.created_at < end)
            .group_by(Bill.created_by, Bill.shop_id, User.email, User.role)
            .order_by(func.sum(Bill.total).desc())
        ).all()
        for created_by, sid, email, role, total, count in perf_rows:
            staff.append(StaffPerformance(
                user_id=created_by, email=email, shop_id=sid, shop_name=shop_name.get(sid, "—"),
                role=role or "—", total_sales=q2(total), bill_count=int(count),
            ))

    return OwnerOverview(
        start_date=d_from, end_date=d_to, shop_count=len(shop_ids),
        total_sales=q2(agg_sales), bill_count=agg_bills, cash_total=q2(agg_cash),
        upi_total=q2(agg_upi), due_total=q2(agg_due), total_expenses=q2(agg_exp),
        net_sales=q2(agg_sales - agg_exp), shops=rows, staff=staff,
    )


# ── Staff management scoped to one owned shop ─────────────────────────────────
def _staff_query(shop_id: uuid.UUID):
    return select(User).where(
        User.shop_id == shop_id,
        User.role.in_((ROLE_MANAGER, ROLE_SALESPERSON)),
    )


@router.get("/shops/{shop_id}/staff", response_model=list[OwnerStaffOut])
def list_staff(
    shop_id: uuid.UUID,
    db: Session = Depends(get_db),
    owner: User = Depends(require_owner),
) -> list[User]:
    _owned_shop_or_404(db, owner, shop_id)
    rows = db.execute(_staff_query(shop_id).order_by(User.role.asc(), User.created_at.desc())).scalars().all()
    return list(rows)


@router.post("/shops/{shop_id}/staff", response_model=OwnerStaffOut, status_code=status.HTTP_201_CREATED)
def create_staff(
    shop_id: uuid.UUID,
    payload: OwnerStaffCreate,
    db: Session = Depends(get_db),
    owner: User = Depends(require_owner),
) -> User:
    _owned_shop_or_404(db, owner, shop_id)
    if db.execute(select(User).where(User.email == str(payload.email))).scalar_one_or_none() is not None:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="A user with this email already exists")
    user = User(
        shop_id=shop_id,
        email=str(payload.email),
        password_hash=hash_password(payload.password),
        role=payload.role,
    )
    db.add(user)
    try:
        db.flush()
    except IntegrityError:
        db.rollback()
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="A user with this email already exists")
    db.refresh(user)
    return user


def _staff_or_404(db: Session, shop_id: uuid.UUID, user_id: uuid.UUID) -> User:
    user = db.execute(_staff_query(shop_id).where(User.id == user_id)).scalar_one_or_none()
    if user is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Staff member not found")
    return user


@router.patch("/shops/{shop_id}/staff/{user_id}", response_model=OwnerStaffOut)
def set_staff_active(
    shop_id: uuid.UUID,
    user_id: uuid.UUID,
    payload: OwnerStaffActivate,
    db: Session = Depends(get_db),
    owner: User = Depends(require_owner),
) -> User:
    _owned_shop_or_404(db, owner, shop_id)
    user = _staff_or_404(db, shop_id, user_id)
    user.is_active = payload.is_active
    db.flush()
    db.refresh(user)
    return user


@router.post("/shops/{shop_id}/staff/{user_id}/reset-password", response_model=OwnerStaffOut)
def reset_staff_password(
    shop_id: uuid.UUID,
    user_id: uuid.UUID,
    payload: OwnerStaffResetPassword,
    db: Session = Depends(get_db),
    owner: User = Depends(require_owner),
) -> User:
    _owned_shop_or_404(db, owner, shop_id)
    user = _staff_or_404(db, shop_id, user_id)
    user.password_hash = hash_password(payload.new_password)
    db.flush()
    db.refresh(user)
    return user


@router.delete("/shops/{shop_id}/staff/{user_id}", status_code=status.HTTP_204_NO_CONTENT, response_class=Response)
def delete_staff(
    shop_id: uuid.UUID,
    user_id: uuid.UUID,
    db: Session = Depends(get_db),
    owner: User = Depends(require_owner),
):
    _owned_shop_or_404(db, owner, shop_id)
    user = _staff_or_404(db, shop_id, user_id)
    db.delete(user)
    db.flush()
