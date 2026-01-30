"""JWT authentication for Open Mobile TTS."""

from datetime import datetime, timedelta, timezone
from typing import Optional

from fastapi import Depends, HTTPException, status
from fastapi.security import OAuth2PasswordBearer
from jose import JWTError, jwt
from pwdlib import PasswordHash

from .config import settings

# OAuth2 scheme for token extraction
oauth2_scheme = OAuth2PasswordBearer(tokenUrl="token")

# Password hasher using Argon2 (OWASP recommended)
password_hash = PasswordHash.recommended()


def create_access_token(username: str, expires_hours: Optional[int] = None) -> str:
    """
    Create a JWT access token.

    Args:
        username: The username to encode in the token
        expires_hours: Token expiration time in hours (default from settings)

    Returns:
        Encoded JWT token string
    """
    if expires_hours is None:
        expires_hours = settings.JWT_EXPIRATION_HOURS

    expire = datetime.now(timezone.utc) + timedelta(hours=expires_hours)
    payload = {
        "sub": username,
        "exp": expire,
        "iat": datetime.now(timezone.utc),
    }

    return jwt.encode(payload, settings.JWT_SECRET, algorithm=settings.JWT_ALGORITHM)


def verify_password(plain_password: str, hashed_password: str) -> bool:
    """
    Verify a plain password against a hashed password.

    Args:
        plain_password: The plain text password
        hashed_password: The Argon2 hashed password

    Returns:
        True if password matches, False otherwise
    """
    try:
        return password_hash.verify(plain_password, hashed_password)
    except Exception:
        return False


def verify_token(token: str = Depends(oauth2_scheme)) -> str:
    """
    Verify JWT token and extract username.

    Args:
        token: JWT token from Authorization header

    Returns:
        Username from token

    Raises:
        HTTPException: If token is invalid or expired
    """
    credentials_exception = HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Could not validate credentials",
        headers={"WWW-Authenticate": "Bearer"},
    )

    try:
        payload = jwt.decode(
            token, settings.JWT_SECRET, algorithms=[settings.JWT_ALGORITHM]
        )
        username: Optional[str] = payload.get("sub")

        if username is None:
            raise credentials_exception

        return username

    except JWTError:
        raise credentials_exception


def authenticate_user(username: str, password: str) -> bool:
    """
    Authenticate a user with username and password.

    Args:
        username: The username
        password: The plain text password

    Returns:
        True if authentication successful, False otherwise
    """
    if username != settings.ADMIN_USERNAME:
        return False

    return verify_password(password, settings.ADMIN_PASSWORD_HASH)
