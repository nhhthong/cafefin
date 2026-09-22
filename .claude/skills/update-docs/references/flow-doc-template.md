# Flow doc template

Skeleton for `resources/docs/<NAME>.md`. Copy this, fill every bracketed placeholder with a
value you actually verified (read the code, or ran the request), and delete this comment line.
Real example to compare against: `resources/docs/REGISTER.md`.

---

```markdown
[← Back to docs index](../TUTORIAL.md)

# Flow: <Name> (`<METHOD> <path>`)

<One sentence: what this endpoint does, and its auth requirement.>

---

## 1. Contract

- **Method & path**: `<METHOD> <path>`
- **Auth**: <none | Bearer JWT | Cookie | ...>
- **Rate limit**: <the real limit if one is enforced, or "none yet — <plan task id>" if the plan
  calls for one but it isn't built>
- **Request / response**: JSON

### Request

\`\`\`json
<a real example request body>
\`\`\`

### Response — `<status> <reason>`

\`\`\`json
<the REAL response body you captured with curl, not a guess>
\`\`\`

<One or two sentences on anything non-obvious about the response — how an id is generated, what's
deliberately excluded and why.>

---

## 2. Sequence

\`\`\`mermaid
sequenceDiagram
    autonumber
    actor Client
    participant H as <Controller>.<method>
    participant S as <Service>.<method>
    participant R as <Repository>
    participant DB as <table/store>

    Client->>H: <METHOD> <path>
    H->>S: <call>
    S->>R: <call>
    R->>DB: <operation>

    alt <a real failure branch this code actually handles>
        DB-->>R: <what actually happens>
        R-->>S: <exception type>
        S-->>H: <how it's translated>
        H-->>Client: <status> "<real error detail>"
    else
        DB-->>R: ok
    end

    R-->>S: <result>
    S-->>H: <result>
    H-->>Client: <status> Created {<real response fields>}
\`\`\`
```

Add more `alt` branches for every failure path the code actually handles — one per distinct status
code / error condition, matching the Errors table below exactly.

---

## 3. Step by step

Numbered list, one step per meaningful thing the code does in order. For each step:
- Name the real method/class it happens in.
- If the code made a deliberate choice a reader might question (skipped a check, chose one
  approach over an obvious alternative), say why — pull the reasoning from the source's own
  comments (never from `.claude/`, which won't be in the public repo).
- Teach, don't just narrate: if the step leans on a Java/Spring/fintech concept a learner might not
  already know, spend a sentence on *why* it works that way, not just *what* it does — see
  `SKILL.md`'s "Teach the why" section for the kind of explanation that's worth including.
- Don't invent behavior that "would make sense" but that you haven't confirmed the code does.

---

## 4. Errors

Every row is something you personally triggered (curl) or can point at a passing test for this
session. Table format:

```markdown
| Condition | Status | `detail` (or the real error identifier this code uses) |
|---|:---:|---|
| <real condition> | `<real status>` | `<real body content, verbatim>` |
```
