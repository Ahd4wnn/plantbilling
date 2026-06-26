"""Product management API (shop_owner only).

Tenant isolation is automatic: every route uses the RLS-aware session, so the
shop owner only ever sees/touches their own shop's rows. We NEVER filter by
shop_id manually, and on insert we take shop_id from the authenticated user's
JWT (owner.shop_id) — never from the request body.
"""
from __future__ import annotations

import csv
import datetime as dt
import decimal
import io
import mimetypes
import uuid
import zipfile

from fastapi import (
    APIRouter,
    Depends,
    File,
    HTTPException,
    Query,
    UploadFile,
    status,
)
from fastapi.responses import StreamingResponse
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.auth.dependencies import get_db, require_shop_staff
from app.media import (
    IMAGE_CONTENT_TYPES,
    MAX_IMAGE_BYTES,
    build_media_url,
    delete_relative,
    save_product_image_bytes,
)
from app.models.bill import BillItem
from app.models.product import Product
from app.models.user import User
from app.schemas.product import (
    ProductCreate,
    ProductDeleteResponse,
    ProductOut,
    ProductUpdate,
    BulkDeleteRequest,
    BulkDeleteResponse,
    BulkPhotosResponse,
)

router = APIRouter(prefix="/products", tags=["products"])


def _out(product: Product) -> ProductOut:
    """Serialize a Product model into the outward-facing schema (URL for image)."""
    return ProductOut(
        id=product.id,
        name=product.name,
        category=product.category,
        retail_price=product.retail_price,
        last_wholesale_price=product.last_wholesale_price,
        photo_url=build_media_url(product.photo_path),
        is_active=product.is_active,
        created_at=product.created_at,
    )


def _get_owned_product_or_404(db: Session, product_id: uuid.UUID) -> Product:
    """Fetch a product visible under the current RLS context, else 404.

    RLS makes another shop's product invisible, so 'not found' and 'belongs to
    another shop' are indistinguishable here — both correctly become 404.
    """
    product = db.execute(
        select(Product).where(Product.id == product_id)
    ).scalar_one_or_none()
    if product is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Product not found")
    return product


@router.post("", response_model=ProductOut, status_code=status.HTTP_201_CREATED)
def create_product(
    payload: ProductCreate,
    db: Session = Depends(get_db),
    owner: User = Depends(require_shop_staff),
) -> ProductOut:
    product = Product(
        shop_id=owner.shop_id,  # from JWT, not the request body
        name=payload.name,
        category=payload.category,
        retail_price=payload.retail_price,
        last_wholesale_price=payload.last_wholesale_price,
    )
    db.add(product)
    db.flush()
    db.refresh(product)
    return _out(product)


@router.get("", response_model=list[ProductOut])
def list_products(
    db: Session = Depends(get_db),
    _owner: User = Depends(require_shop_staff),
    q: str | None = Query(default=None, description="Case-insensitive name search"),
    category: str | None = Query(default=None, description="Exact category filter"),
    active: str = Query(
        default="true",
        description="'true' (default) only active, 'all' include inactive, 'false' only inactive",
    ),
) -> list[ProductOut]:
    stmt = select(Product)
    if q:
        stmt = stmt.where(Product.name.ilike(f"%{q}%"))
    if category:
        stmt = stmt.where(Product.category == category)

    active_norm = active.lower()
    if active_norm == "true":
        stmt = stmt.where(Product.is_active.is_(True))
    elif active_norm == "false":
        stmt = stmt.where(Product.is_active.is_(False))
    elif active_norm != "all":
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="active must be one of: true, false, all",
        )

    # Active first, then creation order, with stable ID sort fallback.
    stmt = stmt.order_by(Product.is_active.desc(), Product.created_at.asc(), Product.id.asc())
    return [_out(p) for p in db.execute(stmt).scalars()]


@router.get("/{product_id}", response_model=ProductOut)
def get_product(
    product_id: uuid.UUID,
    db: Session = Depends(get_db),
    _owner: User = Depends(require_shop_staff),
) -> ProductOut:
    return _out(_get_owned_product_or_404(db, product_id))


@router.patch("/{product_id}", response_model=ProductOut)
def update_product(
    product_id: uuid.UUID,
    payload: ProductUpdate,
    db: Session = Depends(get_db),
    _owner: User = Depends(require_shop_staff),
) -> ProductOut:
    product = _get_owned_product_or_404(db, product_id)

    data = payload.model_dump(exclude_unset=True)

    # Reject explicit nulls for non-nullable fields.
    for field in ("name", "retail_price", "is_active"):
        if field in data and data[field] is None:
            raise HTTPException(
                status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
                detail=f"{field} cannot be null",
            )

    for field, value in data.items():
        setattr(product, field, value)

    db.flush()
    db.refresh(product)
    return _out(product)


