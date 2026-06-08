import csv
import json
import re
import time
import xml.etree.ElementTree as ET
from datetime import datetime
from pathlib import Path

import requests
from bs4 import BeautifulSoup


ROOT = Path(__file__).resolve().parents[1]
OUT_DIR = ROOT / "data" / "patch_intel_init"
OUT_DIR.mkdir(parents=True, exist_ok=True)

NS = {
    "vuln": "http://www.icasi.org/CVRF/schema/vuln/1.1",
    "prod": "http://www.icasi.org/CVRF/schema/prod/1.1",
}

TARGET_PRODUCT_NAMES = {
    "Windows 10": {
        "Windows 10 Version 22H2 for x64-based Systems",
        "Windows 10 Version 22H2 for 32-bit Systems",
    },
    "Windows 11": {
        "Windows 11 Version 23H2 for x64-based Systems",
        "Windows 11 Version 24H2 for x64-based Systems",
    },
    "Windows Server 2019": {
        "Windows Server 2019",
        "Windows Server 2019 (Server Core installation)",
    },
    "Windows Server 2022": {
        "Windows Server 2022",
        "Windows Server 2022 (Server Core installation)",
        "Windows Server 2022, 23H2 Edition (Server Core installation)",
    },
}

MONTHS = [
    "2025-May",
    "2025-Apr",
    "2025-Mar",
    "2025-Feb",
    "2025-Jan",
    "2024-Dec",
    "2024-Nov",
    "2024-Oct",
    "2024-Sep",
    "2024-Aug",
]

ISSUE_KBS = [
    "5058379",
    "5058405",
    "5058411",
    "5058392",
    "5040442",
    "5041585",
    "5044285",
    "5046633",
    "5039302",
    "5039211",
    "5037768",
    "5037853",
]

