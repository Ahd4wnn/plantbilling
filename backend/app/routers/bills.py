"""Billing / checkout (shop_owner only).

The server is the source of truth for ALL money math. The client's amounts are
recomputed here: line totals, subtotal, discount, and the post-discount total.
The only hard payment rule is enforced in the app layer (not as a DB constraint,
to avoid rounding lockups): cash_amount + upi_amount must equal the total.

Idempotency: each request carries a client-generated idempotency_key. A repeat
of the same key (retry, double-tap, network replay) returns the original bill
instead of creating a duplicate — safe under concurrency via a per-shop unique
index plus a savepoint that rolls back the losing racer's work.
"""
from __future__ import annotations

import datetime as dt
import uuid
from decimal import ROUND_HALF_UP, Decimal
from zoneinfo import ZoneInfo

from fastapi import APIRouter, Depends, HTTPException, Query, Response, status
from sqlalchemy import func, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.auth.dependencies import get_db, require_shop_staff, require_shop_or_admin, require_manager_or_admin
from app.database import privileged_session
from app.config import get_settings
from app.models.bill import Bill, BillItem
from app.models.customer import Customer
from app.models.product import Product
from app.models.shop import Shop
from app.models.user import User
from app.schemas.bill import (
    BillCreate,
    BillDetailOut,
    BillItemOut,
    BillListItem,
    BillListOut,
    BillOut,
    BillSummaryOut,
    BillUpdate,
)
from app.schemas.whatsapp import SendWhatsAppResult
from app.schemas.report import (
    CategorySales,
    ProductSales,
    DetailedReportResponse,
    SendReportWhatsAppRequest,
)
from app.services.whatsapp.eligibility import apply_phone_consent
from app.services.whatsapp.sender import queue_bill_invoice, trigger_manual_resend, send_whatsapp_invoice_process
from app.services.whatsapp.webhook import validate_verify_token, process_webhook_payload
from app.schemas.whatsapp import WhatsAppStatusOut

router = APIRouter(prefix="/bills", tags=["bills"])

CENT = Decimal("0.01")
ZERO = Decimal("0.00")

# Shop timezone for "today" and date-range boundaries. India has no DST, so this
# is a fixed +05:30; kept as a named zone for future per-shop configurability.
SHOP_TZ = ZoneInfo("Asia/Kolkata")


def _payment_method(cash: Decimal, upi: Decimal, due: Decimal = Decimal("0")) -> str:
    channels = sum(1 for v in (cash, upi, due) if v > 0)
    if channels > 1:
        return "split"
    if due > 0:
        return "due"
    if upi > 0:
        return "upi"
    return "cash"


def _ist_day_bounds_utc(day: dt.date) -> tuple[dt.datetime, dt.datetime]:
    """[start, end) UTC instants spanning the given calendar day in shop time."""
    start_local = dt.datetime.combine(day, dt.time.min, tzinfo=SHOP_TZ)
    end_local = start_local + dt.timedelta(days=1)
    return start_local.astimezone(dt.timezone.utc), end_local.astimezone(dt.timezone.utc)


def _today_ist() -> dt.date:
    return dt.datetime.now(tz=SHOP_TZ).date()


def q2(value: Decimal | int) -> Decimal:
    """Quantize to 2 decimal places, rounding half up."""
    return Decimal(value).quantize(CENT, rounding=ROUND_HALF_UP)


def _http422(message: str) -> HTTPException:
    return HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=message)


def _serialize(
    bill: Bill,
    items: list[BillItem],
    *,
    replay: bool,
    customer_name: str | None,
    salesperson_email: str | None = None,
) -> BillOut:
    return BillOut(
        id=bill.id,
        bill_type=bill.bill_type,
        subtotal=bill.subtotal,
        discount_type=bill.discount_type,
        discount_value=bill.discount_value,
        discount_amount=bill.discount_amount,
        total=bill.total,
        cash_amount=bill.cash_amount,
        upi_amount=bill.upi_amount,
        due_amount=bill.due_amount,
        customer_id=bill.customer_id,
        customer_name=customer_name,
        created_by=bill.created_by,
        salesperson_email=salesperson_email,
        remarks=bill.remarks,
        is_edited=bill.is_edited,
        created_at=bill.created_at,
        items=[BillItemOut.model_validate(it) for it in items],
        idempotent_replay=replay,
        idempotency_key=bill.idempotency_key,
    )


def _customer_name(db: Session, customer_id) -> str | None:
    if customer_id is None:
        return None
    c = db.execute(select(Customer).where(Customer.id == customer_id)).scalar_one_or_none()
    return c.name if c else None


def _user_email(db: Session, user_id: uuid.UUID | None) -> str | None:
    if user_id is None:
        return None
    u = db.execute(select(User).where(User.id == user_id)).scalar_one_or_none()
    return u.email if u else None


