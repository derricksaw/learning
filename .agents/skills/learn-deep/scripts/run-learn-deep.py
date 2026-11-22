#!/usr/bin/env python3
"""Run the end-to-end Gemini Notebook /learn-deep pipeline in one background command.

Pipeline:
1. Verify nlm authentication.
2. Create (or reuse) a notebook and apply tags.
3. Run `nlm research start --auto-import` (fast by default), optionally overlapped
   in parallel with any explicitly provided `--url` or `--file` sources.
4. Trigger "Build a site" via `nlm notebook query`.
5. Poll Studio until completed, download HTML into `--output`, and validate it.
"""

from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
import importlib.util
import json
from pathlib import Path
import re
import sys
import time


def _load_wait_module():
    script_path = Path(__file__).with_name("wait-and-download-site.py")
    spec = importlib.util.spec_from_file_location("wait_and_download_site", script_path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Could not load helper module: {script_path}")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def _with_profile(cmd: list[str], profile: str | None) -> list[str]:
    return [*cmd, "--profile", profile] if profile else cmd


def _parse_created_notebook(stdout: str) -> tuple[str, str]:
    text = stdout.strip()
    try:
        data = json.loads(text)
        if isinstance(data, dict):
            nb_id = data.get("notebook_id") or data.get("id")
            url = data.get("url") or (f"https://notebook.google.com/notebook/{nb_id}" if nb_id else "")
            if nb_id:
                return str(nb_id), str(url)
    except json.JSONDecodeError:
        pass

    id_match = re.search(r"notebook_id:\s*([0-9a-fA-F-]+)", text)
    if not id_match:
        raise RuntimeError(f"Could not parse notebook_id from output:\n{text}")
    nb_id = id_match.group(1)
    url_match = re.search(r"url:\s*(\S+)", text)
    url = url_match.group(1) if url_match else f"https://notebook.google.com/notebook/{nb_id}"
    return nb_id, url


def _list_existing_artifact_ids(wait_mod, notebook_id: str, profile: str | None) -> set[str]:
    proc = wait_mod.run_nlm(_with_profile(["studio", "status", notebook_id, "--json"], profile))
    if proc.returncode != 0:
        return set()
    try:
        items = json.loads(proc.stdout or "[]")
    except json.JSONDecodeError:
        return set()
    if not isinstance(items, list):
        return set()
    return {str(item["id"]) for item in items if isinstance(item, dict) and item.get("id")}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--title", required=True, help="Notebook title (used when creating a new notebook)")
    parser.add_argument("--query", required=True, help="Focused research query for Gemini Notebook")
    parser.add_argument("--output", required=True, help="Destination HTML path inside the repository")
    parser.add_argument("--prompt", default=None, help="Build-a-site prompt text (or '-' to read stdin)")
    parser.add_argument("--prompt-file", default=None, help="Path to file containing the Build-a-site prompt")
    parser.add_argument("--notebook-id", default=None, help="Reuse an existing notebook ID instead of creating one")
    parser.add_argument("--tags", default=None, help="Comma-separated tags (e.g. 'learning,production')")
    parser.add_argument("--mode", choices=["fast", "deep"], default="fast", help="Research mode (default: fast)")
    parser.add_argument("--text", default=None, help="Optional locally generated reference brief text to add in parallel")
    parser.add_argument("--text-file", default=None, help="Path to a file whose text is added as a reference source in parallel")
    parser.add_argument("--text-title", default="Local Reference Brief", help="Title for the --text/--text-file source")
    parser.add_argument("--url", action="append", default=[], help="Optional explicit URL source (repeatable)")
    parser.add_argument("--file", action="append", default=[], help="Optional explicit local file source (repeatable)")
    parser.add_argument("--skip-research", action="store_true", help="Skip research when reusing an already-populated notebook")
    parser.add_argument("--poll-interval", type=int, default=3, help="Studio poll interval seconds (default: 3)")
    parser.add_argument("--max-wait", type=int, default=600, help="Max Studio wait seconds (default: 600)")
    parser.add_argument("--query-timeout", type=int, default=300, help="Timeout for Build-a-site query (default: 300)")
    parser.add_argument("--profile", default=None, help="Optional nlm auth profile")
    args = parser.parse_args()

    if args.prompt_file:
        prompt = Path(args.prompt_file).read_text(encoding="utf-8").strip()
    elif args.prompt == "-":
        prompt = sys.stdin.read().strip()
    elif args.prompt:
        prompt = args.prompt.strip()
    else:
        print("Either --prompt or --prompt-file is required.", file=sys.stderr)
        return 2

    wait_mod = _load_wait_module()
    timings: dict[str, int] = {}
    t_total = time.monotonic()

    # 1. Auth check
    t0 = time.monotonic()
    auth_proc = wait_mod.run_nlm(_with_profile(["login", "--check"], args.profile))
    timings["auth_check"] = int(time.monotonic() - t0)
    if auth_proc.returncode != 0:
        print("nlm auth check failed. Run `nlm auth refresh` or `nlm login`.", file=sys.stderr)
        print(auth_proc.stderr or auth_proc.stdout, file=sys.stderr)
        return 1

    # 2. Create or reuse notebook
    t0 = time.monotonic()
    exclude_ids: set[str] = set()
    if args.notebook_id:
        notebook_id = args.notebook_id
        notebook_url = f"https://notebook.google.com/notebook/{notebook_id}"
        exclude_ids = _list_existing_artifact_ids(wait_mod, notebook_id, args.profile)
    else:
        create_proc = wait_mod.run_nlm(_with_profile(["notebook", "create", args.title, "--json"], args.profile))
        if create_proc.returncode != 0:
            print(create_proc.stderr or create_proc.stdout, file=sys.stderr)
            return 1
        notebook_id, notebook_url = _parse_created_notebook(create_proc.stdout)
        print(f"Created notebook {notebook_id}: {args.title}", flush=True)

    if args.tags:
        tag_proc = wait_mod.run_nlm(_with_profile(["tag", "add", notebook_id, "--tags", args.tags], args.profile))
        if tag_proc.returncode != 0:
            print(f"Warning: tag add failed: {tag_proc.stderr or tag_proc.stdout}", file=sys.stderr)
    timings["notebook_setup"] = int(time.monotonic() - t0)

    # 3. Research + optional local reference brief + any explicit URLs/files in parallel
    t0 = time.monotonic()
    source_tasks: list[tuple[str, list[str]]] = []
    if not args.skip_research:
        source_tasks.append(
            (
                f"research:{args.mode}",
                _with_profile(
                    ["research", "start", args.query, "--notebook-id", notebook_id, "--mode", args.mode, "--auto-import"],
                    args.profile,
                ),
            )
        )
    ref_text = None
    if args.text_file:
        ref_text = Path(args.text_file).read_text(encoding="utf-8").strip()
    elif args.text:
        ref_text = args.text.strip()
    if ref_text:
        source_tasks.append(
            (
                f"text:{args.text_title}",
                _with_profile(
                    ["source", "add", notebook_id, "--text", ref_text, "--title", args.text_title, "--wait"],
                    args.profile,
                ),
            )
        )
    for url in args.url:
        source_tasks.append(
            (
                f"url:{url}",
                _with_profile(["source", "add", notebook_id, "--url", url, "--wait"], args.profile),
            )
        )
    for file_path in args.file:
        source_tasks.append(
            (
                f"file:{file_path}",
                _with_profile(["source", "add", notebook_id, "--file", file_path, "--wait"], args.profile),
            )
        )

    source_errors: list[str] = []
    if source_tasks:
        with ThreadPoolExecutor(max_workers=min(6, len(source_tasks))) as pool:
            futures = {pool.submit(wait_mod.run_nlm, cmd): label for label, cmd in source_tasks}
            for fut in as_completed(futures):
                label = futures[fut]
                proc = fut.result()
                if proc.returncode != 0:
                    msg = f"{label} failed: {(proc.stderr or proc.stdout).strip()}"
                    source_errors.append(msg)
                    print(f"Warning: {msg}", file=sys.stderr, flush=True)
                else:
                    print(f"Completed {label}", flush=True)
    timings["source_ingestion"] = int(time.monotonic() - t0)

    # List ingested sources for reporting
    sources: list[dict] = []
    src_proc = wait_mod.run_nlm(_with_profile(["source", "list", notebook_id, "--json"], args.profile))
    if src_proc.returncode == 0:
        try:
            raw_sources = json.loads(src_proc.stdout or "[]")
            if isinstance(raw_sources, list):
                sources = [
                    {
                        "title": s.get("title"),
                        "url": s.get("url"),
                        "type": s.get("type"),
                        "status": s.get("status"),
                    }
                    for s in raw_sources
                    if isinstance(s, dict)
                ]
        except json.JSONDecodeError:
            pass

    ready_sources = [s for s in sources if s.get("status") == 2]
    if not ready_sources and source_errors:
        print("No ready sources were ingested; aborting before Build a site.", file=sys.stderr)
        return 1

    # 4. Trigger "Build a site"
    t0 = time.monotonic()
    print("Triggering Gemini Notebook 'Build a site'...", flush=True)
    query_cmd = _with_profile(
        ["notebook", "query", notebook_id, prompt, "--timeout", str(args.query_timeout), "--json"],
        args.profile,
    )
    query_proc = wait_mod.run_nlm(query_cmd, timeout=args.query_timeout + 30)
    timings["trigger_site"] = int(time.monotonic() - t0)
    if query_proc.returncode != 0:
        print(query_proc.stderr or query_proc.stdout, file=sys.stderr)
        return 1

    # 5. Poll Studio, download HTML, and validate
    t0 = time.monotonic()
    try:
        artifact = wait_mod.wait_and_download(
            notebook_id=notebook_id,
            output=args.output,
            exclude_ids=exclude_ids,
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
    timings["poll_download_validate"] = int(time.monotonic() - t0)
    timings["total"] = int(time.monotonic() - t_total)

    result = {
        "notebook_id": notebook_id,
        "notebook_title": args.title,
        "notebook_url": notebook_url,
        "research_mode": None if args.skip_research else args.mode,
        "source_count": len(sources),
        "ready_source_count": len(ready_sources),
        "sources": sources,
        "source_warnings": source_errors,
        "artifact": artifact,
        "timings_seconds": timings,
    }
    print(json.dumps(result, indent=2))
    return 0 if artifact["validation"]["ok"] else 3


if __name__ == "__main__":
    raise SystemExit(main())
