# Wiser Home REST Proxy

A REST proxy server for the Wiser Home Smart Hub. It acts as an intermediate HTTP gateway that forwards REST API requests to a local Wiser hub, managing authentication and ensuring clean header forwarding.

---

## Features

- **Automated Scheme Prepending**: Automatically resolves target hub URLs (e.g. defaulting to `http://wiser.local`) and handles missing protocols or trailing slashes seamlessly.
- **Header Injection & Filtering**: Injects the required Wiser hub `Secret` authentication header while filtering out hop-by-hop/metadata headers (e.g. `Connection`, `Host`, `Content-Length`) that could interfere with the HTTP client.
- **Concurrent Request Handling**: Employs a Java cached thread pool executor to handle multiple proxy requests concurrently, preventing serial request blocking.
- **Command Line Interface**: Configurable via command-line arguments or environment variables.
- **Spock Test Suite**: Covered by unit tests for argument parsing and request proxying behavior.

---

## Command Line Usage

You can launch the proxy with custom options via the CLI. If no arguments are provided, it will check environment variables and fall back to defaults.

### Options

| Short Flag | Long Flag | Environment Variable | Default Value | Description |
| :--- | :--- | :--- | :--- | :--- |
| `-h` | `--help` | - | - | Print the help and usage message. |
| `-p` | `--port` | `WISER_PROXY_PORT` | `9080` | The port the proxy server will listen on. |
| `-s` | `--secret` | `WISER_SECRET` | - | Your Wiser hub secret token. |
| `-u` | `--url` | `WISER_URL` | `http://wiser.local` | The target URL of your Wiser Home hub. |

---

## Server Endpoints

Once running, the proxy server exposes the following HTTP endpoints:

### 1. `/data` Proxy Endpoint
* **Methods**: `GET`, `POST`, `PATCH`
* **Path**: `/data/*`
* **Description**: Any request starting with `/data` is forwarded directly to the Wiser hub. 
  For example, a `GET` request to `http://localhost:9080/data/domain/` will be proxied as a `GET` request to `http://wiser.local/data/domain/` with the configured secret token automatically injected as the `Secret` header.

### 2. `/status` Endpoint
* **Methods**: `GET`
* **Description**: Simple health check endpoint.
* **Response**:
  ```json
  {
    "status": "ok",
    "timestamp": 1719733800000
  }
  ```

### 3. `/hello` Endpoint
* **Methods**: `GET`
* **Description**: Simple greeting endpoint showing the hostname of the requester.

---

## Building and Running

The project uses Gradle for builds and execution.

### Prerequisites
- JDK 17 or higher

### Compile Code
To compile the Java and Groovy sources, run:
```bash
./gradlew compileJava compileGroovy
```

### Run Server
To run the server with default configurations:
```bash
./gradlew run
```

To run the server and pass custom arguments:
```bash
./gradlew run --args="-p 8080 -u http://wiser.local -s MY_SECRET_TOKEN"
```

### Run Tests & Coverage
To execute the Spock unit and integration tests along with JaCoCo code coverage:
```bash
./gradlew check jacocoTestReport
```

---

## Test & CI Architecture

### 1. Spock Test Suite
Located under [`src/test/groovy/com/fizzpod/wiserproxy/`](file:///workspace/wiser-home-rest-proxy/src/test/groovy/com/fizzpod/wiserproxy/):
- **[CLISpec.groovy](file:///workspace/wiser-home-rest-proxy/src/test/groovy/com/fizzpod/wiserproxy/CLISpec.groovy)**: Unit tests verifying CLI option parsing, short/long flag overrides, and default arguments.
- **[ProxyIntegrationSpec.groovy](file:///workspace/wiser-home-rest-proxy/src/test/groovy/com/fizzpod/wiserproxy/ProxyIntegrationSpec.groovy)**: Integration tests using an in-memory HTTP target server to verify real HTTP GET, POST, status checks, secret header injection, and response streaming.

### 2. GitHub Actions Workflows
Found in [`.github/workflows/`](file:///workspace/wiser-home-rest-proxy/.github/workflows/):
- **CI Workflow (`ci.yml`)**: Triggered on pushes/PRs to `main` or `develop`. Compiles code, runs tests, generates JaCoCo coverage reports, and builds the shadow JAR.
- **Release Workflow (`release.yml`)**: Triggered when a version tag (`v*` or `release-*`) is pushed. Compiles shadow JARs and distribution archives (`.zip`, `.tar`) and automatically publishes a GitHub Release with binary assets attached.

---

## Developer Workflow

1. **Branching Model**:
   - `develop`: Main development branch for new features and bug fixes.
   - `main`: Production-ready release branch.
2. **Submitting Changes**:
   - Create a feature branch off `develop` and submit a Pull Request targeting `develop`.
   - GitHub Actions CI will run tests and coverage checks automatically.
3. **Creating a Release**:
   - Merge `develop` into `main`.
   - Tag the release commit (e.g. `v1.0.0` or `release-1.0.0`) and push the tag:
     ```bash
     git tag -a v1.0.0 -m "Release v1.0.0"
     git push origin v1.0.0
     ```
   - GitHub Actions will assemble all binary distributions (`shadowJar`, `distZip`, `distTar`) and publish the release with attached assets.
