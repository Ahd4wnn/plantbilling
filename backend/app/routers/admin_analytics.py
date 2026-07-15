"""Admin command-center analytics: PLATFORM health, not shop finances.

PlantBill is a product sold to independent shops; their sales/cash are their own
private data. So these endpoints deliberately expose only SaaS-operator signals:
shop counts, active vs quiet vs unowned shops, onboarding, staff, and app usage
measured as bill *counts* (engagement) — never money.

Admin operates under the 'admin' RLS context, so queries span all shops.
Aggregates are single grouped passes (no per-shop N+1). Days are Asia/Kolkata.
"""
from __future__ import annotations

import datetime as dt
import uuid

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import Date, cast, func, select
from sqlalchemy.orm import Session

from app.auth.dependencies import get_db, require_admin
from app.models.bill import Bill, BillItem
from app.models.customer import Customer
from app.models.product import Product
from app.models.shop import Shop
from app.models.shop_owner import ShopOwner
from app.models.user import ROLE_MANAGER, ROLE_OWNER, ROLE_SALESPERSON, User
from app.routers.bills import _ist_day_bounds_utc, _today_ist
from app.schemas.admin_analytics import (
    AdminActivity,
    AdminOverview,
    AdminShopDetail,
    AdminShopRow,
    AdminStaffRow,
    AttentionItem,
    TrendPoint,
)

router = APIRouter(
    prefix="/admin",
    tags=["admin-analytics"],
    dependencies=[Depends(require_admin)],
)

# IST calendar date of a timestamptz, for day-bucketed trends.
_BILL_DAY = cast(func.timezone("Asia/Kolkata", Bill.created_at), Date)
_SHOP_DAY = cast(func.timezone("Asia/Kolkata", Shop.created_at), Date)


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
        select(Shop.id, Shop.name, Shop.is_active, Shop.created_at, User.email)
        .outerjoin(User, (User.shop_id == Shop.id) & (User.role == ROLE_MANAGER))
        .order_by(Shop.name.asc())
    ).all()

    # Bill counts per shop within the period (engagement, not money).
    bills_by_shop = dict(
        db.execute(
            select(Bill.shop_id, func.count(Bill.id))
            .where(Bill.created_at >= start, Bill.created_at < end)
            .group_by(Bill.shop_id)
        ).all()
    )
    last_bill_by_shop = dict(
        db.execute(select(Bill.shop_id, func.max(Bill.created_at)).group_by(Bill.shop_id)).all()
    )
    staff_by_shop = dict(
        db.execute(
            select(User.shop_id, func.count())
            .where(User.role.in_((ROLE_MANAGER, ROLE_SALESPERSON)))
            .group_by(User.shop_id)
        ).all()
    )
    owners_by_shop = dict(
        db.execute(select(ShopOwner.shop_id, func.count()).group_by(ShopOwner.shop_id)).all()
    )

    shops: list[AdminShopRow] = []
    attention: list[AttentionItem] = []
    active = inactive = new_shops = total_bills = 0
    now_utc = dt.datetime.now(tz=dt.timezone.utc)
    silent_cutoff = now_utc - dt.timedelta(days=silent_days)

    for r in shop_rows:
        bills = int(bills_by_shop.get(r.id, 0))
        last_bill = last_bill_by_shop.get(r.id)
        owner_count = int(owners_by_shop.get(r.id, 0))
        shops.append(AdminShopRow(
            shop_id=r.id, shop_name=r.name, is_active=r.is_active, owner_email=r.email,
            owner_count=owner_count, created_at=r.created_at, bills_in_period=bills,
            staff_count=int(staff_by_shop.get(r.id, 0)), last_bill_at=last_bill,
        ))
        total_bills += bills
        if not r.is_active:
            inactive += 1
        if bills > 0:
            active += 1
        if r.created_at is not None and start <= r.created_at < end:
            new_shops += 1

        # Attention list.
        if not r.is_active:
            attention.append(AttentionItem(shop_id=r.id, shop_name=r.name, kind="inactive", detail="Deactivated"))
        else:
            if owner_count == 0:
                attention.append(AttentionItem(shop_id=r.id, shop_name=r.name, kind="no_owner", detail="No owner assigned"))
            created_old = r.created_at is not None and r.created_at < silent_cutoff
            if created_old and (last_bill is None or last_bill < silent_cutoff):
                if last_bill is None:
                    detail = "Never billed"
                else:
                    days = (now_utc - last_bill).days
                    detail = f"No bills in {days} day{'s' if days != 1 else ''}"
                attention.append(AttentionItem(shop_id=r.id, shop_name=r.name, kind="silent", detail=detail))

    # Trend: bills/day (usage) + shops onboarded/day (growth).
    bill_days = dict(
        db.execute(
            select(_BILL_DAY, func.count(Bill.id))
            .where(Bill.created_at >= start, Bill.created_at < end)
            .group_by(_BILL_DAY)
        ).all()
    )
    shop_days = dict(
        db.execute(
            select(_SHOP_DAY, func.count(Shop.id))
            .where(Shop.created_at >= start, Shop.created_at < end)
            .group_by(_SHOP_DAY)
        ).all()
    )
    trend: list[TrendPoint] = []
    cursor = d_from
    while cursor <= d_to:
        trend.append(TrendPoint(
            date=cursor,
            bills=int(bill_days.get(cursor, 0)),
            new_shops=int(shop_days.get(cursor, 0)),
        ))
        cursor += dt.timedelta(days=1)

    total_staff = db.execute(
        select(func.count()).where(User.role.in_((ROLE_MANAGER, ROLE_SALESPERSON)))
    ).scalar_one()
    total_owners = db.execute(
        select(func.count()).where(User.role == ROLE_OWNER)
    ).scalar_one()

    order = {"inactive": 0, "no_owner": 1, "silent": 2}
    attention.sort(key=lambda a: order.get(a.kind, 9))

    return AdminOverview(
        start_date=d_from, end_date=d_to,
        total_shops=len(shop_rows), active_shops=active, inactive_shops=inactive,
        new_shops=new_shops, total_staff=int(total_staff), total_owners=int(total_owners),
        total_bills=total_bills, shops=shops, trend=trend, attention=attention,
    )


