import hashlib
import json
import uuid
from datetime import datetime, timezone, timedelta
from typing import Dict, Any, List, Optional, Tuple

from fastapi import HTTPException, status, UploadFile
from sqlalchemy import select, update, and_, or_, desc
from sqlalchemy.ext.asyncio import AsyncSession

from backend.domain.models import (
    User, CustomerProfile, DriverProfile, Ride,
    AudioRecordingConsent, AudioRecordingSession, AudioRecordingSegment, AudioRecordingAuditLog
)
from backend.integrations.storage.provider import get_storage_provider

RETENTION_DAYS_DEFAULT = 7


class AudioRecordingService:
    def __init__(self, db: AsyncSession):
        self.db = db
        self.storage = get_storage_provider()

    # -------------------------------------------------------------------------
    # Helper: Verify Ride & Resolve Participants
    # -------------------------------------------------------------------------
    async def _resolve_ride_and_roles(self, ride_id: str, user: User) -> Tuple[Ride, str, str, str]:
        """
        Returns (ride, rider_user_id, driver_user_id, caller_role).
        Validates caller is a participant or administrator.
        """
        ride = await self.db.get(Ride, ride_id)
        if not ride:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=f"Ride '{ride_id}' not found.")

        # Resolve rider user ID
        cust_stmt = select(CustomerProfile.user_id).where(CustomerProfile.id == ride.customer_id)
        cust_res = await self.db.execute(cust_stmt)
        rider_user_id = cust_res.scalar_one_or_none() or ride.customer_id

        # Resolve driver user ID
        driver_user_id = None
        if ride.assigned_driver_id:
            drv_stmt = select(DriverProfile.user_id).where(DriverProfile.id == ride.assigned_driver_id)
            drv_res = await self.db.execute(drv_stmt)
            driver_user_id = drv_res.scalar_one_or_none()

        caller_role = (user.role or "").strip().lower()
        is_rider = (user.id == rider_user_id)
        is_driver = (driver_user_id is not None and user.id == driver_user_id)
        is_admin = caller_role in ["admin", "superadmin", "super administrator"]

        if not (is_rider or is_driver or is_admin):
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Access denied: You are neither a passenger, assigned driver, nor administrator for this trip."
            )

        resolved_role = "CUSTOMER" if is_rider else ("DRIVER" if is_driver else "ADMIN")
        return ride, rider_user_id, (driver_user_id or "unassigned"), resolved_role

    # -------------------------------------------------------------------------
    # 1. Dual-Party Consent Management (DPDP & IT Act 2000)
    # -------------------------------------------------------------------------
    async def record_consent(
        self,
        ride_id: str,
        user: User,
        consented: bool,
        role: Optional[str] = None,
        consent_purpose: str = "IN_CABIN_SAFETY_DPDP_COMPLIANT",
        consent_version: str = "v1.0"
    ) -> Dict[str, Any]:
        ride, rider_user_id, driver_user_id, detected_role = await self._resolve_ride_and_roles(ride_id, user)
        effective_role = (role.upper() if role else detected_role)

        new_status = "GRANTED" if consented else "DECLINED"
        now = datetime.now(timezone.utc)

        # Check existing consent record for this ride + user
        stmt = select(AudioRecordingConsent).where(
            AudioRecordingConsent.ride_id == ride.id,
            AudioRecordingConsent.user_id == user.id,
            AudioRecordingConsent.consent_version == consent_version
        )
        res = await self.db.execute(stmt)
        consent = res.scalar_one_or_none()

        if not consent:
            consent = AudioRecordingConsent(
                id=str(uuid.uuid4()),
                ride_id=ride.id,
                user_id=user.id,
                user_role=effective_role,
                consent_purpose=consent_purpose,
                consent_version=consent_version,
                consent_status=new_status,
                granted_at=now if consented else None,
                withdrawn_at=None
            )
            self.db.add(consent)
        else:
            consent.consent_status = new_status
            if consented:
                consent.granted_at = now
                consent.withdrawn_at = None
            else:
                consent.granted_at = None

        # Update denormalized attributes on Ride model for fast query
        if effective_role == "CUSTOMER":
            ride.audio_recording_consent_passenger = consented
        else:
            ride.audio_recording_consent_driver = consented

        # Evaluate mutual dual-party consent
        both_granted = bool(ride.audio_recording_consent_passenger and ride.audio_recording_consent_driver)
        ride.audio_recording_active = both_granted

        session_id = ride.audio_recording_session_id
        if both_granted:
            if not session_id:
                session_id = f"rec-sess-{uuid.uuid4().hex[:12]}"
                ride.audio_recording_session_id = session_id

            # Create or update session record
            stmt_sess = select(AudioRecordingSession).where(AudioRecordingSession.session_id == session_id)
            sess = (await self.db.execute(stmt_sess)).scalar_one_or_none()
            if not sess:
                sess = AudioRecordingSession(
                    id=str(uuid.uuid4()),
                    session_id=session_id,
                    ride_id=ride.id,
                    rider_user_id=rider_user_id,
                    driver_user_id=driver_user_id,
                    recording_status="AUTHORIZED",
                    authorization_status="AUTHORIZED",
                    rider_consent_id=consent.id if effective_role == "CUSTOMER" else None,
                    driver_consent_id=consent.id if effective_role == "DRIVER" else None
                )
                self.db.add(sess)
            else:
                sess.authorization_status = "AUTHORIZED"
                if effective_role == "CUSTOMER":
                    sess.rider_consent_id = consent.id
                else:
                    sess.driver_consent_id = consent.id

        # Write audit log
        audit = AudioRecordingAuditLog(
            id=str(uuid.uuid4()),
            session_id=session_id,
            ride_id=ride.id,
            actor_id=user.id,
            actor_role=effective_role,
            action="CONSENT_GRANTED" if consented else "CONSENT_DECLINED",
            authorization_result="ALLOWED",
            metadata_json=json.dumps({
                "consented": consented,
                "both_granted": both_granted,
                "version": consent_version,
                "purpose": consent_purpose
            })
        )
        self.db.add(audit)
        await self.db.commit()

        return {
            "ride_id": ride.id,
            "session_id": session_id,
            "user_id": user.id,
            "user_role": effective_role,
            "consent_status": new_status,
            "passenger_consent": ride.audio_recording_consent_passenger,
            "driver_consent": ride.audio_recording_consent_driver,
            "dual_consent_achieved": both_granted,
            "is_recording_active": ride.audio_recording_active,
            "legal_notice": "In-cabin audio recording is strictly opt-in and compliant with DPDP Act 2023. Audio is encrypted with AES-256-GCM and retained for maximum 7 days solely for safety investigations."
        }

    async def withdraw_consent(self, ride_id: str, user: User, reason: Optional[str] = None) -> Dict[str, Any]:
        ride, rider_user_id, driver_user_id, role = await self._resolve_ride_and_roles(ride_id, user)
        now = datetime.now(timezone.utc)

        # Mark user's consent as WITHDRAWN
        stmt = select(AudioRecordingConsent).where(
            AudioRecordingConsent.ride_id == ride.id,
            AudioRecordingConsent.user_id == user.id
        )
        res = await self.db.execute(stmt)
        consents = res.scalars().all()
        for c in consents:
            c.consent_status = "WITHDRAWN"
            c.withdrawn_at = now

        if role == "CUSTOMER":
            ride.audio_recording_consent_passenger = False
        else:
            ride.audio_recording_consent_driver = False

        # Terminate active recording session immediately
        ride.audio_recording_active = False
        session_id = ride.audio_recording_session_id
        if session_id:
            stmt_sess = select(AudioRecordingSession).where(AudioRecordingSession.session_id == session_id)
            sess = (await self.db.execute(stmt_sess)).scalar_one_or_none()
            if sess and sess.recording_status in ["RECORDING", "AUTHORIZED", "AWAITING_CONSENT"]:
                sess.recording_status = "STOPPED"
                sess.authorization_status = "REVOKED"
                sess.stopped_at = now
                sess.stopped_reason = f"CONSENT_WITHDRAWN_BY_{role}: {reason or 'No reason provided'}"

        audit = AudioRecordingAuditLog(
            id=str(uuid.uuid4()),
            session_id=session_id,
            ride_id=ride.id,
            actor_id=user.id,
            actor_role=role,
            action="CONSENT_WITHDRAWN",
            access_reason=reason,
            authorization_result="ALLOWED",
            metadata_json=json.dumps({"withdrawn_by": role, "timestamp": now.isoformat()})
        )
        self.db.add(audit)
        await self.db.commit()

        return {
            "ride_id": ride.id,
            "session_id": session_id,
            "status": "WITHDRAWN",
            "is_recording_active": False,
            "message": "Consent has been withdrawn. In-cabin audio recording stopped immediately."
        }

    async def get_consent_status(self, ride_id: str, user: User) -> Dict[str, Any]:
        ride, rider_user_id, driver_user_id, role = await self._resolve_ride_and_roles(ride_id, user)

        # Fetch active consents
        stmt = select(AudioRecordingConsent).where(AudioRecordingConsent.ride_id == ride.id)
        res = await self.db.execute(stmt)
        consents = res.scalars().all()

        rider_c = next((c for c in consents if c.user_role == "CUSTOMER"), None)
        driver_c = next((c for c in consents if c.user_role == "DRIVER"), None)

        session_data = None
        if ride.audio_recording_session_id:
            sess_stmt = select(AudioRecordingSession).where(AudioRecordingSession.session_id == ride.audio_recording_session_id)
            sess = (await self.db.execute(sess_stmt)).scalar_one_or_none()
            if sess:
                session_data = {
                    "session_id": sess.session_id,
                    "recording_status": sess.recording_status,
                    "authorization_status": sess.authorization_status,
                    "started_at": sess.started_at.isoformat() if sess.started_at else None,
                    "stopped_at": sess.stopped_at.isoformat() if sess.stopped_at else None,
                    "total_segments": sess.total_segments,
                    "total_duration_seconds": sess.total_duration_seconds
                }

        both_granted = bool(ride.audio_recording_consent_passenger and ride.audio_recording_consent_driver)
        return {
            "ride_id": ride.id,
            "passenger_consent": ride.audio_recording_consent_passenger,
            "driver_consent": ride.audio_recording_consent_driver,
            "passenger_consent_status": rider_c.consent_status if rider_c else "NOT_REQUESTED",
            "driver_consent_status": driver_c.consent_status if driver_c else "NOT_REQUESTED",
            "dual_consent_achieved": both_granted,
            "is_recording_active": ride.audio_recording_active,
            "session": session_data,
            "legal_info": {
                "dpdp_compliant": True,
                "retention_days": RETENTION_DAYS_DEFAULT,
                "encryption": "AES-256-GCM"
            }
        }

    # -------------------------------------------------------------------------
    # 2. Recording Session Lifecycle
    # -------------------------------------------------------------------------
    async def authorize_session(self, ride_id: str, user: User) -> Dict[str, Any]:
        ride, rider_user_id, driver_user_id, role = await self._resolve_ride_and_roles(ride_id, user)

        if ride.status in ["COMPLETED", "CANCELLED"]:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Cannot authorize recording for trip in finished status '{ride.status}'."
            )

        if not (ride.audio_recording_consent_passenger and ride.audio_recording_consent_driver):
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Dual-party consent not satisfied. Both rider and driver must explicitly grant consent."
            )

        session_id = ride.audio_recording_session_id or f"rec-sess-{uuid.uuid4().hex[:12]}"
        ride.audio_recording_session_id = session_id
        ride.audio_recording_active = True

        stmt_sess = select(AudioRecordingSession).where(AudioRecordingSession.session_id == session_id)
        sess = (await self.db.execute(stmt_sess)).scalar_one_or_none()
        now = datetime.now(timezone.utc)

        if not sess:
            sess = AudioRecordingSession(
                id=str(uuid.uuid4()),
                session_id=session_id,
                ride_id=ride.id,
                rider_user_id=rider_user_id,
                driver_user_id=driver_user_id,
                recording_status="AUTHORIZED",
                authorization_status="AUTHORIZED",
                started_at=now
            )
            self.db.add(sess)
        else:
            sess.authorization_status = "AUTHORIZED"
            sess.recording_status = "AUTHORIZED"

        audit = AudioRecordingAuditLog(
            id=str(uuid.uuid4()),
            session_id=session_id,
            ride_id=ride.id,
            actor_id=user.id,
            actor_role=role,
            action="SESSION_AUTHORIZED",
            authorization_result="ALLOWED"
        )
        self.db.add(audit)
        await self.db.commit()

        return {
            "session_id": session_id,
            "ride_id": ride.id,
            "status": "AUTHORIZED",
            "authorized": True,
            "encryption_algorithm": sess.encryption_algorithm,
            "encryption_key_id": sess.encryption_key_id,
            "message": "Audio safety session authorized. Mobile client may initialize encrypted recording."
        }

    async def start_session(self, session_id: str, user: User) -> Dict[str, Any]:
        stmt = select(AudioRecordingSession).where(AudioRecordingSession.session_id == session_id)
        sess = (await self.db.execute(stmt)).scalar_one_or_none()
        if not sess:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=f"Recording session '{session_id}' not found.")

        if sess.authorization_status != "AUTHORIZED":
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Session is not authorized.")

        now = datetime.now(timezone.utc)
        sess.recording_status = "RECORDING"
        if not sess.started_at:
            sess.started_at = now
        sess.last_heartbeat_at = now

        audit = AudioRecordingAuditLog(
            id=str(uuid.uuid4()),
            session_id=session_id,
            ride_id=sess.ride_id,
            actor_id=user.id,
            actor_role=(user.role or "USER").upper(),
            action="RECORDING_STARTED",
            authorization_result="ALLOWED"
        )
        self.db.add(audit)
        await self.db.commit()

        return {
            "session_id": session_id,
            "status": "RECORDING",
            "started_at": sess.started_at.isoformat()
        }

    async def heartbeat(self, session_id: str, user: User) -> Dict[str, Any]:
        stmt = select(AudioRecordingSession).where(AudioRecordingSession.session_id == session_id)
        sess = (await self.db.execute(stmt)).scalar_one_or_none()
        if not sess:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Session not found.")

        now = datetime.now(timezone.utc)
        sess.last_heartbeat_at = now
        await self.db.commit()
        return {"session_id": session_id, "status": sess.recording_status, "alive": True, "heartbeat_at": now.isoformat()}

    async def stop_session(self, session_id: str, user: User, reason: str = "USER_STOPPED") -> Dict[str, Any]:
        stmt = select(AudioRecordingSession).where(AudioRecordingSession.session_id == session_id)
        sess = (await self.db.execute(stmt)).scalar_one_or_none()
        if not sess:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Session not found.")

        now = datetime.now(timezone.utc)
        sess.recording_status = "STOPPED"
        sess.stopped_at = now
        sess.stopped_reason = reason

        ride = await self.db.get(Ride, sess.ride_id)
        if ride:
            ride.audio_recording_active = False

        audit = AudioRecordingAuditLog(
            id=str(uuid.uuid4()),
            session_id=session_id,
            ride_id=sess.ride_id,
            actor_id=user.id,
            actor_role=(user.role or "USER").upper(),
            action="RECORDING_STOPPED",
            access_reason=reason,
            authorization_result="ALLOWED"
        )
        self.db.add(audit)
        await self.db.commit()

        return {
            "session_id": session_id,
            "status": "STOPPED",
            "stopped_at": now.isoformat(),
            "reason": reason
        }

    # -------------------------------------------------------------------------
    # 3. Encrypted Segment Ingestion & Integrity Verification
    # -------------------------------------------------------------------------
    async def upload_encrypted_segment(
        self,
        session_id: str,
        user: User,
        segment_sequence: int,
        duration_seconds: float,
        encryption_nonce: str,
        encryption_tag: str,
        file: UploadFile
    ) -> Dict[str, Any]:
        stmt = select(AudioRecordingSession).where(AudioRecordingSession.session_id == session_id)
        sess = (await self.db.execute(stmt)).scalar_one_or_none()
        if not sess:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=f"Recording session '{session_id}' not found.")

        if sess.authorization_status != "AUTHORIZED":
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Cannot upload to an unauthorized session.")

        raw_bytes = await file.read()
        if not raw_bytes or len(raw_bytes) == 0:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Segment payload cannot be empty.")

        # Persist through secure private storage provider
        storage_path, file_size, sha256_hash = await self.storage.save_audio_segment(
            ride_id=sess.ride_id,
            session_id=sess.session_id,
            segment_sequence=segment_sequence,
            encrypted_bytes=raw_bytes
        )

        # Check existing segment record for idempotency
        stmt_seg = select(AudioRecordingSegment).where(
            AudioRecordingSegment.session_id == sess.session_id,
            AudioRecordingSegment.segment_sequence == segment_sequence
        )
        seg = (await self.db.execute(stmt_seg)).scalar_one_or_none()
        now = datetime.now(timezone.utc)
        retention_exp = now + timedelta(days=RETENTION_DAYS_DEFAULT)

        if not seg:
            seg = AudioRecordingSegment(
                id=str(uuid.uuid4()),
                session_id=sess.session_id,
                ride_id=sess.ride_id,
                segment_sequence=segment_sequence,
                storage_bucket="ride-audio-recordings",
                storage_object_path=storage_path,
                file_format=file.content_type or "audio/aac",
                encryption_nonce=encryption_nonce,
                encryption_tag=encryption_tag,
                duration_seconds=duration_seconds,
                file_size_bytes=file_size,
                sha256_hash=sha256_hash,
                upload_status="UPLOADED",
                retention_expiry_at=retention_exp,
                created_at=now
            )
            self.db.add(seg)
            sess.total_segments += 1
            sess.total_duration_seconds += duration_seconds
        else:
            seg.storage_object_path = storage_path
            seg.file_size_bytes = file_size
            seg.sha256_hash = sha256_hash
            seg.upload_status = "UPLOADED"

        audit = AudioRecordingAuditLog(
            id=str(uuid.uuid4()),
            session_id=sess.session_id,
            ride_id=sess.ride_id,
            actor_id=user.id,
            actor_role=(user.role or "USER").upper(),
            action="SEGMENT_UPLOADED",
            authorization_result="ALLOWED",
            metadata_json=json.dumps({
                "sequence": segment_sequence,
                "duration_s": duration_seconds,
                "size_bytes": file_size,
                "sha256": sha256_hash
            })
        )
        self.db.add(audit)
        await self.db.commit()

        return {
            "segment_id": seg.id,
            "session_id": sess.session_id,
            "sequence": segment_sequence,
            "storage_key": seg.storage_object_path,
            "storage_object_path": seg.storage_object_path,
            "size_bytes": file_size,
            "sha256_hash": sha256_hash,
            "retention_expiry": retention_exp.isoformat(),
            "status": "UPLOADED"
        }

    # -------------------------------------------------------------------------
    # 4. Admin Governance & Cryptographic Evidence Playback
    # -------------------------------------------------------------------------
    async def admin_list_sessions(
        self,
        status_filter: Optional[str] = None,
        limit: int = 50,
        offset: int = 0
    ) -> List[Dict[str, Any]]:
        stmt = select(AudioRecordingSession).order_by(desc(AudioRecordingSession.created_at))
        if status_filter:
            stmt = stmt.where(AudioRecordingSession.recording_status == status_filter.upper())
        stmt = stmt.offset(offset).limit(limit)

        res = await self.db.execute(stmt)
        sessions = res.scalars().all()
        return [
            {
                "id": s.id,
                "session_id": s.session_id,
                "ride_id": s.ride_id,
                "rider_user_id": s.rider_user_id,
                "driver_user_id": s.driver_user_id,
                "recording_status": s.recording_status,
                "authorization_status": s.authorization_status,
                "started_at": s.started_at.isoformat() if s.started_at else None,
                "stopped_at": s.stopped_at.isoformat() if s.stopped_at else None,
                "stopped_reason": s.stopped_reason,
                "total_segments": s.total_segments,
                "total_duration_seconds": s.total_duration_seconds,
                "created_at": s.created_at.isoformat()
            }
            for s in sessions
        ]

    async def admin_get_session_details(self, session_id: str, admin_user: User) -> Dict[str, Any]:
        stmt = select(AudioRecordingSession).where(AudioRecordingSession.session_id == session_id)
        sess = (await self.db.execute(stmt)).scalar_one_or_none()
        if not sess:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=f"Session '{session_id}' not found.")

        # Load segments
        seg_stmt = select(AudioRecordingSegment).where(
            AudioRecordingSegment.session_id == session_id
        ).order_by(AudioRecordingSegment.segment_sequence)
        segments = (await self.db.execute(seg_stmt)).scalars().all()

        # Load audit logs
        audit_stmt = select(AudioRecordingAuditLog).where(
            AudioRecordingAuditLog.session_id == session_id
        ).order_by(desc(AudioRecordingAuditLog.created_at)).limit(30)
        logs = (await self.db.execute(audit_stmt)).scalars().all()

        return {
            "status": sess.recording_status,
            "recording_status": sess.recording_status,
            "session_id": sess.session_id,
            "session": {
                "id": sess.id,
                "session_id": sess.session_id,
                "ride_id": sess.ride_id,
                "status": sess.recording_status,
                "rider_user_id": sess.rider_user_id,
                "driver_user_id": sess.driver_user_id,
                "recording_status": sess.recording_status,
                "authorization_status": sess.authorization_status,
                "started_at": sess.started_at.isoformat() if sess.started_at else None,
                "stopped_at": sess.stopped_at.isoformat() if sess.stopped_at else None,
                "stopped_reason": sess.stopped_reason,
                "encryption_algorithm": sess.encryption_algorithm,
                "encryption_key_id": sess.encryption_key_id,
                "total_segments": sess.total_segments,
                "total_duration_seconds": sess.total_duration_seconds
            },
            "segments": [
                {
                    "id": seg.id,
                    "sequence": seg.segment_sequence,
                    "storage_path": seg.storage_object_path,
                    "duration_seconds": seg.duration_seconds,
                    "file_size_bytes": seg.file_size_bytes,
                    "sha256_hash": seg.sha256_hash,
                    "nonce": seg.encryption_nonce,
                    "tag": seg.encryption_tag,
                    "retention_expiry": seg.retention_expiry_at.isoformat(),
                    "is_purged": seg.is_purged
                }
                for seg in segments
            ],
            "audit_logs": [
                {
                    "id": log.id,
                    "actor_id": log.actor_id,
                    "actor_role": log.actor_role,
                    "action": log.action,
                    "authorization_result": log.authorization_result,
                    "access_reason": log.access_reason,
                    "created_at": log.created_at.isoformat()
                }
                for log in logs
            ]
        }

    async def admin_authorize_segment_playback(
        self,
        session_id: str,
        segment_id: str,
        admin_user: User,
        justification_reason: str
    ) -> Dict[str, Any]:
        if not justification_reason or len(justification_reason.strip()) < 10:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="A detailed statutory justification reason (min 10 chars) is legally required to access encrypted safety recordings."
            )

        stmt = select(AudioRecordingSegment).where(
            AudioRecordingSegment.session_id == session_id,
            AudioRecordingSegment.id == segment_id
        )
        seg = (await self.db.execute(stmt)).scalar_one_or_none()
        if not seg:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Audio segment not found.")

        if seg.is_purged:
            raise HTTPException(status_code=status.HTTP_410_GONE, detail="This audio segment has been purged per statutory data retention policy.")

        # Generate short-lived (5 min) authorized signed URL
        signed_url = await self.storage.generate_signed_audio_url(seg.storage_object_path, expires_in_seconds=300)

        # Log audit trail
        audit = AudioRecordingAuditLog(
            id=str(uuid.uuid4()),
            session_id=session_id,
            ride_id=seg.ride_id,
            actor_id=admin_user.id,
            actor_role="ADMIN",
            action="ADMIN_PLAYBACK_AUTHORIZED",
            access_reason=justification_reason.strip(),
            authorization_result="ALLOWED",
            metadata_json=json.dumps({"segment_id": segment_id, "sequence": seg.segment_sequence})
        )
        self.db.add(audit)
        await self.db.commit()

        return {
            "session_id": session_id,
            "segment_id": segment_id,
            "authorized": True,
            "download_url": signed_url,
            "playback_url": signed_url,
            "expires_in_seconds": 300,
            "encryption": {
                "algorithm": "AES-256-GCM",
                "nonce": seg.encryption_nonce,
                "tag": seg.encryption_tag,
                "sha256": seg.sha256_hash
            },
            "warning": "Confidential evidentiary material. Distribution or unauthorized reproduction is prohibited under DPDP Act 2023."
        }

    # -------------------------------------------------------------------------
    # 5. Scheduled Retention Purge Engine
    # -------------------------------------------------------------------------
    async def purge_expired_recordings(self) -> Dict[str, Any]:
        now = datetime.now(timezone.utc)
        stmt = select(AudioRecordingSegment).where(
            AudioRecordingSegment.retention_expiry_at <= now,
            AudioRecordingSegment.is_purged == False
        )
        res = await self.db.execute(stmt)
        expired_segments = res.scalars().all()

        purged_count = 0
        for seg in expired_segments:
            await self.storage.purge_audio_segment(seg.storage_object_path)
            seg.is_purged = True
            seg.purged_at = now
            purged_count += 1

            audit = AudioRecordingAuditLog(
                id=str(uuid.uuid4()),
                session_id=seg.session_id,
                ride_id=seg.ride_id,
                actor_id="system-retention-worker",
                actor_role="SYSTEM",
                action="RETENTION_PURGED",
                access_reason=f"7-day retention expired at {seg.retention_expiry_at.isoformat()}",
                authorization_result="ALLOWED"
            )
            self.db.add(audit)

        await self.db.commit()
        return {
            "purged_count": purged_count,
            "purged_segments_count": purged_count,
            "purged_at": now.isoformat(),
            "policy": "7-day rolling DPDP retention"
        }
