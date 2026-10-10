"""The sole development installer: serialized, owned, and ancestry-checked."""

import argparse
import fcntl
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "com.madebygps.dothabits"


def run(*args, capture=False):
    return subprocess.run(
        args, cwd=ROOT, check=True, text=True,
        stdout=subprocess.PIPE if capture else None,
    ).stdout


def git(*args):
    return run("git", *args, capture=True).strip()


def is_ancestor(older, newer):
    result = subprocess.run(
        ["git", "merge-base", "--is-ancestor", older, newer], cwd=ROOT,
    )
    if result.returncode not in (0, 1):
        raise RuntimeError(f"Cannot compare commits {older} and {newer}; fetch the missing history.")
    return result.returncode == 0


def validate_freshness(head, main, installed, ancestor=is_ancestor):
    if not ancestor(main, head):
        raise RuntimeError("Stale checkout: integrate origin/main before installing.")
    if installed and not ancestor(installed["commit"], head):
        raise RuntimeError("This checkout does not contain the installed commit. Integrate it first.")
    if installed and installed["dirty"]:
        print("Installed build contains uncommitted changes; ensure they are preserved in this checkout.")


def validate_owner(owner, workspace, claim):
    if owner and owner["workspace"] != workspace and not claim:
        raise RuntimeError(f"Install owner is {owner['workspace']}. Ask the user before transferring with --claim.")


def validate_modified_install(installed, owner, workspace):
    if installed and installed["dirty"] and (not owner or owner["workspace"] != workspace):
        raise RuntimeError("Installed build has uncommitted changes from another checkout. Have its owner install a clean, committed build before transferring.")


def source_fingerprint():
    digest = hashlib.sha256()
    digest.update(git("rev-parse", "HEAD").encode())
    paths = run("git", "ls-files", "-co", "--exclude-standard", "-z", capture=True)
    for name in sorted(set(paths.split("\0")) - {""}):
        path = ROOT / name
        digest.update(name.encode())
        digest.update(b"\0")
        if path.is_symlink():
            digest.update(os.readlink(path).encode())
        elif path.is_file():
            digest.update(path.read_bytes())
        else:
            digest.update(b"<deleted>")
    return digest.hexdigest()


def read_build_info(apk):
    with zipfile.ZipFile(apk) as archive:
        if "assets/build-info.json" not in archive.namelist():
            return None
        info = json.loads(archive.read("assets/build-info.json"))
        if not isinstance(info.get("commit"), str) or not isinstance(info.get("dirty"), bool):
            raise RuntimeError("Invalid APK build metadata.")
        return info


def installed_info(adb, directory):
    paths = run(*adb, "shell", "pm", "path", PACKAGE, capture=True).splitlines()
    if not paths:
        return None
    base = next((p.removeprefix("package:") for p in paths if p.endswith("/base.apk")), None)
    if not base:
        raise RuntimeError("Cannot locate installed base APK.")
    apk = directory / "installed.apk"
    run(*adb, "pull", base, str(apk))
    return read_build_info(apk)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--claim", action="store_true", help="Explicitly transfer installer ownership to this checkout.")
    parser.add_argument("--serial", help="Nothing Phone (3) adb serial; required if multiple devices are connected.")
    args = parser.parse_args()
    state = Path.home() / ".local" / "state" / "dot-habits"
    state.mkdir(parents=True, exist_ok=True)
    with (state / "install.lock").open("a") as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise RuntimeError("Another installer is running. Wait for it to finish.") from None
        devices = run("adb", "devices", capture=True).splitlines()[1:]
        serials = [line.split()[0] for line in devices if len(line.split()) == 2 and line.split()[1] == "device"]
        serial = args.serial or (serials[0] if len(serials) == 1 else None)
        if not serial or serial not in serials:
            raise RuntimeError("Connect one authorized Phone (3), or select it with --serial.")
        adb = ["adb", "-s", serial]
        if run(*adb, "shell", "getprop", "ro.product.model", capture=True).strip() != "A024":
            raise RuntimeError("Installer supports only Nothing Phone (3) (A024).")
        owner_file = state / (hashlib.sha256(serial.encode()).hexdigest() + ".json")
        owner = json.loads(owner_file.read_text()) if owner_file.exists() else None
        validate_owner(owner, str(ROOT), args.claim)
        run("git", "fetch", "--quiet", "origin", "main")
        head = git("rev-parse", "HEAD")
        remote_main = git("rev-parse", "refs/remotes/origin/main")
        with tempfile.TemporaryDirectory(prefix="dot-habits-install-") as temporary:
            installed = installed_info(adb, Path(temporary))
            validate_freshness(head, remote_main, installed)
            validate_modified_install(installed, owner, str(ROOT))
            if installed is None:
                print("No build provenance in installed app (or first install); bootstrapping guarded installs.", flush=True)
            before = source_fingerprint()
            run(str(ROOT / "gradlew"), "--console=plain", ":app:assembleDebug")
            apk = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
            candidate = read_build_info(apk)
            if not candidate or candidate["commit"] != head or source_fingerprint() != before:
                raise RuntimeError("Sources changed during the build, or APK metadata is missing. Retry.")
            run("git", "fetch", "--quiet", "origin", "main")
            validate_freshness(head, git("rev-parse", "refs/remotes/origin/main"), installed)
            run(*adb, "install", "-r", str(apk))
            actual = installed_info(adb, Path(temporary))
            if actual != candidate:
                raise RuntimeError("Installed APK does not match the build. Ownership was not updated.")
            owner_file.write_text(json.dumps({"workspace": str(ROOT), "build": candidate}, indent=2) + "\n")
            print(f"Installed {head[:12]}{' (modified)' if candidate['dirty'] else ''}, built {candidate['builtAt']}.")


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, subprocess.CalledProcessError, OSError, ValueError, zipfile.BadZipFile) as error:
        print(f"Install blocked: {error}", file=sys.stderr)
        sys.exit(1)
