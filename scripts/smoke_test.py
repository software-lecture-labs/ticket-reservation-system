"""Run against a disposable local Docker Compose demo, using only Python's standard library.

Creates isolated fixtures and a temporary failure trigger, then cleans them up.
Usage: python scripts/smoke_test.py [--base-url http://localhost:8080]
"""
import argparse
import concurrent.futures
import copy
from contextlib import ExitStack, contextmanager
import http.cookiejar
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


def check_owner_migration():
    schema = "upgrade_owner_" + uuid.uuid4().hex
    migrations = ROOT / "docker/postgres"
    setup = "\n".join((migrations / name).read_text(encoding="utf-8") for name in
                      ("001-schema.sql", "002-demo.sql", "003-venues-categories.sql", "004-users.sql"))
    upgrade = (migrations / "005-reservation-owner.sql").read_text(encoding="utf-8")
    insert = ("INSERT INTO reservations (id,event_id,customer_name,customer_email,quantity,total_price,status,created_at) "
              "VALUES (gen_random_uuid(),1,'Legacy','legacy@example.com',1,350,'ACTIVE',now())")
    result = sql(f"BEGIN; CREATE SCHEMA {schema}; SET LOCAL search_path TO {schema};\n" + setup
                 + "\n" + insert + ";\n" + upgrade
                 + "\nDO $$ BEGIN BEGIN " + insert + "; RAISE EXCEPTION 'missing owner constraint'; "
                   "EXCEPTION WHEN check_violation THEN NULL; END; "
                   "BEGIN UPDATE reservations SET customer_name='Updated'; RAISE EXCEPTION 'legacy update accepted'; "
                   "EXCEPTION WHEN check_violation THEN NULL; END; "
                   "BEGIN UPDATE reservations SET user_id=gen_random_uuid(); RAISE EXCEPTION 'missing user FK'; "
                   "EXCEPTION WHEN foreign_key_violation THEN NULL; END; END $$; "
                   "SELECT count(*) FROM reservations WHERE user_id IS NULL AND status='ACTIVE' AND total_price=350; "
                   "ROLLBACK;")
    expect(result == "1", "005 preserves legacy data and enforces owner and foreign key for new writes")


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


class AuthClient:
    def __init__(self, base, cookies=None):
        self.base = base
        self.jar = http.cookiejar.CookieJar()
        for cookie in cookies or ():
            self.jar.set_cookie(copy.copy(cookie))
        self.http = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))

    def call(self, path, payload=None, token=None, method="GET"):
        return self.api("/api/auth/" + path, payload, token, method)

    def api(self, path, payload=None, token=None, method="GET"):
        headers = {"Content-Type": "application/json"}
        if token is not None:
            headers["X-CSRF-Token"] = token
        data = json.dumps(payload).encode() if payload is not None else None
        req = urllib.request.Request(self.base + path, data, headers, method=method)
        try:
            response = self.http.open(req, timeout=30)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            if path.startswith(("/api/auth/", "/api/reservations")):
                assert "no-store" in response.headers.get("Cache-Control", "")
            body = response.read()
            return response.status, json.loads(body) if body else None, response.headers

    def session_id(self):
        return next(cookie.value for cookie in self.jar if cookie.name == "JSESSIONID")


