# XiaoAI LLM Redirect

English | [简体中文](README.zh-CN.md)

XiaoAI LLM Redirect is an LSPosed module that redirects LLM requests from XiaoAI's built-in OSBot Agent to a custom OpenAI-compatible backend.

> [!IMPORTANT]
> The module currently supports XiaoAI `8.0.17.3013` (versionCode `508000017`). The target app uses obfuscated runtime names, so hooks must be verified again after upgrading XiaoAI.

## Features

- Redirects the base URL, API key, and model used by the main conversation agent.
- Supports separate main, fast, and expert models.
- Preserves XiaoAI's manual modes and automatic routing tiers.
- Supports independent endpoint and model settings for the intent router.
- Optionally redirects auxiliary services such as embeddings and reranking.
- Optionally unlocks the membership gate for local scheduled tasks.
- Redacts API keys in LSPosed logs.

## Requirements

- LSPosed with Xposed API version `93` or newer.
- XiaoAI `8.0.17.3013`, package name `com.miui.voiceassist`.
- An OpenAI-compatible backend with streaming `POST /chat/completions` support.
- Standard OpenAI function calling support when XiaoAI invokes tools.

## Installation

1. Install the module APK.
2. Enable the module in LSPosed and set its scope to XiaoAI (`com.miui.voiceassist`).
3. Open "XiaoAI LLM Redirect", enter the required settings, and enable redirection.
4. Save the settings, force-stop XiaoAI, and start it again.
5. Search the LSPosed logs for `XiaoaiLLM` and verify that the expected hooks loaded.

## Configuration

| Setting | Description |
|---|---|
| API Base URL | OpenAI-compatible API root, such as `https://example.com/v1`. The module uses `/chat/completions`. |
| API Key | Sent as `Authorization: Bearer <key>`. |
| Main model | Used by the default mode and unmatched execution paths. |
| Fast model | Used by manual Quick mode and the automatic `DIRECT` / `FAST` tiers. Falls back to the main model when empty. |
| Expert model | Used by manual Expert mode and the automatic `STANDARD` / `DEEP` tiers. Falls back to the main model when empty. |
| Router settings | Independent base URL, API key, and model for the intent router. Empty values inherit the main settings. |
| Unlock scheduled tasks | Bypasses the membership check for local scheduled tasks. Task execution still uses the configured model. |
| Redirect auxiliary services | Enable only when the backend implements endpoints such as `/embeddings`. |
| Verbose logging | Logs configuration replacements. API keys are redacted. |

## Model Routing

| XiaoAI mode | Selected model |
|---|---|
| Manual Quick mode | Fast model |
| Manual Expert mode | Expert model |
| Automatic `DIRECT` / `FAST` | Fast model |
| Automatic `STANDARD` / `DEEP` | Expert model |
| Unrecognized path | Main model |

Manual Expert mode is detected through `za1.h.isExpectMode()`. This mode enables MiClaw but explicitly sets `deepThinking` to `false`, so the thinking flag cannot identify Expert mode reliably.

Automatic mode is mapped through the tier returned by `dd.j0$f.getTier()`. In the supported XiaoAI version, `STANDARD` and `DEEP` can also return the original model name `xiaomi/mimo`, which makes model-name-only routing unreliable.

## Implementation

Main conversation request path:

```text
i5.b
  -> x5.h1 SettingsRepositoryImpl
  -> d8.a LLMConfig
  -> b8.h OpenAIClient / b8.i OpenAIResponsesClient
  -> POST {baseUrl}/chat/completions
```

Primary hooks:

| Hook | Purpose |
|---|---|
| `d8.a.getBaseUrl()` / `getApiKey()` | Replaces the main conversation endpoint and credentials. |
| `d8.a.getModel()` | Applies manual mode mapping and preserves models selected by automatic routing. |
| `dd.j0$f.getModel()` | Selects the fast or expert model from the automatic routing tier. |
| `za1.h.isExpectMode()` | Detects manual Expert mode. |
| `dd.r0.getApiUrl()` / `getApiKey()` / `getModel()` | Redirects the intent router. |
| `w8.b.checkTimerAccess()` | Optionally unlocks the scheduled-task gate. |
| `i5.b.getMIFY_LLM_BASE_URL()` / `i5.k.getMifyLlmBaseUrl()` | Optionally redirects auxiliary services. |

These are the actual runtime names stored in the APK, but they are obfuscated and may change between app versions. LSPosed package randomization is unrelated to target-app name obfuscation.

## Troubleshooting

- Force-stop and restart XiaoAI after changing module settings. Restarting only the configuration app does not reload the hooks.
- A log entry containing `hook 成功 N/N` indicates that the expected methods were found.
- HTTPS backends must serve a complete trusted certificate chain. Configure Nginx or OpenResty with `fullchain.pem`; otherwise Android may report `Trust anchor for certification path not found`.
- Avoid custom model names containing `mimo`. XiaoAI may classify them as Xiaomi models and enable its specialized Responses API adapter.
- Slow backends may trigger XiaoAI's first-token or idle timeout.

## Known Limitations

- Image, video, and document generation use separate cloud services and do not pass through the main conversation hooks.
- Xiaomi-specific ASR and TTS endpoints are not redirected.
- The backend must support SSE streaming. Some requests also include `stream_options.include_usage`.
- Auxiliary redirection requires the backend to implement the corresponding embeddings or reranking endpoints.
- The API key is stored in local SharedPreferences and must be readable by LSPosed in the target process. Do not commit configuration files or logs containing credentials.

## License

[MIT](LICENSE)
