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

    expect(request(base, "/api/events")[0] == 200, "event catalog responds")
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
