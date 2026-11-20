#!/usr/bin/env python3
"""Build a truthful P0-06 endpoint/scenario report from live-test artifacts.

Route-level anonymous and VIEW-mutation results come from the runtime test CSV.
Actor scenario rows are marked PASS only when their referenced JUnit test method
is present in the current full-suite Surefire XML and has no failure/error.
Uncovered actor/endpoint combinations remain explicitly NOT EXECUTED.
"""
from __future__ import annotations

import csv
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any

REPO_ROOT = Path(__file__).resolve().parents[2]
BACKEND_ROOT = Path(__file__).resolve().parents[1]
MATRIX = BACKEND_ROOT / "src/test/resources/runtime-authorization-matrix.json"
SUREFIRE = BACKEND_ROOT / "target/surefire-reports"
ROUTE_REPORT = BACKEND_ROOT / "target/p0-06-runtime-authorization-report.csv"
SUMMARY_REPORT = BACKEND_ROOT / "target/p0-06-runtime-authorization-summary.json"


def junit_results(report_dir: Path) -> dict[tuple[str, str], str]:
    results: dict[tuple[str, str], str] = {}
    if not report_dir.is_dir():
        raise FileNotFoundError(f"Surefire report directory not found: {report_dir}")
    for xml_path in sorted(report_dir.glob("TEST-*.xml")):
        try:
            root = ET.parse(xml_path).getroot()
        except (ET.ParseError, OSError) as exc:
            raise RuntimeError(f"Cannot parse Surefire report {xml_path}: {exc}") from exc
        for case in root.findall(".//testcase"):
            classname = case.attrib.get("classname", "")
            method = case.attrib.get("name", "")
            if not classname or not method:
                continue
            simple_class = classname.rsplit(".", 1)[-1]
            failed = case.find("failure") is not None or case.find("error") is not None
            skipped = case.find("skipped") is not None
            status = "FAIL" if failed else ("NOT_EXECUTED" if skipped else "PASS")
            results[(simple_class, method)] = status
    return results


def load_route_rows(path: Path) -> list[dict[str, str]]:
    if not path.is_file():
        raise FileNotFoundError(f"Runtime route CSV not found: {path}")
    with path.open("r", encoding="utf-8", newline="") as handle:
        rows = list(csv.DictReader(handle))
    if not rows:
        raise RuntimeError("Runtime route CSV contains no endpoint rows")
    return rows


def scenario_reference(reference: str) -> tuple[str, str]:
    if "#" not in reference:
        raise ValueError(f"Invalid test reference: {reference}")
    class_name, method = reference.split("#", 1)
    return class_name.rsplit(".", 1)[-1], method


def build() -> int:
    matrix = json.loads(MATRIX.read_text(encoding="utf-8"))
    tests = junit_results(SUREFIRE)
    route_rows = load_route_rows(ROUTE_REPORT)
    columns = [
        "row_type", "method", "route", "controller", "operation",
        "effective_permission", "scope", "actor", "expected_status",
        "actual_status", "result", "test_reference",
        "anonymous_expected", "anonymous_actual", "anonymous_result",
        "view_mutation_expected", "view_mutation_actual", "view_mutation_result",
        "other_actor_matrix"
    ]
    combined: list[dict[str, str]] = []

    for route in route_rows:
        row = {column: "" for column in columns}
        row.update({
            "row_type": "LIVE_ENDPOINT",
            "method": route.get("method", ""),
            "route": route.get("route", ""),
            "controller": route.get("controller", ""),
            "operation": route.get("operation", ""),
            "effective_permission": route.get("effective_permission", ""),
            "scope": route.get("scope", ""),
            "anonymous_expected": route.get("anonymous_expected", ""),
            "anonymous_actual": route.get("anonymous_actual", ""),
            "anonymous_result": route.get("anonymous_result", ""),
            "view_mutation_expected": route.get("view_mutation_expected", ""),
            "view_mutation_actual": route.get("view_mutation_actual", ""),
            "view_mutation_result": route.get("view_mutation_result", ""),
            "other_actor_matrix": "NOT_EXECUTED"
        })
        combined.append(row)

    scenario_pass = 0
    scenario_fail = 0
    scenario_unexecuted = 0
    for scenario in matrix.get("runtimeScenarios", []):
        reference = scenario.get("test", "")
        try:
            test_key = scenario_reference(reference)
        except ValueError:
            test_key = ("", "")
        test_status = tests.get(test_key, "NOT_EXECUTED")
        expected = str(scenario.get("expected", "NOT APPLICABLE"))
        actual = expected if test_status == "PASS" else (
            "ASSERTION_FAILED" if test_status == "FAIL" else "NOT_EXECUTED"
        )
        result = test_status
        scenario_pass += result == "PASS"
        scenario_fail += result == "FAIL"
        scenario_unexecuted += result == "NOT_EXECUTED"

        endpoint = scenario.get("endpoint", "")
        method, _, route = endpoint.partition(" ")
        row = {column: "" for column in columns}
        row.update({
            "row_type": "ACTOR_SCENARIO",
            "method": method,
            "route": route,
            "controller": "",
            "operation": "",
            "effective_permission": str(scenario.get("permission", "")),
            "scope": str(scenario.get("scope", "")),
            "actor": str(scenario.get("actor", "")),
            "expected_status": expected,
            "actual_status": actual,
            "result": result,
            "test_reference": reference,
            "other_actor_matrix": "Only this explicit actor/endpoint scenario is represented"
        })
        combined.append(row)

    ROUTE_REPORT.parent.mkdir(parents=True, exist_ok=True)
    with ROUTE_REPORT.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=columns)
        writer.writeheader()
        writer.writerows(combined)

    live_count = len(route_rows)
    anonymous_pass = sum(row.get("anonymous_result") == "PASS" for row in route_rows)
    view_pass = sum(row.get("view_mutation_result") == "PASS" for row in route_rows)
    view_unexecuted = sum(row.get("view_mutation_result") == "NOT_EXECUTED" for row in route_rows)
    summary: dict[str, Any] = {
        "database": "resolved by current CI matrix job",
        "liveEndpointRows": live_count,
        "anonymousRouteChecksPassed": anonymous_pass,
        "viewMutationRouteRowsPassed": view_pass,
        "viewMutationRouteRowsNotExecuted": view_unexecuted,
        "actorScenariosDeclared": len(matrix.get("runtimeScenarios", [])),
        "actorScenariosPassed": scenario_pass,
        "actorScenariosFailed": scenario_fail,
        "actorScenariosNotExecuted": scenario_unexecuted,
        "resultSemantics": "An actor scenario is PASS only when its referenced Surefire test method exists in the full suite and has no failure/error. Other actor/endpoint combinations are not inferred.",
        "files": {
            "endpointAndScenarioCsv": str(ROUTE_REPORT.relative_to(REPO_ROOT)),
            "summaryJson": str(SUMMARY_REPORT.relative_to(REPO_ROOT))
        }
    }
    SUMMARY_REPORT.write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    print("P0-06_RUNTIME_REPORT|" + json.dumps(summary, sort_keys=True))
    return 1 if scenario_fail or scenario_unexecuted else 0


if __name__ == "__main__":
    try:
        sys.exit(build())
    except Exception as exc:
        print(f"P0-06 report generation failed: {exc}", file=sys.stderr)
        sys.exit(2)
