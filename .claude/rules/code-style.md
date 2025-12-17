# Code Style

## Kotlin Conventions

- Based on official Kotlin Coding Conventions
- Auto-formatted via `./gradlew ktlintFormat`
- Rules defined in `.editorconfig`
- **Function syntax**: Use block-body style with braces `{ }` consistently, not expression-body style with `=`
- **Guard clauses**: Use early returns for error conditions and edge cases to reduce nesting
- **If expressions**: Prefer concise single-line style when possible:
  - `val x = if (condition) valueA else valueB`
  - Use multi-line only when branches contain multiple statements or complex logic
- **Magic strings/numbers**: Extract repeated literals to constants in `companion object`
- **Function organization**: Group related functions with section comments
- **Parameter naming**: Use consistent, descriptive names across related functions

## Code Quality Guidelines

- **DRY**: Extract repeated logic into reusable functions
- **Clean Code**: Self-documenting code with clear intent
- **SOLID**: Single responsibility, open/closed, dependency inversion
- **Expressive Naming**: Descriptive names that reveal intent
- **Fix Warnings**: Never suppress, always resolve
- **Consistent Style**: Match existing patterns
- **Comments**: Use sparingly for complex business logic rationale. Prefer clear function names over comments.
- **Metric Accuracy**: All metrics must be deterministic and reproducible across runs
- **Immutability**: Prefer immutable data structures, especially in the model layer
- **Backward Compatibility**: Changes to `.cc.json` format require careful versioning

## Commit Message Conventions

Use [Conventional Commits](https://www.conventionalcommits.org/) format:

```
<type>(<scope>): <description>

[optional body]
```

**Types:**
- `feat` - New feature or capability
- `fix` - Bug fix
- `docs` - Documentation only changes
- `style` - Code style changes (formatting, no logic change)
- `refactor` - Code restructuring without changing behavior
- `test` - Adding or updating tests
- `chore` - Build, CI, dependencies, or tooling changes

**Examples:**
```
feat(vue): add Vue Single File Component support
fix(metrics): correct LOC calculation for empty files
docs: update API usage examples
refactor(extraction): simplify comment parsing logic
test(kotlin): add edge case tests for lambda expressions
chore(deps): update tree-sitter to 0.25.3
```

**Guidelines:**
- Use imperative mood ("add" not "added")
- Keep first line under 72 characters
- Scope is optional but recommended for clarity
- Reference issues when applicable: `fix(metrics): handle null input (#123)`

## Avoid

- Committing with failing tests
- Mixing structural + behavioral changes
- Suppressing warnings