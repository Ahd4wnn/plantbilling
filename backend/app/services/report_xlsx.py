"""Styled Excel (.xlsx) builder for the manager sales report.

Managers said the CSV "cannot even be called a report", so this produces a
polished, multi-tab workbook: a Summary landing tab plus one tab each for
bills, line items, customers, staff, expenses and the edit/delete log.

Design goals (kept deliberately professional, brand-consistent):
- Botanical green header bands (#2E6F40) with white bold headers, matching the app.
- Money stored as REAL NUMBERS with an Indian ₹ format ("₹#,##,##0.00") so Excel
  can sum/sort/filter — no "INR 123.00" text like the old CSV, and no mojibake
  (xlsx stores the ₹ in XML, unlike CSV which needs a BOM and still guesses).
- Frozen header rows, auto-filters, sensible auto-fit column widths, zebra
  banding and thin borders so a manager can read or print it as-is.

The caller (routers/bills.py) assembles the data; this module only formats it.
"""
from __future__ import annotations

import datetime as dt
import io
from decimal import Decimal
from typing import Any, Callable, Sequence

from openpyxl import Workbook
from openpyxl.styles import Alignment, Border, Font, PatternFill, Side
from openpyxl.utils import get_column_letter
from openpyxl.worksheet.worksheet import Worksheet

# ── Brand palette ───────────────────────────────────────────────────────────
BRAND = "2E6F40"          # botanical green (matches the app + launcher icon)
BRAND_DARK = "1E4D2B"     # deeper green for the title band
BAND = "EAF3EC"           # pale green zebra stripe
GRID = "D8E2DA"           # soft grid line
TEXT_DARK = "1A2E22"      # near-black green-tinted body text
MUTED = "5B6B60"          # secondary labels

INR_FMT = '₹#,##,##0.00'   # ₹ with Indian lakh grouping, 2dp
INT_FMT = '#,##0'

_HEADER_FONT = Font(name="Calibri", size=11, bold=True, color="FFFFFF")
_TITLE_FONT = Font(name="Calibri", size=18, bold=True, color="FFFFFF")
_SUB_FONT = Font(name="Calibri", size=10, color="FFFFFF")
_BODY_FONT = Font(name="Calibri", size=11, color=TEXT_DARK)
_LABEL_FONT = Font(name="Calibri", size=11, bold=True, color=TEXT_DARK)
_MUTED_FONT = Font(name="Calibri", size=10, color=MUTED)
_KPI_FONT = Font(name="Calibri", size=13, bold=True, color=BRAND_DARK)

_HEADER_FILL = PatternFill("solid", fgColor=BRAND)
_TITLE_FILL = PatternFill("solid", fgColor=BRAND_DARK)
_BAND_FILL = PatternFill("solid", fgColor=BAND)
_KPI_FILL = PatternFill("solid", fgColor="F4F9F5")

_thin = Side(style="thin", color=GRID)
_BORDER = Border(left=_thin, right=_thin, top=_thin, bottom=_thin)

_LEFT = Alignment(horizontal="left", vertical="center", wrap_text=False)
_LEFT_WRAP = Alignment(horizontal="left", vertical="center", wrap_text=True)
_RIGHT = Alignment(horizontal="right", vertical="center")
_CENTER = Alignment(horizontal="center", vertical="center")


# Column spec: (header, kind, width_hint)
#   kind: "text" | "money" | "int" | "wraptext"
Col = tuple[str, str, int]


def _num(v: Any) -> float:
    if isinstance(v, Decimal):
        return float(v)
    return float(v or 0)


def _autofit(ws: Worksheet, widths: dict[int, int]) -> None:
    for idx, w in widths.items():
        ws.column_dimensions[get_column_letter(idx)].width = w