MANUAL_ISSUES = [
    {
        "patch_id": "KB5058379",
        "product": "Windows 10",
        "issue_type": "Install rollback",
        "issue_title": "Citrix Session Recording Agent causes update rollback",
        "issue_summary": "Devices with certain Citrix components installed, specifically Citrix Session Recording Agent version 2411, can fail to complete installation of the January 2025 Windows security update. After restart, the update can revert with a message similar to 'Something didn’t go as planned. No need to worry – undoing changes'.",
        "affected_scope": "Organizations using Citrix Session Recording Agent 2411",
        "workaround_or_resolution": "Microsoft points to Citrix Session Recording Agent version 2503, released April 28, 2025, and newer versions as the resolution path.",
        "resolved_by": "",
        "reference_url": "https://support.microsoft.com/help/5058379",
    },
    {
        "patch_id": "KB5058379",
        "product": "Windows 10",
        "issue_type": "Display issue",
        "issue_title": "Blurry CJK text with Noto fonts at 96 DPI",
        "issue_summary": "There are reports of blurry or unclear Chinese, Japanese, and Korean text when displayed at 96 DPI in Chromium-based browsers after the Noto fallback font change.",
        "affected_scope": "All users",
        "workaround_or_resolution": "Microsoft shared findings with Google and recommends reporting Noto CJK font issues through the Google Noto Fonts GitHub repository.",
        "resolved_by": "",
        "reference_url": "https://support.microsoft.com/help/5058379",
    },
    {
        "patch_id": "KB5058405",
        "product": "Windows 11",
        "issue_type": "Boot failure",
        "issue_title": "Recovery error 0xc0000098 involving ACPI.sys",
        "issue_summary": "While installing the May 2025 Windows security update, some devices, especially virtual machines in Azure, Citrix, or Hyper-V environments, can hit recovery error 0xc0000098 citing ACPI.sys and fail to boot normally.",
        "affected_scope": "All users, mainly virtual environments",
        "workaround_or_resolution": "Microsoft says the issue is addressed in out-of-band update KB5062170 and provides recovery steps through Windows Recovery Environment or VHD mounting.",
        "resolved_by": "KB5062170",
        "reference_url": "https://support.microsoft.com/help/5058405",
    },
    {
        "patch_id": "KB5058405",
        "product": "Windows 11",
        "issue_type": "Display issue",
        "issue_title": "Noto fonts issue",
        "issue_summary": "There are reports of blurry or unclear Chinese, Japanese, and Korean text when displayed at 96 DPI in Chromium-based browsers after the Noto fallback font change.",
        "affected_scope": "All users",
        "workaround_or_resolution": "Microsoft shared findings with Google and suggests escalating through the Google Noto Fonts GitHub repository for additional support.",
        "resolved_by": "",
        "reference_url": "https://support.microsoft.com/help/5058405",
    },
    {
        "patch_id": "KB5058411",
        "product": "Windows 11",
        "issue_type": "Printing issue",
        "issue_title": "Microsoft Print to PDF might disappear or fail",
        "issue_summary": "After installing KB5055627, Microsoft Print to PDF can disappear from Printers & scanners, and enabling Printing-PrintToPDFServices-Feature can return error 0x800f0922.",
        "affected_scope": "Enterprise customers and IT admins",
        "workaround_or_resolution": "Microsoft states this issue is addressed in KB5060829 and documents temporary feature re-enable steps.",
        "resolved_by": "KB5060829",
        "reference_url": "https://support.microsoft.com/help/5058411",
    },
    {
        "patch_id": "KB5058411",
        "product": "Windows 11",
        "issue_type": "Display issue",
        "issue_title": "Noto fonts issue",
        "issue_summary": "There are reports of blurry or unclear Chinese, Japanese, and Korean text when displayed at 96 DPI in Chromium-based browsers after the Noto fallback font change.",
        "affected_scope": "All users",
        "workaround_or_resolution": "Microsoft shared findings with Google and suggests escalating through the Google Noto Fonts GitHub repository for additional support.",
        "resolved_by": "",
        "reference_url": "https://support.microsoft.com/help/5058411",
    },
    {
        "patch_id": "KB5040442",
        "product": "Windows 11",
        "issue_type": "Licensing issue",
        "issue_title": "Windows Pro to Enterprise upgrade failure",
        "issue_summary": "After installing this update or later updates, devices can have issues upgrading from Windows Pro to a valid Windows Enterprise subscription. OS upgrade operations may fail and LicenseAcquisition can show access denied error 0x80070005.",
        "affected_scope": "Enterprise users",
        "workaround_or_resolution": "Microsoft states this issue is addressed in KB5040527.",
        "resolved_by": "KB5040527",
        "reference_url": "https://support.microsoft.com/help/5040442",
    },
    {
        "patch_id": "KB5040442",
        "product": "Windows 11",
        "issue_type": "API regression",
        "issue_title": "Windows Update Agent API script failures",
        "issue_summary": "After installing this update, scripts using the Windows Update Agent API can return empty IUpdate object properties and error 0x8002802B when methods are called.",
        "affected_scope": "Enterprise users",
        "workaround_or_resolution": "Microsoft states this issue is addressed in KB5040527.",
        "resolved_by": "KB5040527",
        "reference_url": "https://support.microsoft.com/help/5040442",
    },
    {
        "patch_id": "KB5040442",
        "product": "Windows 11",
        "issue_type": "BitLocker recovery",
        "issue_title": "Unexpected BitLocker recovery screen after startup",
        "issue_summary": "After installing the July 9, 2024 Windows security update, some devices can show a BitLocker recovery screen at startup and require the recovery key from the Microsoft account to unlock the drive.",
        "affected_scope": "All users",
        "workaround_or_resolution": "Microsoft states this issue is addressed in KB5041585.",
        "resolved_by": "KB5041585",
        "reference_url": "https://support.microsoft.com/help/5040442",
    },
    {
        "patch_id": "KB5041585",
        "product": "Windows 11",
        "issue_type": "Boot failure",
        "issue_title": "Linux dual-boot startup failure after SBAT update",
        "issue_summary": "After installing this security update, devices using Windows and Linux dual-boot can fail to boot Linux and show 'Verifying shim SBAT data failed: Security Policy Violation' because the SBAT setting was applied when dual boot was not detected correctly.",
        "affected_scope": "All users",
        "workaround_or_resolution": "Microsoft says the September 2024 Windows security update KB5043076 and later updates do not contain the settings that caused this issue.",
        "resolved_by": "KB5043076",
        "reference_url": "https://support.microsoft.com/help/5041585",
    },
    {
        "patch_id": "KB5044285",
        "product": "Windows 11",
        "issue_type": "Service failure",
        "issue_title": "OpenSSH service fails to start",
        "issue_summary": "Following installation of the October 2024 security update, some devices report that the OpenSSH service fails to start, preventing SSH connections. The service can fail with no detailed logging and manual intervention is required to run sshd.exe.",
        "affected_scope": "All users",
        "workaround_or_resolution": "Microsoft states this issue is addressed in KB5052094.",
        "resolved_by": "KB5052094",
        "reference_url": "https://support.microsoft.com/help/5044285",
    },
    {
        "patch_id": "KB5046633",
        "product": "Windows 11",
        "issue_type": "Service failure",
        "issue_title": "OpenSSH service fails to start",
        "issue_summary": "Following installation of the October 2024 security update, some devices report that the OpenSSH service fails to start, preventing SSH connections. The service can fail with no detailed logging and manual intervention is required to run sshd.exe.",
        "affected_scope": "All users",
        "workaround_or_resolution": "Microsoft states this issue is addressed in KB5052094.",
        "resolved_by": "KB5052094",
        "reference_url": "https://support.microsoft.com/help/5046633",
    },
    {
        "patch_id": "KB5058392",
        "product": "Windows Server 2019",
        "issue_type": "Install rollback",
        "issue_title": "Citrix Session Recording Agent causes update rollback",
        "issue_summary": "Devices with certain Citrix components installed, specifically Citrix Session Recording Agent version 2411, can fail to complete installation of the Windows security update and revert changes after restart.",
        "affected_scope": "Organizations using Citrix Session Recording Agent 2411",
        "workaround_or_resolution": "Microsoft points to Citrix Session Recording Agent version 2503, released April 28, 2025, and newer versions as the resolution path.",
        "resolved_by": "",
        "reference_url": "https://support.microsoft.com/help/5058392",
    },
]

