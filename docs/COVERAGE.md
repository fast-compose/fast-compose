# Compose API coverage

Fast Compose can only call Compose APIs that have a binding. This page shows how much of the Compose API is bound and
how that is measured.

Numbers are for version 0.1.0, checked against Jetpack Compose `1.12.0-beta01` and the current Material 3 API.

## What it can run

**Kotlin.** Functions, default and named arguments, lambdas, classes, data classes, objects, enums, interfaces,
extension functions and properties, `when`, ranges, string templates, destructuring, `try`/`catch`/`throw`, collections
and their common operations, delegated properties (`by remember`), and coroutines (`LaunchedEffect`, `launch`,
`delay`, `suspend` functions, `Flow`).

**Compose.** Layouts, `Text` and text fields, lazy lists and grids, pagers, state and effects, modifiers, drawing,
gestures, animations and transitions, `CompositionLocal`, Material 3 components, theming and icons, and network
images with Coil's `AsyncImage`.

No imports are needed: bound APIs work without `import` lines.

## Limitations

Fast Compose runs a large, practical subset of Kotlin and Compose, not all of it:

- **Only bound APIs.** Code can call the APIs that have bindings, plus anything declared in the code itself.
  Libraries such as ViewModel, Hilt or Room are not available unless you add bindings for them.
- **Types are not checked.** Type annotations are parsed and used for overloads and `is` checks, but a wrong type is
  reported when the code runs, not before.
- **Not every language feature.** Advanced features such as reflection are not supported yet; unsupported code
  shows a clear error where it occurs.
- **Interpreted speed.** Fast enough for UI, but heavy computation runs slower than compiled code. Code that runs for
  more than 2 seconds without a pause is stopped, so a runaway loop cannot freeze the host app.

## Summary

| Category | Covered | |
|---|---|---|
| Composables | 109 of 181 | 60% |
| Modifiers | 69 of 179 | 38% |
| `remember*` functions | 14 of 51 | 27% |
| Members of `*Defaults` objects | 17 of 683 | 2% |

## By library

### Composables

| Library | Covered | Not covered yet |
|---|---|---|
| runtime | 4 of 4 (100%) | |
| ui | 3 of 3 (100%) | |
| foundation | 14 of 16 (87%) | `BasicSecureTextField`, `BasicTooltipBox` |
| foundation-layout | 7 of 9 (77%) | `FlexBox`, `Grid` |
| animation | 3 of 5 (60%) | `SharedTransitionLayout`, `SharedTransitionScope` |
| material3 | 78 of 144 (54%) | See below |

Most of the Material 3 composables without a binding are new Material 3 Expressive components, still in alpha:
button groups, toggle buttons, loading indicators, carousels, floating toolbars and flexible app bars. Common
components not covered yet are `SwipeToDismissBox`, `PullToRefreshBox`, `TimePicker`, `DateRangePicker`,
`BasicAlertDialog` and `SecureTextField`.

### Modifiers

| Library | Covered |
|---|---|
| foundation-layout | 30 of 59 (50%) |
| foundation | 16 of 41 (39%) |
| ui | 19 of 53 (35%) |
| material3 | 3 of 17 (17%) |
| animation | 1 of 9 (11%) |

The common layout, drawing, gesture, scrolling and graphics modifiers are covered. Not covered yet are mostly focus,
drag and drop, shared elements, `anchoredDraggable` and `basicMarquee`.

### `remember*` functions

| Library | Covered |
|---|---|
| runtime | 3 of 6 (50%) |
| animation-core | 1 of 2 (50%) |
| foundation | 6 of 17 (35%) |
| material3 | 4 of 21 (19%) |
| animation, ui | 0 of 5 |

### Defaults

`*Defaults` objects hold hundreds of design tokens, such as `AlertDialogDefaults.TonalElevation`. The bound ones are
those previews use most: colors, shapes, elevations and content padding of buttons, cards, text fields, top app bars
and similar components. Without a binding, a component still uses its default; only reading the token in code is not
possible.

## How it is measured

The [binding check](../binding-check) reads the official AndroidX API files in `binding-check/api` and compares them
with every registered binding:

- **Composables:** public top-level `@Composable` functions whose name starts with an uppercase letter.
- **Modifiers:** extension functions on `Modifier`.
- **`remember*` functions:** public top-level functions whose name starts with `remember`.
- **Defaults:** members of objects whose name ends with `Defaults`.

An API counts as covered when a binding with its name exists, for the same receiver. APIs in
`binding-check/excluded.txt` are left out, such as Android-only APIs (`AndroidView`) and runtime internals
(`ComposeNode`).

To produce the numbers:

```bash
./gradlew :binding-check:jvmTest
```

The full report, with every API not covered yet, is written to `binding-check/build/reports/binding-check.txt`.

## Correctness

Coverage counts names; the binding check also verifies each binding's signature against Compose. A build fails when
a binding has:

- a parameter Compose does not have
- a required parameter missing
- parameters in a different order than Compose
- a trailing lambda that fills a different parameter than in Compose
- a parameter that it declares but never reads

So calls written for Fast Compose use the same parameter names and order as real Compose. Optional parameters that a
binding does not support yet are listed as warnings in the report.
