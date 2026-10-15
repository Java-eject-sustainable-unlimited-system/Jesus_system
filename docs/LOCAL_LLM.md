# Local LLM Security Review

`tools/llm_guard.py` is an optional, read-only Python orchestrator that asks a
local Ollama model to review the Java watchdog and the Ada/Rust JVM prototype.
It does not alter files, execute model-generated instructions, terminate
processes, or trigger watchdog recovery. The existing Java watchdog remains
responsible for its configured health checks and recovery behavior.

## Setup and usage

Install and start Ollama separately, then pull a code-capable model, for example:

```sh
ollama pull qwen2.5-coder:7b
ollama serve
```

From the repository root, run:

```sh
python3 tools/llm_guard.py --model qwen2.5-coder:7b
```

The tool reads Java sources under `src/main/java`, Ada sources under
`ada-jvm/src`, and Rust sources under `ada-jvm/rust-vm/src`. It sends one source
file at a time to the Ollama `/api/chat` endpoint and prints the model's review.
Use `--root` for another checkout, `--ollama-url` for a different loopback
address, and `--timeout` to adjust the per-file request timeout. Only HTTP
loopback URLs are accepted so source is not intentionally sent to a remote
service. This is an LLM-assisted review, not a substitute for tests, formal
verification, or expert security review; findings may be incomplete or
incorrect.

Run its standard-library tests with:

```sh
python3 -m unittest discover -s tools -p 'test_*.py'
```
