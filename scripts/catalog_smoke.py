"""Member 2 (catalog) integration checks; invoked by smoke_test.py against Payara and PostgreSQL."""
from datetime import datetime, timedelta, timezone
import subprocess


def check_catalog(base, user, admin, sql, expect):
    """user and admin are (AuthClient, logged-in user, CSRF token) tuples from smoke_test.test_account."""
    anonymous = type(admin[0])(base)

    def api(actor, path, payload=None, method="GET", token="default"):
        client, _, own_token = actor
        return client.api(path, payload, own_token if token == "default" else token, method)[:2]

    def iso(days):
        return (datetime.now(timezone.utc) + timedelta(days=days)).strftime("%Y-%m-%dT%H:%M:%SZ")

    venue_id = anonymous.api("/api/venues")[1][0]["id"]
    category_id = anonymous.api("/api/categories")[1][0]["id"]
    payload = {"title": "Katalog smoke", "venueId": venue_id, "categoryId": category_id,
               "startsAt": iso(30), "capacity": 5, "ticketPrice": 0}

    expect(int(sql("SELECT count(*) FROM events WHERE active")) >= 3, "migration keeps existing events active")

    # Access control: anonymous 401, USER 403, ADMIN without a valid CSRF token 403.
    expect(anonymous.api("/api/events", payload, method="POST")[0] == 401, "anonymous cannot create events")
    status, body = api(user, "/api/events", payload, "POST")
    expect(status == 403 and body["code"] == "FORBIDDEN", "USER cannot create events")
    for bad_token in (None, "invalid", user[2]):
        status, body = api(admin, "/api/events", payload, "POST", bad_token)
        expect(status == 403 and body["code"] == "CSRF", "admin create requires a valid CSRF token")

    # Server-side validation returns 400 and creates nothing.
    before = sql("SELECT count(*) FROM events")
    invalid = [dict(payload, startsAt=iso(-1)), dict(payload, ticketPrice=-1), dict(payload, ticketPrice=1.005),
               dict(payload, capacity=0), dict(payload, capacity=100001), dict(payload, title="  "),
               dict(payload, venueId=999999999), dict(payload, categoryId=None), dict(payload, startsAt="yarin"),
               {k: v for k, v in payload.items() if k != "startsAt"}]
    expect(all(api(admin, "/api/events", item, "POST")[0] == 400 for item in invalid),
           "past date, bad price, bad capacity and unknown links return 400")
    expect(sql("SELECT count(*) FROM events") == before, "rejected requests create no event")

    status, created = api(admin, "/api/events", payload, "POST")
    expect(status == 201 and created["active"] and created["availableSeats"] == 5
           and created["venueId"] == venue_id and float(created["ticketPrice"]) == 0, "admin creates a free event")
    event_id = created["id"]
    path = f"/api/events/{event_id}"
    try:
        expect(any(item["id"] == event_id for item in anonymous.api("/api/events")[1])
               and anonymous.api(path)[0] == 200, "new event is public in the catalog")

        # Reservation interplay: three seats are held, so capacity cannot go below three.
        status, held = api(user, "/api/reservations", {"eventId": event_id, "customerName": "Katalog",
                           "customerEmail": "katalog@example.com", "quantity": 3}, "POST")
        expect(status == 201, "user holds seats on the new event")
        status, _ = api(user, path, payload, "PUT")
        expect(status == 403, "USER cannot update events")
        expect(api(admin, "/api/events/9223372036854775807", payload, "PUT")[0] == 404, "updating a missing event returns 404")
        status, body = api(admin, path, dict(payload, capacity=2), "PUT")
        expect(status == 409, "capacity below held seats is refused")
        status, body = api(admin, path, dict(payload, capacity=3, ticketPrice=45.5), "PUT")
        expect(status == 200 and body["capacity"] == 3 and body["availableSeats"] == 0
               and float(body["ticketPrice"]) == 45.5, "capacity equal to held seats is accepted")
        expect(sql(f"SELECT count(*) FROM event_seats WHERE event_id={event_id}") == "3",
               "unassigned seat rows above the new capacity are removed")
        status, body = api(admin, path, dict(payload, capacity=6), "PUT")
        expect(status == 200 and body["availableSeats"] == 3, "growing capacity adds free seats")
        expect(len(anonymous.api(path + "/seats")[1]) == 6, "seat map follows the new capacity")
        status, _ = api(user, "/api/reservations", {"eventId": event_id, "customerName": "Katalog",
                        "customerEmail": "katalog@example.com", "quantity": 1, "seatNumbers": [6]}, "POST")
        expect(status == 201, "user holds seat number 6")
        status, body = api(admin, path, dict(payload, capacity=5), "PUT")
        expect(status == 409, "capacity is refused when a held seat would fall outside it")
        expect(sql(f"SELECT capacity FROM events WHERE id={event_id}") == "6", "refused update leaves the event unchanged")

        # Events with reservations cannot be deleted; they are deactivated instead.
        try:
            sql(f"DELETE FROM events WHERE id={event_id}")
            deleted = True
        except subprocess.CalledProcessError:
            deleted = False
        expect(not deleted, "database refuses to delete an event that has reservations")

        expect(api(user, path + "/deactivate", None, "POST")[0] == 403, "USER cannot deactivate events")
        status, body = api(admin, path + "/deactivate", None, "POST")
        expect(status == 200 and body["active"] is False, "admin deactivates the event")
        expect(all(item["id"] != event_id for item in anonymous.api("/api/events")[1])
               and anonymous.api(path)[0] == 404, "inactive event disappears from the public catalog")
        status, _ = api(user, "/api/reservations", {"eventId": event_id, "customerName": "Katalog",
                        "customerEmail": "katalog@example.com", "quantity": 1}, "POST")
        expect(status == 409, "inactive event accepts no new reservations")
        expect(api(user, "/api/reservations/" + held["id"])[0] == 200, "existing reservation history is preserved")
        expect(api(admin, path + "/activate", None, "POST")[1]["active"] is True
               and anonymous.api(path)[0] == 200, "admin reactivates the event")
    finally:
        sql(f"DELETE FROM reservations WHERE event_id={event_id}; DELETE FROM events WHERE id={event_id}")