@router.delete("/{product_id}", response_model=ProductDeleteResponse)
def delete_product(
    product_id: uuid.UUID,
    db: Session = Depends(get_db),
    _owner: User = Depends(require_shop_staff),
    hard: bool = Query(default=False, description="Permanently delete (only if no sales history)"),
) -> ProductDeleteResponse:
    product = _get_owned_product_or_404(db, product_id)

    if not hard:
        product.is_active = False
        db.flush()
        return ProductDeleteResponse(
            id=product.id, hard_deleted=False, detail="Product deactivated (soft delete)."
        )

    # Hard delete only if it has no sales history.
    ref_count = db.execute(
        select(func.count()).select_from(BillItem).where(BillItem.product_id == product_id)
    ).scalar_one()
    if ref_count > 0:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail=(
                "This product has sales history and cannot be permanently deleted. "
                "Deactivate it instead (soft delete)."
            ),
        )

    delete_relative(product.photo_path)
    db.delete(product)
    db.flush()
    return ProductDeleteResponse(
        id=product_id, hard_deleted=True, detail="Product permanently deleted."
    )


@router.post("/{product_id}/image", response_model=ProductOut)
async def upload_product_image(
    product_id: uuid.UUID,
    db: Session = Depends(get_db),
    _owner: User = Depends(require_shop_staff),
    file: UploadFile = File(...),
) -> ProductOut:
    product = _get_owned_product_or_404(db, product_id)

    ext = IMAGE_CONTENT_TYPES.get((file.content_type or "").lower())
    if ext is None:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="Unsupported image type. Allowed: JPEG, PNG, WebP.",
        )

    # Read with a hard size cap (reject before buffering the whole oversized file).
    data = bytearray()
    while True:
        chunk = await file.read(1024 * 1024)
        if not chunk:
            break
        data.extend(chunk)
        if len(data) > MAX_IMAGE_BYTES:
            raise HTTPException(
                status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
                detail="Image is too large. Maximum size is 5 MB.",
            )
    if not data:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="Uploaded file is empty.",
        )

    old_path = product.photo_path
    new_rel = save_product_image_bytes(bytes(data), ext)
    product.photo_path = new_rel
    db.flush()

    # Remove the previous image only after the new one is committed to disk + DB.
    if old_path and old_path != new_rel:
        delete_relative(old_path)

    db.refresh(product)
    return _out(product)


@router.delete("/{product_id}/image", response_model=ProductOut)
def delete_product_image(
    product_id: uuid.UUID,
    db: Session = Depends(get_db),
    _owner: User = Depends(require_shop_staff),
) -> ProductOut:
    product = _get_owned_product_or_404(db, product_id)
    if product.photo_path:
        delete_relative(product.photo_path)
        product.photo_path = None
        db.flush()
        db.refresh(product)
    return _out(product)


