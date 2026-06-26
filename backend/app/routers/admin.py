"""Admin-only shop & owner management.

Every route here requires the platform admin (require_admin). The admin operates
under the 'admin' RLS context, so it can read/write across all shops. There is
no public/self registration — shop owners exist only because the admin created
them.
"""
from __future__ import annotations

import uuid

from fastapi import APIRouter, Depends, HTTPException, Query, Response, status
from sqlalchemy import func, or_, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.auth.dependencies import get_db, require_admin
from app.auth.security import hash_password
from app.models.customer import Customer
from app.models.shop import Shop
from app.models.user import ROLE_MANAGER, ROLE_OWNER, User
from app.schemas.admin import (
    AdminCustomerList,
    AdminCustomerRow,
    AssignOwnerRequest,
    OwnerAccountCreate,
    OwnerAccountInfo,
    OwnerInfo,
    ResetPasswordRequest,
    ShopCreateRequest,
    ShopCreateResponse,
    ShopListRow,
    ShopSummary,
    ShopUpdateRequest,
)

router = APIRouter(
    prefix="/admin",
    tags=["admin"],
    dependencies=[Depends(require_admin)],
)


@router.post("/shops", response_model=ShopCreateResponse, status_code=status.HTTP_201_CREATED)
def create_shop(payload: ShopCreateRequest, db: Session = Depends(get_db)) -> ShopCreateResponse:
    """Create a shop and its first shop_owner user in one transaction."""
    existing = db.execute(
        select(User).where(User.email == str(payload.owner_email))
    ).scalar_one_or_none()
    if existing is not None:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="A user with this email already exists",
        )

    # Optional: link the shop to an existing multi-shop owner account.
    if payload.owner_id is not None:
        owner_acct = db.execute(
            select(User).where(User.id == payload.owner_id, User.role == ROLE_OWNER)
        ).scalar_one_or_none()
        if owner_acct is None:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Owner account not found")

    shop = Shop(
        name=payload.name,
        owner_id=payload.owner_id,
        owner_name=payload.owner_name,
        owner_phone=payload.owner_phone,
    )
    db.add(shop)
    db.flush()  # assign shop.id before creating the manager

    owner = User(
        shop_id=shop.id,
        email=str(payload.owner_email),
        password_hash=hash_password(payload.owner_password),
        role=ROLE_MANAGER,
    )
    db.add(owner)

    try:
        db.flush()
    except IntegrityError:
        db.rollback()
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="A user with this email already exists",
        )

    db.refresh(shop)
    db.refresh(owner)
    return ShopCreateResponse(
        shop=ShopSummary.model_validate(shop),
        owner=OwnerInfo.model_validate(owner),
    )


@router.get("/shops", response_model=list[ShopListRow])
def list_shops(db: Session = Depends(get_db)) -> list[ShopListRow]:
    """All shops (newest first), each joined to its owner's login email.

    The owner is the shop's single shop_owner user created alongside the shop.
    Stats (product/bill counts, sales) are intentionally omitted for now to keep
    this query fast — a future enhancement if the dashboard needs them.
    """
    from sqlalchemy.orm import aliased

    OwnerAcct = aliased(User)  # the linked multi-shop owner account (shops.owner_id)
    rows = db.execute(
        select(
            Shop.id,
            Shop.name,
            Shop.owner_id,
            Shop.owner_name,
            Shop.owner_phone,
            Shop.is_active,
            Shop.created_at,
            Shop.settings,
            User.email.label("owner_email"),
            OwnerAcct.email.label("owner_email_account"),
        )
        .outerjoin(User, (User.shop_id == Shop.id) & (User.role == ROLE_MANAGER))
        .outerjoin(OwnerAcct, OwnerAcct.id == Shop.owner_id)
        .order_by(Shop.created_at.desc())
    ).all()
    return [
        ShopListRow(
            id=r.id,
            name=r.name,
            owner_id=r.owner_id,
            owner_email_account=r.owner_email_account,
            owner_name=r.owner_name,
            owner_phone=r.owner_phone,
            owner_email=r.owner_email,
            is_active=r.is_active,
            created_at=r.created_at,
            whatsapp_auto_send=r.settings.get("whatsapp_auto_send", False) if r.settings else False,
        )
        for r in rows
    ]