def check_catalog_search(base, admin, sql, expect):
    """Filters, pagination and deterministic ordering of GET /api/events against the real database."""
    import uuid
    from urllib.parse import quote

    anonymous = type(admin[0])(base)
    marker = "Filtre-" + uuid.uuid4().hex
    venues = anonymous.api("/api/venues")[1]
    categories = anonymous.api("/api/categories")[1]
    v0, v1 = venues[0], venues[1]
    c0, c1 = categories[0]["id"], categories[1]["id"]

    def iso(days):
        return (datetime.now(timezone.utc) + timedelta(days=days)).strftime("%Y-%m-%dT%H:%M:%SZ")

    def create(name, venue, category, days):
        payload = {"title": f"{marker} {name}", "venueId": venue["id"], "categoryId": category,
                   "startsAt": iso(days), "capacity": 5, "ticketPrice": 10}
        status, body, _ = admin[0].api("/api/events", payload, admin[2], "POST")
        expect(status == 201, f"search fixture {name} created")
        return body

    def search(query):
        status, body, headers = anonymous.api("/api/events?" + query)
        return status, body, headers.get("X-Total-Count")

    def ids(body):
        return [item["id"] for item in body]

    try:
        e1 = create("Alfa", v0, c0, 400)
        e2 = create("Beta", v1, c1, 401)
        e3 = create("Gama", v0, c1, 402)
        title = "title=" + quote(marker)

        status, body, total = search(title)
        expect(status == 200 and ids(body) == [e1["id"], e2["id"], e3["id"]] and total == "3",
               "title filter returns matches ordered by start time with X-Total-Count")
        expect(ids(search("title=" + quote(marker.upper()))[1]) == [e1["id"], e2["id"], e3["id"]],
               "title filter is case-insensitive")

        status, body, total = search(title + "&size=2")
        expect(ids(body) == [e1["id"], e2["id"]] and total == "3", "first page holds the earliest events")
        status, body, total = search(title + "&page=1&size=2")
        expect(ids(body) == [e3["id"]] and total == "3", "second page holds the remainder")
        status, body, total = search(title + "&page=9&size=2")
        expect(status == 200 and body == [] and total == "3", "out-of-range page is empty")
        expect(search(title + "&size=2")[1] == search(title + "&size=2")[1], "repeated queries are deterministic")

        expect(ids(search(title + f"&categoryId={c1}")[1]) == [e2["id"], e3["id"]], "category filter")
        expect(ids(search(title + f"&categoryId={c0}")[1]) == [e1["id"]], "category filter, other category")
        expect(ids(search(title + "&city=" + quote(v0["city"].upper()))[1]) == [e1["id"], e3["id"]],
               "city filter is case-insensitive")
        expect(ids(search(title + "&city=" + quote(v0["city"]) + f"&categoryId={c1}")[1]) == [e3["id"]],
               "filters combine with AND")
        window = f"&from={e2['startsAt']}&to={e3['startsAt']}"
        expect(ids(search(title + window)[1]) == [e2["id"]], "date range includes from and excludes to")

        # Turkish letters: the database lowercases both sides, so "İstanbul" must match however it is typed.
        istanbul = next(v for v in venues if v["city"].startswith("İst"))
        e4 = create("İstanbul Gecesi", istanbul, c0, 403)
        in_istanbul = [item["id"] for item in (e1, e2, e3, e4) if item["venueId"] == istanbul["id"]]
        for spelling in ("İstanbul", "İSTANBUL", "istanbul", "ISTANBUL"):
            expect(ids(search(title + "&city=" + quote(spelling))[1]) == in_istanbul,
                   f"city filter matches '{spelling}' with Turkish letters")
        for spelling in ("İstanbul Gecesi", "ISTANBUL GECESI", "istanbul gecesi"):
            expect(ids(search("title=" + quote(marker + " " + spelling))[1]) == [e4["id"]],
                   f"title filter matches '{spelling}'")

        # '%' and '_' are literal characters in user input, not LIKE wildcards.
        expect(search("title=" + quote(marker + "_"))[1] == [], "underscore is not a wildcard")
        expect(search("title=" + quote(marker + "%"))[1] == [], "percent sign is not a wildcard")

        bad = ["size=0", "size=101", "page=-1", "from=yarin", "to=2030-13-45",
               f"from={e3['startsAt']}&to={e1['startsAt']}", "categoryId=abc", "categoryId=0",
               "title=" + "a" * 101, "city=" + "a" * 101]
        expect(all(search(query)[0] == 400 for query in bad), "invalid filters and paging return 400")
        status, body, total = search("")
        expect(status == 200 and total.isdigit() and int(total) >= len(body), "no parameters keeps the old list contract")

        expect(admin[0].api(f"/api/events/{e2['id']}/deactivate", None, admin[2], "POST")[0] == 200, "deactivate fixture")
        status, body, total = search(title)
        expect(ids(body) == [e1["id"], e3["id"], e4["id"]] and total == "3", "inactive events leave the filtered catalog")
    finally:
        sql(f"DELETE FROM events WHERE title LIKE '{marker}%'")