@router.post("", response_model=BillOut)
async def create_bill(
    payload: BillCreate,
    response: Response,
    db: Session = Depends(get_db),
    owner: User = Depends(require_shop_staff),
) -> BillOut:
    # 1) Idempotent replay: if this key already produced a bill, return it as-is.
    existing = db.execute(
        select(Bill).where(Bill.idempotency_key == payload.idempotency_key)
    ).scalar_one_or_none()
    if existing is not None:
        items = list(
            db.execute(select(BillItem).where(BillItem.bill_id == existing.id)).scalars()
        )
        response.status_code = status.HTTP_200_OK
        return _serialize(
            existing,
            items,
            replay=True,
            customer_name=_customer_name(db, existing.customer_id),
            salesperson_email=_user_email(db, existing.created_by),
        )

    # 2) Fetch shop details
    shop = db.execute(select(Shop).where(Shop.id == owner.shop_id)).scalar_one_or_none()

    # 3) Resolve products (RLS-scoped to this shop) and recompute line totals.
    product_ids = [it.product_id for it in payload.items]
    products = {
        p.id: p
        for p in db.execute(select(Product).where(Product.id.in_(product_ids))).scalars()
    }

    computed_items: list[dict] = []
    subtotal = Decimal("0.00")
    for it in payload.items:
        product = products.get(it.product_id)
        if product is None:
            # Missing or another shop's product (RLS-hidden) — treat the same.
            raise _http422("One of the products is unavailable. Please remove it and try again.")
        if not product.is_active:
            raise _http422(f"“{product.name}” is no longer available. Please remove it and try again.")

        # Single billing mode: the price is editable per line (pre-filled from the
        # product's saved price on the client). The server still recomputes totals.
        unit_price = q2(it.unit_price)
        if unit_price < ZERO:
            raise _http422(f"Enter a valid price for “{product.name}”.")

        line_total = q2(unit_price * it.quantity)
        subtotal += line_total
        computed_items.append(
            {"product": product, "unit_price": unit_price, "quantity": it.quantity, "line_total": line_total}
        )
    subtotal = q2(subtotal)

    # 3) Discount (validated server-side; client validates too for instant feedback).
    discount_value = q2(payload.discount_value)
    if payload.discount_type == "percent":
        if discount_value > Decimal("100"):
            raise _http422("Discount percentage cannot be more than 100%.")
        discount_amount = q2(subtotal * discount_value / Decimal("100"))
    else:  # flat ₹ amount
        if discount_value > subtotal:
            raise _http422("Discount cannot be more than the subtotal.")
        discount_amount = discount_value

    total = q2(subtotal - discount_amount)
    if total < 0:
        raise _http422("The total cannot be negative.")

    # 4) Payment must balance exactly to the total.
    cash_amount = q2(payload.cash_amount)
    upi_amount = q2(payload.upi_amount)
    due_amount = q2(payload.due_amount)
    if cash_amount + upi_amount + due_amount != total:
        raise _http422(
            f"Cash + UPI + Due (₹{cash_amount + upi_amount + due_amount:.2f}) must equal the total (₹{total:.2f})."
        )

    # 5) Persist customer (if entered) + bill + items inside ONE savepoint, so a
    #    concurrent duplicate-key race rolls the whole unit back cleanly (no
    #    orphan customer) and we replay the winner's bill instead.
    #    bill_type is retained in the schema for historical bills but is no longer
    #    a user choice — single billing mode always records 'retail'.
    bill = Bill(
        shop_id=owner.shop_id,
        bill_type="retail",
        subtotal=subtotal,
        discount_type=payload.discount_type,
        discount_value=discount_value,
        discount_amount=discount_amount,
        total=total,
        cash_amount=cash_amount,
        upi_amount=upi_amount,
        due_amount=due_amount,
        remarks=payload.remarks,
        created_by=owner.id,
        idempotency_key=payload.idempotency_key,
    )
    bill_items: list[BillItem] = []
    customer_name: str | None = None
    try:
        with db.begin_nested():
            if payload.new_customer is not None:
                new_c = Customer(
                    shop_id=owner.shop_id,
                    name=payload.new_customer.name,
                )
                # Phone given for receipts at checkout = WhatsApp consent.
                apply_phone_consent(new_c, payload.new_customer.phone)
                db.add(new_c)
                db.flush()
                bill.customer_id = new_c.id
                customer_name = new_c.name

            db.add(bill)
            db.flush()  # assign bill.id
            for ci in computed_items:
                product: Product = ci["product"]
                item = BillItem(
                    bill_id=bill.id,
                    product_id=product.id,
                    product_name=product.name,  # denormalized snapshot
                    unit_price=ci["unit_price"],
                    quantity=ci["quantity"],
                    line_total=ci["line_total"],
                )
                db.add(item)
                bill_items.append(item)
                # Note: the product's saved price is owner-controlled in the
                # Products tab; per-line sale edits are intentionally NOT written
                # back, so the catalog default stays stable.
            db.flush()
    except IntegrityError:
        # A concurrent request with the same key won the race. The savepoint (and
        # its customer insert) was rolled back automatically, leaving the outer
        # transaction — and its RLS context — intact, so we can read the winner.
        winner = db.execute(
            select(Bill).where(Bill.idempotency_key == payload.idempotency_key)
        ).scalar_one()
        win_items = list(
            db.execute(select(BillItem).where(BillItem.bill_id == winner.id)).scalars()
        )
        response.status_code = status.HTTP_200_OK
        return _serialize(
            winner,
            win_items,
            replay=True,
            customer_name=_customer_name(db, winner.customer_id),
            salesperson_email=_user_email(db, winner.created_by),
        )

    # If an existing customer was attached, fetch its name for the response.
    if customer_name is None and bill.customer_id is not None:
        customer_name = _customer_name(db, bill.customer_id)

    # 6) Queue WhatsApp background invoice send if enabled.
    queue_bill_invoice(db, bill)

    response.status_code = status.HTTP_201_CREATED
    return _serialize(
        bill,
        bill_items,
        replay=False,
        customer_name=customer_name,
        salesperson_email=owner.email,
    )


