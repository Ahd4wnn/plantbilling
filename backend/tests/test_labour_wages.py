"""The wage engine, against hand-computed cases.

This is money paid to real people, and the monthly formula has several branches
(full month, joining month, current month, allowance exhausted, floor at zero), so
each one gets an explicit expected number worked out by hand rather than by
re-running the implementation.

Note the `last_marked` argument throughout: the current month accrues only as far
as attendance has been recorded, so a test asking about a *completed* month has to
say the month was marked to its end. Passing `last_marked=today` reads as "the
record is up to date", which is the ordinary case.
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
WAGE = D("30000")              # 30000/30 = 1000 per day — keeps the arithmetic obvious
SEP = dt.date(2026, 9, 1)      # September: 30 days
SEP_END = dt.date(2026, 9, 30)
AUG = dt.date(2026, 8, 1)      # August: 31 days


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
    got = monthly_earnings(WAGE, 2, SEP, SEP_END, {}, last_marked=SEP_END)
    assert got == D("30000.00")


def test_leaves_within_the_allowance_cost_nothing():
    got = monthly_earnings(WAGE, 2, SEP, SEP_END, {SEP: D("2")}, last_marked=SEP_END)
    assert got == D("30000.00")


def test_leaves_beyond_the_allowance_are_deducted_at_a_thirtieth():
    # 3 leaves, 2 paid -> 1 unpaid -> 30000 - (30000/30 * 1)
    got = monthly_earnings(WAGE, 2, SEP, SEP_END, {SEP: D("3")}, last_marked=SEP_END)
    assert got == D("29000.00")


def test_half_day_counts_as_half_a_leave():
    # 2 absent + 2 half-days = 3.0 leaves; 2 paid -> 1.0 unpaid
    got = monthly_earnings(WAGE, 2, SEP, SEP_END, {SEP: D("3.0")}, last_marked=SEP_END)
    assert got == D("29000.00")


def test_joining_mid_month_is_prorated():
    # Joined the 20th, marked through the 29th = 10 days at 1000/day
    aug_29 = dt.date(2026, 8, 29)
    got = monthly_earnings(WAGE, 2, dt.date(2026, 8, 20), aug_29, {}, last_marked=aug_29)
    assert got == D("10000.00")


def test_a_month_of_pure_absence_pays_nothing_and_never_goes_negative():
    got = monthly_earnings(WAGE, 0, SEP, SEP_END, {SEP: D("30")}, last_marked=SEP_END)
    assert got == D("0.00")
    # Even more leaves than days in the month can't produce a negative month.
    worse = monthly_earnings(WAGE, 0, SEP, SEP_END, {SEP: D("45")}, last_marked=SEP_END)
    assert worse == D("0.00")


def test_spans_several_months_with_both_ends_prorated():
    # Aug 20-31 = 12 days = 12000 · Sep full = 30000 · Oct 1-10 = 10 days = 10000
    oct_10 = dt.date(2026, 10, 10)
    got = monthly_earnings(WAGE, 0, dt.date(2026, 8, 20), oct_10, {}, last_marked=oct_10)
    assert got == D("52000.00")


def test_the_allowance_resets_every_month():
    # 2 leaves in each of two months, allowance 2 -> nothing deducted at all.
    # Both months are complete, so each pays the full salary — a 31-day August is
    # not worth more than a 30-day September (see the February case below).
    spread = monthly_earnings(
        WAGE, 2, AUG, SEP_END, {AUG: D("2"), SEP: D("2")}, last_marked=SEP_END
    )
    assert spread == D("60000.00")

    # The same 4 leaves bunched into ONE month costs 2 days' pay.
    bunched = monthly_earnings(WAGE, 2, AUG, SEP_END, {SEP: D("4")}, last_marked=SEP_END)
    assert bunched == D("58000.00")


def test_unmarked_days_are_not_leaves():
    # A month with only a handful of marks still pays in full: the days nobody
    # touched must not be read as absence.
    got = monthly_earnings(WAGE, 0, SEP, SEP_END, {SEP: D("0")}, last_marked=SEP_END)
    assert got == D("30000.00")


def test_a_zero_salary_or_a_future_joining_date_earns_nothing():
    assert monthly_earnings(D("0"), 2, SEP, SEP_END, {}, last_marked=SEP_END) == D("0")
    assert monthly_earnings(WAGE, 2, dt.date(2026, 12, 1), SEP, {}, last_marked=SEP) == D("0")


def test_february_full_month_still_pays_the_whole_salary():
    # 28 days, but a full month is a full month — the /30 divisor is only used
    # for part months and for leave deductions.
    feb = dt.date(2026, 2, 1)
    feb_end = dt.date(2026, 2, 28)
    got = monthly_earnings(WAGE, 0, feb, feb_end, {}, last_marked=feb_end)
    assert got == D("30000.00")


# ── The current month only accrues as far as attendance was recorded ─────────
def test_the_current_month_stops_at_the_last_marked_day():
    """The reported bug, with the shop's real numbers.

    ₹16,500/month = ₹550/day. Joined 1 Sept, today is the 4th, attendance marked
    on the 1st, 2nd and 3rd — one of them a leave, inside a 2-day allowance. The
    engine used to count the unmarked 4th and return 2200.
    """
    wage, joined = D("16500"), dt.date(2026, 9, 1)
    today, marked_to = dt.date(2026, 9, 4), dt.date(2026, 9, 3)

    got = monthly_earnings(wage, 2, joined, today, {SEP: D("1")}, last_marked=marked_to)
    assert got == D("1650.00")           # 3 days × 550, leave inside the allowance

    # Marking today adds exactly one more day.
    with_today = monthly_earnings(wage, 2, joined, today, {SEP: D("1")}, last_marked=today)
    assert with_today == D("2200.00")


def test_a_day_missed_in_the_middle_of_the_month_is_still_paid():
    # Marked on the 1st, 3rd and 4th; today is the 5th. The 2nd was never touched,
    # but the window runs 1st-4th — a forgotten tap must not cost a day's pay.
    got = monthly_earnings(
        WAGE, 0, SEP, dt.date(2026, 9, 5), {}, last_marked=dt.date(2026, 9, 4)
    )
    assert got == D("4000.00")


def test_the_current_month_accrues_as_attendance_is_recorded():
    # One more marked day is worth exactly one more day's pay, with `today` fixed.
    today = dt.date(2026, 9, 20)
    day_9 = monthly_earnings(WAGE, 0, SEP, today, {}, last_marked=dt.date(2026, 9, 9))
    day_10 = monthly_earnings(WAGE, 0, SEP, today, {}, last_marked=dt.date(2026, 9, 10))
    assert day_9 == D("9000.00")
    assert day_10 - day_9 == D("1000.00")


def test_nothing_marked_this_month_earns_nothing_yet():
    # ...but completed months are untouched: August still pays in full.
    got = monthly_earnings(WAGE, 0, AUG, dt.date(2026, 9, 15), {}, last_marked=None)
    assert got == D("30000.00")

    # A worker who joined this month and has no marks at all is simply at zero.
    fresh = monthly_earnings(WAGE, 0, SEP, dt.date(2026, 9, 15), {}, last_marked=None)
    assert fresh == D("0.00")


def test_last_marked_never_lets_a_month_run_past_today():
    # A stray future mark (a typo in the attendance screen) can't pay ahead.
    got = monthly_earnings(
        WAGE, 0, SEP, dt.date(2026, 9, 4), {}, last_marked=dt.date(2026, 9, 25)
    )
    assert got == D("4000.00")
