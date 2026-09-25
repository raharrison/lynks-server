#!/usr/bin/env python3
"""Create, list, activate, deactivate, reset, sign out and delete Lynks users.

Usage:
    python3 -m venv venv
    source venv/bin/activate
    pip install -r requirements.txt
    python manage_users.py list
    python manage_users.py create <username> [--inactive]
    python manage_users.py activate <username>
    python manage_users.py deactivate <username>
    python manage_users.py set-password <username>
    python manage_users.py unlink-sso <username>
    python manage_users.py revoke-sessions <username>
    python manage_users.py delete <username> [--yes]

Passwords are prompted for, or read from stdin with --password-stdin.
"""

import argparse
import base64
import getpass
import os
import re
import secrets
import sys
from datetime import datetime, timezone

import bcrypt
import psycopg

# Mirrors lynks.user.Credentials
USERNAME_PATTERN = re.compile(r"^[A-Za-z0-9_.-]{3,25}$")
PASSWORD_MIN_LENGTH = 8
PASSWORD_MAX_BYTES = 72
BCRYPT_COST = 12


def read_env_file(env_file_path: str) -> dict:
    env_vars = {}
    with open(env_file_path) as f:
        for line in f:
            line = line.strip()
            if line.startswith('#') or not line:
                continue
            key, value = line.split('=', 1)
            env_vars[key] = value
    return env_vars


def connect_db() -> psycopg.Connection:
    current_dir = os.path.dirname(os.path.realpath(__file__))
    env_vars = read_env_file(os.path.join(current_dir, "../config/.env"))
    return psycopg.connect(
        user=env_vars["POSTGRES_USER"],
        password=env_vars["POSTGRES_PASSWORD"],
        host="127.0.0.1",
        port=5432,
        dbname=env_vars["POSTGRES_DB"]
    )


def fail(message: str) -> None:
    print(message, file=sys.stderr)
    sys.exit(1)


# Same shape as RandomUtils.generateUid: 12 random bytes as unpadded base64url
def generate_uid() -> str:
    return base64.urlsafe_b64encode(secrets.token_bytes(12)).decode().rstrip("=")


def check_username(username: str) -> None:
    if not USERNAME_PATTERN.match(username):
        fail("Username must be 3 to 25 characters of letters, digits, '.', '_' or '-'")


def check_password(password: str) -> None:
    if len(password) < PASSWORD_MIN_LENGTH:
        fail(f"Password must be at least {PASSWORD_MIN_LENGTH} characters")
    if len(password.encode()) > PASSWORD_MAX_BYTES:
        fail(f"Password must be at most {PASSWORD_MAX_BYTES} bytes")


def read_password(from_stdin: bool) -> str:
    if from_stdin:
        password = sys.stdin.readline().rstrip("\n")
    else:
        password = getpass.getpass("Password: ")
        if getpass.getpass("Repeat password: ") != password:
            fail("Passwords do not match")
    check_password(password)
    return password


def hash_password(password: str) -> str:
    return bcrypt.hashpw(password.encode(), bcrypt.gensalt(BCRYPT_COST)).decode()


def now() -> datetime:
    return datetime.now(timezone.utc)


def find_user_id(conn: psycopg.Connection, username: str) -> str:
    row = conn.execute("SELECT id FROM user_profiles WHERE username = %s", (username,)).fetchone()
    if not row:
        fail(f"User not found: {username}")
    return row[0]


def list_users(conn: psycopg.Connection, _args) -> None:
    rows = conn.execute("""
        SELECT u.username, u.display_name, u.activated, u.totp IS NOT NULL, u.oidc_subject IS NOT NULL,
               u.jolt_token IS NOT NULL, u.date_created,
               (SELECT COUNT(*) FROM entries e WHERE e.user_id = u.id),
               (SELECT COUNT(*) FROM user_sessions s WHERE s.user_id = u.id AND s.expires_at > NOW())
        FROM user_profiles u
        ORDER BY u.date_created
    """).fetchall()
    if not rows:
        print("No users")
        return
    print(f"{'USERNAME':<25} {'DISPLAY NAME':<25} {'ACTIVE':<7} {'2FA':<4} {'SSO':<4} {'JOLT':<5} {'ENTRIES':>7} "
          f"{'SESSIONS':>8}  CREATED")
    for username, display_name, activated, totp, sso, jolt, created, entries, sessions in rows:
        active = "yes" if activated else "no"
        two_factor = "yes" if totp else "no"
        sso_linked = "yes" if sso else "no"
        jolt_set = "yes" if jolt else "no"
        print(f"{username:<25} {display_name or '':<25} {active:<7} {two_factor:<4} {sso_linked:<4} {jolt_set:<5} "
              f"{entries:>7} {sessions:>8}  {created:%Y-%m-%d %H:%M}")


