"""Retired destructive dummy-data cleanup entry point.

The former implementation deleted operational, financial, identity, and audit
records from the configured database and attempted to create/overwrite a
hard-coded Root Admin. That behavior is unsafe and is intentionally disabled.

This module performs no database or cache operations. Any future data cleanup
must be a separately reviewed, explicitly scoped, dry-run-first workflow that
preserves the single authorized administrator account.
"""

import asyncio


async def clear_backend_data() -> None:
    """Fail closed; destructive production cleanup is no longer supported."""
    raise RuntimeError(
        "Destructive dummy-data cleanup is disabled. "
        "No database rows or cache keys were changed. "
        "Use a reviewed, explicitly scoped maintenance procedure instead."
    )


if __name__ == "__main__":
    try:
        asyncio.run(clear_backend_data())
    except RuntimeError as exc:
        raise SystemExit(str(exc)) from exc
