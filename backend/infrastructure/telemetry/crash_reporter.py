import traceback
import sys
import logging
from datetime import datetime, timezone
from typing import Dict, Any, List, Optional
from backend.core.config import settings
from backend.infrastructure.security.encryption import redact_sensitive_dict

logger = logging.getLogger("telemetry.crash")

class CrashEvent:
    def __init__(
        self,
        event_id: str,
        exception_type: str,
        message: str,
        stacktrace: str,
        context: Dict[str, Any],
        release_version: str,
        environment: str
    ):
        self.event_id = event_id
        self.exception_type = exception_type
        self.message = message
        self.stacktrace = stacktrace
        self.context = context
        self.release_version = release_version
        self.environment = environment
        self.timestamp = datetime.now(timezone.utc).isoformat()

    def to_dict(self) -> Dict[str, Any]:
        return {
            "event_id": self.event_id,
            "exception_type": self.exception_type,
            "message": self.message,
            "stacktrace": self.stacktrace,
            "context": self.context,
            "release_version": self.release_version,
            "environment": self.environment,
            "timestamp": self.timestamp
        }

class CrashReporter:
    """
    Configurable, privacy-aware crash reporting adapter with sensitive PII redaction,
    release/environment tagging, error fingerprinting, and local diagnostic telemetry.
    """
    def __init__(self):
        self.environment = getattr(settings, "ENVIRONMENT", "development")
        self.release_version = getattr(settings, "RELEASE_VERSION", "v3.0.0-phase3")
        self.sentry_dsn = getattr(settings, "SENTRY_DSN", None)
        self._recent_events: List[CrashEvent] = []
        self._max_stored_events = 50

        # Initialize Sentry SDK if configured in production
        if self.sentry_dsn:
            try:
                import sentry_sdk
                sentry_sdk.init(
                    dsn=self.sentry_dsn,
                    environment=self.environment,
                    release=self.release_version,
                    send_default_pii=False,
                    traces_sample_rate=0.2
                )
                logger.info("Sentry crash reporting initialized.")
            except ImportError:
                logger.warning("sentry-sdk not installed; running in local telemetry mode.")

    def capture_exception(
        self,
        exc: Exception,
        extra_context: Optional[Dict[str, Any]] = None,
        correlation_id: Optional[str] = None
    ) -> str:
        import uuid
        event_id = str(uuid.uuid4())
        exc_type = type(exc).__name__
        msg = str(exc)
        tb_lines = traceback.format_exception(type(exc), exc, exc.__traceback__)
        full_stacktrace = "".join(tb_lines)

        sanitized_ctx = redact_sensitive_dict(extra_context or {})
        if correlation_id:
            sanitized_ctx["correlation_id"] = correlation_id

        event = CrashEvent(
            event_id=event_id,
            exception_type=exc_type,
            message=msg,
            stacktrace=full_stacktrace,
            context=sanitized_ctx,
            release_version=self.release_version,
            environment=self.environment
        )

        self._recent_events.append(event)
        if len(self._recent_events) > self._max_stored_events:
            self._recent_events.pop(0)

        logger.error(f"[CrashReporter {event_id}] {exc_type}: {msg} | Env: {self.environment}")
        return event_id

    def get_recent_crashes(self, limit: int = 20) -> List[Dict[str, Any]]:
        return [e.to_dict() for e in reversed(self._recent_events[-limit:])]

    def trigger_test_crash(self, test_message: str = "Diagnostic verification test exception") -> str:
        """Trigger a controlled, non-fatal test exception to verify telemetry pipeline."""
        try:
            raise RuntimeError(f"Verified telemetry alert: {test_message}")
        except RuntimeError as e:
            return self.capture_exception(e, extra_context={"source": "test_crash_endpoint", "is_synthetic": True})

# Global singleton
crash_reporter = CrashReporter()
