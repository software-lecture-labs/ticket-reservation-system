"""Run against a disposable local Docker Compose demo, using only Python's standard library.

Creates isolated fixtures and a temporary failure trigger, then cleans them up.
Usage: python scripts/smoke_test.py [--base-url http://localhost:8080]
"""
import argparse
import concurrent.futures
import json
import os
from pathlib import Path
import subprocess
import time
import urllib.error
import urllib.request
import uuid


ROOT = Path(__file__).resolve().parent.parent


def sql(statement):
    # Read only the two non-secret database identifiers needed by psql in the container.
    config = {}
    env_file = ROOT / ".env"
    if env_file.exists():
        for line in env_file.read_text(encoding="utf-8-sig").splitlines():
            if "=" in line and not line.lstrip().startswith("#"):
                key, value = line.split("=", 1)
                config[key.strip()] = value.strip()
    command = ["docker", "compose", "exec", "-T", "db", "psql", "-X", "-qAt",
               "-U", os.environ.get("DB_USER", config.get("DB_USER", "tickets")),
               "-d", os.environ.get("DB_NAME", config.get("DB_NAME", "tickets")),
               "-v", "ON_ERROR_STOP=1", "-c", statement]
    return subprocess.check_output(command, cwd=ROOT, text=True, encoding="utf-8").strip()


