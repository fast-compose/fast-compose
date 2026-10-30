# Bindings

A binding maps a name in user code to a real Compose API. Bindings live in packs; the parser and
interpreter never change when an API is added.

## Packages

All code is in the `fast-compose` module, in packages under `ma7moud3ly.fast.compose`:

| Package               | Contents                                                                    | Changes when                |
|-----------------------|-----------------------------------------------------------------------------|-----------------------------|
| `parser`              | Kotlin source → AST                                                         | Kotlin syntax support grows |
| `interpreter`         | Runs the AST; `Registry`, `Args`, `Signature`, `BindingPack`                | Language features grow      |
| `bindings.foundation` | `FoundationBindings`: layouts, `Text`, state, modifiers, drawing, animation | Foundation APIs are added   |
| `bindings.material3`  | `Material3Bindings`: Material 3 components and defaults                     | Material APIs are added     |
| `bindings.icons`      | `IconBindings`: `Icons.*`                                                   | Icon sets are added         |
| `bindings.resources`  | `ResourceBindings`: `R`/`Res`, `painterResource`, `stringResource`          | Resource kinds are added    |
| `bindings.coil`       | `CoilBindings`: `AsyncImage`, `rememberAsyncImagePainter`                   | Coil APIs are added         |
| `ui`                  | `FastComposePreview`, `BasicComposePreview`, presets, `@Preview` config     | Preview UI changes          |

## Adding a component

Find the pack for the API's library and register it with its signature, written like the Kotlin declaration:

```kotlin
composable("Badge(modifier, containerColor, content)") { a ->
    Badge(
        modifier = a.modifier(),
        containerColor = a.color("containerColor", BadgeDefaults.containerColor),
        content = a.scopedContentOrNull<RowScope>("content"),
    )
}
```

- List parameters in the same order as the real API, so positional calls work.
- A trailing lambda fills the last parameter before `*`.
- `vararg keys` collects the remaining positional arguments: `"remember(vararg keys, calculation)"`.
- Parameters after `*` can only be passed by name.
- Unknown parameter names are reported to the user with a suggestion.

## Overloads

Register the same name again with another signature. A call uses the first signature its arguments fit:

```kotlin
member<Modifier>("padding(all)") { m, a -> m.padding(a.dp("all")) }
member<Modifier>("padding(horizontal, vertical)") { m, a -> m.padding(a.dp("horizontal"), a.dp("vertical")) }
```

When overloads take the same number of arguments, add `name: Type` to tell them apart by argument type:

```kotlin
member<Modifier>("padding(all: Dp)") { m, a -> m.padding(a.dp("all")) }
member<Modifier>("padding(paddingValues: PaddingValues)") { m, a -> m.padding(a.typed("paddingValues", PaddingValues())) }
```

Types are Kotlin basics (`String`, `Int`, `Float`, `Number`, `Boolean`, `Pair`, `List`, `Map`, `Any`), function types
(`() -> Unit`), or names registered with `type<T>("Name")`. Unknown types match anything.

## Kinds of bindings

| Registration                                       | User code                         |
|----------------------------------------------------|-----------------------------------|
| `composable("Text(text, ...)")`                    | `Text("Hi")`                      |
| `function("Color(color)")`                         | `Color(0xFF00FF00)`               |
| `suspendFunction("delay(timeMillis)")`             | `delay(500)`                      |
| `value("Modifier", Modifier)`                      | `Modifier`                        |
| `member<Modifier>("padding(all)")`                 | `Modifier.padding(8.dp)`          |
| `composableMember<CompositionLocal<*>>("current")` | `LocalContentColor.current`       |
| `suspendMember<Animatable<*, *>>("animateTo(targetValue, animationSpec)")` | `anim.animateTo(1f)` |
| `properties<Offset>("x" to { it.x })`              | `offset.x`                        |
| `constants<Alignment.Companion>("Center" to ...)`  | `Alignment.Center`                |
| `type<Modifier>("Modifier")`                       | `x is Modifier`                   |
| `hint("Card", "...")`                              | message for an unknown name       |

## Reading arguments

`Args` getters never throw. A missing argument gives the default; a value of the wrong type is reported and
replaced by the default.

- Values: `string`, `int`, `long`, `float`, `boolean`, `dp`, `sp`, `color`, `typed<T>`, `modifier()`
- Composable lambdas: `content`, `scopedContent<S>`, `content1`, `decorator` (wraps inner content, e.g. `decorationBox`)
- Callbacks: `action`, `action1`, `action2`, `scopedAction<S>`, `function1`, `function2`, `composableFunction1`
- Calculations: `calculation`, `scopedCalculation<S>`, `scopedFunction1<S>`, `scopedFunction2<S>`, `suspendBlock<S>`,
  `suspendAction1`
- `receiver<T>()`: the implicit receiver at the call site, e.g. `RowScope` for `Modifier.weight(1f)`

## Writing a new pack

```kotlin
object MyDesignSystem : BindingPack {
    override fun install(registry: Registry) = with(registry) {
        composable("BrandButton(text, onClick, modifier)") { a ->
            BrandButton(a.string("text"), a.action("onClick"), a.modifier())
        }
    }
}

FastComposePreview(code, registry = rememberFullRegistry(MyDesignSystem))
```

## Resources

`ResourceBindings` binds `R`/`Res`, `painterResource` and `stringResource`. See [RESOURCES.md](RESOURCES.md) for how
files are provided and resolved.

## Network images

`CoilBindings` binds Coil's `AsyncImage` and `rememberAsyncImagePainter`. It is part of the full API, so
`FastComposePreview` and `rememberFullRegistry()` include it.

`AsyncImage(model = …)` passes the model to Coil unchanged and uses Coil's singleton `ImageLoader`, which a host
configures with `setSingletonImageLoaderFactory`. Images that fail to load draw a placeholder and are logged.

## Checking bindings against Compose

`binding-check` compares every binding signature with the AndroidX API files in `binding-check/api`:

```bash
./gradlew :binding-check:jvmTest
```

- Errors fail the test: unknown parameters, missing required parameters, a different parameter order, or a trailing
  lambda that fills a different parameter than in Compose.
- Warnings, bindings not found in the API files, and coverage (composables, `Modifier` functions, `*Defaults`
  members and `remember*` functions) are written to `binding-check/build/reports/binding-check.txt`.
- `binding-check/allowlist.txt` lists findings to ignore, one per line, exactly as the report prints them.
- `binding-check/excluded.txt` lists APIs left out of the coverage numbers, such as Android-only or internal APIs.
- Parameters that a signature declares but the binding never reads fail the test too. A parameter is read when its
  name appears as a string in the binding or in a helper it calls. `binding-check/unused-allowlist.txt` lists
  intended exceptions as `Binding.parameter`.
- `binding-check/update-api.sh` downloads the API files; update its versions when the Compose version changes.

## Stability

- `Registry`, `Args`, `Signature` and `BindingPack` are the contract between the engine and packs.
  New getters and registration functions may be added; existing ones keep their behavior.
- Packs depend only on that contract, never on interpreter internals.
- Presets in `fast-compose` are built once and shared; user packs and extensions are layered on them
  without copying.
