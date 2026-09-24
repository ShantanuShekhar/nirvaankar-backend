#!/usr/bin/env python3
"""Generate India geo master seed SQL for geo_districts / geo_localities / geo_pincodes."""

from __future__ import annotations

import csv
import re
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CSV_PATH = ROOT / "scripts" / "india_pincodes_source.csv"
OUT_PATH = ROOT / "src" / "main" / "resources" / "db" / "manual" / "INDIA_GEO_FULL_SEED.sql"

STATE_MAP = {
    "ANDAMAN & NICOBAR ISLANDS": "Andaman and Nicobar Islands",
    "ANDHRA PRADESH": "Andhra Pradesh",
    "ARUNACHAL PRADESH": "Arunachal Pradesh",
    "ASSAM": "Assam",
    "BIHAR": "Bihar",
    "CHANDIGARH": "Chandigarh",
    "CHATTISGARH": "Chhattisgarh",
    "CHHATTISGARH": "Chhattisgarh",
    "DADRA & NAGAR HAVELI": "Dadra and Nagar Haveli and Daman and Diu",
    "DAMAN & DIU": "Dadra and Nagar Haveli and Daman and Diu",
    "DELHI": "Delhi",
    "GOA": "Goa",
    "GUJARAT": "Gujarat",
    "HARYANA": "Haryana",
    "HIMACHAL PRADESH": "Himachal Pradesh",
    "JAMMU & KASHMIR": "Jammu and Kashmir",
    "JHARKHAND": "Jharkhand",
    "KARNATAKA": "Karnataka",
    "KERALA": "Kerala",
    "LADAKH": "Ladakh",
    "LAKSHADWEEP": "Lakshadweep",
    "LAKSHDWEEP": "Lakshadweep",
    "MADHYA PRADESH": "Madhya Pradesh",
    "MAHARASHTRA": "Maharashtra",
    "MANIPUR": "Manipur",
    "MEGHALAYA": "Meghalaya",
    "MIZORAM": "Mizoram",
    "NAGALAND": "Nagaland",
    "ODISHA": "Odisha",
    "ORISSA": "Odisha",
    "PONDICHERRY": "Puducherry",
    "PUNJAB": "Punjab",
    "RAJASTHAN": "Rajasthan",
    "SIKKIM": "Sikkim",
    "TAMIL NADU": "Tamil Nadu",
    "TELANGANA": "Telangana",
    "TRIPURA": "Tripura",
    "UTTAR PRADESH": "Uttar Pradesh",
    "UTTARAKHAND": "Uttarakhand",
    "UTTARANCHAL": "Uttarakhand",
    "WEST BENGAL": "West Bengal",
}

STATE_CODES = {
    "Andaman and Nicobar Islands": "AN",
    "Andhra Pradesh": "AP",
    "Arunachal Pradesh": "AR",
    "Assam": "AS",
    "Bihar": "BR",
    "Chandigarh": "CH",
    "Chhattisgarh": "CG",
    "Dadra and Nagar Haveli and Daman and Diu": "DN",
    "Delhi": "DL",
    "Goa": "GA",
    "Gujarat": "GJ",
    "Haryana": "HR",
    "Himachal Pradesh": "HP",
    "Jammu and Kashmir": "JK",
    "Jharkhand": "JH",
    "Karnataka": "KA",
    "Kerala": "KL",
    "Ladakh": "LA",
    "Lakshadweep": "LD",
    "Madhya Pradesh": "MP",
    "Maharashtra": "MH",
    "Manipur": "MN",
    "Meghalaya": "ML",
    "Mizoram": "MZ",
    "Nagaland": "NL",
    "Odisha": "OR",
    "Puducherry": "PY",
    "Punjab": "PB",
    "Rajasthan": "RJ",
    "Sikkim": "SK",
    "Tamil Nadu": "TN",
    "Telangana": "TS",
    "Tripura": "TR",
    "Uttar Pradesh": "UP",
    "Uttarakhand": "UK",
    "West Bengal": "WB",
}

OFFICE_RANK = {"H.O": 0, "S.O": 1, "B.O": 2}


def sql_escape(value: str) -> str:
    return value.replace("\\", "\\\\").replace("'", "''")


def clean_text(value: str | None, max_len: int) -> str:
    if not value:
        return ""
    text = re.sub(r"\s+", " ", value.strip())
    if text.upper() in {"NA", "NULL", "N/A", "-"}:
        return ""
    return text[:max_len]


