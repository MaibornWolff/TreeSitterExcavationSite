# Planning

**For every new instruction, create a plan** in the `plans/` folder using `template.md` as a base:

- Plans act as high-level guidance for implementation
- Focus on mandatory steps only, avoid excessive detail
- Keep plans simple and concise
- Avoid over-elaboration, detailed sections, or comprehensive documentation style
- Plans should be brief, actionable outlines rather than detailed specifications
- Use checkable steps (markdown checkboxes) to track progress
- Update `state` field as work progresses: `todo` → `progress` → `complete`
- Always use the AskUserQuestion tool to clarify ambiguous requirements before finalizing the plan

## TDD Requirement

All implementation steps must follow the Red → Green → Refactor cycle (see `tdd.md`):

- Each task step must be structured as: write failing test → implement → run tests → refactor
- Steps in the plan should be ordered so tests come before implementation code
- Never plan implementation steps without a preceding test step
