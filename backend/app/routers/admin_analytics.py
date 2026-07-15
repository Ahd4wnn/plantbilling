"""Admin command-center analytics: cross-shop overview, per-shop detail, staff.

Every route requires the platform admin. Admin operates under the 'admin' RLS
context, so these queries naturally span all shops. Aggregates are computed in a
single grouped pass per metric (never per-shop N+1). Day boundaries are
Asia/Kolkata, money stays server-authoritative via the bills.py helpers.
"""
from __future__ import annotations

import datetime as dt
import uuid
from decimal import Decimal

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import Date, cast, func, select
from sqlalchemy.orm import Session

from app.auth.dependencies import get_db, require_admin
from app.models.bill import Bill
from app.models.customer import Customer
from app.models.expense import Expense
from app.models.shop import Shop
from app.models.shop_owner import ShopOwner
from app.models.user import ROLE_MANAGER, ROLE_SALESPERSON, User
from app.routers.bills import (
    SHOP_TZ,
    _generate_report_data,
    _ist_day_bounds_utc,
    _payment_method,
    _today_ist,
    cash_flows_through,
    q2,
)
from app.schemas.admin_analytics import (
    AdminOverview,
    AdminRecentBill,
    AdminShopDetail,
    AdminShopRow,
    AdminStaffPerformance,
    AdminStaffRow,
    AttentionItem,
    TrendPoint,
)

router = APIRouter(
    prefix="/admin",
    tags=["admin-analytics"],
    dependencies=[Depends(require_admin)],
)

ZERO = Decimal("0.00")

# IST calendar date of a timestamptz, for day-bucketed trend series.
_IST_DAY = cast(func.timezone("Asia/Kolkata", Bill.created_at), Date)


