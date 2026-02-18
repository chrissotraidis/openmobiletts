"""Logging configuration for Open Mobile TTS server."""

import glob
import logging
import os
import re
import sys
from datetime import datetime
from logging.handlers import TimedRotatingFileHandler
from typing import List

# Log directory
LOG_DIR = os.getenv("LOG_DIR", "/tmp/openmobiletts_logs")
os.makedirs(LOG_DIR, exist_ok=True)

# Log file path (base name - rotation adds date suffix)
LOG_FILE = os.path.join(LOG_DIR, "openmobiletts.log")

# Log level from environment (default: INFO)
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()

# Log retention (days)
LOG_RETENTION_DAYS = int(os.getenv("LOG_RETENTION_DAYS", "7"))


def setup_logging():
    """Configure logging for the application."""
    # Create formatter
    formatter = logging.Formatter(
        fmt="%(asctime)s | %(levelname)-8s | %(name)s | %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S"
    )

    # Root logger
    root_logger = logging.getLogger()
    root_logger.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))

    # Console handler (stdout)
    console_handler = logging.StreamHandler(sys.stdout)
    console_handler.setLevel(logging.INFO)
    console_handler.setFormatter(formatter)
    root_logger.addHandler(console_handler)

    # File handler with rotation (daily, keep 7 days)
    file_handler = TimedRotatingFileHandler(
        LOG_FILE,
        when='midnight',
        backupCount=LOG_RETENTION_DAYS,
        encoding='utf-8'
    )
    file_handler.setLevel(logging.DEBUG)  # File gets all details
    file_handler.setFormatter(formatter)
    file_handler.suffix = "%Y-%m-%d"  # Adds date to rotated files
    root_logger.addHandler(file_handler)

    # Suppress noisy third-party loggers
    logging.getLogger("uvicorn.access").setLevel(logging.WARNING)
    logging.getLogger("httpx").setLevel(logging.WARNING)

    return root_logger


def get_logger(name: str) -> logging.Logger:
    """Get a logger instance for a module.

    Args:
        name: Module name (typically __name__)

    Returns:
        Configured logger instance
    """
    return logging.getLogger(name)


# Text preview helper for logging
def preview_text(text: str, max_len: int = 200) -> str:
    """Create a preview of text for logging (truncated if long).

    Args:
        text: Text to preview
        max_len: Maximum length before truncation

    Returns:
        Truncated text with ellipsis if needed
    """
    if len(text) <= max_len:
        return repr(text)
    return repr(text[:max_len]) + f"... ({len(text)} chars total)"


# Log parsing regex
LOG_PATTERN = re.compile(
    r'^(?P<timestamp>\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}) \| '
    r'(?P<level>\w+)\s*\| '
    r'(?P<module>[^\|]+)\| '
    r'(?P<message>.*)$'
)


def get_log_files() -> List[str]:
    """Get list of available log files, newest first."""
    pattern = os.path.join(LOG_DIR, "openmobiletts.log*")
    files = glob.glob(pattern)
    # Sort by modification time, newest first
    return sorted(files, key=os.path.getmtime, reverse=True)


def read_logs(max_lines: int = 500, level: str = None) -> List[dict]:
    """Read recent log entries as structured data.

    Args:
        max_lines: Maximum number of log lines to return
        level: Filter by log level (INFO, WARNING, ERROR, DEBUG)

    Returns:
        List of log entry dicts with timestamp, level, module, message
    """
    entries = []
    log_files = get_log_files()

    for log_file in log_files:
        if len(entries) >= max_lines:
            break

        try:
            with open(log_file, 'r', encoding='utf-8') as f:
                lines = f.readlines()

            # Process lines in reverse (newest first)
            for line in reversed(lines):
                if len(entries) >= max_lines:
                    break

                line = line.strip()
                if not line:
                    continue

                match = LOG_PATTERN.match(line)
                if match:
                    entry = {
                        'timestamp': match.group('timestamp'),
                        'level': match.group('level').strip(),
                        'module': match.group('module').strip(),
                        'message': match.group('message'),
                    }

                    # Apply level filter if specified
                    if level and entry['level'] != level.upper():
                        continue

                    entries.append(entry)
                else:
                    # Non-matching line (might be multiline continuation)
                    # Append to previous entry's message if exists
                    if entries and line:
                        entries[-1]['message'] += '\n' + line

        except (IOError, OSError):
            continue

    return entries


def export_logs_json(max_lines: int = 1000) -> dict:
    """Export logs as a JSON-serializable dict for mobile export.

    Args:
        max_lines: Maximum number of log lines to include

    Returns:
        Dict with metadata and log entries
    """
    entries = read_logs(max_lines=max_lines)

    return {
        'exported_at': datetime.now().isoformat(),
        'app_version': '0.2.0',
        'log_level': LOG_LEVEL,
        'entry_count': len(entries),
        'entries': entries,
    }