def _write_table(
    ws: Worksheet,
    top: int,
    title: str,
    cols: Sequence[Col],
    rows: Sequence[Sequence[Any]],
    empty_text: str = "(nothing to show for this period)",
) -> int:
    """Render a titled, styled table starting at row `top`. Returns next free row."""
    ncols = len(cols)
    # Section title band
    ws.merge_cells(start_row=top, start_column=1, end_row=top, end_column=ncols)
    tcell = ws.cell(row=top, column=1, value=title)
    tcell.font = Font(name="Calibri", size=12, bold=True, color=BRAND_DARK)
    tcell.alignment = _LEFT
    ws.row_dimensions[top].height = 22

    header_row = top + 1
    for c, (label, _kind, _w) in enumerate(cols, start=1):
        cell = ws.cell(row=header_row, column=c, value=label)
        cell.font = _HEADER_FONT
        cell.fill = _HEADER_FILL
        cell.alignment = _CENTER
        cell.border = _BORDER
    ws.row_dimensions[header_row].height = 20

    r = header_row + 1
    if not rows:
        ws.merge_cells(start_row=r, start_column=1, end_row=r, end_column=ncols)
        ec = ws.cell(row=r, column=1, value=empty_text)
        ec.font = _MUTED_FONT
        ec.alignment = _LEFT
        ec.border = _BORDER
        return r + 2

    for i, row in enumerate(rows):
        band = _BAND_FILL if i % 2 else None
        for c, ((_label, kind, _w), value) in enumerate(zip(cols, row), start=1):
            cell = ws.cell(row=r, column=c)
            if kind == "money":
                cell.value = _num(value)
                cell.number_format = INR_FMT
                cell.alignment = _RIGHT
            elif kind == "int":
                cell.value = int(value or 0)
                cell.number_format = INT_FMT
                cell.alignment = _RIGHT
            elif kind == "wraptext":
                cell.value = value
                cell.alignment = _LEFT_WRAP
            else:
                cell.value = value
                cell.alignment = _LEFT
            cell.font = _BODY_FONT
            cell.border = _BORDER
            if band:
                cell.fill = band
        r += 1

    # Freeze header + auto-filter the data block
    ws.freeze_panes = ws.cell(row=header_row + 1, column=1)
    ws.auto_filter.ref = (
        f"{get_column_letter(1)}{header_row}:{get_column_letter(ncols)}{r - 1}"
    )
    _autofit(ws, {i: w for i, (_l, _k, w) in enumerate(cols, start=1)})
    return r + 2


def _build_summary_tab(ws: Worksheet, meta: dict, kpis: list[tuple[str, Any, str]]) -> None:
    ws.sheet_view.showGridLines = False
    ws.column_dimensions["A"].width = 3
    ws.column_dimensions["B"].width = 30
    ws.column_dimensions["C"].width = 26
    ws.column_dimensions["D"].width = 3

    # Title band (B2:C4)
    ws.merge_cells("B2:C2")
    t = ws.cell(row=2, column=2, value="PLANTBILL")
    t.font = Font(name="Calibri", size=10, bold=True, color="B7D9C0")
    t.alignment = _LEFT
    ws.merge_cells("B3:C3")
    t2 = ws.cell(row=3, column=2, value="Sales Report")
    t2.font = _TITLE_FONT
    t2.alignment = _LEFT
    for rr in (2, 3, 4):
        for cc in (2, 3):
            ws.cell(row=rr, column=cc).fill = _TITLE_FILL
    ws.merge_cells("B4:C4")
    t3 = ws.cell(row=4, column=2, value=meta["shop_name"])
    t3.font = _SUB_FONT
    t3.alignment = _LEFT
    ws.row_dimensions[2].height = 16
    ws.row_dimensions[3].height = 30
    ws.row_dimensions[4].height = 18

    # Meta block
    meta_rows = [
        ("Period", meta["period"]),
        ("Generated by", meta["generated_by"]),
        ("Generated at", meta["generated_at"]),
    ]
    if meta.get("staff_filter"):
        meta_rows.append(("Filtered to staff", meta["staff_filter"]))
    r = 6
    for label, value in meta_rows:
        lc = ws.cell(row=r, column=2, value=label)
        lc.font = _MUTED_FONT
        lc.alignment = _LEFT
        vc = ws.cell(row=r, column=3, value=value)
        vc.font = _LABEL_FONT
        vc.alignment = _LEFT
        r += 1

    # KPI cards (label / value pairs)
    r += 1
    hdr = ws.cell(row=r, column=2, value="AT A GLANCE")
    hdr.font = Font(name="Calibri", size=11, bold=True, color=BRAND)
    r += 1
    for label, value, kind in kpis:
        lc = ws.cell(row=r, column=2, value=label)
        lc.font = _LABEL_FONT
        lc.alignment = _LEFT
        lc.fill = _KPI_FILL
        lc.border = _BORDER
        vc = ws.cell(row=r, column=3)
        if kind == "money":
            vc.value = _num(value)
            vc.number_format = INR_FMT
        elif kind == "int":
            vc.value = int(value or 0)
            vc.number_format = INT_FMT
        else:
            vc.value = value
        vc.font = _KPI_FONT
        vc.alignment = _RIGHT
        vc.fill = _KPI_FILL
        vc.border = _BORDER
        ws.row_dimensions[r].height = 20
        r += 1