# ── Reads: today's summary, history list, detail ─────────────────────────────
@router.get("/summary/today", response_model=BillSummaryOut)
def summary_for_day(
    db: Session = Depends(get_db),
    user: User = Depends(require_shop_or_admin),
    date: dt.date | None = Query(default=None, description="Day in shop time (YYYY-MM-DD); defaults to today"),
    created_by: uuid.UUID | None = Query(default=None, description="Filter by salesperson user ID"),
    shop_id: uuid.UUID | None = Query(default=None, description="Restrict to a shop (admin only)"),
) -> BillSummaryOut:
    day = date or _today_ist()
    start, end = _ist_day_bounds_utc(day)

    stmt = select(Bill.total, Bill.cash_amount, Bill.upi_amount, Bill.due_amount).where(
        Bill.created_at >= start, Bill.created_at < end
    )
    if created_by is not None:
        stmt = stmt.where(Bill.created_by == created_by)
    if user.role == "admin" and shop_id is not None:
        stmt = stmt.where(Bill.shop_id == shop_id)

    rows = db.execute(stmt).all()

    total_sales = ZERO
    cash_total = ZERO
    upi_total = ZERO
    due_total = ZERO
    for total, cash, upi, due in rows:
        total_sales += total
        cash_total += cash
        upi_total += upi
        due_total += due

    # Fetch expenses for the day
    target_shop_id = user.shop_id
    if user.role == "admin" and shop_id is not None:
        target_shop_id = shop_id

    expenses_rows = []
    if target_shop_id is not None:
        from app.models.expense import Expense
        stmt_exp = select(Expense).where(
            Expense.shop_id == target_shop_id,
            Expense.created_at >= start,
            Expense.created_at < end
        )
        if created_by is not None:
            stmt_exp = stmt_exp.where(Expense.created_by == created_by)
        stmt_exp = stmt_exp.order_by(Expense.created_at.desc())
        expenses_rows = list(db.execute(stmt_exp).scalars())

    total_expenses = sum((e.amount for e in expenses_rows), ZERO)
    net_sales = total_sales - total_expenses

    return BillSummaryOut(
        date=day,
        total_sales=q2(total_sales),
        bill_count=len(rows),
        cash_total=q2(cash_total),
        upi_total=q2(upi_total),
        due_total=q2(due_total),
        total_expenses=q2(total_expenses),
        net_sales=q2(net_sales),
        expenses=expenses_rows,
    )


@router.get("", response_model=BillListOut)
def list_bills(
    db: Session = Depends(get_db),
    user: User = Depends(require_shop_or_admin),
    date_from: dt.date | None = Query(default=None, description="Inclusive start day (shop time)"),
    date_to: dt.date | None = Query(default=None, description="Inclusive end day (shop time)"),
    bill_type: str | None = Query(default=None, description="'retail' or 'wholesale'"),
    customer_id: uuid.UUID | None = Query(default=None),
    created_by: uuid.UUID | None = Query(default=None, description="Filter by salesperson user ID"),
    is_edited: bool | None = Query(default=None, description="Filter by whether the bill was edited"),
    has_due: bool | None = Query(default=None, description="Only bills with money still owed (due > 0)"),
    shop_id: uuid.UUID | None = Query(default=None, description="Restrict to a shop (admin only)"),
    limit: int = Query(default=20, ge=1, le=100),
    offset: int = Query(default=0, ge=0),
) -> BillListOut:
    if bill_type is not None and bill_type not in ("retail", "wholesale"):
        raise _http422("bill_type must be 'retail' or 'wholesale'.")

    stmt = (
        select(
            Bill.id,
            Bill.created_at,
            Bill.bill_type,
            Bill.total,
            Bill.cash_amount,
            Bill.upi_amount,
            Bill.due_amount,
            Customer.name.label("customer_name"),
            Customer.phone.label("customer_phone"),
            Bill.is_edited,
        )
        .outerjoin(Customer, Customer.id == Bill.customer_id)
        .order_by(Bill.created_at.desc(), Bill.id.desc())
    )
    if date_from is not None:
        stmt = stmt.where(Bill.created_at >= _ist_day_bounds_utc(date_from)[0])
    if date_to is not None:
        stmt = stmt.where(Bill.created_at < _ist_day_bounds_utc(date_to)[1])
    if bill_type is not None:
        stmt = stmt.where(Bill.bill_type == bill_type)
    if customer_id is not None:
        stmt = stmt.where(Bill.customer_id == customer_id)
    if created_by is not None:
        stmt = stmt.where(Bill.created_by == created_by)
    if is_edited is not None:
        stmt = stmt.where(Bill.is_edited == is_edited)
    if has_due:
        stmt = stmt.where(Bill.due_amount > ZERO)
    if user.role == "admin" and shop_id is not None:
        stmt = stmt.where(Bill.shop_id == shop_id)

    # Fetch one extra row to know whether there's another page.
    rows = db.execute(stmt.limit(limit + 1).offset(offset)).all()
    has_more = len(rows) > limit
    page = rows[:limit]

    ids = [r.id for r in page]
    counts: dict[uuid.UUID, int] = {}
    if ids:
        counts = dict(
            db.execute(
                select(BillItem.bill_id, func.count())
                .where(BillItem.bill_id.in_(ids))
                .group_by(BillItem.bill_id)
            ).all()
        )

    items = [
        BillListItem(
            id=r.id,
            created_at=r.created_at,
            bill_type=r.bill_type,
            total=r.total,
            due_amount=r.due_amount,
            customer_name=r.customer_name,
            customer_phone=r.customer_phone,
            item_count=counts.get(r.id, 0),
            payment_method=_payment_method(r.cash_amount, r.upi_amount, r.due_amount),
            is_edited=r.is_edited,
        )
        for r in page
    ]
    return BillListOut(items=items, limit=limit, offset=offset, has_more=has_more)