# ── Multi-shop owner accounts ─────────────────────────────────────────────────
@router.post("/owners", response_model=OwnerAccountInfo, status_code=status.HTTP_201_CREATED)
def create_owner(payload: OwnerAccountCreate, db: Session = Depends(get_db)) -> OwnerAccountInfo:
    """Create a multi-shop owner login (no shop attached). Assign shops separately."""
    if db.execute(select(User).where(User.email == str(payload.email))).scalar_one_or_none() is not None:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="A user with this email already exists")
    owner = User(
        shop_id=None,
        email=str(payload.email),
        password_hash=hash_password(payload.password),
        role=ROLE_OWNER,
    )
    db.add(owner)
    try:
        db.flush()
    except IntegrityError:
        db.rollback()
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="A user with this email already exists")
    db.refresh(owner)
    return OwnerAccountInfo(id=owner.id, email=owner.email, is_active=owner.is_active, created_at=owner.created_at, shop_count=0)


@router.get("/owners", response_model=list[OwnerAccountInfo])
def list_owners(db: Session = Depends(get_db)) -> list[OwnerAccountInfo]:
    owners = db.execute(
        select(User).where(User.role == ROLE_OWNER).order_by(User.created_at.desc())
    ).scalars().all()
    counts = dict(
        db.execute(
            select(Shop.owner_id, func.count()).where(Shop.owner_id.is_not(None)).group_by(Shop.owner_id)
        ).all()
    )
    return [
        OwnerAccountInfo(id=o.id, email=o.email, is_active=o.is_active, created_at=o.created_at, shop_count=counts.get(o.id, 0))
        for o in owners
    ]


@router.post("/shops/{shop_id}/assign-owner", response_model=ShopSummary)
def assign_owner(shop_id: uuid.UUID, payload: AssignOwnerRequest, db: Session = Depends(get_db)) -> Shop:
    """Set (or clear, with null) the business owner of a shop."""
    shop = db.execute(select(Shop).where(Shop.id == shop_id)).scalar_one_or_none()
    if shop is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Shop not found")
    if payload.owner_id is not None:
        owner_acct = db.execute(
            select(User).where(User.id == payload.owner_id, User.role == ROLE_OWNER)
        ).scalar_one_or_none()
        if owner_acct is None:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Owner account not found")
    shop.owner_id = payload.owner_id
    db.flush()
    db.refresh(shop)
    return shop


@router.get("/customers", response_model=AdminCustomerList)
def list_all_customers(
    db: Session = Depends(get_db),
    q: str | None = Query(default=None, description="Search customer name OR phone (case-insensitive)"),
    shop_id: uuid.UUID | None = Query(default=None, description="Restrict to one shop"),
    limit: int = Query(default=50, ge=1, le=200),
    offset: int = Query(default=0, ge=0),
) -> AdminCustomerList:
    """Cross-shop customer directory (admin sees all shops via the admin RLS policy).

    Ordered by created_at desc (most recent first). Contains end-customers'
    personal contact details, so it is admin-only and not exportable.
    """
    base = select(Customer, Shop.name.label("shop_name")).join(Shop, Shop.id == Customer.shop_id)
    if q:
        like = f"%{q}%"
        base = base.where(or_(Customer.name.ilike(like), Customer.phone.ilike(like)))
    if shop_id is not None:
        base = base.where(Customer.shop_id == shop_id)

    total = db.execute(select(func.count()).select_from(base.subquery())).scalar_one()

    rows = db.execute(
        base.order_by(Customer.created_at.desc()).limit(limit).offset(offset)
    ).all()

    items = [
        AdminCustomerRow(
            id=c.id,
            name=c.name,
            phone=c.phone,
            shop_id=c.shop_id,
            shop_name=shop_name,
            created_at=c.created_at,
        )
        for c, shop_name in rows
    ]
    return AdminCustomerList(items=items, total=total, limit=limit, offset=offset)