def create_user(conn: psycopg.Connection, args) -> None:
    check_username(args.username)
    if conn.execute("SELECT 1 FROM user_profiles WHERE username = %s", (args.username,)).fetchone():
        fail(f"User already exists: {args.username}")
    password_hash = hash_password(read_password(args.password_stdin))
    time = now()
    conn.execute(
        "INSERT INTO user_profiles (id, username, password_hash, digest, date_created, date_updated, activated) "
        "VALUES (%s, %s, %s, FALSE, %s, %s, %s)",
        (generate_uid(), args.username, password_hash, time, time, not args.inactive)
    )
    state = "inactive" if args.inactive else "active"
    print(f"Created {state} user: {args.username}")


def set_activated(conn: psycopg.Connection, username: str, activated: bool) -> None:
    user_id = find_user_id(conn, username)
    conn.execute(
        "UPDATE user_profiles SET activated = %s, date_updated = %s WHERE id = %s",
        (activated, now(), user_id)
    )
    if activated:
        print(f"Activated user: {username}")
    else:
        # the server checks sessions against the database on every request
        print(f"Deactivated user: {username}. Their signed in sessions stop working immediately")


def activate_user(conn: psycopg.Connection, args) -> None:
    set_activated(conn, args.username, True)


def deactivate_user(conn: psycopg.Connection, args) -> None:
    set_activated(conn, args.username, False)


def set_password(conn: psycopg.Connection, args) -> None:
    user_id = find_user_id(conn, args.username)
    password_hash = hash_password(read_password(args.password_stdin))
    conn.execute(
        "UPDATE user_profiles SET password_hash = %s, date_updated = %s WHERE id = %s",
        (password_hash, now(), user_id)
    )
    # a reset usually means the old password is not to be trusted, so nothing signed in with it survives
    revoked = conn.execute("DELETE FROM user_sessions WHERE user_id = %s", (user_id,)).rowcount
    print(f"Password updated for user: {args.username}. Signed out {revoked} sessions")


def unlink_sso(conn: psycopg.Connection, args) -> None:
    user_id = find_user_id(conn, args.username)
    updated = conn.execute(
        "UPDATE user_profiles SET oidc_subject = NULL, date_updated = %s WHERE id = %s AND oidc_subject IS NOT NULL",
        (now(), user_id)
    ).rowcount
    if updated:
        print(f"Unlinked single sign-on from user: {args.username}. Existing sessions stay signed in")
    else:
        print(f"User has no single sign-on link: {args.username}")


def revoke_sessions(conn: psycopg.Connection, args) -> None:
    user_id = find_user_id(conn, args.username)
    revoked = conn.execute("DELETE FROM user_sessions WHERE user_id = %s", (user_id,)).rowcount
    print(f"Signed out {revoked} sessions for user: {args.username}")


def delete_user(conn: psycopg.Connection, args) -> None:
    user_id = find_user_id(conn, args.username)
    entries = conn.execute("SELECT COUNT(*) FROM entries WHERE user_id = %s", (user_id,)).fetchone()[0]
    if not args.yes:
        answer = input(f"Delete {args.username} and their {entries} entries, tags, collections, comments, "
                       f"reminders and notifications? Type the username to confirm: ")
        if answer != args.username:
            fail("Not deleted")
    conn.execute("DELETE FROM user_profiles WHERE id = %s", (user_id,))
    # every row cascades from user_profiles, but the files on disk have no row to cascade from
    print(f"Deleted user: {args.username}. Their resource files are removed by the next orphan cleanup "
          f"once they are more than a day old")


def main() -> None:
    parser = argparse.ArgumentParser(description="Manage Lynks users")
    commands = parser.add_subparsers(dest="command", required=True)

    commands.add_parser("list", help="list all users").set_defaults(func=list_users)

    create = commands.add_parser("create", help="create a user, active unless --inactive")
    create.add_argument("username")
    create.add_argument("--inactive", action="store_true", help="create without activating")
    create.add_argument("--password-stdin", action="store_true", help="read the password from stdin")
    create.set_defaults(func=create_user)

    activate = commands.add_parser("activate", help="allow a user to sign in")
    activate.add_argument("username")
    activate.set_defaults(func=activate_user)

    deactivate = commands.add_parser("deactivate", help="stop a user signing in, keeping their data")
    deactivate.add_argument("username")
    deactivate.set_defaults(func=deactivate_user)

    password = commands.add_parser("set-password", help="reset a user's password and sign them out everywhere")
    password.add_argument("username")
    password.add_argument("--password-stdin", action="store_true", help="read the password from stdin")
    password.set_defaults(func=set_password)

    unlink = commands.add_parser("unlink-sso", help="remove a user's single sign-on link so it can be linked again")
    unlink.add_argument("username")
    unlink.set_defaults(func=unlink_sso)

    revoke = commands.add_parser("revoke-sessions", help="sign a user out everywhere")
    revoke.add_argument("username")
    revoke.set_defaults(func=revoke_sessions)

    delete = commands.add_parser("delete", help="delete a user and all of their data")
    delete.add_argument("username")
    delete.add_argument("--yes", action="store_true", help="skip the confirmation prompt")
    delete.set_defaults(func=delete_user)

    args = parser.parse_args()
    try:
        with connect_db() as conn:
            args.func(conn, args)
    except psycopg.Error as error:
        fail(f"Database error: {error}")


if __name__ == "__main__":
    main()