def check_sessions(base):
    email = f"session-{uuid.uuid4().hex}@example.com"
    other_email = f"other-{uuid.uuid4().hex}@example.com"
    password = uuid.uuid4().hex
    client, other = AuthClient(base), AuthClient(base)
    credentials = {"email": " " + email.upper() + " ", "password": password, "role": "ADMIN"}
    try:
        status, user, _ = client.call("register", credentials, method="POST")
        expect(status == 201, "session fixture registered")
        status, other_user, _ = other.call("register", {"email": other_email, "password": password}, method="POST")
        expect(status == 201 and other_user["id"] != user["id"], "second independent account registered")
        expect(client.call("me")[0] == 401, "anonymous me returns 401")
        status, csrf, headers = client.call("csrf")
        token = csrf["token"]
        cookie_headers = " ".join(headers.get_all("Set-Cookie", [])).lower()
        expect(status == 200 and "httponly" in cookie_headers and "samesite=lax" in cookie_headers,
               "local session cookie carries HttpOnly and SameSite=Lax")
        before = AuthClient(base, client.jar)
        expect(client.call("login", credentials, method="POST")[0] == 403, "login requires CSRF header")
        other_token = other.call("csrf")[1]["token"]
        expect(client.call("login", credentials, other_token, "POST")[0] == 403, "CSRF token is session-bound")
        expect(client.call("login", {"email": email}, token, "POST")[0] == 400, "incomplete credentials return 400")
        wrong = client.call("login", dict(credentials, password=password + "x"), token, "POST")
        unknown = client.call("login", dict(credentials, email="missing-" + email), token, "POST")
        expect(wrong[0] == 401 and wrong[:2] == unknown[:2], "wrong and missing credentials have identical errors")
        expect(client.call("me")[0] == 401, "failed login leaves the caller anonymous")
        status, logged_in, _ = client.call("login", credentials, token, "POST")
        expect(status == 200 and logged_in == user and user["role"] == "USER", "login returns the stored user")
        expect(before.session_id() != client.session_id() and before.call("me")[0] == 401,
               "pre-login session rotates and remains anonymous")
        expect(client.call("me")[:2] == (200, user), "container principal and role match on a subsequent request")
        expect(other.call("login", {"email": other_email, "password": password}, other_token, "POST")[:2]
               == (200, other_user), "second client signs in independently")
        expect(other.call("me")[1] == other_user and client.call("me")[1] == user, "client identities stay isolated")
        switch_token = client.call("csrf")[1]["token"]
        old_account = AuthClient(base, client.jar)
        expect(client.call("login", {"email": other_email, "password": password + "x"}, switch_token, "POST")[0] == 401,
               "failed account switch returns 401")
        expect(client.call("me")[:2] == (200, user) and client.session_id() == old_account.session_id(),
               "failed switch preserves the previously authenticated account")
        expect(client.call("login", {"email": other_email, "password": password}, switch_token, "POST")[:2]
               == (200, other_user), "same client can switch accounts")
        expect(client.call("me")[:2] == (200, other_user) and client.session_id() != old_account.session_id(),
               "switched session uses the new container identity")
        expect(old_account.call("me")[0] == 401, "former account session is invalid after switching")
        expect(client.api("/api/reservations", {}, switch_token, "POST")[0] == 403,
               "a switched account cannot mutate reservations with its previous token")
        expect(client.call("logout", token=switch_token, method="POST")[0] == 403,
               "former account token is invalid after switching")
        authenticated = AuthClient(base, client.jar)
        expect(client.call("logout", token=token, method="POST")[0] == 403, "pre-login token cannot authorize logout")
        token = client.call("csrf")[1]["token"]
        expect(client.call("logout", token=token, method="POST")[0] == 204, "logout succeeds")
        expect(authenticated.call("me")[0] == 401 and client.call("me")[0] == 401, "logged-out session loses access")
        expect(authenticated.api("/api/reservations", {}, token, "POST")[0] == 401,
               "logged out session cannot create a reservation")
        expect(client.call("logout", method="POST")[0] == 204, "anonymous logout is idempotent")
        expect(other.call("me")[:2] == (200, other_user), "logout leaves the other client's identity intact")
        logs = subprocess.check_output(["docker", "compose", "logs", "--no-color", "app"], cwd=ROOT)
        stored_hash = sql(f"SELECT password_hash FROM users WHERE email='{email}'")
        expect(stored_hash and stored_hash.encode() not in logs and password.encode() not in logs,
               "login credentials and stored hash are absent from app logs")
    finally:
        sql(f"DELETE FROM users WHERE email IN ('{email}', '{other_email}')")


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
    check_owner_migration()
    check_registration(base)
    check_sessions(base)
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

    with ExitStack() as stack:
        accounts = [stack.enter_context(test_account(base, role)) for role in ("USER", "USER", "ADMIN")]
        check_reservations(base, *accounts)


@contextmanager
def test_account(base, role):
    client = AuthClient(base)
    email = f"ownership-{uuid.uuid4().hex}@example.com"
    payload = {"email": email, "password": uuid.uuid4().hex, "role": "ADMIN"}
    status, registered, _ = client.call("register", payload, method="POST")
    expect(status == 201 and registered["role"] == "USER", "public account starts as USER")
    try:
        if role == "ADMIN":
            sql(f"UPDATE users SET role='ADMIN' WHERE id='{uuid.UUID(registered['id'])}'")
        token = client.call("csrf")[1]["token"]
        status, logged_in, _ = client.call("login", payload, token, "POST")
        expect(status == 200 and logged_in["role"] == role and client.call("me")[1] == logged_in,
               f"container authenticates database role {role}")
        yield client, logged_in, client.call("csrf")[1]["token"]
    finally:
        sql(f"DELETE FROM users WHERE email='{email}'")