HEADERS = {"User-Agent": "Mozilla/5.0"}
SESSION = requests.Session()
SESSION.headers.update(HEADERS)


def to_datetime_string(date_text: str) -> str:
    if not date_text:
        return ""
    for fmt in ("%B %d, %Y", "%Y-%m-%d"):
        try:
            return datetime.strptime(date_text, fmt).strftime("%Y-%m-%d 00:00:00")
        except ValueError:
            continue
    return ""


def normalize_severity(value: str) -> str:
    if not value:
        return ""
    low = str(value).strip().lower()
    mapping = {
        "critical": "Critical",
        "high": "High",
        "important": "High",
        "medium": "Medium",
        "moderate": "Medium",
        "low": "Low",
    }
    return mapping.get(low, str(value).strip().title())


def infer_issue_severity(issue_type: str) -> str:
    mapping = {
        "Boot failure": "High",
        "BitLocker recovery": "High",
        "Install rollback": "High",
        "Service failure": "High",
        "Printing issue": "Medium",
        "API regression": "Medium",
        "Display issue": "Low",
        "Licensing issue": "Medium",
        "Known issue": "Medium",
    }
    return mapping.get(issue_type, "Medium")


def classify_exploit_status(kev_flag: int, sug_item) -> str:
    if kev_flag:
        return "Known Exploited"
    if sug_item:
        if str(sug_item.get("exploited", "")).lower() == "yes":
            return "Exploited"
        if str(sug_item.get("publiclyDisclosed", "")).lower() == "yes":
            return "Publicly Disclosed"
        latest = (sug_item.get("latestSoftwareRelease") or "").strip()
        if latest:
            return latest[:32]
    return "Not Known"


def extract_os_version(product_detail: str) -> str:
    match = re.search(r"Version ([^ ]+)", product_detail)
    if match:
        return match.group(1)
    if "Server 2019" in product_detail:
        return "2019"
    if "Server 2022" in product_detail:
        return "2022"
    return ""


