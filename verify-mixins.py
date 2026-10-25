#!/usr/bin/env python3
"""Offline validation of the mixin targets.

Compiling a mixin project without the Mixin annotation processor does not validate that the
referenced members actually exist. This script parses the mixin sources, resolves the target class
from @Mixin(...) and checks every @Inject/@WrapOperation method string and every @Shadow field
against `javap -p -s` output of the 26.3 client classes.
"""
import os
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
CLIENT = Path(os.environ.get("CLIENT_JAR_DIR", ROOT / "libs" / "client-26.3"))
SRC = ROOT / "src/main/java"

MIXIN_RE = re.compile(r'@Mixin\((?:\s*targets\s*=\s*)?([A-Za-z0-9_.]+)\.class')
METHOD_RE = re.compile(r'method\s*=\s*"([^"]+)"')
TARGET_RE = re.compile(r'target\s*=\s*"([^"]+)"')
SHADOW_RE = re.compile(r'@Shadow[^;]*?private\s+([A-Za-z0-9_.$]+)\s+([A-Za-z0-9_$]+)\s*;', re.S)
INVOKER_RE = re.compile(r'@Invoker\("([^"]+)"\)')
ACCESSOR_RE = re.compile(r'@Accessor\("([^"]+)"\)')


def javap(cls):
    out = subprocess.run(
        ["javap", "-p", "-s", "-classpath", str(CLIENT), cls],
        capture_output=True, text=True)
    if out.returncode != 0:
        return None, out.stderr
    members = set()
    lines = out.stdout.splitlines()
    for i, line in enumerate(lines):
        if "descriptor:" in line:
            desc = line.split("descriptor:")[1].strip()
            # walk back to the declaring line
            for back in range(i - 1, max(i - 3, -1), -1):
                decl = lines[back].strip()
                if decl.endswith(";") or "(" in decl:
                    name = None
                    if "(" in decl:
                        name = decl.split("(")[0].split()[-1]
                    else:
                        name = decl.rstrip(";").split()[-1]
                    members.add((name, desc))
                    break
    return members, out.stdout


def main():
    failures = []
    checks = 0
    for path in sorted(SRC.rglob("*.java")):
        text = path.read_text(encoding="utf-8")
        if "@Mixin(" not in text:
            continue
        mixin = MIXIN_RE.search(text)
        if not mixin:
            failures.append(f"{path.name}: cannot parse @Mixin target")
            continue
        target = mixin.group(1)
        imports = {}
        for line in text.splitlines():
            line = line.strip()
            if line.startswith("import ") and line.endswith(";"):
                fqn = line[len("import "):-1]
                imports[fqn.split(".")[-1]] = fqn
        target = imports.get(target, target)
        members, raw = javap(target)
        if members is None:
            failures.append(f"{path.name}: target class {target} not found ({raw.strip()[:120]})")
            continue

        for method in METHOD_RE.findall(text):
            checks += 1
            if "(" in method:
                name = method.split("(")[0]
                desc = method[method.index("("):]
                if (name, desc) not in members:
                    failures.append(f"{path.name}: {target}#{name}{desc} missing")
            else:
                if not any(name == method for name, _ in members):
                    failures.append(f"{path.name}: {target}#{method} missing")

        for ref in INVOKER_RE.findall(text) + ACCESSOR_RE.findall(text):
            checks += 1
            if not any(name == ref for name, _ in members):
                failures.append(f"{path.name}: {target}#{ref} missing (invoker/accessor)")

        for desc_ref in TARGET_RE.findall(text):
            # e.g. Lnet/minecraft/.../GuiRenderState;forEachText(Ljava/util/function/Consumer;)V
            if not desc_ref.startswith("L") or ";" not in desc_ref:
                continue
            checks += 1
            owner, rest = desc_ref[1:].split(";", 1)
            owner = owner.replace("/", ".")
            sub_members, _ = javap(owner)
            if sub_members is None:
                failures.append(f"{path.name}: wrap target owner {owner} not found")
                continue
            name = rest.split("(")[0]
            desc = rest[rest.index("("):]
            if (name, desc) not in sub_members:
                failures.append(f"{path.name}: wrap target {owner}#{name}{desc} missing")

        for ftype, fname in SHADOW_RE.findall(text):
            checks += 1
            simple = ftype.split(".")[-1]
            if not any(name == fname for name, _ in members):
                failures.append(f"{path.name}: shadow field {target}#{fname} missing")

    print(f"{checks} references checked")
    if failures:
        print("\nFAILURES:")
        for failure in failures:
            print("  -", failure)
        return 1
    print("all mixin targets resolve")
    return 0


if __name__ == "__main__":
    sys.exit(main())
