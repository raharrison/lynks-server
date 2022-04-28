#!/usr/bin/env python3

import sys
import os
import psycopg2

def read_env_file(env_file_path: str):
    env_vars = {}
    with open(env_file_path, 'r') as f:
        for line in f:
            if line.startswith('#') or not line.strip():
                continue
            key, value = line.strip().split('=', 1)
            env_vars[key] = value
    return env_vars


def connect_db():
    current_file = os.path.abspath(__file__))
    env_file = os.path.join(current_file, "../config/.env")
    env_vars = read_env_file(env_file)
    user = env_vars["POSTGRES_USER"]
    password = env_vars["POSTGRES_PASSWORD"]
    db = env_vars["POSTGRES_DB"]
    return psycopg2.connect(user=user, password=password, host="127.0.0.1", port="5432", database=db)


def activate_user(username: str):
    activate_sql = "UPDATE USER_PROFILE SET ACTIVATED = TRUE WHERE USERNAME = %s"
    print("Connecting to Lynks database..")
    with connect_db() as conn:
        try:
            print("Successfully connected to database")
            print(f"Attempting to activate user '{username}'")
            cur = conn.execute(activate_sql, (username,))
            conn.commit()
            rows_updated = cur.rowcount
            cur.close()
            if rows_updated == 0:
                print("User not activated: username not found")
            else:
                print("Successfully activated user '{username}'")
        except (Exception, psycopg2.DatabaseError) as error:
            print("Failed to activate user", error)


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Expected usage: activate_user.py <username>")

    username = sys.argv[1]
    activate_user(username)