def extract_os_name(product_detail: str) -> str:
    if "Windows 10" in product_detail:
        return "Windows 10"
    if "Windows 11" in product_detail:
        return "Windows 11"
    if "Windows Server 2019" in product_detail:
        return "Windows Server 2019"
    if "Windows Server 2022" in product_detail:
        return "Windows Server 2022"
    return product_detail


def get_json(url: str):
    for _ in range(3):
        try:
            r = SESSION.get(url, timeout=45)
            r.raise_for_status()
            return r.json()
        except Exception:
            time.sleep(1)
    return None


def get_text(url: str):
    for _ in range(3):
        try:
            r = SESSION.get(url, timeout=45)
            r.raise_for_status()
            return r.text
        except Exception:
            time.sleep(1)
    return ""


def normalize_product(name: str) -> str:
    for family, names in TARGET_PRODUCT_NAMES.items():
        if name in names:
            return family
    return name


def parse_month_cvrf(month: str):
    xml_text = get_text(f"https://api.msrc.microsoft.com/cvrf/v3.0/cvrf/{month}")
    root = ET.fromstring(xml_text)
    products = {
        fp.attrib.get("ProductID"): (fp.text or "").strip()
        for fp in root.findall(".//prod:FullProductName", NS)
    }

    target_ids = {
        pid: name
        for pid, name in products.items()
        if any(name in s for s in TARGET_PRODUCT_NAMES.values())
    }

    rows = []
    baseline_rows = []

    for vuln in root.findall(".//vuln:Vulnerability", NS):
        cve_id = vuln.findtext("vuln:CVE", namespaces=NS)
        if not cve_id:
            continue

        remediations = vuln.findall("vuln:Remediations/vuln:Remediation", NS)
        fixes = [r for r in remediations if r.attrib.get("Type") == "Vendor Fix"]
        releases = [r for r in remediations if r.attrib.get("Type") == "Release Notes"]

        for fix in fixes:
            kb = (fix.findtext("vuln:Description", namespaces=NS) or "").strip()
            if not re.fullmatch(r"\d{7}", kb):
                continue

            product_ids = [e.text for e in fix.findall("vuln:ProductID", NS)]
            fixed_build = (fix.findtext("vuln:FixedBuild", namespaces=NS) or "").strip()
            supercedence = (fix.findtext("vuln:Supercedence", namespaces=NS) or "").strip()
            catalog_url = (
                fix.findtext("vuln:URL", namespaces=NS)
                or f"https://catalog.update.microsoft.com/v7/site/Search.aspx?q=KB{kb}"
            ).strip()
            support_url = f"https://support.microsoft.com/help/{kb}"

            release_url = support_url
            for rel in releases:
                rel_ids = [e.text for e in rel.findall("vuln:ProductID", NS)]
                if set(product_ids) & set(rel_ids):
                    candidate = (rel.findtext("vuln:URL", namespaces=NS) or "").strip()
                    if candidate:
                        release_url = candidate
                        break

            for pid in product_ids:
                if pid not in target_ids:
                    continue
                product_name = products[pid]
                product_family = normalize_product(product_name)
                rows.append(
                    {
                        "patch_id": f"KB{kb}",
                        "cve_id": cve_id,
                        "vendor": "Microsoft",
                        "product": product_family,
                        "product_detail": product_name,
                        "release_number": month,
                        "fixed_build": fixed_build,
                        "supersedes_kb": f"KB{supercedence}" if supercedence else "",
                        "catalog_url": catalog_url,
                        "reference_url": release_url,
                    }
                )
                baseline_rows.append(
                    {
                        "product": product_family,
                        "product_detail": product_name,
                        "patch_id": f"KB{kb}",
                        "release_cycle": month,
                        "release_date": "",
                        "os_build": fixed_build,
                        "patch_type": "LCU",
                        "source_url": release_url,
                    }
                )
    return rows, baseline_rows