def parse_bulk_file(file_content: bytes, filename: str) -> list[dict]:
    # Determine type
    is_excel = filename.endswith(".xlsx") or filename.endswith(".xls")
    
    rows = []
    if is_excel:
        import openpyxl
        try:
            wb = openpyxl.load_workbook(io.BytesIO(file_content), data_only=True)
            sheet = wb.active
        except Exception:
            raise HTTPException(
                status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
                detail="Failed to parse Excel file. The file may be corrupt or invalid."
            )
            
        header = []
        first_row = True
        for row in sheet.iter_rows(values_only=True):
            if first_row:
                header = [str(cell).strip() if cell is not None else "" for cell in row]
                first_row = False
            else:
                if any(cell is not None for cell in row):
                    rows.append(dict(zip(header, row)))
    else:
        try:
            text_content = file_content.decode("utf-8")
        except UnicodeDecodeError:
            try:
                text_content = file_content.decode("latin-1")
            except Exception:
                raise HTTPException(
                    status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
                    detail="Failed to decode CSV file. Please make sure it is in UTF-8 or Latin-1 format."
                )
        reader = csv.DictReader(io.StringIO(text_content))
        for row in reader:
            cleaned_row = {str(k).strip(): str(v).strip() for k, v in row.items() if k is not None}
            if any(cleaned_row.values()):
                rows.append(cleaned_row)
                
    normalized_products = []
    for r_idx, row in enumerate(rows, start=2):
        name_val = None
        category_val = None
        retail_val = None
        wholesale_val = None
        stock_val = 0
        
        for k, v in row.items():
            if not k:
                continue
            k_lower = k.lower().replace(" ", "").replace("_", "")
            
            if k_lower in ("name", "productname", "product"):
                name_val = v
            elif k_lower in ("category", "categoryname", "cat"):
                category_val = v
            elif k_lower in ("retailprice", "price", "retail", "sellingprice"):
                retail_val = v
            elif k_lower in ("wholesaleprice", "lastwholesaleprice", "wholesale"):
                wholesale_val = v
            elif k_lower in ("stock", "stockquantity", "quantity", "qty", "openingstock"):
                stock_val = v

        if not name_val or not str(name_val).strip():
            has_any_data = any(str(val).strip() for val in (category_val, retail_val, wholesale_val) if val is not None)
            if has_any_data:
                raise HTTPException(
                    status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
                    detail=f"Row {r_idx}: Product Name is required."
                )
            continue
            
        try:
            retail_price = decimal.Decimal(str(retail_val or "0").replace("₹", "").replace(",", "").strip())
        except (ValueError, decimal.InvalidOperation):
            raise HTTPException(
                status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
                detail=f"Row {r_idx} ({name_val}): Invalid retail price '{retail_val}'."
            )
            
        wholesale_price = None
        if wholesale_val is not None and str(wholesale_val).strip():
            try:
                wholesale_price = decimal.Decimal(str(wholesale_val).replace("₹", "").replace(",", "").strip())
            except (ValueError, decimal.InvalidOperation):
                raise HTTPException(
                    status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
                    detail=f"Row {r_idx} ({name_val}): Invalid wholesale price '{wholesale_val}'."
                )
                
        try:
            stock = int(float(str(stock_val or "0").strip()))
        except ValueError:
            raise HTTPException(
                status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
                detail=f"Row {r_idx} ({name_val}): Invalid stock value '{stock_val}'."
            )
            
        normalized_products.append({
            "name": str(name_val).strip(),
            "category": str(category_val).strip() if category_val else None,
            "retail_price": retail_price,
            "last_wholesale_price": wholesale_price,
            "stock": stock
        })
        
    return normalized_products


@router.get("/sample-file")
def download_sample_file(
    _owner: User = Depends(require_shop_staff),
):
    """Download a sample CSV file for bulk product upload."""
    sample_content = (
        "Product Name,Category,Retail Price,Wholesale Price,Stock\n"
        "Rose Plant,Flowering Plants,150.00,120.00,50\n"
        "Areca Palm,Indoor Plants,350.00,280.00,20\n"
        "Ceramic Pot 6 inch,Pots & Planters,180.00,,100\n"
        "Organic Fertilizer 1kg,Soil & Fertilizers,99.00,75.00,15\n"
    )
    return StreamingResponse(
        io.StringIO(sample_content),
        media_type="text/csv",
        headers={"Content-Disposition": "attachment; filename=plantora_product_upload_sample.csv"}
    )


@router.post("/bulk-upload", response_model=list[ProductOut])
async def bulk_upload_products(
    file: UploadFile = File(...),
    db: Session = Depends(get_db),
    owner: User = Depends(require_shop_staff),
) -> list[ProductOut]:
    """Bulk upload products from a CSV or Excel file."""
    content = await file.read()
    if len(content) > 10 * 1024 * 1024:
        raise HTTPException(
            status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
            detail="File is too large. Maximum size is 10 MB."
        )
        
    filename = (file.filename or "").lower()
    if not (filename.endswith(".csv") or filename.endswith(".xlsx") or filename.endswith(".xls")):
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="Unsupported file format. Please upload a CSV or Excel (.xlsx) file."
        )
        
    parsed_items = parse_bulk_file(content, filename)
    if not parsed_items:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="The uploaded file contains no valid product rows."
        )
        
    now = dt.datetime.now(dt.timezone.utc)
    created_products = []
    for i, item in enumerate(parsed_items):
        product = Product(
            shop_id=owner.shop_id,
            name=item["name"],
            category=item["category"],
            retail_price=item["retail_price"],
            last_wholesale_price=item["last_wholesale_price"],
            stock=item["stock"],
            created_at=now + dt.timedelta(milliseconds=i),
        )
        db.add(product)
        created_products.append(product)
        
    db.flush()
    for product in created_products:
        db.refresh(product)
        
    return [_out(p) for p in created_products]


