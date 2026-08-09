import io
import decimal
import datetime as dt
from pathlib import Path
from zoneinfo import ZoneInfo

from reportlab.lib.pagesizes import A4
from reportlab.lib import colors
from reportlab.platypus import (
    SimpleDocTemplate,
    Paragraph,
    Spacer,
    Table,
    TableStyle,
    HRFlowable,
)
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.graphics.shapes import Drawing, Circle, Polygon, Rect

from app.models.bill import Bill, BillItem
from app.models.shop import Shop
from app.models.customer import Customer
from app.config import get_settings
from app.services.whatsapp.exceptions import PDFGenerationError
from app.media import invoices_dir

settings = get_settings()
SHOP_TZ = ZoneInfo("Asia/Kolkata")


def _format_pdf_money(amount: decimal.Decimal) -> str:
    """Format decimal money into Rs. X,XX,XXX.XX standard text."""
    q = amount.quantize(decimal.Decimal("0.01"))
    negative = q < 0
    s = f"{abs(q):.2f}"
    intpart, dec = s.split(".")
    
    # Simple Indian grouping or standard comma grouping
    if len(intpart) > 3:
        last3 = intpart[-3:]
        rest = intpart[:-3]
        import re
        rest = re.sub(r"(\d)(?=(\d\d)+$)", r"\1,", rest)
        intpart = f"{rest},{last3}"
        
    prefix = "-" if negative else ""
    return f"{prefix}Rs. {intpart}.{dec}"


def draw_leaf_icon() -> Drawing:
    """Draw a professional leaf branding badge using vector shapes in ReportLab."""
    d = Drawing(40, 40)
    # Circle badge background (brand green #1F7A4D)
    bg_color = colors.HexColor("#1F7A4D")
    d.add(Circle(20, 20, 20, fillColor=bg_color, strokeColor=None))
    
    # Drawn leaf polygon vectors inside the circle
    # Left leaf lobe
    d.add(Polygon(
        [20, 10, 12, 22, 20, 32],
        fillColor=colors.HexColor("#A7F3D0"),  # Mint/light green
        strokeColor=None
    ))
    # Right leaf lobe
    d.add(Polygon(
        [20, 10, 28, 22, 20, 32],
        fillColor=colors.HexColor("#34D399"),  # Emerald/medium green
        strokeColor=None
    ))
    return d