def fetch_nvd_details(cve_ids):
    result = {}
    for cve_id in sorted(cve_ids):
        url = f"https://services.nvd.nist.gov/rest/json/cves/2.0?cveId={cve_id}"
        data = get_json(url)
        vulns = data.get("vulnerabilities", []) if data else []
        score = ""
        severity = ""
        if vulns:
            metrics = vulns[0].get("cve", {}).get("metrics", {})
            for key in ("cvssMetricV31", "cvssMetricV30", "cvssMetricV2"):
                if key in metrics and metrics[key]:
                    metric = metrics[key][0]
                    score = metric.get("cvssData", {}).get("baseScore", "")
                    severity = metric.get("cvssData", {}).get("baseSeverity", "") or metric.get("baseSeverity", "")
                    break
        if not score or not severity:
            sug = get_json(
                f"https://api.msrc.microsoft.com/sug/v2.0/en-US/vulnerability?$filter=cveNumber eq '{cve_id}'"
            )
            value = sug.get("value", []) if sug else []
            if value:
                score = score or value[0].get("baseScore", "")
                severity = severity or value[0].get("severity", "")
        result[cve_id] = {
            "cvss_score": score,
            "severity": severity,
        }
        time.sleep(0.1)
    return result


def fetch_sug_details(cve_ids):
    result = {}
    for cve_id in sorted(cve_ids):
        sug = get_json(
            f"https://api.msrc.microsoft.com/sug/v2.0/en-US/vulnerability?$filter=cveNumber eq '{cve_id}'"
        )
        value = sug.get("value", []) if sug else []
        result[cve_id] = value[0] if value else {}
        time.sleep(0.05)
    return result


def fetch_kev_set():
    data = get_json("https://www.cisa.gov/sites/default/files/feeds/known_exploited_vulnerabilities.json")
    return {item["cveID"] for item in data.get("vulnerabilities", [])}


def fetch_release_date(kb: str) -> str:
    html = get_text(f"https://support.microsoft.com/help/{kb}")
    if not html:
        return ""
    m = re.search(r"<title>([^<]+)</title>", html, re.I)
    if not m:
        return ""
    title = m.group(1)
    m = re.search(r"([A-Z][a-z]+ \d{1,2}, \d{4})", title)
    return m.group(1) if m else ""


def classify_issue(text: str) -> str:
    low = text.lower()
    if "bitlocker" in low:
        return "BitLocker recovery"
    if "boot" in low or "startup" in low or "reboot loop" in low or "automatic repair" in low:
        return "Boot failure"
    if "openssh" in low or "ssh" in low:
        return "Service failure"
    if "print to pdf" in low or "printer" in low or "printing" in low:
        return "Printing issue"
    if "citrix" in low or "undoing changes" in low:
        return "Install rollback"
    if "blurry" in low or "font" in low:
        return "Display issue"
    if "license" in low or "enterprise" in low:
        return "Licensing issue"
    if "windows update agent api" in low:
        return "API regression"
    return "Known issue"


def detect_product_from_title(title: str) -> str:
    low = title.lower()
    if "windows 10" in low:
        return "Windows 10"
    if "windows 11" in low:
        return "Windows 11"
    if "server 2022" in low:
        return "Windows Server 2022"
    if "server 2019" in low:
        return "Windows Server 2019"
    return "Windows"


