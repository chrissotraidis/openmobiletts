#!/usr/bin/env python3
"""Helper script to generate Argon2 password hash for admin user."""

import sys
from pwdlib import PasswordHash


def main():
    """Generate password hash from command line input."""
    if len(sys.argv) > 1:
        password = sys.argv[1]
    else:
        password = input("Enter password to hash: ")

    if not password:
        print("Error: Password cannot be empty", file=sys.stderr)
        sys.exit(1)

    ph = PasswordHash.recommended()
    password_hash = ph.hash(password)

    print("\nGenerated Argon2 password hash:")
    print(password_hash)
    print("\nAdd this to your .env file:")
    print(f"ADMIN_PASSWORD_HASH={password_hash}")


if __name__ == "__main__":
    main()
