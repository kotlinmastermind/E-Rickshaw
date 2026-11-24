"""corporate enterprise suite schema and data migration

Revision ID: 0006_corporate_enterprise_suite
Revises: 0005_drop_unused_phantom_tables
Create Date: 2026-11-23

"""
from alembic import op
import sqlalchemy as sa
from sqlalchemy.engine.reflection import Inspector
import uuid
import datetime

# revision identifiers, used by Alembic.
revision = '0006_corporate_enterprise_suite'
down_revision = '0005_drop_unused_phantom_tables'
branch_labels = None
depends_on = None


def upgrade():
    conn = op.get_bind()
    inspector = Inspector.from_engine(conn)
    existing_tables = set(inspector.get_table_names())

    # 1. Update corporate_accounts table columns
    if 'corporate_accounts' in existing_tables:
        cols = {c['name'] for c in inspector.get_columns('corporate_accounts')}
        if 'trade_name' not in cols:
            op.add_column('corporate_accounts', sa.Column('trade_name', sa.String(128), nullable=True))
        if 'pan' not in cols:
            op.add_column('corporate_accounts', sa.Column('pan', sa.String(32), nullable=True))
        if 'registered_address' not in cols:
            op.add_column('corporate_accounts', sa.Column('registered_address', sa.Text(), nullable=True))
        if 'billing_address' not in cols:
            op.add_column('corporate_accounts', sa.Column('billing_address', sa.Text(), nullable=True))
        if 'billing_contact_name' not in cols:
            op.add_column('corporate_accounts', sa.Column('billing_contact_name', sa.String(128), nullable=True))
        if 'billing_contact_phone' not in cols:
            op.add_column('corporate_accounts', sa.Column('billing_contact_phone', sa.String(32), nullable=True))
        if 'admin_user_id' not in cols:
            op.add_column('corporate_accounts', sa.Column('admin_user_id', sa.String(36), sa.ForeignKey('users.id', ondelete='SET NULL'), nullable=True))
            op.create_index('ix_corporate_accounts_admin_user_id', 'corporate_accounts', ['admin_user_id'])
        if 'monthly_spending_limit_cents' not in cols:
            op.add_column('corporate_accounts', sa.Column('monthly_spending_limit_cents', sa.Integer(), server_default='10000000', nullable=True))
        if 'currency' not in cols:
            op.add_column('corporate_accounts', sa.Column('currency', sa.String(8), server_default='INR', nullable=True))
        if 'payment_terms' not in cols:
            op.add_column('corporate_accounts', sa.Column('payment_terms', sa.String(32), server_default='NET_30', nullable=True))
        if 'default_cost_center' not in cols:
            op.add_column('corporate_accounts', sa.Column('default_cost_center', sa.String(64), nullable=True))
        if 'rejection_reason' not in cols:
            op.add_column('corporate_accounts', sa.Column('rejection_reason', sa.Text(), nullable=True))
        if 'suspension_reason' not in cols:
            op.add_column('corporate_accounts', sa.Column('suspension_reason', sa.Text(), nullable=True))
        if 'approved_by' not in cols:
            op.add_column('corporate_accounts', sa.Column('approved_by', sa.String(36), nullable=True))
        if 'approved_at' not in cols:
            op.add_column('corporate_accounts', sa.Column('approved_at', sa.DateTime(timezone=True), nullable=True))

    # 2. Create corporate_employees table
    if 'corporate_employees' not in existing_tables:
        op.create_table(
            'corporate_employees',
            sa.Column('id', sa.String(36), primary_key=True),
            sa.Column('corporate_id', sa.String(36), sa.ForeignKey('corporate_accounts.id', ondelete='CASCADE'), nullable=False),
            sa.Column('user_id', sa.String(36), sa.ForeignKey('users.id', ondelete='SET NULL'), nullable=True),
            sa.Column('employee_id', sa.String(64), nullable=False),
            sa.Column('employee_name', sa.String(128), nullable=False),
            sa.Column('employee_email', sa.String(128), nullable=False),
            sa.Column('employee_phone', sa.String(32), nullable=True),
            sa.Column('department', sa.String(64), nullable=True),
            sa.Column('cost_center', sa.String(64), nullable=True),
            sa.Column('designation', sa.String(64), nullable=True),
            sa.Column('status', sa.String(32), server_default='ACTIVE', nullable=False),
            sa.Column('monthly_ride_limit', sa.Integer(), server_default='50', nullable=True),
            sa.Column('monthly_spending_limit_cents', sa.Integer(), server_default='5000000', nullable=True),
            sa.Column('per_ride_limit_cents', sa.Integer(), server_default='200000', nullable=True),
            sa.Column('allowed_vehicle_types_json', sa.Text(), nullable=True),
            sa.Column('approval_required', sa.Boolean(), server_default='false', nullable=False),
            sa.Column('created_at', sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=True),
            sa.Column('updated_at', sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=True),
            sa.UniqueConstraint('corporate_id', 'employee_id', name='uq_corp_emp_id'),
        )
        op.create_index('ix_corporate_employees_corporate_id', 'corporate_employees', ['corporate_id'])
        op.create_index('ix_corporate_employees_user_id', 'corporate_employees', ['user_id'])

    # 3. Update corporate_travel_policies
    if 'corporate_travel_policies' in existing_tables:
        cols = {c['name'] for c in inspector.get_columns('corporate_travel_policies')}
        if 'monthly_ride_limit' not in cols:
            op.add_column('corporate_travel_policies', sa.Column('monthly_ride_limit', sa.Integer(), server_default='60', nullable=True))
        if 'monthly_spending_limit_cents' not in cols:
            op.add_column('corporate_travel_policies', sa.Column('monthly_spending_limit_cents', sa.Integer(), server_default='10000000', nullable=True))
        if 'daily_ride_limit' not in cols:
            op.add_column('corporate_travel_policies', sa.Column('daily_ride_limit', sa.Integer(), server_default='4', nullable=True))
        if 'approval_required' not in cols:
            op.add_column('corporate_travel_policies', sa.Column('approval_required', sa.Boolean(), server_default='false', nullable=False))
        if 'updated_at' not in cols:
            op.add_column('corporate_travel_policies', sa.Column('updated_at', sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=True))

    # 4. Update corporate_invoices
    if 'corporate_invoices' in existing_tables:
        cols = {c['name'] for c in inspector.get_columns('corporate_invoices')}
        if 'subtotal_cents' not in cols:
            op.add_column('corporate_invoices', sa.Column('subtotal_cents', sa.Integer(), server_default='0', nullable=False))
        if 'tax_amount_cents' not in cols:
            op.add_column('corporate_invoices', sa.Column('tax_amount_cents', sa.Integer(), server_default='0', nullable=False))
        if 'discount_cents' not in cols:
            op.add_column('corporate_invoices', sa.Column('discount_cents', sa.Integer(), server_default='0', nullable=False))
        if 'platform_fee_cents' not in cols:
            op.add_column('corporate_invoices', sa.Column('platform_fee_cents', sa.Integer(), server_default='0', nullable=False))
        if 'amount_paid_cents' not in cols:
            op.add_column('corporate_invoices', sa.Column('amount_paid_cents', sa.Integer(), server_default='0', nullable=False))
        if 'paid_at' not in cols:
            op.add_column('corporate_invoices', sa.Column('paid_at', sa.DateTime(timezone=True), nullable=True))
        if 'updated_at' not in cols:
            op.add_column('corporate_invoices', sa.Column('updated_at', sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=True))

    # 5. Create corporate_invoice_items table
    if 'corporate_invoice_items' not in existing_tables:
        op.create_table(
            'corporate_invoice_items',
            sa.Column('id', sa.String(36), primary_key=True),
            sa.Column('invoice_id', sa.String(36), sa.ForeignKey('corporate_invoices.id', ondelete='CASCADE'), nullable=False),
            sa.Column('ride_id', sa.String(36), sa.ForeignKey('rides.id', ondelete='SET NULL'), nullable=True),
            sa.Column('employee_id', sa.String(64), nullable=True),
            sa.Column('passenger_name', sa.String(128), nullable=True),
            sa.Column('cost_center', sa.String(64), nullable=True),
            sa.Column('fare_cents', sa.Integer(), nullable=False),
            sa.Column('tax_cents', sa.Integer(), server_default='0', nullable=False),
            sa.Column('created_at', sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=True),
        )
        op.create_index('ix_corporate_invoice_items_invoice_id', 'corporate_invoice_items', ['invoice_id'])

    # 6. Update rides table
    if 'rides' in existing_tables:
        cols = {c['name'] for c in inspector.get_columns('rides')}
        if 'corporate_id' not in cols:
            op.add_column('rides', sa.Column('corporate_id', sa.String(36), nullable=True))
            op.create_index('ix_rides_corporate_id', 'rides', ['corporate_id'])
        if 'corporate_employee_id' not in cols:
            op.add_column('rides', sa.Column('corporate_employee_id', sa.String(36), nullable=True))
        if 'corporate_approval_status' not in cols:
            op.add_column('rides', sa.Column('corporate_approval_status', sa.String(32), server_default='NOT_REQUIRED', nullable=True))

    # 7. Safe Data Migration: Extract legacy corporate notes from customer_profiles.accessibility_notes
    if 'customer_profiles' in existing_tables:
        profiles_res = conn.execute(sa.text(
            "SELECT id, user_id, accessibility_notes FROM customer_profiles WHERE accessibility_notes LIKE '%GSTIN:%'"
        )).fetchall()

        for prof in profiles_res:
            profile_id, user_id, notes = prof[0], prof[1], prof[2]
            if not notes or 'GSTIN:' not in notes:
                continue

            # Parse legacy formatted strings: GSTIN:XXX;CC:YYY;EXP:ZZZ or | format
            gstin = None
            cost_center = "DEFAULT"
            project_code = "EXP-DEFAULT"

            parts = notes.replace('|', ';').split(';')
            clean_parts = []
            for p in parts:
                kv = p.split(':')
                if len(kv) >= 2:
                    k = kv[0].strip().upper()
                    v = ':'.join(kv[1:]).strip()
                    if k == 'GSTIN':
                        gstin = v
                    elif k in ('CC', 'COST CENTER', 'COST_CENTER'):
                        cost_center = v
                    elif k in ('EXP', 'PROJECT', 'EXPENSE'):
                        project_code = v
                    else:
                        clean_parts.append(p)
                else:
                    clean_parts.append(p)

            if gstin and user_id:
                # Check if corporate account already created for this user/GSTIN
                existing_corp = conn.execute(sa.text(
                    "SELECT id FROM corporate_accounts WHERE tax_id = :tax_id OR admin_user_id = :user_id"
                ), {"tax_id": gstin, "user_id": user_id}).fetchone()

                if not existing_corp:
                    corp_id = str(uuid.uuid4())
                    now = datetime.datetime.now(datetime.timezone.utc)
                    conn.execute(sa.text("""
                        INSERT INTO corporate_accounts (
                            id, company_name, tax_id, billing_email, admin_user_id, 
                            credit_limit_cents, balance_due_cents, monthly_spending_limit_cents,
                            status, default_cost_center, created_at, updated_at
                        ) VALUES (
                            :id, :company_name, :tax_id, :billing_email, :admin_user_id,
                            50000000, 0, 10000000, 'APPROVED', :default_cost_center, :now, :now
                        )
                    """), {
                        "id": corp_id,
                        "company_name": f"Corporate ({gstin})",
                        "tax_id": gstin,
                        "billing_email": "billing@vaisentra.corp",
                        "admin_user_id": user_id,
                        "default_cost_center": cost_center,
                        "now": now
                    })

                    # Insert admin employee record
                    emp_id = str(uuid.uuid4())
                    conn.execute(sa.text("""
                        INSERT INTO corporate_employees (
                            id, corporate_id, user_id, employee_id, employee_name,
                            employee_email, department, cost_center, designation, status,
                            monthly_ride_limit, monthly_spending_limit_cents, per_ride_limit_cents,
                            approval_required, created_at, updated_at
                        ) VALUES (
                            :id, :corp_id, :user_id, 'EMP-ADMIN-01', 'Corporate Admin',
                            'admin@vaisentra.corp', 'Management', :cost_center, 'Corporate Account Owner',
                            'ACTIVE', 100, 10000000, 500000, false, :now, :now
                        )
                    """), {
                        "id": emp_id,
                        "corp_id": corp_id,
                        "user_id": user_id,
                        "cost_center": cost_center,
                        "now": now
                    })

                    # Insert default travel policy
                    pol_id = str(uuid.uuid4())
                    conn.execute(sa.text("""
                        INSERT INTO corporate_travel_policies (
                            id, corporate_id, policy_name, allowed_categories_json,
                            max_fare_per_ride_cents, monthly_ride_limit, monthly_spending_limit_cents,
                            daily_ride_limit, approval_required, is_active, created_at, updated_at
                        ) VALUES (
                            :id, :corp_id, 'Standard Corporate Mobility Policy',
                            '["SEDAN", "LUXURY_SUV", "EXECUTIVE_LIMO", "AUTO_RICKSHAW", "E_RICKSHAW"]',
                            500000, 60, 10000000, 4, false, true, :now, :now
                        )
                    """), {
                        "id": pol_id,
                        "corp_id": corp_id,
                        "now": now
                    })

            # Clean the corporate string from accessibility_notes
            remaining_notes = "; ".join([p.strip() for p in clean_parts if p.strip()]) or None
            conn.execute(sa.text(
                "UPDATE customer_profiles SET accessibility_notes = :notes WHERE id = :id"
            ), {"notes": remaining_notes, "id": profile_id})


def downgrade():
    pass