def parse_known_issues_for_kb(kb: str):
    url = f"https://support.microsoft.com/help/{kb}"
    html = get_text(url)
    if not html:
        return []
    soup = BeautifulSoup(html, "html.parser")
    title = soup.title.get_text(" ", strip=True) if soup.title else f"KB{kb}"
    text = soup.get_text("\n")
    start = text.find("Known issues in this update")
    if start == -1:
        return []
    segment = text[start:start + 14000]
    end_markers = ["How to get this update", "Before you install this update", "Install this update"]
    end_positions = [segment.find(marker) for marker in end_markers if segment.find(marker) != -1]
    if end_positions:
        segment = segment[: min(end_positions)]

    clean = segment.replace("\xa0", " ")
    issue_rows = []

    pattern = re.compile(
        r"(?:Applies to:?\s*(?P<scope>[^\n]+)\n+)?(?P<heading>[^\n]{3,120})\n+"
        r"(?:Applies to:?\s*(?P<scope2>[^\n]+)\n+)?"
        r"(?:Symptoms?|Symptom)\n+(?P<symptom>.+?)\n+"
        r"(?:Workaround|Resolution)\n+(?P<resolution>.+?)(?=\n{2,}[A-Z][^\n]{2,120}\n+(?:Applies to|Symptoms?|Symptom)|$)",
        re.S,
    )

    for match in pattern.finditer(clean):
        heading = " ".join(match.group("heading").split())
        symptom = " ".join(match.group("symptom").split())
        resolution = " ".join(match.group("resolution").split())
        scope = " ".join((match.group("scope") or match.group("scope2") or "").split())
        if heading.lower().startswith("known issues"):
            continue
        if len(symptom) < 40:
            continue
        resolved_by = ""
        kb_match = re.search(r"\bKB\d{7}\b", resolution)
        if kb_match:
            resolved_by = kb_match.group(0)
        issue_rows.append(
            {
                "patch_id": f"KB{kb}",
                "product": detect_product_from_title(title),
                "issue_type": classify_issue(f"{heading} {symptom}"),
                "issue_title": heading,
                "issue_summary": symptom[:1200],
                "affected_scope": scope,
                "workaround_or_resolution": resolution[:1200],
                "resolved_by": resolved_by,
                "reference_url": url,
            }
        )
    return issue_rows


def write_csv(path: Path, rows, fieldnames):
    with path.open("w", newline="", encoding="utf-8-sig") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def write_sql(path: Path, table: str, rows, fieldnames):
    def esc(value):
        if value is None or value == "":
            return "NULL"
        value = str(value)
        return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"

    lines = [f"INSERT INTO {table} ({', '.join(fieldnames)}) VALUES"]
    values = []
    for row in rows:
        values.append("(" + ", ".join(esc(row.get(k, "")) for k in fieldnames) + ")")
    content = ",\n".join(values) + ";\n"
    path.write_text(lines[0] + "\n" + content, encoding="utf-8")


def dedupe_rows(rows, keys):
    seen = set()
    out = []
    for row in rows:
        key = tuple(row.get(k, "") for k in keys)
        if key in seen:
            continue
        seen.add(key)
        out.append(row)
    return out