@router.get("/overview", response_model=AdminOverview)
def admin_overview(
    date_from: dt.date | None = Query(default=None),
    date_to: dt.date | None = Query(default=None),
    silent_days: int = Query(default=7, ge=1, le=90),
    db: Session = Depends(get_db),
) -> AdminOverview:
    today = _today_ist()
    d_from = date_from or today
    d_to = date_to or today
    start, end = _ist_day_bounds_utc(d_from)[0], _ist_day_bounds_utc(d_to)[1]

    # All shops + their manager login.
    shop_rows = db.execute(
        select(
            Shop.id, Shop.name, Shop.is_active, Shop.created_at, User.email
        )
        .outerjoin(User, (User.shop_id == Shop.id) & (User.role == ROLE_MANAGER))
        .order_by(Shop.name.asc())
    ).all()
    shop_name = {r.id: r.name for r in shop_rows}

    # Sales aggregated per shop within the period.
    sales_by_shop = {
        r[0]: r
        for r in db.execute(
            select(
                Bill.shop_id,
                func.coalesce(func.sum(Bill.total), 0),
                func.count(Bill.id),
                func.coalesce(func.sum(Bill.cash_amount), 0),
                func.coalesce(func.sum(Bill.upi_amount), 0),
                func.coalesce(func.sum(Bill.due_amount), 0),
            )
            .where(Bill.created_at >= start, Bill.created_at < end)
            .group_by(Bill.shop_id)
        ).all()
    }
    exp_by_shop = {
        r[0]: r[1]
        for r in db.execute(
            select(Expense.shop_id, func.coalesce(func.sum(Expense.amount), 0))
            .where(Expense.created_at >= start, Expense.created_at < end)
            .group_by(Expense.shop_id)
        ).all()
    }
    # Most recent bill ever, per shop (health / churn signal).
    last_bill_by_shop = dict(
        db.execute(select(Bill.shop_id, func.max(Bill.created_at)).group_by(Bill.shop_id)).all()
    )
    # Staff count per shop.
    staff_by_shop = dict(
        db.execute(
            select(User.shop_id, func.count())
            .where(User.role.in_((ROLE_MANAGER, ROLE_SALESPERSON)))
            .group_by(User.shop_id)
        ).all()
    )
    owned_shop_ids = {
        r[0] for r in db.execute(select(ShopOwner.shop_id).distinct()).all()
    }

    shops: list[AdminShopRow] = []
    agg_sales = agg_cash = agg_upi = agg_due = agg_exp = ZERO
    agg_bills = active = 0
    attention: list[AttentionItem] = []
    silent_cutoff = dt.datetime.now(tz=dt.timezone.utc) - dt.timedelta(days=silent_days)

    for r in shop_rows:
        sr = sales_by_shop.get(r.id)
        total = q2(sr[1]) if sr else ZERO
        count = int(sr[2]) if sr else 0
        cash = q2(sr[3]) if sr else ZERO
        upi = q2(sr[4]) if sr else ZERO
        due = q2(sr[5]) if sr else ZERO
        exp = q2(exp_by_shop.get(r.id, 0))
        last_bill = last_bill_by_shop.get(r.id)
        shops.append(AdminShopRow(
            shop_id=r.id, shop_name=r.name, is_active=r.is_active, owner_email=r.email,
            total_sales=total, bill_count=count, cash_total=cash, upi_total=upi,
            due_total=due, total_expenses=exp, net_sales=q2(total - exp),
            staff_count=int(staff_by_shop.get(r.id, 0)), last_bill_at=last_bill,
        ))
        agg_sales += total; agg_cash += cash; agg_upi += upi; agg_due += due
        agg_exp += exp; agg_bills += count
        if r.is_active:
            active += 1

        # Attention list.
        if not r.is_active:
            attention.append(AttentionItem(
                shop_id=r.id, shop_name=r.name, kind="inactive", detail="Deactivated",
            ))
        else:
            if r.id not in owned_shop_ids:
                attention.append(AttentionItem(
                    shop_id=r.id, shop_name=r.name, kind="no_owner",
                    detail="No owner assigned",
                ))
            # Silent = created a while ago but no recent bills.
            created_old = r.created_at is not None and r.created_at < silent_cutoff
            if created_old and (last_bill is None or last_bill < silent_cutoff):
                if last_bill is None:
                    detail = "Never billed"
                else:
                    days = (dt.datetime.now(tz=dt.timezone.utc) - last_bill).days
                    detail = f"No bills in {days} day{'s' if days != 1 else ''}"
                attention.append(AttentionItem(
                    shop_id=r.id, shop_name=r.name, kind="silent", detail=detail,
                ))

    # Trend: platform-wide sales per IST day across the period.
    trend_rows = db.execute(
        select(
            _IST_DAY.label("day"),
            func.coalesce(func.sum(Bill.total), 0),
            func.count(Bill.id),
        )
        .where(Bill.created_at >= start, Bill.created_at < end)
        .group_by(_IST_DAY)
        .order_by(_IST_DAY)
    ).all()
    by_day = {row.day: row for row in trend_rows}
    trend: list[TrendPoint] = []
    cursor = d_from
    while cursor <= d_to:
        row = by_day.get(cursor)
        trend.append(TrendPoint(
            date=cursor,
            sales=q2(row[1]) if row else ZERO,
            bill_count=int(row[2]) if row else 0,
        ))
        cursor += dt.timedelta(days=1)

    # Top sellers across every shop in the period.
    staff: list[AdminStaffPerformance] = []
    perf_rows = db.execute(
        select(
            Bill.created_by, Bill.shop_id, User.email, User.role,
            func.coalesce(func.sum(Bill.total), 0), func.count(Bill.id),
        )
        .outerjoin(User, User.id == Bill.created_by)
        .where(Bill.created_at >= start, Bill.created_at < end)
        .group_by(Bill.created_by, Bill.shop_id, User.email, User.role)
        .order_by(func.sum(Bill.total).desc())
        .limit(10)
    ).all()
    for created_by, sid, email, role, total, count in perf_rows:
        staff.append(AdminStaffPerformance(
            user_id=created_by, email=email, shop_id=sid,
            shop_name=shop_name.get(sid, "—"), role=role or "—",
            total_sales=q2(total), bill_count=int(count),
        ))

    # Order attention: inactive first, then no_owner, then silent.
    order = {"inactive": 0, "no_owner": 1, "silent": 2}
    attention.sort(key=lambda a: order.get(a.kind, 9))

    return AdminOverview(
        start_date=d_from, end_date=d_to,
        total_shops=len(shop_rows), active_shops=active,
        total_sales=q2(agg_sales), bill_count=agg_bills, cash_total=q2(agg_cash),
        upi_total=q2(agg_upi), due_total=q2(agg_due), total_expenses=q2(agg_exp),
        net_sales=q2(agg_sales - agg_exp), shops=shops, trend=trend,
        attention=attention, staff=staff,
    )


