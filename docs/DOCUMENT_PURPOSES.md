# Documentation Structure & Purposes

This document clarifies the distinct purpose of each markdown file to prevent overlap.

## Root Directory

### README.md
**Purpose**: Project overview and quick start
**Audience**: New users, GitHub visitors
**Contents**:
- Project description and features
- Quick start installation steps
- Links to detailed documentation
- License and acknowledgments
**Overlap**: None - entry point to all other docs

### claude.md
**Purpose**: Development guidelines for AI assistants
**Audience**: Claude/AI developers working on this project
**Contents**:
- Code style preferences
- File organization rules
- Development workflow
**Overlap**: None - internal development doc

## Component READMEs

### client/README.md
**Purpose**: Frontend-specific setup and structure
**Audience**: Frontend developers
**Contents**:
- Svelte/SvelteKit setup
- Client-only dependencies
- Component structure
- PWA configuration
**Overlap**: None - client-specific only

### server/README.md
**Purpose**: Backend-specific setup and API reference
**Audience**: Backend developers
**Contents**:
- Python/FastAPI setup
- Server-only dependencies
- API endpoint documentation
- Docker deployment
**Overlap**: None - server-specific only

## Documentation Folder

### docs/README.md
**Purpose**: Documentation index and navigation
**Audience**: Anyone looking for specific information
**Contents**:
- Links to all other docs
- Quick reference table
- Document purpose summary
**Overlap**: None - just an index

### docs/QUICK_REFERENCE.md
**Purpose**: One-page cheat sheet for daily use
**Audience**: Regular users who need quick answers
**Contents**:
- Common commands
- Default credentials
- File locations
- Quick troubleshooting steps
**Unique Focus**: Speed over completeness
**Overlap Prevention**: No explanations of "why", just "how"

### docs/HOW_IT_WORKS.md
**Purpose**: Complete system explanation
**Audience**: Users who want to understand the internals
**Contents**:
- Is model local or API?
- Architecture diagram
- Data flow from text → speech
- Where files are stored
- Streaming protocol details
**Unique Focus**: Understanding, not instructions
**Overlap Prevention**: No setup instructions, no command examples

### docs/LIMITS_AND_CONSTRAINTS.md
**Purpose**: Detailed performance characteristics
**Audience**: Users planning production usage or large-scale use
**Contents**:
- Token limits and technical reasons
- Processing speed tables
- Memory requirements
- Comparison to cloud APIs
- How to overcome limits
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
**Audience**: Project managers, contributors
**Contents**:
- What's implemented (✅)
- What's in progress (🚧)
- What's planned (📋)
**Unique Focus**: Present and future, not past
**Overlap Prevention**: Status only, no explanations of features

### docs/technical-architecture.md
**Purpose**: Design decisions and rationale
**Audience**: Developers understanding why things are built this way
**Contents**:
- Why Svelte over React?
- Why FastAPI over Flask?
- Streaming protocol design choices
- Security architecture
**Unique Focus**: Rationale and alternatives considered
**Overlap Prevention**: No setup steps, no usage instructions

### docs/testing-summary.md
**Purpose**: Test coverage and results
**Audience**: QA engineers, developers
**Contents**:
- Unit test results (24 tests)
- Integration test coverage
- Known test failures
- Testing philosophy
**Unique Focus**: Quality assurance data
**Overlap Prevention**: No feature descriptions, just test results

## Overlap Matrix

This matrix shows which documents cover which topics (X = primary, x = mention):

| Topic | README | QUICK_REF | HOW_IT_WORKS | LIMITS | TECH_ARCH | IMPL_STATUS | TESTING | CHANGELOG |
|-------|--------|-----------|--------------|--------|-----------|-------------|---------|-----------|
| Quick start | X | X | - | - | - | - | - | - |
| Commands | - | X | - | - | - | - | - | - |
| Architecture | x | - | X | - | X | - | - | - |
| Model location | x | x | X | - | - | - | - | - |
| Performance | - | x | - | X | - | - | - | - |
| Token limits | - | - | x | X | - | - | - | - |
| Setup steps | X | X | - | - | - | - | - | - |
| API endpoints | x | x | - | - | - | - | - | - |
| Design rationale | - | - | - | - | X | - | - | - |
| Feature status | - | - | - | - | - | X | - | - |
| Test results | - | - | - | - | - | - | X | - |
| Bug fixes | - | - | - | - | - | - | - | X |
| Troubleshooting | - | X | x | - | - | - | - | - |

## Rules to Prevent Overlap

1. **QUICK_REFERENCE** = Commands only, no explanations
2. **HOW_IT_WORKS** = Concepts only, no commands
3. **LIMITS** = Numbers only, no architecture
4. **TECH_ARCH** = Design only, no implementation
5. **IMPL_STATUS** = Status only, no descriptions
6. **TESTING** = Results only, no feature details
7. **CHANGELOG** = History only, no current state

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

## Validation Checklist

Before adding content to a doc, ask:

- [ ] Is this content already in another doc?
- [ ] Does this fit the doc's specific purpose?
- [ ] Am I duplicating something that should just be a link?
- [ ] Could this be in docs/README.md as a pointer instead?

If unsure, use docs/README.md to link to the right place rather than duplicating content.