def main():
    patch_rows = []
    baseline_rows = []
    release_dates = {}

    for month in MONTHS:
        month_patch_rows, month_baseline_rows = parse_month_cvrf(month)
        patch_rows.extend(month_patch_rows)
        baseline_rows.extend(month_baseline_rows)

    patch_rows = dedupe_rows(
        patch_rows,
        ["patch_id", "cve_id", "product_detail"],
    )

    selected_patch_rows = patch_rows[:100]
    cve_ids = {row["cve_id"] for row in selected_patch_rows}
    kev_set = fetch_kev_set()
    nvd_map = fetch_nvd_details(cve_ids)
    sug_map = fetch_sug_details(cve_ids)

    final_patch_rows = []
    for row in selected_patch_rows:
        extra = nvd_map.get(row["cve_id"], {})
        sug_item = sug_map.get(row["cve_id"], {})
        kev_flag = 1 if row["cve_id"] in kev_set else 0
        affected_range = row["product_detail"]
        if row.get("supersedes_kb"):
            affected_range = f"{row['product_detail']} before {row['supersedes_kb']}"
        final_patch_rows.append(
            {
                "patch_id": row["patch_id"],
                "cve_id": row["cve_id"],
                "vendor": row["vendor"],
                "product": row["product"],
                "affected_version_range": affected_range,
                "fixed_version": row.get("fixed_build", ""),
                "fix_type": "Security Update",
                "exploit_status": classify_exploit_status(kev_flag, sug_item),
                "kev_flag": kev_flag,
                "cvss_score": extra.get("cvss_score", ""),
                "severity": normalize_severity(extra.get("severity", "")),
                "reference_url": row["reference_url"],
            }
        )

    baseline_rows = dedupe_rows(
        baseline_rows,
        ["product_detail", "patch_id"],
    )
    family_seen = {family: 0 for family in TARGET_PRODUCT_NAMES}
    selected_baseline_rows = []
    for row in baseline_rows:
        family = row["product"]
        if family not in family_seen:
            continue
        if family_seen[family] >= 10:
            continue
        kb = row["patch_id"].replace("KB", "")
        if kb not in release_dates:
            release_dates[kb] = fetch_release_date(kb)
        os_name = extract_os_name(row["product_detail"])
        selected_baseline_rows.append(
            {
                "os_family": "Windows",
                "os_name": os_name,
                "os_version": extract_os_version(row["product_detail"]),
                "os_build": row["os_build"],
                "product_name": row["product_detail"],
                "baseline_patch_id": row["patch_id"],
                "patch_release_date": to_datetime_string(release_dates[kb]),
                "is_latest": 1 if family_seen[family] == 0 else 0,
                "is_security_baseline": 1,
                "reference_url": row["source_url"],
            }
        )
        family_seen[family] += 1
        if sum(family_seen.values()) >= 40:
            break

    issue_rows = []
    for kb in ISSUE_KBS:
        issue_rows.extend(parse_known_issues_for_kb(kb))
    issue_rows.extend(MANUAL_ISSUES)
    issue_rows = dedupe_rows(issue_rows, ["patch_id", "issue_title"])
    selected_issue_rows = issue_rows[:20]
    for row in selected_issue_rows:
        kb = row["patch_id"].replace("KB", "")
        if kb not in release_dates:
            release_dates[kb] = fetch_release_date(kb)

    final_issue_rows = []
    for row in selected_issue_rows:
        vendor_notice_id = row["patch_id"]
        description = row.get("issue_summary", "")
        title = row.get("issue_title", "")
        if title and title.lower() not in description.lower():
            description = f"{title}: {description}"
        final_issue_rows.append(
            {
                "patch_id": row["patch_id"],
                "vendor_notice_id": vendor_notice_id,
                "issue_type": row["issue_type"],
                "affected_scope": row.get("affected_scope", ""),
                "description": description,
                "severity": infer_issue_severity(row["issue_type"]),
                "workaround": row.get("workaround_or_resolution", ""),
                "publish_time": to_datetime_string(release_dates.get(row["patch_id"].replace("KB", ""), "")),
                "reference_url": row["reference_url"],
            }
        )

    patch_fields = [
        "patch_id",
        "cve_id",
        "vendor",
        "product",
        "affected_version_range",
        "fixed_version",
        "fix_type",
        "exploit_status",
        "kev_flag",
        "cvss_score",
        "severity",
        "reference_url",
    ]
    issue_fields = [
        "patch_id",
        "vendor_notice_id",
        "issue_type",
        "affected_scope",
        "description",
        "severity",
        "workaround",
        "publish_time",
        "reference_url",
    ]
    baseline_fields = [
        "os_family",
        "os_name",
        "os_version",
        "os_build",
        "product_name",
        "baseline_patch_id",
        "patch_release_date",
        "is_latest",
        "is_security_baseline",
        "reference_url",
    ]

    write_csv(OUT_DIR / "patch_cve_map.csv", final_patch_rows, patch_fields)
    write_csv(OUT_DIR / "patch_issue_intel.csv", final_issue_rows, issue_fields)
    write_csv(OUT_DIR / "patch_baseline.csv", selected_baseline_rows, baseline_fields)

    write_sql(OUT_DIR / "patch_cve_map.sql", "patch_cve_map", final_patch_rows, patch_fields)
    write_sql(OUT_DIR / "patch_issue_intel.sql", "patch_issue_intel", final_issue_rows, issue_fields)
    write_sql(OUT_DIR / "patch_baseline.sql", "patch_baseline", selected_baseline_rows, baseline_fields)

    summary = {
        "patch_cve_map_count": len(final_patch_rows),
        "patch_issue_intel_count": len(final_issue_rows),
        "patch_baseline_count": len(selected_baseline_rows),
        "output_dir": str(OUT_DIR),
    }
    (OUT_DIR / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
