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
NO_PERMISSION_REPORT = BACKEND_ROOT / "target/p0-06-runtime-no-permission-http.csv"
ACTOR_ENDPOINT_REPORT = BACKEND_ROOT / "target/p0-06-runtime-actor-endpoint-http.csv"
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


def load_route_rows(path: Path, missing_inputs: list[str]) -> list[dict[str, str]]:
    # Keep producing a truthful partial report after an earlier test failure.
    # Missing evidence is recorded explicitly and remains a release blocker;
    # it is never converted into PASS or silently treated as zero failures.
    if not path.is_file():
        missing_inputs.append(str(path.relative_to(REPO_ROOT)))
        return []
    with path.open("r", encoding="utf-8", newline="") as handle:
        return list(csv.DictReader(handle))


def normalize_route(value: str) -> str:
    path = value.split("?", 1)[0].rstrip("/") or "/"
    parts = []
    for segment in path.split("/"):
        if len(segment) > 1 and segment.startswith("{") and segment.endswith("}"):
            parts.append("{}")
        else:
            parts.append(segment)
    return "/".join(parts)


def scenario_reference(reference: str) -> tuple[str, str]:
    if "#" not in reference:
        raise ValueError(f"Invalid test reference: {reference}")
    class_name, method = reference.split("#", 1)
    return class_name.rsplit(".", 1)[-1], method


