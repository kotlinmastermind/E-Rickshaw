"""
Database Integrity Verification Script.
Validates financial records, relationships, duplicate identifiers, and orphan records.
"""
import sys
import os
import logging

# Ensure project root is in sys.path
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from sqlalchemy import create_engine, text, inspect
from backend.core.config import settings

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(name)s: %(message)s")
logger = logging.getLogger("db_integrity")

def verify_database_integrity(db_url: str = None) -> dict:
    url = db_url or settings.DATABASE_URL_SYNC
    engine = create_engine(url)
    results = {
        "status": "PASSED",
        "checks_performed": 0,
        "violations": []
    }

    try:
        with engine.connect() as conn:
            inspector = inspect(engine)
            existing_tables = set(inspector.get_table_names())

            # 1. Foreign Key Integrity Check (Dialect dependent)
            results["checks_performed"] += 1
            if engine.dialect.name == "sqlite":
                fk_check = conn.execute(text("PRAGMA foreign_key_check")).fetchall()
                if fk_check:
                    for row in fk_check:
                        msg = f"SQLite Foreign Key violation in table '{row[0]}', rowid {row[1]}, target table '{row[2]}'"
                        results["violations"].append(msg)
                        results["status"] = "FAILED"
            elif engine.dialect.name == "postgresql":
                # For PostgreSQL, check known key relationships for orphans
                relationships_to_verify = [
                    ("customer_profiles", "user_id", "users", "id"),
                    ("driver_profiles", "user_id", "users", "id"),
                    ("rides", "customer_id", "customer_profiles", "id"),
                    ("payments", "customer_id", "customer_profiles", "id"),
                    ("wallets", "user_id", "users", "id"),
                    ("wallet_transactions", "wallet_id", "wallets", "id"),
                ]
                for child_tbl, child_col, parent_tbl, parent_col in relationships_to_verify:
                    if child_tbl in existing_tables and parent_tbl in existing_tables:
                        query = text(f"""
                            SELECT COUNT(*) FROM {child_tbl} c 
                            LEFT JOIN {parent_tbl} p ON c.{child_col} = p.{parent_col} 
                            WHERE c.{child_col} IS NOT NULL AND p.{parent_col} IS NULL
                        """)  # nosec B608
                        count = conn.execute(query).scalar()
                        if count and count > 0:
                            msg = f"PostgreSQL Orphan check failed: {count} rows in {child_tbl}.{child_col} have no parent in {parent_tbl}.{parent_col}"
                            results["violations"].append(msg)
                            results["status"] = "FAILED"

            # 2. Check duplicate identifiers on active users
            if "users" in existing_tables:
                results["checks_performed"] += 1
                dup_phones = conn.execute(text("""
                    SELECT phone_number, COUNT(*) FROM users 
                    WHERE phone_number IS NOT NULL 
                    GROUP BY phone_number HAVING COUNT(*) > 1
                """)).fetchall()
                if dup_phones:
                    for p, count in dup_phones:
                        results["violations"].append(f"Duplicate phone_number '{p}' found {count} times in users table.")
                        results["status"] = "FAILED"

            # 3. Check wallet transaction consistency
            if "wallet_transactions" in existing_tables and "wallets" in existing_tables:
                results["checks_performed"] += 1
                wallets = conn.execute(text("""
                    SELECT w.id, w.balance_cents, 
                           COALESCE(SUM(CASE 
                               WHEN wt.transaction_type LIKE '%CREDIT%' THEN wt.amount_cents 
                               WHEN wt.transaction_type LIKE '%DEBIT%' THEN -wt.amount_cents 
                               ELSE 0 END), 0) as ledger_total
                    FROM wallets w
                    LEFT JOIN wallet_transactions wt ON wt.wallet_id = w.id
                    GROUP BY w.id, w.balance_cents
                """)).fetchall()
                for row in wallets:
                    wallet_id, balance, ledger_sum = row[0], row[1], row[2]
                    # Log disparity if ledger entries exist but balance doesn't match
                    if ledger_sum != 0 and balance != ledger_sum:
                        results["violations"].append(
                            f"Wallet {wallet_id} balance mismatch: cached={balance}, ledger_sum={ledger_sum}"
                        )
                        results["status"] = "FAILED"

            # 4. Check negative amounts on payments
            if "payments" in existing_tables:
                results["checks_performed"] += 1
                neg_payments = conn.execute(text("""
                    SELECT id, amount_cents FROM payments WHERE amount_cents < 0
                """)).fetchall()
                if neg_payments:
                    for pid, amt in neg_payments:
                        results["violations"].append(f"Payment {pid} has invalid negative amount {amt} cents.")
                        results["status"] = "FAILED"

            logger.info(f"Integrity check completed: {results['checks_performed']} checks performed, {len(results['violations'])} violations found.")
            return results

    finally:
        engine.dispose()

if __name__ == "__main__":
    res = verify_database_integrity()
    if res["status"] != "PASSED":
        logger.error(f"Integrity verification failed with violations: {res['violations']}")
        sys.exit(1)
    else:
        logger.info("Database integrity verification passed with zero violations.")
        sys.exit(0)