def check_ownership(base, first, second, admin, event_id, payload):
    anonymous = AuthClient(base)
    path = "/api/reservations"
    uid = uuid.uuid4()
    expect(anonymous.api(path, payload, method="POST")[0] == 401, "anonymous create denied")
    expect(anonymous.api(f"{path}/{uid}")[0] == 401, "anonymous lookup denied before resource existence check")
    expect(anonymous.api(f"{path}/{uid}/cancel", method="POST")[0] == 401, "anonymous cancel denied")
    expect(first[0].api(f"{path}/{uid}")[0] == 404, "authenticated lookup of absent reservation returns 404")
    for token in (None, "invalid", second[2]):
        expect(first[0].api(path, payload, token, "POST")[0] == 403, "create requires current session token")
    bookings = []
    for client, user, token in (first, second):
        status, body, _ = client.api(path, payload, token, "POST")
        expect(status == 201, "owner creates a reservation")
        item = f"{path}/{uuid.UUID(body['id'])}"
        expect(client.api(item)[0] == 200, "owner reads reservation")
        bookings.append(item)
    for actor, item in ((first, bookings[1]), (second, bookings[0]), (admin, bookings[0])):
        expect(actor[0].api(item)[:2] == (403, {"code": "FORBIDDEN", "message": "Bu işleme erişim izniniz yok."}),
               "another account cannot read the reservation")
        expect(actor[0].api(item + "/cancel", token=actor[2], method="POST")[0] == 403,
               "another account cannot cancel the reservation")
    for invalid in (None, "invalid", second[2]):
        expect(first[0].api(bookings[0] + "/cancel", token=invalid, method="POST")[0] == 403,
               "owner cancellation requires a valid CSRF token")
    expect(sql(f"SELECT count(*) FROM reservations WHERE event_id={event_id} AND status='ACTIVE'") == "2"
           and sql(f"SELECT available_seats FROM events WHERE id={event_id}") == "0",
           "denied cancellations preserve status and inventory")
    expect(first[0].api(bookings[0] + "/cancel", token=first[2], method="POST")[0] == 200, "owner cancels")
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
        statuses = list(executor.map(lambda _: AuthClient(base, second[0].jar).api(
            bookings[1] + "/cancel", token=second[2], method="POST")[0], range(2)))
    expect(sorted(statuses) == [200, 409] and sql(f"SELECT available_seats FROM events WHERE id={event_id}") == "2",
           "concurrent owner cancellations restore seats exactly once")
    status, body, _ = admin[0].api(path, payload, admin[2], "POST")
    expect(status == 201 and admin[0].api(f"{path}/{body['id']}/cancel", token=admin[2], method="POST")[0] == 200,
           "ADMIN can manage own reservation")


def check_reservations(base, owner, second, admin):
    def owner_request(path, payload=None, method="GET"):
        return owner[0].api(path, payload, owner[2], method)[:2]

    marker = uuid.uuid4().hex
    trigger_name = "smoke_" + marker
    event_id = int(sql("INSERT INTO events (title, venue, starts_at, capacity, available_seats, ticket_price) "
                       "VALUES ('Smoke test', 'CI', CURRENT_TIMESTAMP + INTERVAL '7 days', 2, 2, 125.50) RETURNING id"))
    payload = {"eventId": event_id, "customerName": "Smoke Test",
               "customerEmail": "smoke@example.com", "quantity": 1}
    try:
        check_ownership(base, owner, second, admin, event_id, payload)
        status, _ = owner_request("/api/reservations", dict(payload, quantity=0), "POST")
        expect(status == 400, "invalid quantity returns 400")
        status, _ = owner_request("/api/reservations", dict(payload, customerEmail="invalid"), "POST")
        expect(status == 400, "invalid email returns 400")
        expect(request(base, "/api/events/9223372036854775807")[0] == 404, "missing event returns 404")
        status, created = owner_request("/api/reservations", dict(payload, userId=second[1]["id"]), "POST")
        expect(status == 201 and float(created["totalPrice"]) == 125.50, "reservation uses server-side price")
        reference = created["id"]
        expect(sql(f"SELECT user_id FROM reservations WHERE id='{uuid.UUID(reference)}'") == owner[1]["id"],
               "server principal sets reservation owner even when the payload names a different user")
        expect(owner_request("/api/reservations/" + reference)[0] == 200, "reservation lookup works")
        expect(sql(f"SELECT available_seats FROM events WHERE id={event_id}") == "1", "reservation decrements seats")
        status, _ = owner_request("/api/reservations", dict(payload, quantity=2), "POST")
        expect(status == 409, "overselling returns 409")

        # Two requests compete for the single remaining seat on a real application server/database.
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
            results = list(executor.map(lambda _: owner_request("/api/reservations", payload, "POST"), range(2)))
        expect(sorted(status for status, _ in results) == [201, 409], "concurrent requests cannot oversell the last seat")
        expect(sql(f"SELECT available_seats FROM events WHERE id={event_id}") == "0", "inventory stays non-negative")

        status, cancelled = owner_request(f"/api/reservations/{reference}/cancel", method="POST")
        expect(status == 200 and cancelled["status"] == "CANCELLED", "cancellation succeeds")
        expect(owner_request(f"/api/reservations/{reference}/cancel", method="POST")[0] == 409,
               "repeated cancellation returns 409")
        expect(sql(f"SELECT available_seats FROM events WHERE id={event_id}") == "1", "cancellation returns seats once")

        # Force a persistence failure after the service has changed inventory in its JTA transaction.
        sql(f"CREATE FUNCTION {trigger_name}() RETURNS trigger LANGUAGE plpgsql AS $$ "
            f"BEGIN IF NEW.event_id={event_id} AND NEW.customer_email='rollback@example.com' THEN "
            "RAISE EXCEPTION 'deliberate smoke-test failure'; END IF; RETURN NEW; END; $$; "
            f"CREATE TRIGGER {trigger_name} BEFORE INSERT ON reservations "
            f"FOR EACH ROW EXECUTE FUNCTION {trigger_name}()")
        status, body = owner_request("/api/reservations", dict(payload, customerEmail="rollback@example.com"), "POST")
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