def request(base, path, payload=None, method="GET"):
    data = json.dumps(payload).encode() if payload is not None else None
    req = urllib.request.Request(base + path, data=data, method=method,
                                 headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        return error.code, json.loads(error.read().decode())


def expect(condition, message):
    if not condition:
        raise AssertionError(message)
    print("PASS:", message, flush=True)


def check_user_migration():
    # Exercise an upgrade from the committed 003 schema without touching the app's tables.
    schema = "upgrade_" + uuid.uuid4().hex
    migrations = ROOT / "docker" / "postgres"
    previous = ["001-schema.sql", "002-demo.sql", "003-venues-categories.sql"]
    setup = "\n".join((migrations / name).read_text(encoding="utf-8") for name in previous)
    upgrade = (migrations / "004-users.sql").read_text(encoding="utf-8")
    result = sql(f"BEGIN; CREATE SCHEMA {schema}; SET LOCAL search_path TO {schema};\n"
                 + setup + "\nCREATE TEMP TABLE original_events AS SELECT * FROM events;\n" + upgrade
                 + """
                 DO $test$
                 DECLARE address text;
                 BEGIN
                     FOREACH address IN ARRAY ARRAY['', 'ada', 'ada@örnek.com', 'ADA@example.com',
                             ' ada@example.com', 'a..b@example.com', repeat('a', 65) || '@example.com']
                     LOOP
                         BEGIN
                             INSERT INTO users VALUES (gen_random_uuid(), address, 'fixture', 'USER');
                             RAISE EXCEPTION 'Database accepted an invalid email';
                         EXCEPTION WHEN check_violation THEN NULL;
                         END;
                     END LOOP;
                 END $test$;
                 """
                 + "\nSELECT NOT EXISTS (SELECT * FROM original_events EXCEPT SELECT * FROM events)"
                   " AND (SELECT count(*) FROM original_events) = 3 AND (SELECT count(*) FROM users) = 0;"
                   "ROLLBACK;")
    expect(result == "t", "004 upgrades a populated catalog and preserves existing event rows")


def check_registration(base):
    email = f"signup-{uuid.uuid4().hex}@example.com"
    password = uuid.uuid4().hex
    endpoint = "/api/auth/register"

    def register(address):
        return request(base, endpoint, {"email": address, "password": password, "role": "ADMIN"}, "POST")

    try:
        for invalid in ({"email": email}, {"password": password}, {"email": "ada@örnek.com", "password": password},
                        {"email": email, "password": "short"}):
            status, error = request(base, endpoint, invalid, "POST")
            expect(status == 400 and error["code"] == "VALIDATION", "invalid registration returns 400")
            expect(password not in json.dumps(error), "validation response omits the password")
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
            results = list(executor.map(register, [email, " " + email.upper() + " "]))
        expect(sorted(status for status, _ in results) == [201, 409], "concurrent duplicates create one user")
        created = next(body for status, body in results if status == 201)
        user_id = uuid.UUID(created["id"])
        expect(created == {"id": str(user_id), "email": email, "role": "USER"},
               "registration exposes only public fields and assigns USER")
        status, error = register(email)
        expect(status == 409 and error["code"] == "CONFLICT" and password not in json.dumps(error),
               "a later duplicate returns a safe conflict response")
        expect(sql(f"SELECT count(*) FROM users WHERE email='{email}' AND id='{user_id}'"
                   " AND role='USER' AND password_hash LIKE 'PBKDF2WithHmacSHA256:600000:%'") == "1",
               "database contains one user with a salted password hash")
        # A direct duplicate must hit the unique constraint independently of the API.
        sql(f"DO $$ BEGIN INSERT INTO users SELECT gen_random_uuid(), email, password_hash, role "
            f"FROM users WHERE id='{user_id}'; RAISE EXCEPTION 'missing email constraint'; "
            "EXCEPTION WHEN unique_violation THEN NULL; END $$;")
        logs = subprocess.check_output(["docker", "compose", "logs", "--no-color", "app"], cwd=ROOT)
        expect(password.encode() not in logs, "registration password is absent from application logs")
    finally:
        sql(f"DELETE FROM users WHERE email='{email}'")


def run(base):
    deadline = time.monotonic() + 240
    while True:
        try:
            status, _ = request(base, "/api/health")
            if status == 200:
                break
        except (OSError, ValueError):
            pass
        if time.monotonic() >= deadline:
            raise RuntimeError("Payara/database did not become ready in 240 seconds")
        time.sleep(2)

    check_user_migration()
    check_registration(base)
    expect(request(base, "/api/events")[0] == 200, "event catalog responds")
    status, venues = request(base, "/api/venues")
    expect(status == 200 and {"İstanbul", "Ankara", "İzmir"} <= {v["city"] for v in venues},
           "venue list returns demo venues")
    expect(all(set(v) == {"id", "name", "city", "address"} for v in venues), "venue DTO exposes only public fields")
    status, categories = request(base, "/api/categories")
    expect(status == 200 and {"Konser", "Tiyatro", "Konferans"} <= {c["name"] for c in categories},
           "category list returns demo categories")
    expect(int(sql("SELECT count(*) FROM events WHERE venue_id IS NOT NULL AND category_id IS NOT NULL")) >= 3,
           "demo events are linked to a venue and category")
    with urllib.request.urlopen(base + "/", timeout=30) as response:
        expect(b"booking" in response.read(), "Jakarta Faces booking page renders")

    marker = uuid.uuid4().hex
    trigger_name = "smoke_" + marker
    event_id = int(sql("INSERT INTO events (title, venue, starts_at, capacity, available_seats, ticket_price) "
                       "VALUES ('Smoke test', 'CI', CURRENT_TIMESTAMP + INTERVAL '7 days', 2, 2, 125.50) RETURNING id"))
    payload = {"eventId": event_id, "customerName": "Smoke Test",
               "customerEmail": "smoke@example.com", "quantity": 1}
    try:
        status, _ = request(base, "/api/reservations", dict(payload, quantity=0), "POST")
        expect(status == 400, "invalid quantity returns 400")
        status, _ = request(base, "/api/reservations", dict(payload, customerEmail="invalid"), "POST")
        expect(status == 400, "invalid email returns 400")
        expect(request(base, "/api/events/9223372036854775807")[0] == 404, "missing event returns 404")
        status, created = request(base, "/api/reservations", payload, "POST")
        expect(status == 201 and float(created["totalPrice"]) == 125.50, "reservation uses server-side price")
        reference = created["id"]
        expect(request(base, "/api/reservations/" + reference)[0] == 200, "reservation lookup works")
        expect(sql(f"SELECT available_seats FROM events WHERE id={event_id}") == "1", "reservation decrements seats")
        status, _ = request(base, "/api/reservations", dict(payload, quantity=2), "POST")
        expect(status == 409, "overselling returns 409")

        # Two requests compete for the single remaining seat on a real application server/database.
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
            results = list(executor.map(lambda _: request(base, "/api/reservations", payload, "POST"), range(2)))
        expect(sorted(status for status, _ in results) == [201, 409], "concurrent requests cannot oversell the last seat")
        expect(sql(f"SELECT available_seats FROM events WHERE id={event_id}") == "0", "inventory stays non-negative")

        status, cancelled = request(base, f"/api/reservations/{reference}/cancel", method="POST")
        expect(status == 200 and cancelled["status"] == "CANCELLED", "cancellation succeeds")
        expect(request(base, f"/api/reservations/{reference}/cancel", method="POST")[0] == 409,
               "repeated cancellation returns 409")
        expect(sql(f"SELECT available_seats FROM events WHERE id={event_id}") == "1", "cancellation returns seats once")

        # Force a persistence failure after the service has changed inventory in its JTA transaction.
        sql(f"CREATE FUNCTION {trigger_name}() RETURNS trigger LANGUAGE plpgsql AS $$ "
            f"BEGIN IF NEW.event_id={event_id} AND NEW.customer_email='rollback@example.com' THEN "
            "RAISE EXCEPTION 'deliberate smoke-test failure'; END IF; RETURN NEW; END; $$; "
            f"CREATE TRIGGER {trigger_name} BEFORE INSERT ON reservations "
            f"FOR EACH ROW EXECUTE FUNCTION {trigger_name}()")
        status, body = request(base, "/api/reservations", dict(payload, customerEmail="rollback@example.com"), "POST")
        expect(status == 500 and body["code"] == "INTERNAL_ERROR", "persistence failure returns a safe API error")
        expect(sql(f"SELECT available_seats FROM events WHERE id={event_id}") == "1", "failed transaction preserves inventory")
        expect(sql(f"SELECT count(*) FROM reservations WHERE event_id={event_id} "
                   "AND customer_email='rollback@example.com'") == "0", "failed transaction leaves no reservation")
        print("All application-server integration checks passed.", flush=True)
    finally:
        sql(f"DROP TRIGGER IF EXISTS {trigger_name} ON reservations; "
            f"DROP FUNCTION IF EXISTS {trigger_name}(); "
            f"DELETE FROM reservations WHERE event_id={event_id}; DELETE FROM events WHERE id={event_id}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://localhost:8080")
    arguments = parser.parse_args()
    run(arguments.base_url.rstrip("/"))
