# Resources

Code can use images and strings from Android `R` and Compose Multiplatform `Res`, as in an app:

```kotlin
Image(painterResource(R.drawable.logo), contentDescription = null)
Text(stringResource(Res.string.title))
```

Resources are part of the full API, so they work in `FastComposePreview` and `FastComposeContent`. The files come
from a `ResourceProvider` that the host app supplies.

## Providing files

Pass a provider to the preview:

```kotlin
val resources = MemoryResources()
resources.put("drawable/logo.xml", logoXml)
resources.put("drawable-night/logo.xml", darkLogoXml)
resources.put("values/strings.xml", stringsXml)
resources.put("values-ar/strings.xml", arabicStringsXml)

FastComposePreview(code, resources = resources)
```

Or provide `LocalResourceProvider` around the preview instead:

```kotlin
CompositionLocalProvider(LocalResourceProvider provides resources) {
    FastComposePreview(code)
}
```

Files are laid out like Android `res/` or Compose Multiplatform `composeResources/`. `R.drawable.logo` and
`Res.string.title` resolve by type and name.

## Providers

| Provider | Reads files from |
|---|---|
| `MemoryResources` | Files you put in it, e.g. from drag and drop or an editor |
| `StorageResources` | A folder, through a `ResourceStorage` such as `FileStorage` |
| `UrlResources` | A base URL, downloading files when previews need them |

### MemoryResources

Holds the files you put in it with `put(path, bytes)`. Previews update when files change.

### StorageResources

`StorageResources(storage, scope)` reads a folder through a `ResourceStorage`, which lists paths with versions and
reads files. Only paths are listed up front, from the folders previews use; a file is read when a preview first needs
it, then cached. Call `refresh()` after files change.

- `FileStorage(folder)`, on the JVM and Android, uses `java.io`. On the JVM, `storage.watch { resources.refresh() }`
  follows changes.
- Other file systems, such as FileKit or Okio, need a small `ResourceStorage` adapter.

### UrlResources

`UrlResources(baseUrl, scope, fetch)` downloads files when previews need them. An optional `resources.json` file, an
array of paths, lists the files; without it, strings come from `values*/strings.xml` and images are found by trying
`.png`, `.webp`, `.jpg` and `.xml`.

## Variants

Variants follow the preview's `@Preview` settings and screen density:

| Qualifier | Examples |
|---|---|
| Locale | `values-ar`, `values-pt-rBR`, `values-b+pt+BR` |
| Night mode | `drawable-night` |
| Density | `drawable-mdpi` … `drawable-xxxhdpi` |

## Formats

- **Images:** PNG, JPEG, WebP, BMP and vector drawable XML. SVG is not supported yet.
- **Strings:** `strings.xml`, with `%s`, `%d`, `%.2f` and `%1$s` arguments.

## Missing files

- A missing image draws a placeholder, and a missing string shows its name; both are logged as warnings.
- While a file is being read, images show a placeholder and nothing is logged.
