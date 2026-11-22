#!/usr/bin/env python3
"""Poll Gemini Notebook Studio for a 'Build a site' HTML artifact, download it, and validate it."""

from __future__ import annotations

import argparse
import html as html_lib
import json
import re
import subprocess
import sys
import time
from pathlib import Path

SENSITIVE_PATTERNS = [
    ("email", r"[A-Za-z0-9._%+-]+@gmail\.com"),
    ("google_api_key", r"AIza[0-9A-Za-z\-_]{20,}"),
    ("oauth_token", r"ya29\.[0-9A-Za-z\-_]+"),
    ("cookie_sapisid", r"SAPISID|__Secure-1PSID|__Secure-3PSID"),
    ("bearer_token", r"Bearer\s+[A-Za-z0-9\-_.]{20,}"),
]


def run_nlm(args: list[str], timeout: int | None = None) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["nlm", *args],
        capture_output=True,
        text=True,
        check=False,
        timeout=timeout,
    )


def find_site_artifact(
    notebook_id: str,
    artifact_id: str | None,
    profile: str | None,
    exclude_ids: set[str],
) -> dict | None:
    cmd = ["studio", "status", notebook_id, "--json", "--full"]
    if profile:
        cmd.extend(["--profile", profile])
    proc = run_nlm(cmd)
    if proc.returncode != 0:
        raise RuntimeError(proc.stderr.strip() or proc.stdout.strip() or "nlm studio status failed")

    artifacts = json.loads(proc.stdout or "[]")
    if not isinstance(artifacts, list):
        return None

    if artifact_id:
        for item in artifacts:
            if item.get("id") == artifact_id or item.get("artifact_id") == artifact_id:
                return item
        return None

    candidates = [
        item
        for item in artifacts
        if item.get("type") == "file"
        and (item.get("mime_type") == "text/html" or str(item.get("download_filename", "")).endswith(".html"))
        and item.get("id") not in exclude_ids
    ]
    if not candidates:
        # Fallback: any new type="file" artifact before mime_type is populated
        candidates = [
            item
            for item in artifacts
            if item.get("type") == "file" and item.get("id") not in exclude_ids
        ]
    if not candidates:
        return None

    candidates.sort(key=lambda x: str(x.get("created_at") or ""), reverse=True)
    return candidates[0]


def validate_html(path: Path) -> dict:
    """Run lightweight structural, metadata, and secret checks on a downloaded HTML file."""
    text = path.read_text(encoding="utf-8", errors="replace")
    stripped = text.strip()
    starts_doctype = stripped.lower().startswith("<!doctype html>")
    ends_html = stripped.lower().endswith("</html>")

    sensitive_hits: list[str] = []
    for label, pat in SENSITIVE_PATTERNS:
        if re.search(pat, text, re.IGNORECASE):
            sensitive_hits.append(label)

    title_match = re.search(r"<title[^>]*>(.*?)</title>", text, re.IGNORECASE | re.DOTALL)
    title = html_lib.unescape(re.sub(r"\s+", " ", title_match.group(1)).strip()) if title_match else ""

    raw_headings = re.findall(r"<h[12][^>]*>(.*?)</h[12]>", text, re.IGNORECASE | re.DOTALL)
    headings = []
    for raw in raw_headings:
        clean = html_lib.unescape(re.sub(r"<[^>]+>", "", raw))
        clean = re.sub(r"\s+", " ", clean).strip()
        if clean:
            headings.append(clean)

    external_urls = sorted(set(re.findall(r'https?://[^\s"\'<>)+]+', text)))

    ok = path.stat().st_size > 0 and starts_doctype and ends_html and not sensitive_hits
    return {
        "ok": ok,
        "bytes": path.stat().st_size,
        "lines": len(text.splitlines()),
        "starts_with_doctype": starts_doctype,
        "ends_with_html": ends_html,
        "sensitive_hits": sensitive_hits,
        "title": title,
        "headings": headings[:20],
        "external_urls": external_urls[:20],
    }


def wait_and_download(
    notebook_id: str,
    output: str | Path,
    artifact_id: str | None = None,
    exclude_ids: set[str] | None = None,
    poll_interval: int = 3,
    max_wait: int = 600,
    profile: str | None = None,
) -> dict:
    started = time.monotonic()
    deadline = started + max_wait
    excluded = set(exclude_ids or ())
    target_id = artifact_id

    while True:
        elapsed = int(time.monotonic() - started)
        try:
            artifact = find_site_artifact(notebook_id, target_id, profile, excluded)
        except Exception as exc:
            print(f"[{elapsed}s] Studio status check failed: {exc}", file=sys.stderr, flush=True)
            if time.monotonic() >= deadline:
                raise RuntimeError(f"Studio status failed at timeout: {exc}") from exc
            time.sleep(poll_interval)
            continue

        if artifact is None:
            print(f"[{elapsed}s] Waiting for site artifact to appear in Studio...", flush=True)
        else:
            target_id = artifact.get("id") or artifact.get("artifact_id")
            status = artifact.get("status")
            title = artifact.get("title") or "(untitled)"
            print(f"[{elapsed}s] Artifact {target_id} ({title}): status={status}", flush=True)

            if status == "completed":
                out_path = Path(output)
                out_path.parent.mkdir(parents=True, exist_ok=True)
                dl_cmd = ["download", "file", notebook_id, "--id", str(target_id), "--output", str(out_path)]
                if profile:
                    dl_cmd.extend(["--profile", profile])
                dl_proc = run_nlm(dl_cmd)
                if dl_proc.returncode != 0:
                    raise RuntimeError(dl_proc.stderr.strip() or dl_proc.stdout.strip() or "nlm download failed")

                validation = validate_html(out_path)
                return {
                    "artifact_id": target_id,
                    "title": title,
                    "download_filename": artifact.get("download_filename"),
                    "mime_type": artifact.get("mime_type"),
                    "output": str(out_path),
                    "bytes": validation["bytes"],
                    "elapsed_seconds": int(time.monotonic() - started),
                    "validation": validation,
                }

            if status == "failed":
                raise RuntimeError(f"Artifact {target_id} failed during generation.")

        if time.monotonic() >= deadline:
            raise TimeoutError(f"Timed out after {max_wait}s waiting for site artifact.")

        time.sleep(poll_interval)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--notebook-id", required=True, help="Gemini Notebook ID or alias")
    parser.add_argument("--output", required=True, help="Destination HTML path inside the repository")
    parser.add_argument("--artifact-id", default=None, help="Specific Studio artifact ID to wait for")
    parser.add_argument(
        "--exclude-id",
        action="append",
        default=[],
        help="Pre-existing artifact ID to ignore when auto-detecting the newly created site",
    )
    parser.add_argument("--poll-interval", type=int, default=3, help="Seconds between polls (default: 3)")
    parser.add_argument("--max-wait", type=int, default=600, help="Max wait seconds before timing out (default: 600)")
    parser.add_argument("--profile", default=None, help="Optional nlm auth profile")
    args = parser.parse_args()

    try:
        summary = wait_and_download(
            notebook_id=args.notebook_id,
            output=args.output,
            artifact_id=args.artifact_id,
            exclude_ids=set(args.exclude_id),
            poll_interval=args.poll_interval,
            max_wait=args.max_wait,
            profile=args.profile,
        )
    except TimeoutError as exc:
        print(str(exc), file=sys.stderr)
        return 2
    except Exception as exc:
        print(str(exc), file=sys.stderr)
        return 1

    print(json.dumps(summary, indent=2))
    return 0 if summary["validation"]["ok"] else 3


if __name__ == "__main__":
    raise SystemExit(main())
