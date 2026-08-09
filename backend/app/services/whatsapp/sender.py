import logging
import uuid
import datetime as dt
from sqlalchemy import select, text
from sqlalchemy.orm import Session

from app.config import get_settings
from app.models.bill import Bill, BillItem
from app.models.shop import Shop
from app.models.customer import Customer
from app.services.whatsapp.client import WhatsAppCloudClient
from app.services.whatsapp.exceptions import WhatsAppError, PDFGenerationError, WhatsAppAPIError
from app.services.whatsapp.media import generate_invoice_pdf, save_invoice_pdf
from app.services.whatsapp.phone import normalize_indian_phone
from app.services.whatsapp.eligibility import is_whatsapp_eligible

logger = logging.getLogger("plantora.whatsapp.sender")
settings = get_settings()


def queue_bill_invoice(db: Session, bill: Bill) -> None:
    """Pre-evaluates auto-send settings and schedules the bill for background delivery.

    Args:
        db: Active SQLAlchemy database session
        bill: The newly committed Bill model
    """
    shop = db.execute(select(Shop).where(Shop.id == bill.shop_id)).scalar_one_or_none()
    if not shop:
        bill.whatsapp_status = "none"
        return

    # Check if auto WhatsApp settings are enabled
    if not shop.whatsapp_auto_send:
        bill.whatsapp_status = "none"
        return

    # Check customer presence, phone number, and eligibility
    customer = None
    if bill.customer_id is not None:
        customer = db.execute(
            select(Customer).where(Customer.id == bill.customer_id)
        ).scalar_one_or_none()

    if not customer or not customer.phone:
        bill.whatsapp_status = "none"
        return

    if not is_whatsapp_eligible(customer):
        bill.whatsapp_status = "none"
        return

    # Mark as queued to be processed by background worker
    bill.whatsapp_status = "queued"
    bill.retry_count = 0
    bill.whatsapp_error = None
    db.flush()


async def send_whatsapp_invoice_process(db: Session, bill: Bill) -> None:
    """Orchestrate PDF invoice compilation, upload, and WhatsApp transmission.

    This runs inside the background task worker and is completely isolated.
    It never raises exceptions; all failures are logged and recorded in the DB.
    """
    # 1. Refresh & lock status to prevent duplicate worker pickup
    db.execute(text("SELECT set_config('app.user_role', 'admin', true)"))
    bill.whatsapp_status = "sending"
    db.commit()
    
    db.execute(text("SELECT set_config('app.user_role', 'admin', true)"))

    try:
        # Load associated models
        shop = db.execute(select(Shop).where(Shop.id == bill.shop_id)).scalar_one_or_none()
        if not shop:
            raise WhatsAppError("Associated Shop model not found.")

        customer = None
        if bill.customer_id is not None:
            customer = db.execute(
                select(Customer).where(Customer.id == bill.customer_id)
            ).scalar_one_or_none()

        if not customer or not customer.phone:
            raise WhatsAppError("Customer or customer phone number is missing.")

        logger.info(
            "WhatsApp Send Process Started | Bill ID: %s | Customer Phone: %s | Retries: %s",
            bill.id, customer.phone, bill.retry_count
        )

        # Normalize phone number
        norm = normalize_indian_phone(customer.phone, settings.WHATSAPP_COUNTRY_CODE)
        if not norm:
            raise WhatsAppError(f"Customer phone number '{customer.phone}' is not a valid Indian mobile number.")

        # Fetch bill items
        items = list(
            db.execute(
                select(BillItem).where(BillItem.bill_id == bill.id).order_by(BillItem.id.asc())
            ).scalars()
        )

        # 2. Generate PDF Invoice (only if it doesn't already exist)
        pdf_path = bill.invoice_url
        if pdf_path:
            # Check if file exists, if not generate it
            full_path = Path(settings.MEDIA_ROOT).resolve() / pdf_path
            if not full_path.exists():
                pdf_path = None

        if not pdf_path:
            pdf_bytes = generate_invoice_pdf(bill, items, shop, customer)
            # Write to disk
            pdf_path = save_invoice_pdf(str(bill.id), pdf_bytes)
            bill.invoice_url = pdf_path
            db.flush()
        else:
            # Read existing file bytes
            full_path = Path(settings.MEDIA_ROOT).resolve() / pdf_path
            pdf_bytes = full_path.read_bytes()

        # 3. Global send switch check
        if not settings.WHATSAPP_ENABLED:
            # Log as manual fallback since WhatsApp is globally disabled
            raise WhatsAppError("Meta WhatsApp Cloud API is globally disabled in server environment settings.")

        # 4. Meta Upload & Delivery
        client = WhatsAppCloudClient()
        bill_id_short = (
            f"{bill.bill_seq:04d}" if getattr(bill, "bill_seq", None) is not None
            else str(bill.id).split("-")[0].upper()
        )
        filename = f"invoice_{bill_id_short}.pdf"

        # Step 4a: Upload PDF to get media_id
        media_id = await client.upload_media(pdf_bytes, filename, "application/pdf")

        # Step 4b: Send message (prefer Template if template name is configured, otherwise fallback to direct document)
        template_name = settings.WHATSAPP_TEMPLATE_NAME or "plantora_invoice"
        lang = shop.whatsapp_language or "en"
        
        # Format variables for the template: customer_name, bill_id, bill_total
        cust_name = customer.name or "Valued Customer"
        total_str = f"Rs. {bill.total:.2f}"
        body_params = [cust_name, bill_id_short, total_str]

        try:
            # Attempt to send template (standard production route)
            msg_id = await client.send_template_document(
                to_phone=norm.wa_me,
                template_name=template_name,
                lang_code=lang,
                media_id=media_id,
                file_name=filename,
                body_parameters=body_params
            )
        except WhatsAppAPIError as template_err:
            logger.warning("Template send failed, attempting direct document fallback: %s", template_err)
            # Fallback: Try direct document message (succeeds if inside active 24h window)
            caption = f"Here is your invoice from {shop.business_name or shop.name}."
            msg_id = await client.send_document_message(
                to_phone=norm.wa_me,
                media_id=media_id,
                file_name=filename,
                caption=caption
            )

        # 5. Success Recording
        bill.whatsapp_message_id = msg_id
        bill.whatsapp_status = "sent"
        bill.whatsapp_sent_at = dt.datetime.now(dt.timezone.utc)
        bill.whatsapp_error = None
        logger.info(
            "WhatsApp Send Succeeded | Bill ID: %s | Message ID: %s",
            bill.id, msg_id
        )

    except Exception as e:
        # 6. Error & Retry Recording
        error_msg = str(e)
        logger.error(
            "WhatsApp Send Failed | Bill ID: %s | Error: %s | Retries: %s",
            bill.id, error_msg, bill.retry_count
        )
        bill.whatsapp_status = "failed"
        bill.whatsapp_error = error_msg
        bill.last_retry_at = dt.datetime.now(dt.timezone.utc)
        bill.retry_count += 1
    
    finally:
        db.commit()


async def trigger_manual_resend(db: Session, bill: Bill) -> None:
    """Manually queue/trigger an invoice resend. Resets retry counters."""
    bill.whatsapp_status = "queued"
    bill.retry_count = 0
    bill.whatsapp_error = None
    db.commit()
    # Trigger the background worker process immediately
    await send_whatsapp_invoice_process(db, bill)
from pathlib import Path
