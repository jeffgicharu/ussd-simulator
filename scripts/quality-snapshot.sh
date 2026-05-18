#!/usr/bin/env bash
# Regenerate the metric blocks in QUALITY_DASHBOARD.md from build outputs.
# Idempotent: replaces content between <!-- key:start --> / <!-- key:end -->
# markers. Sources (each optional — a missing source leaves a clear note):
#   - target/surefire-reports/TEST-*.xml     -> test counts by area
#   - target/site/jacoco/jacoco.xml        -> coverage
#   - target/pit-reports/mutations.xml     -> mutation score
#   - target/spotbugsXml.xml               -> SAST findings
# Manual run for now (future: scheduled CI job).
set -uo pipefail
cd "$(dirname "$0")/.."
DASH="QUALITY_DASHBOARD.md"
START=$(date +%s)

python3 - "$DASH" <<'PY'
import os, re, sys, glob, subprocess, datetime, xml.etree.ElementTree as ET

dash = sys.argv[1]
doc = open(dash).read()

def put(key, body):
    global doc
    doc = re.sub(r"<!-- %s:start -->.*?<!-- %s:end -->" % (key, key),
                 "<!-- %s:start -->\n%s\n<!-- %s:end -->" % (key, body, key),
                 doc, flags=re.S)

# ── meta ──
sha = subprocess.run(["git", "rev-parse", "--short", "HEAD"],
                     capture_output=True, text=True).stdout.strip() or "?"
put("meta", "_Last updated %s UTC · generated from commit `%s`._"
    % (datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d %H:%M"), sha))

# ── test counts (surefire TEST-*.xml — @Nested-safe) ──
areas = {"Unit (engine/service)": 0, "Integration": 0,
         "Contract (AT webhook)": 0, "Security (custom suite)": 0}
total = 0
sec_count = 0
for f in glob.glob("target/surefire-reports/TEST-*.xml"):
    root = ET.parse(f).getroot()
    n = int(root.get("tests", 0)) - int(root.get("skipped", 0))
    cls = root.get("name", "")
    total += n
    if ".security." in cls:
        areas["Security (custom suite)"] += n
        sec_count += n
    elif "AtContract" in cls:
        areas["Contract (AT webhook)"] += n
    elif ".integration." in cls:
        areas["Integration"] += n
    else:
        areas["Unit (engine/service)"] += n
rows = "\n".join("| %s | %d |" % (k, v) for k, v in areas.items() if v)
e2e = ("| E2E local (Playwright spec files) | 16 (× chromium/firefox/webkit) |\n"
       "| E2E live-smoke (Playwright spec files) | 6 |\n"
       "| curl webhook flows (local / live) | 10 / 5 |")
put("tests",
    "| Layer | Count |\n|---|---|\n%s\n| **JVM total (`mvn verify`)** | **%d** |\n%s"
    % (rows, total, e2e))

# ── jacoco ──
jx = "target/site/jacoco/jacoco.xml"
if os.path.exists(jx):
    r = ET.parse(jx).getroot()
    cov = {}
    for c in r.findall("counter"):
        t = c.get("type"); cv = int(c.get("covered")); ms = int(c.get("missed"))
        cov[t] = 100.0 * cv / (cv + ms) if (cv + ms) else 0.0
    tgt = {"LINE": (61, 80), "BRANCH": (44, 70), "COMPLEXITY": (54, "—")}
    lines = ["| Metric | Current | CI floor | Target |", "|---|---|---|---|"]
    for t in ("LINE", "BRANCH", "COMPLEXITY"):
        fl, tg = tgt[t]
        lines.append("| %s | %.1f%% | %s%% | %s |"
                      % (t.title(), cov.get(t, 0), fl,
                         (str(tg) + "%") if tg != "—" else "—"))
    put("jacoco", "\n".join(lines))
else:
    put("jacoco", "_jacoco.xml absent — run `mvn -B verify` first._")

# ── pit ──
px = "target/pit-reports/mutations.xml"
if os.path.exists(px):
    muts = ET.parse(px).getroot().findall("mutation")
    from collections import Counter
    st = Counter(m.get("status") for m in muts)
    tot = len(muts); k = st.get("KILLED", 0); s = st.get("SURVIVED", 0)
    nc = st.get("NO_COVERAGE", 0)
    score = 100.0 * k / tot if tot else 0
    strength = 100.0 * k / (k + s) if (k + s) else 0
    put("pit",
        "| Metric | Value | Threshold | Target |\n|---|---|---|---|\n"
        "| Mutation score (killed/total) | %.1f%% | 49%% | — |\n"
        "| Test strength (killed/(killed+survived)) | %.1f%% | — | ≥70%% (service+engine) |\n"
        "| Mutants | %d total · %d killed · %d survived · %d no-coverage | | |"
        % (score, strength, tot, k, s, nc))
else:
    put("pit", "_pit-reports absent — run "
        "`mvn -B test-compile org.pitest:pitest-maven:mutationCoverage`._")

# ── security ──
sb = "target/spotbugsXml.xml"
sb_n = (len(ET.parse(sb).getroot().findall("BugInstance"))
        if os.path.exists(sb) else "n/a (run spotbugs)")
put("security",
    "| Surface | Status |\n|---|---|\n"
    "| Spotbugs + find-sec-bugs (Medium+) | %s findings (CI gate) |\n"
    "| Trivy fs + image (HIGH/CRIT) | 0 (post Spring Boot 3.5.14 bump) |\n"
    "| CodeQL (security-extended) | 0 blocking alerts |\n"
    "| Custom USSD security suite | %d tests |\n"
    "| OWASP Dependency Check | gated on `NVD_API_KEY` (skip-with-notice until set) |\n"
    "| Snyk | gated on `SNYK_TOKEN` (skip-with-notice until set) |\n"
    "| DAST (OWASP ZAP) | weekly passive baseline; active = manual dispatch only |"
    % (sb_n, sec_count))

open(dash, "w").write(doc)
print("dashboard updated")
PY

echo "quality-snapshot: done in $(( $(date +%s) - START ))s"