@router.get("/{bill_id}", response_model=BillDetailOut)
def get_bill(
    bill_id: uuid.UUID,
    db: Session = Depends(get_db),
    user: User = Depends(require_shop_or_admin),
) -> BillDetailOut:
    # RLS makes another shop's bill invisible (unless admin), so 'not found'
    # and 'not yours' both correctly collapse to 404.
    bill = db.execute(select(Bill).where(Bill.id == bill_id)).scalar_one_or_none()
    if bill is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Bill not found")

    items = list(
        db.execute(
            select(BillItem).where(BillItem.bill_id == bill.id).order_by(BillItem.id.asc())
        ).scalars()
    )

    customer_name: str | None = None
    customer_phone: str | None = None
    if bill.customer_id is not None:
        c = db.execute(
            select(Customer).where(Customer.id == bill.customer_id)
        ).scalar_one_or_none()
        if c is not None:
            customer_name, customer_phone = c.name, c.phone

    shop = db.execute(select(Shop).where(Shop.id == bill.shop_id)).scalar_one_or_none()
    creator_email = _user_email(db, bill.created_by)

    return BillDetailOut(
        id=bill.id,
        shop_name=shop.name if shop else None,
        business_name=(shop.business_name or shop.name) if shop else None,
        business_address=shop.business_address if shop else None,
        business_phone=shop.business_phone if shop else None,
        bill_type=bill.bill_type,
        subtotal=bill.subtotal,
        discount_type=bill.discount_type,
        discount_value=bill.discount_value,
        discount_amount=bill.discount_amount,
        total=bill.total,
        cash_amount=bill.cash_amount,
        upi_amount=bill.upi_amount,
        due_amount=bill.due_amount,
        payment_method=_payment_method(bill.cash_amount, bill.upi_amount, bill.due_amount),
        customer_id=bill.customer_id,
        customer_name=customer_name,
        customer_phone=customer_phone,
        salesperson_email=creator_email,
        remarks=bill.remarks,
        is_edited=bill.is_edited,
        created_at=bill.created_at,
        items=[BillItemOut.model_validate(it) for it in items],
    )


