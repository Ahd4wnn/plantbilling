"""What a worker has earned, from their attendance.

Pure arithmetic — no database, no request context — so the rules below can be
unit-tested directly. `app/routers/labour.py` reads the attendance rows and calls
in here; nothing else computes wages.

Two modes:

**Daily.** Straightforward: rupees-per-day x days marked present, where a half-day
counts as half a day.

**Monthly.** Attendance drives *deductions*, not earnings. A full month pays the
full salary; leaves beyond the worker's monthly paid-leave allowance are deducted
at monthly_wage / 30 per leave. Deliberate choices baked in here:

- **Part months are pro-rated.** The joining month and the current, unfinished
  month pay monthly_wage / 30 per calendar day in range. Someone who joined on the
  20th earns roughly a third of a month, and the current month accrues day by day
  — so a monthly worker's balance moves through the month exactly as a daily
  worker's does, instead of jumping on the 1st.
- **Only marked absences count as leave**, with a half-day as half a leave. Days
  nobody marked at all are ignored. Attendance here is entered by hand by a shop
  manager who may be busy or away; treating an unmarked day as absence would cut a
  real person's pay because somebody forgot to tap a button.
- **The allowance resets monthly** and does not carry forward, and it is granted in
  full even in a part month — a worker joining on the 25th still gets that month's
  paid leaves. Both are the generous reading, chosen on purpose.
- **A month never earns less than zero.** A month of nothing but absences pays
  nothing; it does not claw back against other months.

The /30 divisor is fixed, not the real length of the month. It is what shops
actually use when they say "one day's pay", and it keeps a leave in February worth
the same as a leave in March.
"""
from __future__ import annotations

import calendar
import datetime as dt
from decimal import ROUND_HALF_UP, Decimal
from typing import Mapping

ZERO = Decimal("0")

# Days of work credited by each attendance status.
STATUS_DAYS: Mapping[str, Decimal] = {
    "present": Decimal("1"),
    "half_day": Decimal("0.5"),
    "absent": ZERO,
}

# Leave taken by each attendance status — the mirror image of STATUS_DAYS, and what
# the monthly deduction counts. An unmarked day appears in neither.
STATUS_LEAVES: Mapping[str, Decimal] = {
    "present": ZERO,
    "half_day": Decimal("0.5"),
    "absent": Decimal("1"),
}

# A "month" for the purpose of one day's pay. See the module docstring.
DAYS_PER_MONTH = Decimal("30")


def q2(value: Decimal) -> Decimal:
    """Money, 2dp, ROUND_HALF_UP — the project-wide rule."""
    return Decimal(value).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)


def month_start(day: dt.date) -> dt.date:
    return day.replace(day=1)


def month_end(day: dt.date) -> dt.date:
    return day.replace(day=calendar.monthrange(day.year, day.month)[1])


def next_month(day: dt.date) -> dt.date:
    """First day of the month after the one `day` falls in."""
    return (month_end(day) + dt.timedelta(days=1)).replace(day=1)


def daily_earnings(wage_per_day: Decimal, days_worked: Decimal) -> Decimal:
    """Rupees earned by a daily-wage worker."""
    return q2(Decimal(wage_per_day) * Decimal(days_worked))


def unpaid_leaves_in_month(leaves: Decimal, allowance: int) -> Decimal:
    """Leaves that actually cost the worker money, after the monthly allowance."""
    return max(ZERO, Decimal(leaves) - Decimal(allowance))


def monthly_earnings(
    monthly_wage: Decimal,
    paid_leaves_per_month: int,
    joined_on: dt.date,
    today: dt.date,
    leaves_by_month: Mapping[dt.date, Decimal],
) -> Decimal:
    """Rupees earned by a monthly-salary worker, from joining up to `today`.

    `leaves_by_month` is keyed by the FIRST day of each month; a month with no
    entry simply has no leaves. Months are walked rather than derived from the
    leave map because a month with perfect attendance still has to be paid.
    """
    wage = Decimal(monthly_wage)
    if wage <= ZERO or joined_on > today:
        return ZERO

    daily_rate = wage / DAYS_PER_MONTH
    total = ZERO

    cursor = month_start(joined_on)
    final = month_start(today)
    while cursor <= final:
        first, last = cursor, month_end(cursor)
        window_start = max(joined_on, first)
        window_end = min(today, last)
        if window_end >= window_start:
            covers_whole_month = window_start == first and window_end == last
            if covers_whole_month:
                base = wage
            else:
                days_in_window = Decimal((window_end - window_start).days + 1)
                base = daily_rate * days_in_window

            unpaid = unpaid_leaves_in_month(
                leaves_by_month.get(cursor, ZERO), paid_leaves_per_month
            )
            # Floored at zero: a month of pure absence pays nothing, and must not
            # eat into what other months earned.
            total += max(ZERO, base - daily_rate * unpaid)

        cursor = next_month(cursor)

    # Rounded once at the end, so a worker of many months doesn't accumulate a
    # rupee of rounding drift per month.
    return q2(total)
