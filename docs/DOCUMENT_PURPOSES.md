# Documentation Structure & Purposes

This document clarifies the distinct purpose of each markdown file to prevent overlap.

## Root Directory

### README.md
**Purpose**: Project overview and quick start
**Audience**: New users, GitHub visitors
**Contents**:
- Project description and features
- Quick start: `python run.py`
- Links to detailed documentation
**Overlap**: None — entry point to all other docs

### claude.md
**Purpose**: Development guidelines for AI assistants
**Audience**: Claude/AI developers working on this project
**Contents**:
- Code style preferences
- File organization rules
- Development workflow
**Overlap**: None — internal development doc

## Documentation Folder

### docs/README.md
**Purpose**: Documentation index and navigation
**Audience**: Anyone looking for specific information
**Contents**:
- Links to all other docs
- Quick reference table
- Document purpose summary
**Overlap**: None — just an index

### docs/QUICK_REFERENCE.md
**Purpose**: One-page cheat sheet for daily use
**Audience**: Regular users who need quick answers
**Contents**:
- Common commands (`python run.py`, Docker)
- Configuration options
- File locations
- Quick troubleshooting steps
**Unique Focus**: Speed over completeness
**Overlap Prevention**: No explanations of "why", just "how"

### docs/SETUP_GUIDE.md
**Purpose**: Full setup walkthrough
**Audience**: First-time users
**Contents**:
- Prerequisites
- Step-by-step installation
- Docker alternative
- Development mode setup
**Unique Focus**: Getting from zero to running
**Overlap Prevention**: No architecture or internals

### docs/HOW_IT_WORKS.md
**Purpose**: Complete system explanation
**Audience**: Users who want to understand the internals
**Contents**:
- Architecture diagram
- Data flow from text → speech
- Streaming protocol details
- Where files are stored
**Unique Focus**: Understanding, not instructions
**Overlap Prevention**: No setup instructions, no command examples

### docs/LIMITS_AND_CONSTRAINTS.md
**Purpose**: Detailed performance characteristics
**Audience**: Users planning usage
**Contents**:
- Token limits and technical reasons
- Processing speed tables
- Memory requirements
- Comparison to cloud APIs
**Unique Focus**: Quantitative data and benchmarks
**Overlap Prevention**: No architecture explanations, just metrics

### docs/CHANGELOG.md
**Purpose**: Historical record of changes
**Audience**: Developers tracking what was fixed
**Contents**:
- Bugs fixed with dates
- Features added
- Breaking changes
**Unique Focus**: Chronological history
**Overlap Prevention**: Past tense only, no current status

### docs/implementation-status.md
**Purpose**: Current feature completeness
**Audience**: Contributors
**Contents**:
- What's implemented (✅)
- What's planned (📋)
**Unique Focus**: Present and future, not past
**Overlap Prevention**: Status only, no explanations of features

### docs/technical-architecture.md
**Purpose**: Design decisions and rationale
**Audience**: Developers understanding why things are built this way
**Contents**:
- Why Svelte? Why FastAPI? Why Kokoro?
- Streaming protocol design choices
- Performance characteristics
**Unique Focus**: Rationale and alternatives considered
**Overlap Prevention**: No setup steps, no usage instructions

### docs/testing-summary.md
**Purpose**: Test coverage and results
**Audience**: QA engineers, developers
**Contents**:
- Automated test results
- Manual testing results
- Known issues and gaps
**Unique Focus**: Quality assurance data
**Overlap Prevention**: No feature descriptions, just test results

### docs/MIGRATION.md
**Purpose**: Records the architecture change from client-server to monolithic
**Audience**: Developers needing historical context
**Contents**:
- What changed and why
- What was removed and added
**Unique Focus**: One specific architectural decision
**Overlap Prevention**: Historical record only

### docs/SECURITY_CHECKLIST.md
**Purpose**: Security considerations for deployment
**Audience**: Anyone deploying beyond localhost
**Contents**:
- Network security for VPS deployment
- Android/WiFi security considerations
- Git hygiene (what not to commit)
**Unique Focus**: Security only
**Overlap Prevention**: No setup instructions

### docs/ROADMAP.md
**Purpose**: Master version plan — v1 vs v2 architecture, what changes, implementation phases
**Audience**: Everyone (planning reference before starting v2)
**Contents**:
- v1 (current): Capacitor + server architecture
- v2 (planned): Native Sherpa-ONNX + desktop kept
- What changes, what stays, what gets added/removed
- Implementation phases, tech stack, expected performance
**Unique Focus**: Single source of truth for version strategy
**Overlap Prevention**: References IMPLEMENTATION_PLAN.md for code-level details, OFFLINE_TTS_FEASIBILITY.md for research

### docs/ANDROID_APP_GUIDE.md
**Purpose**: Complete guide for running the app on Android
**Audience**: Users who want to use the app on their Android phone
**Contents**:
- How Capacitor wraps the web app
- Android Studio setup and build
- Server connection configuration
- Troubleshooting
**Unique Focus**: Android-specific setup and usage
**Overlap Prevention**: No general architecture or server setup

### docs/IMPLEMENTATION_PLAN.md
**Purpose**: Roadmap for a future native Android app with on-device TTS
**Audience**: Future contributors
**Contents**:
- Sherpa-ONNX integration plan
- Native Kotlin architecture
- Model optimization strategy
**Unique Focus**: Aspirational future direction
**Overlap Prevention**: Not about the current Capacitor implementation

### docs/OFFLINE_TTS_FEASIBILITY.md
**Purpose**: Research into running Kokoro TTS directly on a mobile device
**Audience**: Future contributors
**Contents**:
- Model size and memory analysis
- INT8 quantization feasibility
- Device performance expectations
**Unique Focus**: Technical feasibility research
**Overlap Prevention**: No implementation details, just analysis

## Rules to Prevent Overlap

1. **QUICK_REFERENCE** = Commands only, no explanations
2. **SETUP_GUIDE** = Installation only, no architecture
3. **HOW_IT_WORKS** = Concepts only, no commands
4. **LIMITS** = Numbers only, no architecture
5. **TECH_ARCH** = Design only, no implementation
6. **IMPL_STATUS** = Status only, no descriptions
7. **TESTING** = Results only, no feature details
8. **CHANGELOG** = History only, no current state
9. **MIGRATION** = Architecture change record only

## When to Update Which Doc

| Change Type | Update This |
|-------------|-------------|
| Fixed a bug | CHANGELOG.md |
| Added a feature | CHANGELOG.md + implementation-status.md |
| Changed architecture | technical-architecture.md + HOW_IT_WORKS.md |
| New command | QUICK_REFERENCE.md |
| Performance change | LIMITS_AND_CONSTRAINTS.md |
| Test results change | testing-summary.md |
| Project description | README.md |
| Android changes | ANDROID_APP_GUIDE.md |
| Security changes | SECURITY_CHECKLIST.md |