@router.get("/shops/{shop_id}/detail", response_model=AdminShopDetail)
def admin_shop_detail(
    shop_id: uuid.UUID,
    db: Session = Depends(get_db),
) -> AdminShopDetail:
    shop = db.execute(select(Shop).where(Shop.id == shop_id)).scalar_one_or_none()
    if shop is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Shop not found")

    now_utc = dt.datetime.now(tz=dt.timezone.utc)
    d7 = now_utc - dt.timedelta(days=7)
    d30 = now_utc - dt.timedelta(days=30)

    owner_email = db.execute(
        select(User.email).where(User.shop_id == shop_id, User.role == ROLE_MANAGER)
    ).scalar_one_or_none()
    owner_emails = list(
        db.execute(
            select(User.email).join(ShopOwner, ShopOwner.owner_id == User.id).where(ShopOwner.shop_id == shop_id)
        ).scalars()
    )
    staff_count = db.execute(
        select(func.count()).where(User.shop_id == shop_id, User.role.in_((ROLE_MANAGER, ROLE_SALESPERSON)))
    ).scalar_one()
    products_count = db.execute(select(func.count()).where(Product.shop_id == shop_id)).scalar_one()
    customers_count = db.execute(select(func.count()).where(Customer.shop_id == shop_id)).scalar_one()
    bills_7 = db.execute(
        select(func.count()).where(Bill.shop_id == shop_id, Bill.created_at >= d7)
    ).scalar_one()
    bills_30 = db.execute(
        select(func.count()).where(Bill.shop_id == shop_id, Bill.created_at >= d30)
    ).scalar_one()
    last_bill_at = db.execute(
        select(func.max(Bill.created_at)).where(Bill.shop_id == shop_id)
    ).scalar_one()

    activity_rows = db.execute(
        select(Bill.id, Bill.created_at, User.email)
        .outerjoin(User, User.id == Bill.created_by)
        .where(Bill.shop_id == shop_id)
        .order_by(Bill.created_at.desc(), Bill.id.desc())
        .limit(10)
    ).all()
    ids = [r.id for r in activity_rows]
    item_counts: dict[uuid.UUID, int] = {}
    if ids:
        item_counts = dict(
            db.execute(
                select(BillItem.bill_id, func.count()).where(BillItem.bill_id.in_(ids)).group_by(BillItem.bill_id)
            ).all()
        )
    recent = [
        AdminActivity(created_at=r.created_at, salesperson_email=r.email, item_count=int(item_counts.get(r.id, 0)))
        for r in activity_rows
    ]

    return AdminShopDetail(
        shop_id=shop.id, shop_name=shop.name, is_active=shop.is_active, created_at=shop.created_at,
        business_name=shop.business_name, business_address=shop.business_address,
        business_phone=shop.business_phone, business_email=shop.business_email,
        business_upi=shop.business_upi, owner_email=owner_email, owner_emails=owner_emails,
        staff_count=int(staff_count), products_count=int(products_count),
        customers_count=int(customers_count), bills_7=int(bills_7), bills_30=int(bills_30),
        last_bill_at=last_bill_at, recent_activity=recent,
    )


@router.get("/staff", response_model=list[AdminStaffRow])
def admin_staff_directory(
    db: Session = Depends(get_db),
) -> list[AdminStaffRow]:
    """Every manager + salesperson across all shops, with lifetime usage (bill counts)."""
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
            select(Bill.created_by, func.count(Bill.id), func.max(Bill.created_at)).group_by(Bill.created_by)
        ).all()
    }

    out: list[AdminStaffRow] = []
    for u in users:
        p = perf.get(u.id)
        out.append(AdminStaffRow(
            user_id=u.id, email=u.email, role=u.role, is_active=u.is_active,
            shop_id=u.shop_id, shop_name=u.name, created_at=u.created_at,
            bill_count=int(p[1]) if p else 0, last_bill_at=p[2] if p else None,
        ))
    return out
