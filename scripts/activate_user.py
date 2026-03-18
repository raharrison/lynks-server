#!/usr/bin/env python3

import sys
import os
import psycopg

# -- Usage --
# python3 -m venv venv
# source venv/bin/activate
# pip install -r requirements.txt
# python activate_user.py <user>


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
    env_file = os.path.join(current_dir, "../config/.env")
    env_vars = read_env_file(env_file)
    return psycopg.connect(
        user=env_vars["POSTGRES_USER"],
        password=env_vars["POSTGRES_PASSWORD"],
        host="127.0.0.1",
        port=5432,
        dbname=env_vars["POSTGRES_DB"]
    )


def activate_user(username: str) -> None:
    activate_sql = "UPDATE user_profiles SET activated = TRUE WHERE username = %s"
    print("Connecting to Lynks database..")
    try:
        with connect_db() as conn:
            print("Successfully connected to database")
            print(f"Attempting to activate user: {username}")
            with conn.cursor() as cur:
                cur.execute(activate_sql, (username,))
                if cur.rowcount == 0:
                    print("User not activated: username not found")
                else:
                    print(f"Successfully activated user: {username}")
    except Exception as error:
        print("Failed to activate user", error)


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Expected usage: activate_user.py <username>")
        exit(1)

    activate_user(sys.argv[1])