@router.get("/shops/{shop_id}/detail", response_model=AdminShopDetail)
def admin_shop_detail(
    shop_id: uuid.UUID,
    db: Session = Depends(get_db),
) -> AdminShopDetail:
    shop = db.execute(select(Shop).where(Shop.id == shop_id)).scalar_one_or_none()
    if shop is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Shop not found")

    today = _today_ist()
    report = _generate_report_data(db, shop_id, today - dt.timedelta(days=29), today, None)

    owner_email = db.execute(
        select(User.email).where(User.shop_id == shop_id, User.role == ROLE_MANAGER)
    ).scalar_one_or_none()
    staff_count = db.execute(
        select(func.count()).where(
            User.shop_id == shop_id, User.role.in_((ROLE_MANAGER, ROLE_SALESPERSON))
        )
    ).scalar_one()
    last_bill_at = db.execute(
        select(func.max(Bill.created_at)).where(Bill.shop_id == shop_id)
    ).scalar_one()

    now_utc = dt.datetime.now(tz=dt.timezone.utc)
    running = q2(Decimal(shop.cash_in_hand_base) + cash_flows_through(db, shop_id, now_utc))

    bill_rows = db.execute(
        select(
            Bill.id, Bill.created_at, Bill.total, Bill.cash_amount, Bill.upi_amount,
            Bill.due_amount, Customer.name, User.email,
        )
        .outerjoin(Customer, Customer.id == Bill.customer_id)
        .outerjoin(User, User.id == Bill.created_by)
        .where(Bill.shop_id == shop_id)
        .order_by(Bill.created_at.desc(), Bill.id.desc())
        .limit(10)
    ).all()
    recent = [
        AdminRecentBill(
            id=b.id, created_at=b.created_at, total=q2(b.total),
            payment_method=_payment_method(b.cash_amount, b.upi_amount, b.due_amount),
            customer_name=b.name, salesperson_email=b.email,
        )
        for b in bill_rows
    ]

    return AdminShopDetail(
        shop_id=shop.id, shop_name=shop.name, is_active=shop.is_active,
        business_name=shop.business_name, business_address=shop.business_address,
        business_phone=shop.business_phone, business_email=shop.business_email,
        business_upi=shop.business_upi, owner_email=owner_email,
        cash_in_hand_running=running, last_bill_at=last_bill_at,
        staff_count=int(staff_count), report=report.model_dump(mode="json"),
        recent_bills=recent,
    )


@router.get("/staff", response_model=list[AdminStaffRow])
def admin_staff_directory(
    db: Session = Depends(get_db),
) -> list[AdminStaffRow]:
    """Every manager + salesperson across all shops, with lifetime billing stats."""
    users = db.execute(
        select(User.id, User.email, User.role, User.is_active, User.shop_id,
               User.created_at, Shop.name)
        .outerjoin(Shop, Shop.id == User.shop_id)
        .where(User.role.in_((ROLE_MANAGER, ROLE_SALESPERSON)))
        .order_by(Shop.name.asc(), User.role.asc())
    ).all()

    perf = {
        r[0]: r
        for r in db.execute(
            select(
                Bill.created_by,
                func.coalesce(func.sum(Bill.total), 0),
                func.count(Bill.id),
                func.max(Bill.created_at),
            ).group_by(Bill.created_by)
        ).all()
    }

    out: list[AdminStaffRow] = []
    for u in users:
        p = perf.get(u.id)
        out.append(AdminStaffRow(
            user_id=u.id, email=u.email, role=u.role, is_active=u.is_active,
            shop_id=u.shop_id, shop_name=u.name, created_at=u.created_at,
            total_sales=q2(p[1]) if p else ZERO,
            bill_count=int(p[2]) if p else 0,
            last_bill_at=p[3] if p else None,
        ))
    return out
