---
name: armin-webapp-functional-test
description: >-
  Test a web app by using it like a user: open pages, enter data, submit forms,
  click through workflows, and confirm results stick. Use after UI/feature
  changes, before saying done, or when the user asks to verify the app works.
author: "Armin Dashti"
---
# Web App Functional Test

Prove the feature works end-to-end in a real browser. Look alone is not enough.

**Reporting override:** When running this skill, ignore `armin-write-completion-report` (`C:\Users\armin\.cursor\skills\armin\armin-write-completion-report`). Use this skill's Title / Page / Status grid with failure reasons below it instead; do not append the other skill's completion-report template.

## When

- After a change that affects pages, forms, grids, saves, or navigation.
- When the user asks to test, verify, or try data entry in the app.
- Skip if the change is docs/skills-only with no running UI → `Functional test skipped: docs-only`.

## Always

1. Prefer the project's own base URL / env (`SOURCE_NEWUI_BASE_URL`, compose ports, etc.).
2. Use Cursor's built-in browser (or the project's browser skill if it names one).
3. Log in with credentials from env vars when needed. Never print secrets.
4. Exercise the **changed** flow with real data entry, not only a page open.
5. Confirm the result: row appears, toast/success, reload still shows data, or DB/API reflects the save when that is the project's check.
6. Screenshot key steps; report results in the grid below.
7. If something fails, fix it (or report the blocker) and re-test before saying done.

## Steps

1. **Scope** — Prefer target URLs supplied by the user; stay within those pages and necessary workflow steps. If none are supplied, warn before testing: "No target URLs were provided; testing may take a long time. Providing target URLs will narrow the scope." Then use changed/known pages, not an unlimited crawl. List 1–3 happy-path actions to try.
2. **Open** — Go to the real local URL. Hard-reload (bypass cache).
3. **Login** — If required, use env credentials; land on the expected home/page.
4. **Enter data** — Fill required fields with safe test values (clearly fake, e.g. `TEST-` prefix). Submit / save.
5. **Navigate** — Click the buttons, open dialogs, change tabs, use search/filter if that is part of the flow.
6. **Verify** — After save: success message or new row; refresh the page; open the record again. Check console for new errors and network for failed requests on the actions you took.
7. **Edge (short)** — One invalid submit (empty required field) if the form has validation; confirm it blocks cleanly.
8. **Report** — Use the required results grid below, followed by failure reasons. Include screenshots and leftover test rows if any; never include secrets.

## Results grid

Report one row per executed test using exactly these columns:

| Title | Page | Status |
| --- | --- | --- |

- **Title:** the test title.
- **Page:** the tested URL without the configured base URL; show a relative path, preserving relevant query/fragment. Never include secrets.
- **Status:** exactly `✅ Passed` or `❌ Failed`, based on verified results. Never claim an unexecuted test passed; list blocked/unrun tests separately.

Immediately below the grid, list each failed test by title and page, then explain the expected result, actual result, and reason (or say the cause is unknown). If none failed, write "No failed tests."

## Never

- Claim pass from HTTP 200 or a screenshot of an empty form.
- Skip login when the feature needs an authenticated session.
- Use real production/personal data; use marked test values.
- Print passwords or tokens.
- Leave the app broken; re-test after fixes.
