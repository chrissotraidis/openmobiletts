"""Tests for the one-command launcher's environment boundary."""

import importlib.util
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("openmobiletts_launcher", ROOT / "run.py")
launcher = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(launcher)


def test_launcher_respects_an_active_virtual_environment(monkeypatch):
    monkeypatch.setattr(launcher, "_in_virtual_environment", lambda: True)
    monkeypatch.setattr(
        launcher.os,
        "execve",
        lambda *args: (_ for _ in ()).throw(AssertionError("must not re-exec")),
    )

    launcher.ensure_python_environment()


def test_launcher_reexecs_inside_existing_project_environment(tmp_path, monkeypatch):
    project_venv = tmp_path / ".venv"
    venv_python = project_venv / "bin" / "python"
    venv_python.parent.mkdir(parents=True)
    venv_python.touch()

    monkeypatch.setattr(launcher, "PROJECT_VENV", project_venv)
    monkeypatch.setattr(launcher, "_in_virtual_environment", lambda: False)

    captured = {}

    def capture_exec(executable, argv, env):
        captured.update(executable=executable, argv=argv, env=env)
        raise RuntimeError("re-exec captured")

    monkeypatch.setattr(launcher.os, "execve", capture_exec)

    try:
        launcher.ensure_python_environment()
    except RuntimeError as error:
        assert str(error) == "re-exec captured"
    else:
        raise AssertionError("launcher did not re-exec")

    assert captured["executable"] == str(venv_python)
    assert captured["argv"][0] == str(venv_python)
    assert Path(captured["argv"][1]) == ROOT / "run.py"
