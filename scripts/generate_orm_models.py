import json
import re

with open("scripts/extracted_schemas.json", "r", encoding="utf-8") as f:
    schemas = json.load(f)

# Helper to map SQL type to SQLAlchemy Mapped type
def map_col_type(col_name, sql_type, extra):
    is_nullable = "nullable=False" not in extra and "primary_key=True" not in extra and "primary_key" not in sql_type
    if "primary_key" in extra or "primary_key" in sql_type:
        is_nullable = False
    
    st = sql_type.lower()
    
    # Defaults and FKs
    fk_match = re.search(r"sa\.ForeignKey\(['\"]([^'\"]+)['\"]\)", extra)
    fk = f', ForeignKey("{fk_match.group(1)}")' if fk_match else ""
    
    idx = ", index=True" if "index=True" in extra else ""
    uniq = ", unique=True" if "unique=True" in extra else ""
    
    if "string" in st or "varchar" in st:
        len_match = re.search(r"\d+", st)
        length = len_match.group(0) if len_match else "255"
        base_t = f"String({length})"
        py_t = "str"
    elif "text" in st:
        base_t = "Text"
        py_t = "str"
    elif "integer" in st:
        base_t = "Integer"
        py_t = "int"
    elif "float" in st or "numeric" in st:
        base_t = "Float"
        py_t = "float"
    elif "boolean" in st:
        base_t = "Boolean"
        py_t = "bool"
    elif "datetime" in st or "timestamp" in st:
        base_t = "DateTime(timezone=True)"
        py_t = "datetime"
    else:
        base_t = "Text"
        py_t = "str"

    default_str = ""
    if "default=" in extra or "server_default=" in extra:
        d_match = re.search(r"(?:server_)?default=([^\),]+)", extra)
        if d_match:
            d_val = d_match.group(1).strip()
            if d_val == "sa.false()":
                default_str = ", default=False"
            elif d_val == "sa.true()":
                default_str = ", default=True"
            elif d_val in ('"0"', "'0'"):
                default_str = ", default=0" if py_t == "int" else ", default=False"
            elif d_val in ('"1"', "'1'"):
                default_str = ", default=1" if py_t == "int" else ", default=True"
            elif d_val == "sa.func.now()":
                default_str = ", default=utc_now"
            else:
                default_str = f", default={d_val}"
    
    if col_name == "id" and "primary_key" in (extra + sql_type):
        return f"    {col_name}: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)"
    
    if is_nullable:
        return f"    {col_name}: Mapped[Optional[{py_t}]] = mapped_column({base_t}{fk}{idx}{uniq}{default_str}, nullable=True)"
    else:
        return f"    {col_name}: Mapped[{py_t}] = mapped_column({base_t}{fk}{idx}{uniq}{default_str}, nullable=False)"

print("Helper ready")

