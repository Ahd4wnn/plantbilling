"""Customer management (shop_owner only), used by billing.

RLS scopes every query to the owner's shop. On insert, shop_id comes from the
authenticated user's JWT — never the request body.
"""
from __future__ import annotations

import datetime as dt
import uuid

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import or_, select
from sqlalchemy.orm import Session

from app.auth.dependencies import get_db, require_shop_staff, require_manager_or_admin, require_shop_or_admin
from app.models.customer import Customer
from app.models.user import User
from app.schemas.customer import CustomerCreate, CustomerOut
from app.services.whatsapp.eligibility import apply_phone_consent

router = APIRouter(prefix="/customers", tags=["customers"])


@router.get("", response_model=list[CustomerOut])
def list_customers(
    db: Session = Depends(get_db),
    _owner: User = Depends(require_shop_staff),
    q: str | None = Query(default=None, description="Case-insensitive name/phone search"),
) -> list[Customer]:
    stmt = select(Customer)
    if q:
        like = f"%{q}%"
        stmt = stmt.where(or_(Customer.name.ilike(like), Customer.phone.ilike(like)))
    stmt = stmt.order_by(Customer.name.asc())
    return list(db.execute(stmt).scalars())


@router.get("/download")
def download_customers_csv(
    db: Session = Depends(get_db),
    user: User = Depends(require_manager_or_admin),
    q: str | None = Query(default=None),
    shop_id: uuid.UUID | None = Query(default=None),
):
    """Download the shop's customers database as a CSV file."""
    import csv
    import io
    from fastapi.responses import StreamingResponse
    from app.models.shop import Shop

    target_shop_id = user.shop_id
    if user.role == "admin":
        if shop_id is None:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="shop_id is required for admin role")
        target_shop_id = shop_id

    if target_shop_id is None:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="shop_id is required")

    stmt = select(Customer).where(Customer.shop_id == target_shop_id)
    if q:
        like = f"%{q}%"
        stmt = stmt.where(or_(Customer.name.ilike(like), Customer.phone.ilike(like)))
    stmt = stmt.order_by(Customer.name.asc())
    customers = list(db.execute(stmt).scalars())

    shop = db.execute(select(Shop).where(Shop.id == target_shop_id)).scalar_one_or_none()
    shop_name = shop.name if shop else "Nursery"

    output = io.StringIO()
    writer = csv.writer(output)

    writer.writerow(["Customer Database", shop_name])
    writer.writerow([])
    writer.writerow(["Name", "Phone Number", "Joined Date"])
    for c in customers:
        joined_str = c.created_at.strftime("%Y-%m-%d") if c.created_at else ""
        writer.writerow([c.name, c.phone or "—", joined_str])

    output.seek(0)
    headers = {
        "Content-Disposition": f"attachment; filename=customers_{shop_name.replace(' ', '_').lower()}.csv"
    }
    return StreamingResponse(
        io.BytesIO(output.getvalue().encode("utf-8")),
        media_type="text/csv",
        headers=headers
    )


@router.post("", response_model=CustomerOut, status_code=status.HTTP_201_CREATED)
def create_customer(
    payload: CustomerCreate,
    db: Session = Depends(get_db),
    owner: User = Depends(require_shop_staff),
) -> Customer:
    customer = Customer(
        shop_id=owner.shop_id,  # from JWT, not the request body
        name=payload.name,
    )
    # Providing a phone for receipts IS the consent — stamp it here.
    apply_phone_consent(customer, payload.phone)
    db.add(customer)
    db.flush()
    db.refresh(customer)
    return customer


@router.post("/{customer_id}/whatsapp-optout", response_model=CustomerOut)
def whatsapp_optout(
    customer_id: uuid.UUID,
    db: Session = Depends(get_db),
    _owner: User = Depends(require_shop_staff),
) -> Customer:
    """Permanently suppress WhatsApp sending to this customer ("stop sending me these").

    Once set, opt-out always wins over consent — sending will always refuse.
    RLS scopes this to the owner's shop; another shop's customer reads as 404.
    """
    customer = db.execute(
        select(Customer).where(Customer.id == customer_id)
    ).scalar_one_or_none()
    if customer is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Customer not found")

    if not customer.whatsapp_opted_out:
        customer.whatsapp_opted_out = True
        customer.whatsapp_opted_out_at = dt.datetime.now(tz=dt.timezone.utc)
        db.flush()
        db.refresh(customer)
    return customer
