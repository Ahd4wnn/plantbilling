from __future__ import annotations

import datetime as dt
import decimal
import uuid
from pydantic import BaseModel, field_serializer
from app.schemas.expense import ExpenseOut


class CategorySales(BaseModel):
    category: str | None
    quantity: int
    total_sales: decimal.Decimal

    @field_serializer("total_sales")
    def _ser_sales(self, v: decimal.Decimal) -> str:
        return f"{v:.2f}"


class ProductSales(BaseModel):
    product_name: str
    quantity: int
    total_sales: decimal.Decimal

    @field_serializer("total_sales")
    def _ser_sales(self, v: decimal.Decimal) -> str:
        return f"{v:.2f}"


class ExpenseCategoryTotal(BaseModel):
    """Total spend per expense category across the whole period (all the "Petrol"
    together), so the report shows one line per category instead of per-day rows."""

    category: str
    total: decimal.Decimal
    count: int

    @field_serializer("total")
    def _ser_total(self, v: decimal.Decimal) -> str:
        return f"{v:.2f}"


class DetailedReportResponse(BaseModel):
    start_date: dt.date
    end_date: dt.date
    total_sales: decimal.Decimal
    bill_count: int
    cash_total: decimal.Decimal
    upi_total: decimal.Decimal
    due_total: decimal.Decimal
    average_bill_value: decimal.Decimal
    total_expenses: decimal.Decimal
    net_sales: decimal.Decimal
    expenses: list[ExpenseOut]
    expenses_by_category: list[ExpenseCategoryTotal] = []
    categories: list[CategorySales]
    top_products: list[ProductSales]

    @field_serializer("total_sales", "cash_total", "upi_total", "due_total", "average_bill_value", "total_expenses", "net_sales")
    def _ser_money(self, v: decimal.Decimal) -> str:
        return f"{v:.2f}"


class SendReportWhatsAppRequest(BaseModel):
    phone: str
    date_from: dt.date | None = None
    date_to: dt.date | None = None
    created_by: uuid.UUID | None = None
    shop_id: uuid.UUID | None = None