@router.get("/customers/download")
def download_all_customers_csv(
    db: Session = Depends(get_db),
    q: str | None = Query(default=None),
    shop_id: uuid.UUID | None = Query(default=None),
    _admin: User = Depends(require_admin),
):
    """Download all customers matching filters as a CSV file (admin only)."""
    import csv
    import io
    from fastapi.responses import StreamingResponse

    base = select(Customer, Shop.name.label("shop_name")).join(Shop, Shop.id == Customer.shop_id)
    if q:
        like = f"%{q}%"
        base = base.where(or_(Customer.name.ilike(like), Customer.phone.ilike(like)))
    if shop_id is not None:
        base = base.where(Customer.shop_id == shop_id)

    rows = db.execute(base.order_by(Customer.created_at.desc())).all()

    output = io.StringIO()
    writer = csv.writer(output)

    writer.writerow(["Admin Customer Database Export"])
    writer.writerow([])
    writer.writerow(["Name", "Phone Number", "Nursery / Shop", "Joined Date"])
    for c, shop_name in rows:
        joined_str = c.created_at.strftime("%Y-%m-%d") if c.created_at else ""
        writer.writerow([c.name, c.phone or "—", shop_name, joined_str])

    output.seek(0)
    headers = {
        "Content-Disposition": "attachment; filename=admin_customers_export.csv"
    }
    return StreamingResponse(
        io.BytesIO(output.getvalue().encode("utf-8")),
        media_type="text/csv",
        headers=headers
    )


@router.patch("/shops/{shop_id}", response_model=ShopSummary)
def update_shop(
    shop_id: uuid.UUID,
    payload: ShopUpdateRequest,
    db: Session = Depends(get_db),
) -> Shop:
    shop = db.execute(select(Shop).where(Shop.id == shop_id)).scalar_one_or_none()
    if shop is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Shop not found")
    if payload.is_active is not None:
        shop.is_active = payload.is_active
    if payload.whatsapp_auto_send is not None:
        shop.whatsapp_auto_send = payload.whatsapp_auto_send
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
    if payload.whatsapp_message_template is not None:
        shop.whatsapp_message_template = payload.whatsapp_message_template
    db.flush()
    db.refresh(shop)
    return shop


@router.delete("/shops/{shop_id}", status_code=status.HTTP_204_NO_CONTENT, response_class=Response)
def delete_shop(
    shop_id: uuid.UUID,
    db: Session = Depends(get_db),
):
    shop = db.execute(select(Shop).where(Shop.id == shop_id)).scalar_one_or_none()
    if shop is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Shop not found")
    db.delete(shop)
    db.flush()


@router.post("/shops/{shop_id}/reset-password", response_model=OwnerInfo)
def reset_owner_password(
    shop_id: uuid.UUID,
    payload: ResetPasswordRequest,
    db: Session = Depends(get_db),
) -> User:
    """Set a new password for the given shop's owner."""
    owner = db.execute(
        select(User).where(User.shop_id == shop_id, User.role == ROLE_MANAGER)
    ).scalar_one_or_none()
    if owner is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND, detail="Shop owner not found"
        )
    owner.password_hash = hash_password(payload.new_password)
    db.flush()
    db.refresh(owner)
    return owner


@router.get("/shops/{shop_id}/users", response_model=list[OwnerInfo])
def list_shop_users(
    shop_id: uuid.UUID,
    db: Session = Depends(get_db),
) -> list[User]:
    """List all users (owners and salespeople) for a given shop."""
    stmt = select(User).where(User.shop_id == shop_id).order_by(User.role.asc(), User.created_at.desc())
    return list(db.execute(stmt).scalars().all())