@router.post("/bulk-delete", response_model=BulkDeleteResponse)
def bulk_delete_products(
    payload: BulkDeleteRequest,
    db: Session = Depends(get_db),
    _owner: User = Depends(require_shop_staff),
) -> BulkDeleteResponse:
    """Bulk delete or deactivate products.
    
    If a product has sales history, it is soft-deleted (deactivated).
    Otherwise, it is permanently deleted.
    """
    hard_deleted_count = 0
    soft_deleted_count = 0
    
    stmt = select(Product).where(Product.id.in_(payload.product_ids))
    products = db.execute(stmt).scalars().all()
    
    product_ids_found = [p.id for p in products]
    if not product_ids_found:
        return BulkDeleteResponse(
            detail="No matching products found.",
            hard_deleted=0,
            soft_deleted=0
        )
        
    ref_stmt = select(BillItem.product_id).where(
        BillItem.product_id.in_(product_ids_found)
    ).group_by(BillItem.product_id)
    ids_with_history = set(db.execute(ref_stmt).scalars().all())
    
    for product in products:
        if product.id in ids_with_history:
            product.is_active = False
            soft_deleted_count += 1
        else:
            delete_relative(product.photo_path)
            db.delete(product)
            hard_deleted_count += 1
            
    db.flush()
    return BulkDeleteResponse(
        detail=f"Successfully deleted {hard_deleted_count} and deactivated {soft_deleted_count} products.",
        hard_deleted=hard_deleted_count,
        soft_deleted=soft_deleted_count
    )


@router.post("/bulk-photos", response_model=BulkPhotosResponse)
async def bulk_upload_photos(
    file: UploadFile = File(...),
    db: Session = Depends(get_db),
    owner: User = Depends(require_shop_staff),
) -> BulkPhotosResponse:
    """Bulk upload product images from a ZIP archive.
    
    Each image in the ZIP is matched against the shop's active products:
    - By matching the filename (without extension) to the Product Name (slugified/stripped).
    """
    content = await file.read()
    if len(content) > 50 * 1024 * 1024:
        raise HTTPException(
            status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
            detail="ZIP file is too large. Maximum size is 50 MB."
        )
        
    filename = (file.filename or "").lower()
    if not filename.endswith(".zip"):
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="Please upload a valid ZIP archive (.zip) containing images."
        )
        
    products = db.execute(
        select(Product).where(Product.shop_id == owner.shop_id, Product.is_active.is_(True))
    ).scalars().all()
    
    if not products:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="No active products found in your shop. Please upload products first."
        )
        
    def slugify(text: str) -> str:
        return "".join(c for c in text.lower() if c.isalnum())
        
    product_map = {slugify(p.name): p for p in products}
    
    matched_count = 0
    errors = []
    
    try:
        with zipfile.ZipFile(io.BytesIO(content)) as z:
            for zip_info in z.infolist():
                if zip_info.is_dir():
                    continue
                base_name = zip_info.filename.split("/")[-1]
                if not base_name or base_name.startswith("."):
                    continue
                    
                mime_type, _ = mimetypes.guess_type(base_name)
                ext = IMAGE_CONTENT_TYPES.get((mime_type or "").lower())
                
                if not ext:
                    parts = base_name.rsplit(".", 1)
                    if len(parts) == 2:
                        file_ext = "." + parts[1].lower()
                        if file_ext in (".jpg", ".jpeg"):
                            ext = ".jpg"
                        elif file_ext == ".png":
                            ext = ".png"
                        elif file_ext == ".webp":
                            ext = ".webp"
                            
                if not ext:
                    continue
                    
                name_without_ext = base_name.rsplit(".", 1)[0]
                slug = slugify(name_without_ext)
                
                product = product_map.get(slug)
                if product:
                    img_data = z.read(zip_info.filename)
                    if len(img_data) > MAX_IMAGE_BYTES:
                        errors.append(f"{base_name}: Exceeds 5MB size limit.")
                        continue
                        
                    old_path = product.photo_path
                    new_rel = save_product_image_bytes(img_data, ext)
                    product.photo_path = new_rel
                    db.flush()
                    
                    if old_path and old_path != new_rel:
                        delete_relative(old_path)
                        
                    matched_count += 1
    except zipfile.BadZipFile:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="Failed to open ZIP archive. The file may be corrupt."
        )
        
    db.flush()
    
    detail = f"Successfully matched and uploaded {matched_count} product images."
    if errors:
        detail += f" Encountered {len(errors)} issues (e.g. size limits)."
        
    return BulkPhotosResponse(
        detail=detail,
        matched=matched_count,
        errors=errors
    )
