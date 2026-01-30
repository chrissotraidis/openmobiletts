"""Tests for authentication module."""

import pytest
from jose import jwt
from datetime import datetime, timezone

from src.auth import (
    create_access_token,
    verify_password,
    authenticate_user,
)
from src.config import settings


class TestAuth:
    """Test authentication functionality."""

    def test_create_access_token(self):
        """Test JWT token creation."""
        username = "testuser"
        token = create_access_token(username, expires_hours=1)

        assert isinstance(token, str)
        assert len(token) > 0

        # Decode and verify token
        payload = jwt.decode(
            token, settings.JWT_SECRET, algorithms=[settings.JWT_ALGORITHM]
        )
        assert payload["sub"] == username
        assert "exp" in payload
        assert "iat" in payload

    def test_verify_password_correct(self):
        """Test password verification with correct password."""
        # This is the hash for "testpassword123" from our .env
        result = verify_password(
            "testpassword123",
            settings.ADMIN_PASSWORD_HASH,
        )
        assert result is True

    def test_verify_password_incorrect(self):
        """Test password verification with incorrect password."""
        result = verify_password(
            "wrongpassword",
            settings.ADMIN_PASSWORD_HASH,
        )
        assert result is False

    def test_authenticate_user_success(self):
        """Test user authentication with correct credentials."""
        result = authenticate_user("admin", "testpassword123")
        assert result is True

    def test_authenticate_user_wrong_username(self):
        """Test user authentication with wrong username."""
        result = authenticate_user("wronguser", "testpassword123")
        assert result is False

    def test_authenticate_user_wrong_password(self):
        """Test user authentication with wrong password."""
        result = authenticate_user("admin", "wrongpassword")
        assert result is False
