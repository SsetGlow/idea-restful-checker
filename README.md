# restful-checker

IntelliJ IDEA plugin for searching Spring-style REST endpoints and calling them from the editor.

## Features

- Search REST endpoints with `Command + \` on macOS.
- Browse and call endpoints from the right-side `Restful` tool window.
- Show a gutter action on Spring mapping methods.
- Call endpoints with editable hosts, headers, query parameters, request bodies, and path variables.
- Infer first-time request params and body defaults from explicit `@RequestParam` and `@RequestBody` parameters.
- Resolve host placeholders and Spring path prefixes from project configuration and plugin variables.

## Development

```bash
./gradlew runIde
```

The plugin scans common Spring configuration files such as `application.yml`, `application.yaml`, and `application.properties`.

## Build

```bash
./gradlew buildPlugin
```

`buildPlugin` and `publishPlugin` automatically increment `pluginVersion` in `gradle.properties` before packaging, so each generated Marketplace artifact has a new patch version. The ZIP artifact is written to `build/distributions/`.

## Publish to JetBrains Marketplace

First-time publication must be created from the JetBrains Marketplace web UI:

1. Log in to https://plugins.jetbrains.com.
2. Open the account menu and choose `Upload plugin`.
3. Select or create a vendor profile and accept the Marketplace Developer Agreement.
4. Upload the ZIP from `build/distributions/`.
5. Fill in plugin details, license/EULA, tags, release channel, source code URL, and hidden/ads flags as needed.

After the plugin exists on Marketplace, later updates can be uploaded with Gradle:

```bash
export PUBLISH_TOKEN=perm:...
./gradlew publishPlugin
```

If signing is required, provide the signing values through environment variables before publishing:

```bash
export CERTIFICATE_CHAIN=...
export PRIVATE_KEY=...
export PRIVATE_KEY_PASSWORD=...
```
