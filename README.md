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

### Run Tests
To execute the Spock unit tests:
```bash
./gradlew test
```

---

## Test Architecture

The codebase includes two Spock specifications under `src/test/groovy/com/fizzpod/wiserproxy/`:
- **[CLISpec.groovy](file:///workspace/wiser-home-rest-proxy/src/test/groovy/com/fizzpod/wiserproxy/CLISpec.groovy)**: Verifies CLI argument parsing, option overrides, and environment variable fallbacks.
- **[ProxyFunctionsSpec.groovy](file:///workspace/wiser-home-rest-proxy/src/test/groovy/com/fizzpod/wiserproxy/ProxyFunctionsSpec.groovy)**: Verifies internal URL resolution, header filtering (stripping hop-by-hop headers), and authentication insertion.