def generate_invoice_pdf(
    bill: Bill,
    items: list[BillItem],
    shop: Shop,
    customer: Customer | None = None
) -> bytes:
    """Generate a highly professional, beautifully styled A4 PDF invoice in memory.

    Args:
        bill: The Bill database model
        items: List of BillItem models associated with the bill
        shop: The Shop database model
        customer: Optional Customer database model

    Returns:
        bytes: The PDF file bytes.
    """
    buffer = io.BytesIO()
    
    try:
        # Page size is A4 (595.27 x 841.89 points). Margin is 36 points (0.5 inch).
        # Printable width: 595.27 - 72 = 523.27 points.
        doc = SimpleDocTemplate(
            buffer,
            pagesize=A4,
            leftMargin=36,
            rightMargin=36,
            topMargin=36,
            bottomMargin=36
        )
        
        styles = getSampleStyleSheet()
        
        # Define clean, modern typography styles using HexColor codes matching the brand.
        primary_color = colors.HexColor("#1F7A4D")    # Botanical Green
        dark_neutral = colors.HexColor("#1F2937")     # Charcoal/Ink
        soft_neutral = colors.HexColor("#4B5563")     # Slate Gray
        border_color = colors.HexColor("#E5E7EB")     # Light gray for grids
        
        title_style = ParagraphStyle(
            "InvoiceTitle",
            parent=styles["Normal"],
            fontName="Helvetica-Bold",
            fontSize=22,
            textColor=primary_color,
            spaceAfter=4
        )
        
        tagline_style = ParagraphStyle(
            "Tagline",
            parent=styles["Normal"],
            fontName="Helvetica",
            fontSize=10,
            textColor=soft_neutral
        )
        
        shop_title_style = ParagraphStyle(
            "ShopTitle",
            parent=styles["Normal"],
            fontName="Helvetica-Bold",
            fontSize=14,
            textColor=dark_neutral,
            alignment=2 # Right aligned
        )
        
        shop_text_style = ParagraphStyle(
            "ShopText",
            parent=styles["Normal"],
            fontName="Helvetica",
            fontSize=9,
            textColor=soft_neutral,
            alignment=2,
            leading=12
        )
        
        section_heading = ParagraphStyle(
            "SectionHeading",
            parent=styles["Normal"],
            fontName="Helvetica-Bold",
            fontSize=10,
            textColor=primary_color,
            spaceAfter=6
        )
        
        meta_text_style = ParagraphStyle(
            "MetaText",
            parent=styles["Normal"],
            fontName="Helvetica",
            fontSize=9.5,
            textColor=dark_neutral,
            leading=14
        )
        
        meta_bold_style = ParagraphStyle(
            "MetaBold",
            parent=meta_text_style,
            fontName="Helvetica-Bold"
        )
        
        table_header_style = ParagraphStyle(
            "TableHeader",
            parent=styles["Normal"],
            fontName="Helvetica-Bold",
            fontSize=9.5,
            textColor=colors.white,
            alignment=0
        )
        
        table_cell_style = ParagraphStyle(
            "TableCell",
            parent=styles["Normal"],
            fontName="Helvetica",
            fontSize=9,
            textColor=dark_neutral,
            leading=12
        )
        
        table_cell_bold = ParagraphStyle(
            "TableCellBold",
            parent=table_cell_style,
            fontName="Helvetica-Bold"
        )
        
        summary_label_style = ParagraphStyle(
            "SummaryLabel",
            parent=styles["Normal"],
            fontName="Helvetica",
            fontSize=9.5,
            textColor=soft_neutral,
            alignment=2
        )
        
        summary_val_style = ParagraphStyle(
            "SummaryValue",
            parent=styles["Normal"],
            fontName="Helvetica-Bold",
            fontSize=9.5,
            textColor=dark_neutral,
            alignment=2
        )
        
        footer_style = ParagraphStyle(
            "FooterText",
            parent=styles["Normal"],
            fontName="Helvetica",
            fontSize=8.5,
            textColor=soft_neutral,
            alignment=1,
            leading=12
        )

        elements = []
        
        # ── HEADER ROW ────────────────────────────────────────────────────────
        # Left side: Logo Badge + Branding
        logo_drawing = draw_leaf_icon()
        brand_meta = [
            Paragraph("PLANTORA", title_style),
            Paragraph("Nursery Billing & Invoice Solutions", tagline_style),
        ]
        
        left_header_table = Table([[logo_drawing, brand_meta]], colWidths=[45, 200])
        left_header_table.setStyle(TableStyle([
            ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
            ("LEFTPADDING", (1, 0), (1, 0), 6),
            ("BOTTOMPADDING", (0, 0), (-1, -1), 0),
            ("TOPPADDING", (0, 0), (-1, -1), 0),
        ]))
        
        # Right side: Shop details
        shop_name = shop.business_name or shop.name or "Nursery"
        shop_addr = shop.business_address or "Shop Address Not Configured"
        shop_phone = f"Ph: {shop.business_phone}" if shop.business_phone else ""
        shop_email = f"Email: {shop.business_email}" if shop.business_email else ""
        shop_upi = f"UPI VPA: {shop.business_upi}" if shop.business_upi else ""
        
        shop_details_flow = [
            Paragraph(shop_name.upper(), shop_title_style),
            Spacer(1, 3)
        ]
        if shop_addr:
            shop_details_flow.append(Paragraph(shop_addr, shop_text_style))
        for text_line in (shop_phone, shop_email, shop_upi):
            if text_line:
                shop_details_flow.append(Paragraph(text_line, shop_text_style))
                
        header_container_table = Table([[left_header_table, shop_details_flow]], colWidths=[260, 263])
        header_container_table.setStyle(TableStyle([
            ("VALIGN", (0, 0), (-1, -1), "TOP"),
            ("LEFTPADDING", (0, 0), (-1, -1), 0),
            ("RIGHTPADDING", (0, 0), (-1, -1), 0),
            ("BOTTOMPADDING", (0, 0), (-1, -1), 0),
            ("TOPPADDING", (0, 0), (-1, -1), 0),
        ]))
        elements.append(header_container_table)
        elements.append(Spacer(1, 10))
        
        # Horizontal divider
        elements.append(HRFlowable(width="100%", thickness=1.5, color=primary_color, spaceBefore=4, spaceAfter=15))
        
        # ── METADATA & CUSTOMER ROW ───────────────────────────────────────────
        # Left side: Invoice details
        date_ist = bill.created_at.astimezone(SHOP_TZ).strftime("%d-%b-%Y %I:%M %p")
        # Prefer the human-facing per-shop bill number (0001…); fall back to the
        # UUID fragment for legacy bills with no assigned number.
        invoice_id_short = (
            f"{bill.bill_seq:04d}" if getattr(bill, "bill_seq", None) is not None
            else str(bill.id).split("-")[0].upper()
        )
        
        # Determine payment method label
        pm = "Cash"
        if bill.cash_amount > 0 and bill.upi_amount > 0:
            pm = "Split (Cash + UPI)"
        elif bill.upi_amount > 0:
            pm = "UPI"
        elif bill.due_amount > 0 and bill.cash_amount == 0 and bill.upi_amount == 0:
            pm = "Due Credit"
        elif bill.due_amount > 0:
            pm = "Split (Due Credit)"
            
        meta_flow = [
            Paragraph("INVOICE DETAILS", section_heading),
            Paragraph(f"<b>Invoice ID:</b> #{invoice_id_short}", meta_text_style),
            Paragraph(f"<b>Date/Time:</b> {date_ist}", meta_text_style),
            Paragraph(f"<b>Payment Mode:</b> {pm}", meta_text_style),
        ]
        
        # Right side: Billed To Customer info
        cust_name = customer.name if customer else (bill.remarks or "Cash Customer")
        cust_phone = f"Ph: {customer.phone}" if (customer and customer.phone) else ""
        
        customer_flow = [
            Paragraph("BILLED TO", section_heading),
            Paragraph(f"<b>Name:</b> {cust_name}", meta_text_style),
        ]
        if cust_phone:
            customer_flow.append(Paragraph(f"<b>Mobile:</b> {cust_phone}", meta_text_style))
        if bill.remarks and customer:
            customer_flow.append(Paragraph(f"<b>Notes:</b> {bill.remarks}", meta_text_style))

        meta_customer_table = Table([[meta_flow, customer_flow]], colWidths=[260, 263])
        meta_customer_table.setStyle(TableStyle([
            ("VALIGN", (0, 0), (-1, -1), "TOP"),
            ("LEFTPADDING", (0, 0), (-1, -1), 0),
            ("RIGHTPADDING", (0, 0), (-1, -1), 0),
            ("BOTTOMPADDING", (0, 0), (-1, -1), 0),
            ("TOPPADDING", (0, 0), (-1, -1), 0),
        ]))
        elements.append(meta_customer_table)
        elements.append(Spacer(1, 15))
        
        # ── ITEMS TABLE ───────────────────────────────────────────────────────
        # Printable width: 523 points. Let's apportion colWidths:
        # Product Name: 263, Price: 90, Qty: 60, Total: 110
        table_data = [[
            Paragraph("Product Name", table_header_style),
            Paragraph("Unit Price", table_header_style),
            Paragraph("Qty", table_header_style),
            Paragraph("Line Total", table_header_style),
        ]]
        
        for item in items:
            table_data.append([
                Paragraph(item.product_name, table_cell_style),
                Paragraph(_format_pdf_money(item.unit_price), table_cell_style),
                Paragraph(str(item.quantity), table_cell_style),
                Paragraph(_format_pdf_money(item.line_total), table_cell_bold),
            ])
            
        items_table = Table(table_data, colWidths=[263, 90, 60, 110])
        items_table.setStyle(TableStyle([
            ("BACKGROUND", (0, 0), (-1, 0), primary_color),
            ("ALIGN", (0, 0), (-1, -1), "LEFT"),
            ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
            ("BOTTOMPADDING", (0, 0), (-1, -1), 6),
            ("TOPPADDING", (0, 0), (-1, -1), 6),
            ("LINEBELOW", (0, 0), (-1, -1), 0.5, border_color),
        ]))
        elements.append(items_table)
        elements.append(Spacer(1, 15))
        
        # ── BILL SUMMARY BLOCK (Right aligned) ────────────────────────────────
        # Subtotal, Discount, Net Total, Paid Amounts
        summary_rows = [
            [Paragraph("Subtotal:", summary_label_style), Paragraph(_format_pdf_money(bill.subtotal), summary_val_style)],
        ]
        
        if bill.discount_amount > 0:
            disc_label = "Discount:"
            if bill.discount_type == "percent":
                disc_label = f"Discount ({bill.discount_value.normalize()}%):"
            summary_rows.append([
                Paragraph(disc_label, summary_label_style),
                Paragraph(f"- {_format_pdf_money(bill.discount_amount)}", summary_val_style)
            ])
            
        summary_rows.append([
            Paragraph("Net Payable:", summary_label_style),
            Paragraph(_format_pdf_money(bill.total), summary_val_style)
        ])
        
        if bill.cash_amount > 0:
            summary_rows.append([
                Paragraph("Cash Paid:", summary_label_style),
                Paragraph(_format_pdf_money(bill.cash_amount), summary_val_style)
            ])
        if bill.upi_amount > 0:
            summary_rows.append([
                Paragraph("UPI Paid:", summary_label_style),
                Paragraph(_format_pdf_money(bill.upi_amount), summary_val_style)
            ])
        if bill.due_amount > 0:
            summary_rows.append([
                Paragraph("Due Balance:", summary_label_style),
                Paragraph(_format_pdf_money(bill.due_amount), summary_val_style)
            ])
            
        summary_table = Table(summary_rows, colWidths=[383, 140])
        summary_table.setStyle(TableStyle([
            ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
            ("RIGHTPADDING", (0, 0), (-1, -1), 0),
            ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
            ("TOPPADDING", (0, 0), (-1, -1), 4),
            # Line above the Net Total or highlight
        ]))
        elements.append(summary_table)
        elements.append(Spacer(1, 30))
        
        # ── FOOTER MESSAGE ────────────────────────────────────────────────────
        custom_footer = shop.whatsapp_footer_message or "Thank you for visiting! Please visit again. 🌿"
        elements.append(HRFlowable(width="100%", thickness=0.5, color=border_color, spaceBefore=4, spaceAfter=10))
        elements.append(Paragraph(custom_footer, footer_style))
        elements.append(Paragraph("Generated by Plantora Billing System", ParagraphStyle("SubFoot", parent=footer_style, fontSize=7, textColor=colors.HexColor("#9CA3AF"))))

        # Build document
        doc.build(elements)
        
        pdf_bytes = buffer.getvalue()
        buffer.close()
        return pdf_bytes

    except Exception as e:
        buffer.close()
        raise PDFGenerationError(f"ReportLab PDF invoice compile failed: {e}") from e


def save_invoice_pdf(bill_id: str, pdf_bytes: bytes) -> str:
    """Save raw PDF invoice bytes to filesystem and return its relative path."""
    dest_dir = invoices_dir()
    dest_dir.mkdir(parents=True, exist_ok=True)
    
    filename = f"invoice_{bill_id}.pdf"
    dest = dest_dir / filename
    dest.write_bytes(pdf_bytes)
    
    # Stored relative path in DB
    return f"invoices/{filename}"