def build() -> int:
    matrix = json.loads(MATRIX.read_text(encoding="utf-8"))
    tests = junit_results(SUREFIRE)
    missing_inputs: list[str] = []
    route_rows = load_route_rows(ROUTE_REPORT, missing_inputs)
    missing_permission_rows = load_route_rows(NO_PERMISSION_REPORT, missing_inputs)
    actor_endpoint_rows = load_route_rows(ACTOR_ENDPOINT_REPORT, missing_inputs)
    actor_endpoint_by_route = {
        (row.get("method", ""), normalize_route(row.get("route", ""))): row
        for row in actor_endpoint_rows
    }
    missing_permission_by_route = {
        (row.get("method", ""), normalize_route(row.get("route", ""))): row
        for row in missing_permission_rows
    }
    columns = [
        "row_type", "method", "route", "controller", "operation",
        "effective_permission", "scope", "actor", "expected_status",
        "actual_status", "result", "test_reference",
        "anonymous_expected", "anonymous_actual", "anonymous_result",
        "view_mutation_expected", "view_mutation_actual", "view_mutation_result",
        "view_mutation_evidence",
        "missing_permission_expected", "missing_permission_actual",
        "missing_permission_result", "missing_permission_evidence",
        "runtime_actor", "runtime_actor_expected", "runtime_actor_actual",
        "runtime_actor_result", "runtime_actor_coverage_result", "runtime_actor_evidence", "runtime_actor_test_reference",
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
            "view_mutation_evidence": route.get("view_mutation_evidence", ""),
            "missing_permission_expected": "",
            "missing_permission_actual": "",
            "missing_permission_result": "NOT_EXECUTED",
            "missing_permission_evidence": "PERSISTED_USER_HTTP",
            "runtime_actor": "",
            "runtime_actor_expected": "",
            "runtime_actor_actual": "",
            "runtime_actor_result": "NOT_EXECUTED",
            "runtime_actor_coverage_result": "NOT_EXECUTED",
            "runtime_actor_evidence": "PERSISTED_ACTOR_HTTP",
            "runtime_actor_test_reference": "PosTest#p006EveryApplicableLiveEndpointExecutesWithMatchingPersistedActor",
            "other_actor_matrix": "NOT_EXECUTED"
        })
        combined.append(row)

    scenario_pass = 0
    scenario_fail = 0
    scenario_unexecuted = 0
    actor_coverage: dict[tuple[str, str], list[str]] = {}
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
        if method and route.startswith("/"):
            key = (method, normalize_route(route))
            actor_coverage.setdefault(key, []).append(
                f"{scenario.get('actor', 'UNKNOWN')}:{result}[{actual}]"
            )
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

    for item in matrix.get("notApplicableResources", []):
        row = {column: "" for column in columns}
        row.update({
            "row_type": "NOT_APPLICABLE_RESOURCE",
            "method": "",
            "route": str(item.get("resource", "")),
            "controller": "",
            "operation": "",
            "effective_permission": "",
            "scope": str(item.get("reason", "")),
            "actor": str(item.get("actor", "")),
            "expected_status": "NOT APPLICABLE",
            "actual_status": "NOT APPLICABLE",
            "result": "NOT APPLICABLE",
            "test_reference": "NO_CUSTOMER_REFUND_HISTORY_ENDPOINT_MAPPED",
            "other_actor_matrix": "NOT APPLICABLE"
        })
        combined.append(row)

    for row in combined:
        if row.get("row_type") != "LIVE_ENDPOINT":
            continue
        key = (row.get("method", ""), normalize_route(row.get("route", "")))
        no_permission = missing_permission_by_route.get(key)
        actor_endpoint = actor_endpoint_by_route.get(key)
        if key[0] == "ANY":
            no_permission = no_permission or missing_permission_by_route.get(("GET", key[1]))
            actor_endpoint = actor_endpoint or actor_endpoint_by_route.get(("GET", key[1]))
        if no_permission is not None:
            row["missing_permission_expected"] = no_permission.get("expected_status", "")
            row["missing_permission_actual"] = no_permission.get("actual_status", "")
            row["missing_permission_result"] = no_permission.get("result", "")
        if actor_endpoint is not None:
            row["runtime_actor"] = actor_endpoint.get("actor", "")
            row["runtime_actor_expected"] = actor_endpoint.get("expected", "")
            row["runtime_actor_actual"] = actor_endpoint.get("actual", "")
            row["runtime_actor_result"] = actor_endpoint.get("result", "")
            row["runtime_actor_evidence"] = "PERSISTED_ACTOR_HTTP"
            row["runtime_actor_test_reference"] = actor_endpoint.get("test_reference", "")
        explicit_scenarios = actor_coverage.get(key, [])
        explicit_scenario_pass = any(item.startswith(" ") is False and ":PASS[" in item for item in explicit_scenarios)
        generic_actor_pass = actor_endpoint is not None and actor_endpoint.get("result") == "PASS"
        row["runtime_actor_coverage_result"] = "PASS" if generic_actor_pass or explicit_scenario_pass else (
            actor_endpoint.get("result", "NOT_EXECUTED") if actor_endpoint is not None else "NOT_EXECUTED"
        )
        row["other_actor_matrix"] = ";".join(explicit_scenarios) or "NOT_EXECUTED"

    ROUTE_REPORT.parent.mkdir(parents=True, exist_ok=True)
    with ROUTE_REPORT.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=columns)
        writer.writeheader()
        writer.writerows(combined)

    live_count = len(route_rows)
    route_rows_with_actor_scenario = sum(
        row.get("row_type") == "LIVE_ENDPOINT"
        and row.get("runtime_actor_coverage_result") == "PASS"
        for row in combined
    )
    route_rows_without_actor_scenario = live_count - route_rows_with_actor_scenario
    public_route_rows_not_applicable = sum(
        row.get("row_type") == "LIVE_ENDPOINT" and row.get("anonymous_expected") == "PUBLIC"
        for row in combined
    )
    anonymous_pass = sum(row.get("anonymous_result") == "PASS" for row in route_rows)
    anonymous_fail = sum(row.get("anonymous_result") == "FAIL" for row in route_rows)
    view_pass = sum(row.get("view_mutation_result") == "PASS" for row in route_rows)
    view_fail = sum(row.get("view_mutation_result") == "FAIL" for row in route_rows)
    view_unexecuted = sum(row.get("view_mutation_result") == "NOT_EXECUTED" for row in route_rows)
    missing_permission_pass = sum(row.get("result") == "PASS" for row in missing_permission_rows)
    missing_permission_fail = sum(row.get("result") == "FAIL" for row in missing_permission_rows)
    missing_permission_unexecuted = sum(row.get("result") == "NOT_EXECUTED" for row in missing_permission_rows)
    missing_permission_route_unexecuted = sum(
        row.get("row_type") == "LIVE_ENDPOINT" and row.get("missing_permission_result") == "NOT_EXECUTED"
        for row in combined
    )
    actor_endpoint_pass = sum(row.get("result") == "PASS" for row in actor_endpoint_rows)
    actor_endpoint_business_rejections = sum(row.get("result") == "AUTHORIZED_REJECTED" for row in actor_endpoint_rows)
    actor_endpoint_fail = sum(row.get("result") == "FAIL" for row in actor_endpoint_rows)
    actor_endpoint_unexecuted = sum(row.get("result") == "NOT_EXECUTED" for row in actor_endpoint_rows)
    actor_endpoint_route_unexecuted = sum(
        row.get("row_type") == "LIVE_ENDPOINT"
        and row.get("anonymous_expected") == "401"
        and row.get("runtime_actor_coverage_result") != "PASS"
        for row in combined
    )
    summary: dict[str, Any] = {
        "database": "resolved by current CI matrix job",
        "liveEndpointRows": live_count,
        "liveEndpointRowsWithSuccessfulPersistedActorEvidence": route_rows_with_actor_scenario,
        "liveEndpointRowsWithoutExplicitActorScenario": route_rows_without_actor_scenario,
        "publicEndpointRowsNotApplicableToProtectedActorMatrix": public_route_rows_not_applicable,
        "anonymousRouteChecksPassed": anonymous_pass,
        "anonymousRouteChecksFailed": anonymous_fail,
        "viewMutationRouteRowsPassed": view_pass,
        "viewMutationRouteRowsFailed": view_fail,
        "viewMutationRouteRowsNotExecuted": view_unexecuted,
        "missingPermissionHttpChecksPassed": missing_permission_pass,
        "missingPermissionHttpChecksFailed": missing_permission_fail,
        "missingPermissionHttpChecksNotExecuted": missing_permission_unexecuted,
        "missingPermissionRouteRowsNotExecuted": missing_permission_route_unexecuted,
        "persistedActorEndpointChecksPassed": actor_endpoint_pass,
        "persistedActorEndpointAuthorizedBusinessRejections": actor_endpoint_business_rejections,
        "persistedActorEndpointChecksFailed": actor_endpoint_fail,
        "persistedActorEndpointChecksNotExecuted": actor_endpoint_unexecuted,
        "protectedRoutesWithoutSuccessfulPersistedActorEvidence": actor_endpoint_route_unexecuted,
        "actorScenariosDeclared": len(matrix.get("runtimeScenarios", [])),
        "notApplicableResourceRows": len(matrix.get("notApplicableResources", [])),
        "actorScenariosPassed": scenario_pass,
        "actorScenariosFailed": scenario_fail,
        "actorScenariosNotExecuted": scenario_unexecuted,
        "missingEvidenceFiles": missing_inputs,
        "resultSemantics": "A protected live endpoint is covered only by a successful persisted-actor HTTP response or an explicit passing fixture-backed actor/endpoint scenario. Generic 4xx responses, including 404/409, never count as positive authorization coverage. Actor scenarios are PASS only when their referenced Surefire test method exists in the full suite and has no failure/error.",
        "files": {
            "endpointAndScenarioCsv": str(ROUTE_REPORT.relative_to(REPO_ROOT)),
            "summaryJson": str(SUMMARY_REPORT.relative_to(REPO_ROOT))
        }
    }
    SUMMARY_REPORT.write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    print("P0-06_RUNTIME_REPORT|" + json.dumps(summary, sort_keys=True))
    return 1 if missing_inputs or anonymous_fail or view_fail or view_unexecuted or missing_permission_fail or missing_permission_unexecuted or missing_permission_route_unexecuted or actor_endpoint_fail or actor_endpoint_unexecuted or actor_endpoint_route_unexecuted or scenario_fail or scenario_unexecuted else 0


if __name__ == "__main__":
    try:
        sys.exit(build())
    except Exception as exc:
        print(f"P0-06 report generation failed: {exc}", file=sys.stderr)
        sys.exit(2)
