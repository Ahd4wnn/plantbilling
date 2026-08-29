"""The wage engine, against hand-computed cases.

This is money paid to real people, and the monthly formula has several branches
(full month, joining month, current month, allowance exhausted, floor at zero), so
each one gets an explicit expected number worked out by hand rather than by
re-running the implementation.
"""
import datetime as dt
from decimal import Decimal

from app.services.labour_wages import (
    daily_earnings,
    month_end,
    month_start,
    monthly_earnings,
    next_month,
    unpaid_leaves_in_month,
)

D = Decimal
WAGE = D("30000")          # 30000/30 = 1000 per day — keeps the arithmetic obvious
SEP = dt.date(2026, 9, 1)  # September: 30 days
AUG = dt.date(2026, 8, 1)  # August: 31 days


# ── Daily workers (unchanged behaviour) ─────────────────────────────────────
def test_daily_earnings_counts_half_days_as_half():
    # 3 present + 2 half-days = 4 days at 500
    assert daily_earnings(D("500"), D("4")) == D("2000.00")


def test_daily_earnings_with_no_attendance_is_zero():
    assert daily_earnings(D("500"), D("0")) == D("0.00")


# ── Month helpers ────────────────────────────────────────────────────────────
def test_month_boundaries_handle_february():
    feb = dt.date(2026, 2, 14)
    assert month_start(feb) == dt.date(2026, 2, 1)
    assert month_end(feb) == dt.date(2026, 2, 28)
    assert next_month(feb) == dt.date(2026, 3, 1)


def test_next_month_rolls_the_year():
    assert next_month(dt.date(2026, 12, 5)) == dt.date(2027, 1, 1)


def test_unpaid_leaves_only_counts_beyond_the_allowance():
    assert unpaid_leaves_in_month(D("1"), 2) == D("0")
    assert unpaid_leaves_in_month(D("2"), 2) == D("0")
    assert unpaid_leaves_in_month(D("3.5"), 2) == D("1.5")


# ── Monthly workers ──────────────────────────────────────────────────────────
def test_full_month_with_no_leaves_pays_the_full_salary():
    assert monthly_earnings(WAGE, 2, SEP, dt.date(2026, 9, 30), {}) == D("30000.00")


def test_leaves_within_the_allowance_cost_nothing():
    got = monthly_earnings(WAGE, 2, SEP, dt.date(2026, 9, 30), {SEP: D("2")})
    assert got == D("30000.00")


def test_leaves_beyond_the_allowance_are_deducted_at_a_thirtieth():
    # 3 leaves, 2 paid -> 1 unpaid -> 30000 - (30000/30 * 1)
    got = monthly_earnings(WAGE, 2, SEP, dt.date(2026, 9, 30), {SEP: D("3")})
    assert got == D("29000.00")


def test_half_day_counts_as_half_a_leave():
    # 2 absent + 2 half-days = 3.0 leaves; 2 paid -> 1.0 unpaid
    got = monthly_earnings(WAGE, 2, SEP, dt.date(2026, 9, 30), {SEP: D("3.0")})
    assert got == D("29000.00")


def test_joining_mid_month_is_prorated():
    # Joined the 20th, month runs to the 29th = 10 days at 1000/day
    got = monthly_earnings(WAGE, 2, dt.date(2026, 8, 20), dt.date(2026, 8, 29), {})
    assert got == D("10000.00")


def test_current_month_accrues_day_by_day():
    # Same worker, one day later, earns exactly one more day's pay.
    day_9 = monthly_earnings(WAGE, 0, SEP, dt.date(2026, 9, 9), {})
    day_10 = monthly_earnings(WAGE, 0, SEP, dt.date(2026, 9, 10), {})
    assert day_10 - day_9 == D("1000.00")


def test_a_month_of_pure_absence_pays_nothing_and_never_goes_negative():
    got = monthly_earnings(WAGE, 0, SEP, dt.date(2026, 9, 30), {SEP: D("30")})
    assert got == D("0.00")
    # Even more leaves than days in the month can't produce a negative month.
    worse = monthly_earnings(WAGE, 0, SEP, dt.date(2026, 9, 30), {SEP: D("45")})
    assert worse == D("0.00")


def test_spans_several_months_with_both_ends_prorated():
    # Aug 20-31 = 12 days = 12000 · Sep full = 30000 · Oct 1-10 = 10 days = 10000
    got = monthly_earnings(WAGE, 0, dt.date(2026, 8, 20), dt.date(2026, 10, 10), {})
    assert got == D("52000.00")


def test_the_allowance_resets_every_month():
    # 2 leaves in each of two months, allowance 2 -> nothing deducted at all.
    # Both months are complete, so each pays the full salary — a 31-day August is
    # not worth more than a 30-day September (see the February case below).
    spread = monthly_earnings(
        WAGE, 2, AUG, dt.date(2026, 9, 30), {AUG: D("2"), SEP: D("2")}
    )
    assert spread == D("60000.00")

    # The same 4 leaves bunched into ONE month costs 2 days' pay.
    bunched = monthly_earnings(WAGE, 2, AUG, dt.date(2026, 9, 30), {SEP: D("4")})
    assert bunched == D("58000.00")


def test_unmarked_days_are_not_leaves():
    # A month with only a handful of marks still pays in full: the days nobody
    # touched must not be read as absence.
    got = monthly_earnings(WAGE, 0, SEP, dt.date(2026, 9, 30), {SEP: D("0")})
    assert got == D("30000.00")


def test_a_zero_salary_or_a_future_joining_date_earns_nothing():
    assert monthly_earnings(D("0"), 2, SEP, dt.date(2026, 9, 30), {}) == D("0")
    assert monthly_earnings(WAGE, 2, dt.date(2026, 12, 1), SEP, {}) == D("0")


def test_february_full_month_still_pays_the_whole_salary():
    # 28 days, but a full month is a full month — the /30 divisor is only used
    # for part months and for leave deductions.
    feb = dt.date(2026, 2, 1)
    got = monthly_earnings(WAGE, 0, feb, dt.date(2026, 2, 28), {})
    assert got == D("30000.00")
