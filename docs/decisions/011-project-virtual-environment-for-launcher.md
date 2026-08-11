# Decision: Isolate Launcher Dependencies in a Project Virtual Environment

**Date:** 2026-08-11
**Who Decided:** Phase Zero modernization implementation
**Status:** Accepted
**Source:** Launcher replay and dependency-conflict evidence

## The Situation

`python3 run.py` installs the production dependency lock before launching the
app. When the selected interpreter cannot write to its global site-packages,
pip falls back to the user site. A replay replaced packages used by unrelated
OpenBB and LangChain installations, even though Open Mobile TTS itself still
passed its tests.

## What We Chose

When no virtual environment is active, `run.py` creates and re-launches itself
inside a repository-local `.venv`. When the user deliberately launches from an
already-active virtual environment, the launcher respects that environment.

The dependency-lock hash is stored inside the active environment so unchanged
launches do not repeatedly run pip.

## What We Rejected

- Installing into the global or user Python environment.
- Requiring every user to create and activate a virtual environment manually.
- Adding a second package/environment manager to the one-command path.

## Consequences

- The default one-command flow cannot replace packages used by unrelated tools.
- First launch creates `.venv` and takes longer; later launches reuse it.
- `.venv` remains untracked and can be deleted to force a clean reinstall.
- Advanced users may supply their own active virtual environment and own its
  dependency lifecycle.
