"""Member 3 integration checks; invoked by smoke_test.py against Payara and PostgreSQL."""
import concurrent.futures
from contextlib import contextmanager
from pathlib import Path
import subprocess
import time
import uuid


def check_member3(base, owner, other, sql, request, expect):
    root = Path(__file__).resolve().parent.parent

    def call(actor, path, payload=None, method="GET"):
        client = type(actor[0])(base, actor[0].jar)
        return client.api(path, payload, actor[2], method)[:2]

    @contextmanager
    def event(capacity=6):
        event_id = int(sql("INSERT INTO events(title,venue,starts_at,capacity,available_seats,ticket_price) "
                           f"VALUES ('Member 3 smoke','CI',now()+interval '7 days',{capacity},{capacity},125.50) RETURNING id"))
        try:
            yield event_id
        finally:
            sql(f"DELETE FROM reservations WHERE event_id={event_id}; DELETE FROM events WHERE id={event_id}")

    def book(actor, event_id, numbers):
        return call(actor, "/api/reservations", {"eventId": event_id, "customerName": "Member 3",
                    "customerEmail": "member3@example.com", "quantity": len(numbers), "seatNumbers": numbers}, "POST")

    def due(reference):
        sql(f"UPDATE reservations SET created_at=now()-interval '20 minutes', expires_at=now()-interval '1 second' "
            f"WHERE id='{uuid.UUID(reference)}'")

    def inventory(event_id, available, allocated):
        return sql(f"SELECT e.available_seats={available} AND "
                   f"(SELECT count(*) FROM event_seats s WHERE s.event_id=e.id AND s.reservation_id IS NOT NULL)={allocated} "
                   f"FROM events e WHERE e.id={event_id}") == "t"

    def migration_body():
        return "\n".join(line for line in (root / "docker/postgres/006-seats-and-reservation-lifecycle.sql")
                         .read_text(encoding="utf-8").splitlines() if line.strip() not in ("BEGIN;", "COMMIT;"))

    schema = "member3_upgrade_" + uuid.uuid4().hex
    old = "\n".join((root / "docker/postgres" / name).read_text(encoding="utf-8") for name in
                    ("001-schema.sql", "002-demo.sql", "003-venues-categories.sql", "004-users.sql"))
    owner_upgrade = (root / "docker/postgres/005-reservation-owner.sql").read_text(encoding="utf-8")
    fixture_user, legacy_id, owned_id = (uuid.uuid4() for _ in range(3))
    setup = (f"BEGIN; CREATE SCHEMA {schema}; SET LOCAL search_path TO {schema};\n" + old
             + f"\nINSERT INTO users VALUES ('{fixture_user}','upgrade@example.com','fixture','USER'); "
               "INSERT INTO reservations(id,event_id,customer_name,customer_email,quantity,total_price,status,created_at) "
             + f"VALUES ('{legacy_id}',1,'Legacy','legacy@example.com',1,350,'ACTIVE',now());\n"
             + owner_upgrade
             + "\nINSERT INTO reservations(id,user_id,event_id,customer_name,customer_email,quantity,total_price,status,created_at) "
             + f"VALUES ('{owned_id}','{fixture_user}',1,'Owned','owned@example.com',2,700,'ACTIVE',now()); "
               "UPDATE events SET available_seats=capacity-3 WHERE id=1;\n")
    result = sql(setup + migration_body()
                 + f"\nSELECT (SELECT count(*) FROM event_seats WHERE reservation_id IS NOT NULL)=3 "
                   "AND (SELECT count(*) FROM reservation_seats)=3 "
                 + f"AND (SELECT expires_at IS NULL AND user_id IS NULL FROM reservations WHERE id='{legacy_id}') "
                 + f"AND (SELECT expires_at>created_at AND total_price=700 FROM reservations WHERE id='{owned_id}') "
                   "AND (SELECT available_seats=97 FROM events WHERE id=1); ROLLBACK;")
    expect(result == "t", "006 preserves populated owner/legacy rows and backfills disjoint seat allocations")

    # A failed upgrade must roll back all of 006, including the first ALTER TABLE.
    schema = "member3_invalid_" + uuid.uuid4().hex
    try:
        sql(f"CREATE SCHEMA {schema}; SET search_path TO {schema};\n" + old + "\n" + owner_upgrade
            + "\nUPDATE events SET available_seats=capacity-1 WHERE id=1;")
        try:
            sql(f"BEGIN; SET LOCAL search_path TO {schema};\n" + migration_body() + "\nCOMMIT;")
        except subprocess.CalledProcessError:
            pass
        else:
            raise AssertionError("006 accepted inconsistent inventory")
        expect(sql("SELECT count(*) FROM information_schema.columns "
                   f"WHERE table_schema='{schema}' AND table_name='reservations' AND column_name='expires_at'") == "0",
               "006 rejects inconsistent inventory and rolls back the entire migration")
    finally:
        sql(f"DROP SCHEMA IF EXISTS {schema} CASCADE")

    with event() as event_id:
        first = book(owner, event_id, [2])
        newest = book(owner, event_id, [4])
        foreign = book(other, event_id, [6])
        expect(all(item[0] == 201 for item in (first, newest, foreign)), "explicit seats can be selected")
        page = call(owner, "/api/reservations?page=0&size=1&userId=" + other[1]["id"])
        expect(page[0] == 200 and page[1]["totalElements"] == 2 and page[1]["totalPages"] == 2
               and [r["id"] for r in page[1]["items"]] == [newest[1]["id"]], "pagination is newest-first and ignores forged owner")
        expect(call(owner, "/api/reservations?page=1&size=1")[1]["items"][0]["id"] == first[1]["id"],
               "second owner page returns the remaining reservation")
        expect(call(owner, "/api/reservations?page=2&size=1")[1]["items"] == [], "out-of-range page is empty")
        for query in ("page=-1", "size=0", "size=101", "page=2147483647&size=100"):
            expect(call(owner, "/api/reservations?" + query)[0] == 400, "invalid pagination is rejected")
        expect(request(base, "/api/reservations")[0] == 401, "anonymous reservation listing is denied")
        for selection in ([1, 1], [0], [99]):
            expect(book(owner, event_id, selection)[0] == 400, "invalid, duplicate or unknown seats are rejected")
        status, seat_list = request(base, f"/api/events/{event_id}/seats")
        expect(status == 200 and [s["number"] for s in seat_list] == list(range(1, 7))
               and all(set(s) == {"number", "available"} for s in seat_list), "public seat map omits reservation identity")
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
            results = list(executor.map(lambda actor: book(actor, event_id, [1]), (owner, other)))
        expect(sorted(code for code, _ in results) == [201, 409] and inventory(event_id, 2, 4),
               "two users racing for the same numbered seat produce exactly one reservation")
        sql(f"DO $$ BEGIN INSERT INTO event_seats(event_id,seat_number) VALUES ({event_id},1); "
            "RAISE EXCEPTION 'missing seat uniqueness'; EXCEPTION WHEN unique_violation THEN NULL; END $$;")

    with event(1) as event_id:
        old_booking = book(owner, event_id, [1])[1]
        due(old_booking["id"])
        next_booking = book(other, event_id, [1])
        expect(next_booking[0] == 201 and inventory(event_id, 0, 1), "booking reclaims an expired seat before the next sweep")
        path = "/api/reservations/" + old_booking["id"]
        expect(call(owner, path)[1]["status"] == "EXPIRED" and call(owner, path)[1]["seatNumbers"] == [1],
               "expired booking retains its historical seat selection")
        expect(call(owner, path + "/cancel", method="POST")[0] == 409 and inventory(event_id, 0, 1),
               "old owner cannot release a seat reassigned to a new reservation")

    with event(1) as event_id:
        booking = book(owner, event_id, [1])[1]
        due(booking["id"])
        path = "/api/reservations/" + booking["id"] + "/cancel"
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
            codes = list(executor.map(lambda _: call(owner, path, method="POST")[0], range(2)))
        expect(sorted(codes) in ([200, 409], [409, 409]) and inventory(event_id, 1, 0),
               "expiry and concurrent cancellation return stock exactly once")

    with event(1) as event_id:
        booking = book(owner, event_id, [1])[1]
        due(booking["id"])
        deadline = time.monotonic() + 45
        while sql(f"SELECT status FROM reservations WHERE id='{uuid.UUID(booking['id'])}'") != "EXPIRED":
            if time.monotonic() >= deadline:
                raise AssertionError("Payara expiry timer did not run")
            time.sleep(1)
        expect(inventory(event_id, 1, 0), "Payara timer independently expires holds and restores inventory")

    with event(2) as event_id:
        booking = book(owner, event_id, [1])[1]
        reference = uuid.UUID(booking["id"])
        sql(f"UPDATE reservations SET status='CONFIRMED',payment_reference='fixture-{reference}' WHERE id='{reference}'")
        result = call(owner, f"/api/reservations/{reference}/cancel", method="POST")
        expect(result[0] == 200 and result[1]["status"] == "REFUND_PENDING" and inventory(event_id, 1, 1),
               "paid cancellation waits for a refund and keeps its seat allocated")
        expect(call(owner, f"/api/reservations/{reference}/cancel", method="POST")[0] == 409,
               "duplicate refund request cannot release inventory")
        expect(call(owner, f"/api/reservations/{reference}/confirm-payment", {}, "POST")[0] == 404,
               "payment callbacks have no customer-accessible REST endpoint")

    with event(1) as event_id:
        request(base, f"/api/events/{event_id}/seats")
        trigger = "member3_" + uuid.uuid4().hex
        sql(f"CREATE FUNCTION {trigger}() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN "
            f"IF NEW.event_id={event_id} AND NEW.reservation_id IS NOT NULL THEN "
            "RAISE EXCEPTION 'deliberate seat claim failure'; END IF; RETURN NEW; END $$; "
            f"CREATE TRIGGER {trigger} BEFORE UPDATE ON event_seats FOR EACH ROW EXECUTE FUNCTION {trigger}();")
        try:
            expect(book(owner, event_id, [1])[0] == 500, "seat allocation failure aborts the request")
            expect(inventory(event_id, 1, 0)
                   and sql(f"SELECT count(*) FROM reservations WHERE event_id={event_id}") == "0",
                   "failed allocation rolls back reservation, seat history and event inventory")
        finally:
            sql(f"DROP TRIGGER {trigger} ON event_seats; DROP FUNCTION {trigger}()")
        booking = book(owner, event_id, [1])[1]
        sql(f"CREATE FUNCTION {trigger}() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN "
            f"IF NEW.event_id={event_id} AND OLD.reservation_id IS NOT NULL AND NEW.reservation_id IS NULL THEN "
            "RAISE EXCEPTION 'deliberate seat release failure'; END IF; RETURN NEW; END $$; "
            f"CREATE TRIGGER {trigger} BEFORE UPDATE ON event_seats FOR EACH ROW EXECUTE FUNCTION {trigger}();")
        try:
            expect(call(owner, f"/api/reservations/{booking['id']}/cancel", method="POST")[0] == 500,
                   "seat release failure aborts cancellation")
            expect(inventory(event_id, 0, 1)
                   and sql(f"SELECT status FROM reservations WHERE id='{uuid.UUID(booking['id'])}'") == "ACTIVE",
                   "failed cancellation rolls back both reservation status and inventory")
        finally:
            sql(f"DROP TRIGGER {trigger} ON event_seats; DROP FUNCTION {trigger}()")
        expect(call(owner, f"/api/reservations/{booking['id']}/cancel", method="POST")[0] == 200,
               "cancellation succeeds after a transient storage failure")
    print("Member 3 application-server integration checks passed.", flush=True)