@router.patch("/{bill_id}", response_model=BillDetailOut)
def update_bill(
    bill_id: uuid.UUID,
    payload: BillUpdate,
    db: Session = Depends(get_db),
    user: User = Depends(require_manager_or_admin),
) -> BillDetailOut:
    """Edit a bill: payment split (Cash/UPI/Due), remarks, and — for owners/admins —
    the line items themselves (price, quantity, add/remove). Recomputes all money
    server-side. Only shop owners and admins can edit bills; nobody but admins delete."""
    bill = db.execute(select(Bill).where(Bill.id == bill_id)).scalar_one_or_none()
    if bill is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Bill not found")

    # ── Optional: rewrite the line items and recompute the whole bill ──
    if payload.items is not None:
        product_ids = [it.product_id for it in payload.items]
        products = {
            p.id: p
            for p in db.execute(select(Product).where(Product.id.in_(product_ids))).scalars()
        }
        computed_items: list[dict] = []
        subtotal = Decimal("0.00")
        for it in payload.items:
            product = products.get(it.product_id)
            if product is None:
                raise _http422("One of the products is unavailable. Please remove it and try again.")
            unit_price = q2(it.unit_price)
            if unit_price < ZERO:
                raise _http422(f"Enter a valid price for “{product.name}”.")
            line_total = q2(unit_price * it.quantity)
            subtotal += line_total
            computed_items.append(
                {"product": product, "unit_price": unit_price, "quantity": it.quantity, "line_total": line_total}
            )
        subtotal = q2(subtotal)

        discount_type = payload.discount_type or bill.discount_type
        discount_value = q2(payload.discount_value) if payload.discount_value is not None else bill.discount_value
        if discount_type == "percent":
            if discount_value > Decimal("100"):
                raise _http422("Discount percentage cannot be more than 100%.")
            discount_amount = q2(subtotal * discount_value / Decimal("100"))
        else:
            if discount_value > subtotal:
                raise _http422("Discount cannot be more than the subtotal.")
            discount_amount = discount_value
        new_total = q2(subtotal - discount_amount)
        if new_total < 0:
            raise _http422("The total cannot be negative.")

        bill.subtotal = subtotal
        bill.discount_type = discount_type
        bill.discount_value = discount_value
        bill.discount_amount = discount_amount
        bill.total = new_total

        # Replace the item rows wholesale (denormalized name + price snapshot).
        for old in db.execute(select(BillItem).where(BillItem.bill_id == bill.id)).scalars():
            db.delete(old)
        db.flush()
        for ci in computed_items:
            product = ci["product"]
            db.add(BillItem(
                bill_id=bill.id,
                product_id=product.id,
                product_name=product.name,
                unit_price=ci["unit_price"],
                quantity=ci["quantity"],
                line_total=ci["line_total"],
            ))

    # Re-calculate the payment splits to verify they match the (possibly new) total.
    cash = q2(payload.cash_amount) if payload.cash_amount is not None else bill.cash_amount
    upi = q2(payload.upi_amount) if payload.upi_amount is not None else bill.upi_amount
    due = q2(payload.due_amount) if payload.due_amount is not None else bill.due_amount

    if cash + upi + due != bill.total:
        raise _http422(
            f"Cash + UPI + Due (₹{cash + upi + due:.2f}) must equal the total (₹{bill.total:.2f})."
        )

    # Perform the updates
    if payload.cash_amount is not None:
        bill.cash_amount = cash
    if payload.upi_amount is not None:
        bill.upi_amount = upi
    if payload.due_amount is not None:
        bill.due_amount = due
    if payload.remarks is not None:
        bill.remarks = payload.remarks
    bill.is_edited = True

    db.flush()

    # Re-retrieve items to serialize into BillDetailOut
    items = list(
        db.execute(
            select(BillItem).where(BillItem.bill_id == bill.id).order_by(BillItem.id.asc())
        ).scalars()
    )

    customer_name: str | None = None
    customer_phone: str | None = None
    if bill.customer_id is not None:
        c = db.execute(
            select(Customer).where(Customer.id == bill.customer_id)
        ).scalar_one_or_none()
        if c is not None:
            customer_name, customer_phone = c.name, c.phone

    shop = db.execute(select(Shop).where(Shop.id == bill.shop_id)).scalar_one_or_none()
    creator_email = _user_email(db, bill.created_by)

    return BillDetailOut(
        id=bill.id,
        shop_name=shop.name if shop else None,
        business_name=(shop.business_name or shop.name) if shop else None,
        business_address=shop.business_address if shop else None,
        business_phone=shop.business_phone if shop else None,
        bill_type=bill.bill_type,
        subtotal=bill.subtotal,
        discount_type=bill.discount_type,
        discount_value=bill.discount_value,
        discount_amount=bill.discount_amount,
        total=bill.total,
        cash_amount=bill.cash_amount,
        upi_amount=bill.upi_amount,
        due_amount=bill.due_amount,
        payment_method=_payment_method(bill.cash_amount, bill.upi_amount, bill.due_amount),
        customer_id=bill.customer_id,
        customer_name=customer_name,
        customer_phone=customer_phone,
        salesperson_email=creator_email,
        remarks=bill.remarks,
        is_edited=bill.is_edited,
        created_at=bill.created_at,
        items=[BillItemOut.model_validate(it) for it in items],
    )


@router.delete("/{bill_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_bill(
    bill_id: uuid.UUID,
    db: Session = Depends(get_db),
    user: User = Depends(require_manager_or_admin),
):
    """Delete a bill. Only the shop manager (or an admin) can delete bills —
    salespeople cannot."""
    bill = db.execute(select(Bill).where(Bill.id == bill_id)).scalar_one_or_none()
    if bill is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Bill not found")

    db.delete(bill)
    db.flush()


@router.post("/{bill_id}/send-whatsapp", response_model=SendWhatsAppResult)
async def send_whatsapp(
    bill_id: uuid.UUID,
    db: Session = Depends(get_db),
    owner: User = Depends(require_shop_staff),
) -> SendWhatsAppResult:
    """Manually trigger sending this bill's WhatsApp invoice immediately."""
    bill = db.execute(select(Bill).where(Bill.id == bill_id)).scalar_one_or_none()
    if bill is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Bill not found")

    # Queue and trigger immediately
    bill.whatsapp_status = "queued"
    bill.retry_count = 0
    bill.whatsapp_error = None
    db.commit()
    
    # Process it now (updates status to sent or failed)
    await send_whatsapp_invoice_process(db, bill)
    
    db.refresh(bill)
    if bill.whatsapp_status == "sent":
        return SendWhatsAppResult(status="sent", detail="Successfully sent WhatsApp invoice.")
    else:
        return SendWhatsAppResult(
            status="failed",
            detail=bill.whatsapp_error or "Failed to send WhatsApp invoice. It will be retried in the background."
        )


@router.post("/{bill_id}/resend-whatsapp", response_model=SendWhatsAppResult)
async def resend_whatsapp(
    bill_id: uuid.UUID,
    db: Session = Depends(get_db),
    owner: User = Depends(require_shop_staff),
) -> SendWhatsAppResult:
    """Manually resend this bill's WhatsApp invoice. Resets retry count and uses existing PDF."""
    bill = db.execute(select(Bill).where(Bill.id == bill_id)).scalar_one_or_none()
    if bill is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Bill not found")

    await trigger_manual_resend(db, bill)
    
    db.refresh(bill)
    if bill.whatsapp_status == "sent":
        return SendWhatsAppResult(status="sent", detail="Successfully resent WhatsApp invoice.")
    else:
        return SendWhatsAppResult(
            status="failed",
            detail=bill.whatsapp_error or "Resend failed. It will be retried in the background."
        )