def build_report_workbook(
    *,
    meta: dict,
    kpis: list[tuple[str, Any, str]],
    bills: Sequence[Sequence[Any]],
    line_items: Sequence[Sequence[Any]],
    customers: Sequence[Sequence[Any]],
    staff: Sequence[Sequence[Any]],
    expenses: Sequence[Sequence[Any]],
    expenses_by_category: Sequence[Sequence[Any]] = (),
    labour: Sequence[Sequence[Any]] = (),
    attendance: Sequence[Sequence[Any]] = (),
    audit: Sequence[Sequence[Any]] = (),
) -> bytes:
    wb = Workbook()

    summary = wb.active
    summary.title = "Summary"
    summary.sheet_properties.tabColor = BRAND
    _build_summary_tab(summary, meta, kpis)

    tabs = [
        ("All Bills", [
            ("Bill No", "text", 12), ("Date & Time", "text", 18), ("Staff", "text", 22),
            ("Customer", "text", 20), ("Phone", "text", 15), ("Items", "wraptext", 40),
            ("Subtotal", "money", 14), ("Discount", "money", 13), ("Total", "money", 14),
            ("Cash", "money", 13), ("UPI", "money", 13), ("Due", "money", 13),
            ("Payment", "text", 11), ("Edited", "text", 9),
        ], bills, "(no bills in this period)"),
        ("Line Items", [
            ("Bill No", "text", 12), ("Date & Time", "text", 18), ("Product", "text", 30),
            ("Unit Price", "money", 14), ("Qty", "int", 8), ("Line Total", "money", 15),
        ], line_items, "(no line items in this period)"),
        ("Customers", [
            ("Customer", "text", 26), ("Phone", "text", 16), ("Bills", "int", 9),
            ("Total Purchased", "money", 18), ("Total Due", "money", 15),
            ("Staff Handled", "wraptext", 30),
        ], customers, "(no bills in this period)"),
        ("Staff", [
            ("Staff", "text", 28), ("Bills", "int", 9), ("Total Sales", "money", 16),
            ("Cash", "money", 14), ("UPI", "money", 14), ("Due", "money", 14),
        ], staff, "(no bills in this period)"),
        ("Expenses by Category", [
            ("Category", "text", 30), ("Count", "int", 9), ("Total", "money", 16),
        ], expenses_by_category, "(no expenses in this period)"),
        ("Expenses", [
            ("Date & Time", "text", 18), ("Recorded By", "text", 26),
            ("Category", "text", 26), ("Remark", "wraptext", 34), ("Amount", "money", 15),
        ], expenses, "(no expenses in this period)"),
        ("Labour Payments", [
            ("Date & Time", "text", 18), ("Labourer", "text", 22), ("Gender", "text", 9),
            ("Type", "text", 11), ("Days", "text", 7), ("Wage", "money", 12), ("Total", "money", 13),
            ("Method", "text", 10), ("Cash", "money", 12), ("UPI", "money", 12),
            ("Due", "money", 12), ("Recorded By", "text", 24), ("Note", "wraptext", 28),
        ], labour, "(no labour payments in this period)"),
        ("Labour Attendance", [
            ("Day", "text", 14), ("Labourer", "text", 24), ("Status", "text", 14),
            ("Marked By", "text", 26),
        ], attendance, "(no attendance marked in this period)"),
        ("Edit & Delete Log", [
            ("Date & Time", "text", 18), ("Action", "text", 10), ("Bill No", "text", 12),
            ("By", "text", 26), ("Details", "wraptext", 50),
        ], audit, "(no edits or deletions in this period)"),
    ]

    for name, cols, rows, empty in tabs:
        ws = wb.create_sheet(name)
        ws.sheet_view.showGridLines = False
        _write_table(ws, 1, name.upper(), cols, rows, empty)

    buf = io.BytesIO()
    wb.save(buf)
    return buf.getvalue()