def normalize_state(raw: str | None) -> str | None:
    if not raw:
        return None
    key = raw.strip().upper()
    if key in {"NULL", "NA", ""}:
        return None
    return STATE_MAP.get(key, raw.strip().title())


def locality_type(office_type: str) -> str:
    t = (office_type or "").strip().upper()
    if t == "B.O":
        return "VILLAGE"
    if t == "H.O":
        return "CITY"
    return "TOWN"


def office_score(row: dict[str, str]) -> tuple:
    office_type = (row.get("officeType") or "").strip().upper()
    delivery = (row.get("Deliverystatus") or "").strip().upper()
    return (
        OFFICE_RANK.get(office_type, 9),
        0 if delivery == "DELIVERY" else 1,
        len(clean_text(row.get("Taluk"), 150)),
    )


def pick_locality_name(row: dict[str, str]) -> str:
    taluk = clean_text(row.get("Taluk"), 150)
    district = clean_text(row.get("Districtname"), 120)
    office = clean_text(row.get("officename"), 150)
    office = re.sub(r"\s+(B\.O|S\.O|H\.O)$", "", office, flags=re.IGNORECASE).strip()
    if taluk:
        return taluk
    if district:
        return district
    return office or "Unknown"


def load_best_pincode_rows() -> list[dict[str, str]]:
    by_pin: dict[str, dict[str, str]] = {}
    with CSV_PATH.open(newline="", encoding="utf-8", errors="replace") as handle:
        reader = csv.DictReader(handle)
        for row in reader:
            pin = (row.get("pincode") or "").strip()
            if not re.fullmatch(r"[1-9][0-9]{5}", pin):
                continue
            state = normalize_state(row.get("statename"))
            if not state:
                continue
            district = clean_text(row.get("Districtname"), 120)
            if not district:
                continue
            current = by_pin.get(pin)
            if current is None or office_score(row) < office_score(current):
                by_pin[pin] = row
    return list(by_pin.values())


def build_seed_rows(rows: list[dict[str, str]]) -> list[tuple[str, str, str, str, str, bool]]:
    seed: list[tuple[str, str, str, str, str, bool]] = []
    for row in rows:
        state = normalize_state(row.get("statename"))
        if not state:
            continue
        district = clean_text(row.get("Districtname"), 120)
        locality = pick_locality_name(row)
        pin = row["pincode"].strip()
        ltype = locality_type(row.get("officeType") or "")
        serviceable = (row.get("Deliverystatus") or "").strip().upper() == "DELIVERY"
        seed.append((state, district, locality, ltype, pin, serviceable))
    return sorted(seed, key=lambda item: (item[0], item[1], item[2], item[4]))