@router.get("/{bill_id}/whatsapp-status", response_model=WhatsAppStatusOut)
def get_whatsapp_status(
    bill_id: uuid.UUID,
    db: Session = Depends(get_db),
    owner: User = Depends(require_shop_staff),
) -> Bill:
    """Get the live WhatsApp delivery and retry status for a bill."""
    bill = db.execute(select(Bill).where(Bill.id == bill_id)).scalar_one_or_none()
    if bill is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Bill not found")
    return bill


@router.get("/whatsapp/webhook", tags=["webhooks"])
def verify_whatsapp_webhook(
    hub_mode: str = Query(alias="hub.mode"),
    hub_challenge: str = Query(alias="hub.challenge"),
    hub_verify_token: str = Query(alias="hub.verify_token"),
):
    """Meta Webhook GET verification endpoint."""
    try:
        challenge = validate_verify_token(hub_verify_token, hub_challenge, hub_mode)
        from fastapi.responses import PlainTextResponse
        return PlainTextResponse(challenge)
    except ValueError as e:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(e))


@router.post("/whatsapp/webhook", tags=["webhooks"])
async def receive_whatsapp_status_update(payload: dict) -> dict:
    """Meta Webhook POST event status callback endpoint (uses privileged context)."""
    from app.database import privileged_session
    with privileged_session() as db:
        await process_webhook_payload(payload, db)
    return {"status": "ok"}


def _generate_report_data(
    db: Session,
    shop_id: uuid.UUID,
    date_from: dt.date,
    date_to: dt.date,
    created_by: uuid.UUID | None,
) -> DetailedReportResponse:
    start_utc, end_utc = _ist_day_bounds_utc(date_from)[0], _ist_day_bounds_utc(date_to)[1]

    # 1. Aggregations on Bills
    stmt = select(
        func.sum(Bill.total).label("total_sales"),
        func.count(Bill.id).label("bill_count"),
        func.sum(Bill.cash_amount).label("cash_total"),
        func.sum(Bill.upi_amount).label("upi_total"),
        func.sum(Bill.due_amount).label("due_total"),
    ).where(
        Bill.shop_id == shop_id,
        Bill.created_at >= start_utc,
        Bill.created_at < end_utc,
    )
    if created_by is not None:
        stmt = stmt.where(Bill.created_by == created_by)

    res = db.execute(stmt).one()
    total_sales = res.total_sales or Decimal("0.00")
    bill_count = res.bill_count or 0
    cash_total = res.cash_total or Decimal("0.00")
    upi_total = res.upi_total or Decimal("0.00")
    due_total = res.due_total or Decimal("0.00")

    average_bill_value = Decimal("0.00")
    if bill_count > 0:
        average_bill_value = q2(total_sales / bill_count)

    # 2. Group by category
    cat_stmt = (
        select(
            Product.category.label("category"),
            func.sum(BillItem.quantity).label("quantity"),
            func.sum(BillItem.line_total).label("total_sales"),
        )
        .select_from(BillItem)
        .join(Bill, Bill.id == BillItem.bill_id)
        .outerjoin(Product, Product.id == BillItem.product_id)
        .where(
            Bill.shop_id == shop_id,
            Bill.created_at >= start_utc,
            Bill.created_at < end_utc,
        )
    )
    if created_by is not None:
        cat_stmt = cat_stmt.where(Bill.created_by == created_by)
    cat_stmt = cat_stmt.group_by(Product.category).order_by(func.sum(BillItem.line_total).desc())
    cat_rows = db.execute(cat_stmt).all()

    categories = [
        CategorySales(
            category=row.category or "Uncategorized",
            quantity=int(row.quantity or 0),
            total_sales=q2(row.total_sales or Decimal("0.00")),
        )
        for row in cat_rows
    ]

    # 3. Top products
    prod_stmt = (
        select(
            BillItem.product_name.label("product_name"),
            func.sum(BillItem.quantity).label("quantity"),
            func.sum(BillItem.line_total).label("total_sales"),
        )
        .select_from(BillItem)
        .join(Bill, Bill.id == BillItem.bill_id)
        .where(
            Bill.shop_id == shop_id,
            Bill.created_at >= start_utc,
            Bill.created_at < end_utc,
        )
    )
    if created_by is not None:
        prod_stmt = prod_stmt.where(Bill.created_by == created_by)
    prod_stmt = prod_stmt.group_by(BillItem.product_name).order_by(func.sum(BillItem.line_total).desc()).limit(15)
    prod_rows = db.execute(prod_stmt).all()

    top_products = [
        ProductSales(
            product_name=str(row.product_name),
            quantity=int(row.quantity or 0),
            total_sales=q2(row.total_sales or Decimal("0.00")),
        )
        for row in prod_rows
    ]

    # Fetch expenses for the period
    from app.models.expense import Expense
    stmt_exp = select(Expense).where(
        Expense.shop_id == shop_id,
        Expense.created_at >= start_utc,
        Expense.created_at < end_utc,
    )
    if created_by is not None:
        stmt_exp = stmt_exp.where(Expense.created_by == created_by)
    stmt_exp = stmt_exp.order_by(Expense.created_at.desc())
    expenses_rows = list(db.execute(stmt_exp).scalars())

    total_expenses = sum((e.amount for e in expenses_rows), ZERO)
    net_sales = total_sales - total_expenses

    return DetailedReportResponse(
        start_date=date_from,
        end_date=date_to,
        total_sales=q2(total_sales),
        bill_count=bill_count,
        cash_total=q2(cash_total),
        upi_total=q2(upi_total),
        due_total=q2(due_total),
        average_bill_value=average_bill_value,
        total_expenses=q2(total_expenses),
        net_sales=q2(net_sales),
        expenses=expenses_rows,
        categories=categories,
        top_products=top_products,
    )


