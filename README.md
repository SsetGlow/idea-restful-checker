# restful-checker

`restful-checker` is an IntelliJ IDEA plugin for finding Spring-style REST endpoints in a project and calling them directly inside the IDE.

## What It Does

- Finds Spring MVC endpoints declared with mapping annotations.
- Shows endpoint call actions in the editor gutter.
- Provides a `Restful` tool window for browsing and opening endpoints.
- Opens a REST call dialog with editable host, URL preview, path variables, request parameters, headers, and request body.
- Generates a cURL preview for the current request.
- Displays response headers and body inside the dialog.
- Remembers per-endpoint request data and cURL panel height.
- Resolves Spring path prefixes and host placeholders from common project configuration files and plugin variables.

## Usage

1. Install the plugin ZIP from disk in IntelliJ IDEA.
2. Open a Spring project.
3. Use one of these entry points:
   - Press `Command + \` on macOS to search REST endpoints.
   - Open the right-side `Restful` tool window to browse endpoints.
   - Click the gutter action beside a Spring mapping method.
4. In the call dialog:
   - Select or type the host.
   - Edit path variables, request parameters, headers, and body as needed.
   - Review the generated cURL command.
   - Click `Send` to call the endpoint.

The call dialog is modeless, so it does not block the rest of IDEA while a local request is stopped at a debugger breakpoint.

## Build

```bash
./gradlew buildPlugin
```

The generated plugin ZIP is written to `build/distributions/`.