def write_sql(seed_rows: list[tuple[str, str, str, str, str, bool]]) -> None:
    states = sorted({row[0] for row in seed_rows})
    OUT_PATH.parent.mkdir(parents=True, exist_ok=True)

    lines: list[str] = [
        "-- =============================================================================",
        "-- India geo master full seed (districts, localities, pincodes)",
        "-- Generated by scripts/generate_india_geo_sql.py",
        "-- Source: India Post open directory (saravanakumargn/All-India-Pincode-Directory)",
        "--",
        "-- Prerequisite: run Flyway migration V6_023__geo_location_masters.sql first.",
        "-- Safe to re-run: uses NOT EXISTS guards; does not delete existing rows.",
        "--",
        f"-- Rows: {len(seed_rows)} unique pincodes | States: {len(states)}",
        "-- =============================================================================",
        "",
        "SET NAMES utf8mb4;",
        "SET FOREIGN_KEY_CHECKS = 0;",
        "",
        "-- Ensure India exists",
        "INSERT INTO geo_countries (iso2, iso3, name, phone_code, is_active)",
        "SELECT 'IN', 'IND', 'India', '+91', TRUE",
        "WHERE NOT EXISTS (SELECT 1 FROM geo_countries WHERE iso2 = 'IN');",
        "",
        "-- Insert any missing states found in the dataset",
    ]

    for state in states:
        code = STATE_CODES.get(state, None)
        code_sql = f"'{sql_escape(code)}'" if code else "NULL"
        lines.extend([
            "INSERT INTO geo_states (country_id, code, name, is_active)",
            f"SELECT c.id, {code_sql}, '{sql_escape(state)}', TRUE",
            "FROM geo_countries c",
            "WHERE c.iso2 = 'IN'",
            f"  AND NOT EXISTS (SELECT 1 FROM geo_states s WHERE s.country_id = c.id AND s.name = '{sql_escape(state)}');",
            "",
        ])

    lines.extend([
        "DROP TABLE IF EXISTS _geo_seed_staging;",
        "CREATE TABLE _geo_seed_staging (",
        "    state_name      VARCHAR(100) NOT NULL,",
        "    district_name   VARCHAR(120) NOT NULL,",
        "    locality_name   VARCHAR(150) NOT NULL,",
        "    locality_type   VARCHAR(20)  NOT NULL,",
        "    pincode         CHAR(6)      NOT NULL,",
        "    is_serviceable  BOOLEAN      NOT NULL,",
        "    PRIMARY KEY (pincode)",
        "    KEY idx_geo_seed_loc (state_name, district_name, locality_name)",
        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;",
        "",
    ])

    batch_size = 400
    for i in range(0, len(seed_rows), batch_size):
        batch = seed_rows[i : i + batch_size]
        lines.append(
            "INSERT INTO _geo_seed_staging "
            "(state_name, district_name, locality_name, locality_type, pincode, is_serviceable) VALUES"
        )
        value_lines = []
        for state, district, locality, ltype, pin, serviceable in batch:
            value_lines.append(
                f"('{sql_escape(state)}', '{sql_escape(district)}', '{sql_escape(locality)}', "
                f"'{sql_escape(ltype)}', '{pin}', {1 if serviceable else 0})"
            )
        lines.append(",\n".join(value_lines) + ";")
        lines.append("")

    lines.extend([
        "-- Districts",
        "INSERT INTO geo_districts (state_id, name, is_active)",
        "SELECT s.id, st.district_name, TRUE",
        "FROM (SELECT DISTINCT state_name, district_name FROM _geo_seed_staging) st",
        "JOIN geo_states s ON s.name = st.state_name",
        "JOIN geo_countries c ON c.id = s.country_id AND c.iso2 = 'IN'",
        "WHERE NOT EXISTS (",
        "    SELECT 1 FROM geo_districts d WHERE d.state_id = s.id AND d.name = st.district_name",
        ");",
        "",
        "-- Localities (city/town/village)",
        "INSERT INTO geo_localities (district_id, name, locality_type, is_active)",
        "SELECT d.id, st.locality_name, st.locality_type, TRUE",
        "FROM (",
        "    SELECT DISTINCT state_name, district_name, locality_name, locality_type",
        "    FROM _geo_seed_staging",
        ") st",
        "JOIN geo_states s ON s.name = st.state_name",
        "JOIN geo_districts d ON d.state_id = s.id AND d.name = st.district_name",
        "WHERE NOT EXISTS (",
        "    SELECT 1 FROM geo_localities l",
        "    WHERE l.district_id = d.id AND l.name = st.locality_name",
        ");",
        "",
        "-- Pincodes (one row per pincode)",
        "INSERT INTO geo_pincodes (locality_id, pincode, is_serviceable, is_active)",
        "SELECT l.id, st.pincode, st.is_serviceable, TRUE",
        "FROM _geo_seed_staging st",
        "JOIN geo_states s ON s.name = st.state_name",
        "JOIN geo_districts d ON d.state_id = s.id AND d.name = st.district_name",
        "JOIN geo_localities l ON l.district_id = d.id AND l.name = st.locality_name",
        "WHERE NOT EXISTS (SELECT 1 FROM geo_pincodes p WHERE p.pincode = st.pincode);",
        "",
        "DROP TABLE IF EXISTS _geo_seed_staging;",
        "SET FOREIGN_KEY_CHECKS = 1;",
        "",
        "-- Verification (optional):",
        "-- SELECT COUNT(*) AS districts FROM geo_districts;",
        "-- SELECT COUNT(*) AS localities FROM geo_localities;",
        "-- SELECT COUNT(*) AS pincodes FROM geo_pincodes;",
        "",
    ])

    OUT_PATH.write_text("\n".join(lines), encoding="utf-8")


def main() -> None:
    rows = load_best_pincode_rows()
    seed_rows = build_seed_rows(rows)
    write_sql(seed_rows)
    print(f"Generated {len(seed_rows)} pincode rows -> {OUT_PATH}")
    print(f"File size: {OUT_PATH.stat().st_size / (1024 * 1024):.2f} MB")


if __name__ == "__main__":
    main()
