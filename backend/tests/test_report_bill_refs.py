"""How the sales report names a bill.

Every tab is headed "Bill No", so whatever goes in that column has to be the
number the shop recognises. The edit & delete log used to print a UUID fragment
there for every row, which managers read as random noise.
"""
import uuid

from app.routers.bills import _audit_bill_ref, _short_uuid


def test_an_audit_entry_shows_the_shops_own_bill_number():
    assert _audit_bill_ref(42, uuid.uuid4()) == "0042"
    assert _audit_bill_ref(1, uuid.uuid4()) == "0001"


def test_bill_numbers_past_9999_are_not_truncated():
    # Zero-padding is a minimum width, not a cap — a busy shop keeps counting.
    assert _audit_bill_ref(12345, uuid.uuid4()) == "12345"


def test_an_entry_naming_no_bill_shows_a_dash():
    # Account deletions live in the same log but aren't about a bill.
    assert _audit_bill_ref(None, None) == "—"


def test_entries_predating_the_stored_number_fall_back_to_the_uuid_fragment():
    # Nothing else survives for these; a fragment beats inventing a number.
    bid = uuid.UUID("3f9a2b1c-0000-0000-0000-000000000000")
    assert _audit_bill_ref(None, bid) == "3F9A2B1C"


def test_short_uuid_is_upper_case_and_handles_none():
    assert _short_uuid(uuid.UUID("abcdef12-3456-0000-0000-000000000000")) == "ABCDEF12"
    assert _short_uuid(None) == "—"