def _format_report_whatsapp(report: DetailedReportResponse, shop_name: str) -> str:
    lines = [
        f"🟢 *{shop_name} Sales Report*",
        f"📅 *Period*: {report.start_date} to {report.end_date}",
        "--------------------------------",
        f"• *Total Sales*: ₹{report.total_sales:.2f}",
        f"• *Total Bills*: {report.bill_count}",
        f"• *Average Bill*: ₹{report.average_bill_value:.2f}",
        "--------------------------------",
        f"💵 *Cash Collected*: ₹{report.cash_total:.2f}",
        f"📱 *UPI Collected*: ₹{report.upi_total:.2f}",
        f"⚠️ *Due Outstanding*: ₹{report.due_total:.2f}",
        "--------------------------------",
        f"📉 *Total Expenses*: ₹{report.total_expenses:.2f}",
        f"📈 *Net Income*: ₹{report.net_sales:.2f}",
        "--------------------------------",
        "*Top Categories*:"
    ]
    for cat in report.categories[:5]:
        lines.append(f" - {cat.category}: {cat.quantity} items (₹{cat.total_sales:.2f})")

    lines.append("--------------------------------")
    lines.append("*Top Products*:")
    for prod in report.top_products[:5]:
        lines.append(f" - {prod.product_name}: {prod.quantity} items (₹{prod.total_sales:.2f})")

    return "\n".join(lines)


@router.get("/summary/report", response_model=DetailedReportResponse)
def get_detailed_report(
    date_from: dt.date | None = Query(default=None),
    date_to: dt.date | None = Query(default=None),
    created_by: uuid.UUID | None = Query(default=None),
    shop_id: uuid.UUID | None = Query(default=None),
    db: Session = Depends(get_db),
    user: User = Depends(require_shop_or_admin),
) -> DetailedReportResponse:
    """Generate a detailed report of sales statistics and breakdown by categories/products."""
    today = _today_ist()
    d_from = date_from or today
    d_to = date_to or today

    target_shop_id = user.shop_id
    if user.role == "admin":
        if shop_id is None:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="shop_id is required for admin role")
        target_shop_id = shop_id

    if target_shop_id is None:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="shop_id is required")

    return _generate_report_data(db, target_shop_id, d_from, d_to, created_by)


@router.get("/summary/report/download")
def download_detailed_report(
    date_from: dt.date | None = Query(default=None),
    date_to: dt.date | None = Query(default=None),
    created_by: uuid.UUID | None = Query(default=None),
    shop_id: uuid.UUID | None = Query(default=None),
    db: Session = Depends(get_db),
    user: User = Depends(require_shop_or_admin),
):
    """Download the detailed report as a CSV file."""
    import csv
    import io
    from fastapi.responses import StreamingResponse

    today = _today_ist()
    d_from = date_from or today
    d_to = date_to or today

    target_shop_id = user.shop_id
    if user.role == "admin":
        if shop_id is None:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="shop_id is required for admin role")
        target_shop_id = shop_id

    if target_shop_id is None:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="shop_id is required")

    report = _generate_report_data(db, target_shop_id, d_from, d_to, created_by)
    shop = db.execute(select(Shop).where(Shop.id == target_shop_id)).scalar_one_or_none()
    shop_name = shop.name if shop else "Nursery"

    output = io.StringIO()
    writer = csv.writer(output)

    writer.writerow(["Detailed Sales Report", shop_name])
    writer.writerow(["Period", f"{d_from} to {d_to}"])
    writer.writerow([])
    writer.writerow(["Summary Metrics"])
    writer.writerow(["Total Sales", f"INR {report.total_sales:.2f}"])
    writer.writerow(["Total Bills", report.bill_count])
    writer.writerow(["Average Bill Value", f"INR {report.average_bill_value:.2f}"])
    writer.writerow(["Cash Collected", f"INR {report.cash_total:.2f}"])
    writer.writerow(["UPI Collected", f"INR {report.upi_total:.2f}"])
    writer.writerow(["Due Amount", f"INR {report.due_total:.2f}"])
    writer.writerow(["Total Expenses", f"INR {report.total_expenses:.2f}"])
    writer.writerow(["Net Income", f"INR {report.net_sales:.2f}"])
    writer.writerow([])

    writer.writerow(["Sales by Category"])
    writer.writerow(["Category", "Quantity Sold", "Total Revenue"])
    for cat in report.categories:
        writer.writerow([cat.category, cat.quantity, f"INR {cat.total_sales:.2f}"])
    writer.writerow([])

    writer.writerow(["Top Products"])
    writer.writerow(["Product Name", "Quantity Sold", "Total Revenue"])
    for prod in report.top_products:
        writer.writerow([prod.product_name, prod.quantity, f"INR {prod.total_sales:.2f}"])

    writer.writerow([])
    writer.writerow(["Detailed Expenses Log"])
    writer.writerow(["Date & Time", "Recorded By", "Reason / Description", "Amount"])
    for exp in report.expenses:
        creator_email = _user_email(db, exp.created_by) or "System"
        date_str = exp.created_at.astimezone(SHOP_TZ).strftime("%Y-%m-%d %H:%M")
        writer.writerow([date_str, creator_email, exp.reason, f"INR {exp.amount:.2f}"])

    output.seek(0)

    headers = {
        "Content-Disposition": f"attachment; filename=sales_report_{d_from}_to_{d_to}.csv"
    }
    return StreamingResponse(
        io.BytesIO(output.getvalue().encode("utf-8")),
        media_type="text/csv",
        headers=headers
    )


@router.post("/summary/report/send-whatsapp", response_model=SendWhatsAppResult)
async def send_report_whatsapp(
    payload: SendReportWhatsAppRequest,
    db: Session = Depends(get_db),
    user: User = Depends(require_shop_or_admin),
) -> SendWhatsAppResult:
    """Send or generate a wa.me share link for the sales report summary on WhatsApp."""
    today = _today_ist()
    d_from = payload.date_from or today
    d_to = payload.date_to or today

    target_shop_id = user.shop_id
    if user.role == "admin":
        if payload.shop_id is None:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="shop_id is required in payload for admin role")
        target_shop_id = payload.shop_id

    if target_shop_id is None:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="shop_id is required")

    report = _generate_report_data(db, target_shop_id, d_from, d_to, payload.created_by)
    shop = db.execute(select(Shop).where(Shop.id == target_shop_id)).scalar_one_or_none()
    shop_name = shop.name if shop else "Nursery"

    message = _format_report_whatsapp(report, shop_name)

    settings = get_settings()
    norm = normalize_indian_phone(payload.phone, settings.WHATSAPP_COUNTRY_CODE)
    if norm is None:
        return SendWhatsAppResult(
            status="not_eligible",
            wa_me_url=None,
            detail="The phone number doesn't look like a valid mobile number.",
        )

    wa_me_url = WaMeLinkBuilder.build(norm.wa_me, message)

    if not settings.WHATSAPP_ENABLED:
        return SendWhatsAppResult(
            status="disabled",
            wa_me_url=wa_me_url,
            detail="WhatsApp sending is turned off. Use the link to share manually.",
        )

    if settings.WHATSAPP_DEFAULT_BACKEND == "wati" and settings.WATI_API_KEY:
        client = WatiClient(
            settings.WATI_API_ENDPOINT,
            settings.WATI_API_KEY,
            timeout=settings.WATI_TIMEOUT_SECONDS,
        )
        if await client.send_text(norm.wa_me, message):
            return SendWhatsAppResult(
                status="sent_via_wati",
                wa_me_url=None,
                detail="Report sent on WhatsApp.",
            )

    return SendWhatsAppResult(
        status="fallback_wa_me",
        wa_me_url=wa_me_url,
        detail="Tap to send this report on WhatsApp.",
    )


@router.get("/public/{bill_id}", response_model=BillDetailOut)
def get_public_bill(
    bill_id: uuid.UUID,
) -> BillDetailOut:
    """Public endpoint to view a bill receipt using its secure UUID capability token."""
    with privileged_session() as db:
        bill = db.execute(select(Bill).where(Bill.id == bill_id)).scalar_one_or_none()
        if bill is None:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Bill not found")

        items = list(
            db.execute(
                select(BillItem).where(BillItem.bill_id == bill.id).order_by(BillItem.id.asc())
            ).scalars()
        )

        customer_name: str | None = None
        customer_phone: str | None = None
        if bill.customer_id is not None:
            c = db.execute(
                select(Customer).where(Customer.id == bill.customer_id)
            ).scalar_one_or_none()
            if c is not None:
                customer_name, customer_phone = c.name, c.phone

        shop = db.execute(select(Shop).where(Shop.id == bill.shop_id)).scalar_one_or_none()
        creator_email = _user_email(db, bill.created_by)

        return BillDetailOut(
            id=bill.id,
            shop_name=shop.name if shop else None,
            business_name=(shop.business_name or shop.name) if shop else None,
            business_address=shop.business_address if shop else None,
            business_phone=shop.business_phone if shop else None,
            bill_type=bill.bill_type,
            subtotal=bill.subtotal,
            discount_type=bill.discount_type,
            discount_value=bill.discount_value,
            discount_amount=bill.discount_amount,
            total=bill.total,
            cash_amount=bill.cash_amount,
            upi_amount=bill.upi_amount,
            due_amount=bill.due_amount,
            payment_method=_payment_method(bill.cash_amount, bill.upi_amount, bill.due_amount),
            customer_id=bill.customer_id,
            customer_name=customer_name,
            customer_phone=customer_phone,
            salesperson_email=creator_email,
            remarks=bill.remarks,
            is_edited=bill.is_edited,
            created_at=bill.created_at,
            items=[
                BillItemOut(
                    product_id=it.product_id,
                    product_name=it.product_name,
                    unit_price=it.unit_price,
                    quantity=it.quantity,
                    line_total=it.line_total,
                )
                for it in items
            ],
        )


