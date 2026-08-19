# XiaoAI LLM Redirect

[English](README.md) | 简体中文

XiaoAI LLM Redirect 是一个用于小爱同学的 LSPosed 模块，可将内置 OSBot Agent 的大模型请求重定向到自定义 OpenAI 兼容后端。

> [!IMPORTANT]
> 当前仅适配小爱同学 `8.0.17.3013`（versionCode `508000017`）。目标应用使用了混淆类名，升级小爱同学后需要重新验证 Hook 点。

## 功能

- 重定向主对话使用的 Base URL、API Key 和模型名。
- 分别配置主模型、快速模型和专家模型。
- 保留小爱原生的手动模式与自动路由档位。
- 为意图路由小模型配置独立的地址、凭据和模型。
- 可选重定向 embeddings、rerank 等辅助服务。
- 可选放开本地定时任务的会员门控。
- 在 LSPosed 日志中对 API Key 进行脱敏。

## 环境要求

- 已安装并启用 LSPosed，Xposed API 版本不低于 `93`。
- 小爱同学 `8.0.17.3013`，包名 `com.miui.voiceassist`。
- OpenAI 兼容后端支持流式 `POST /chat/completions`。
- 使用工具调用时，后端支持标准 OpenAI function calling 格式。

## 安装

1. 安装模块 APK。
2. 在 LSPosed 中启用模块，并将作用域设置为“小爱同学” (`com.miui.voiceassist`)。
3. 打开“小爱 LLM 重定向”，填写所需配置并启用重定向。
4. 保存配置后强制停止小爱同学，再重新启动。
5. 在 LSPosed 日志中搜索 `XiaoaiLLM`，确认预期的 Hook 点已加载。

## 发布

已签名 APK 可从 [GitHub Releases](../../releases) 下载。

## 配置

| 配置 | 说明 |
|---|---|
| API Base URL | OpenAI 兼容 API 根地址，例如 `https://example.com/v1`。模块使用 `/chat/completions`。 |
| API Key | 作为 `Authorization: Bearer <key>` 发送。 |
| 主模型 | 默认模式或无法匹配档位时使用的模型。 |
| 快速模型 | 手动快速模式，以及自动模式的 `DIRECT` / `FAST` 档位。留空时使用主模型。 |
| 专家模型 | 手动专家模式，以及自动模式的 `STANDARD` / `DEEP` 档位。留空时使用主模型。 |
| 路由配置 | 意图路由小模型的独立 Base URL、API Key 和模型；留空项继承主配置。 |
| 解锁定时任务 | 放开本地定时任务的会员检查。任务执行仍使用已配置的模型。 |
| 重定向辅助服务 | 仅当后端支持 `/embeddings` 等接口时启用。 |
| 详细日志 | 记录配置替换过程，API Key 会进行脱敏。 |

## 模型路由

| 小爱模式 | 模块选择 |
|---|---|
| 手动快速模式 | 快速模型 |
| 手动专家模式 | 专家模型 |
| 自动 `DIRECT` / `FAST` | 快速模型 |
| 自动 `STANDARD` / `DEEP` | 专家模型 |
| 未识别路径 | 主模型 |

手动专家模式通过 `za1.h.isExpectMode()` 判断。该模式会启用 MiClaw，但会明确将 `deepThinking` 设为 `false`，因此不能使用 thinking 开关可靠地判断专家模式。

自动模式通过 `dd.j0$f.getTier()` 获取档位。当前版本的 `STANDARD` / `DEEP` 也可能返回原始模型名 `xiaomi/mimo`，所以只根据原模型名进行映射并不可靠。

## 实现

主对话请求链路：

```text
i5.b
  -> x5.h1 SettingsRepositoryImpl
  -> d8.a LLMConfig
  -> b8.h OpenAIClient / b8.i OpenAIResponsesClient
  -> POST {baseUrl}/chat/completions
```

主要 Hook 点：

| Hook 点 | 作用 |
|---|---|
| `d8.a.getBaseUrl()` / `getApiKey()` | 替换主对话地址和凭据。 |
| `d8.a.getModel()` | 应用手动模式映射，并保留自动路由已选定的模型。 |
| `dd.j0$f.getModel()` | 按自动路由档位选择快速或专家模型。 |
| `za1.h.isExpectMode()` | 判断当前是否为手动专家模式。 |
| `dd.r0.getApiUrl()` / `getApiKey()` / `getModel()` | 重定向意图路由小模型。 |
| `w8.b.checkTimerAccess()` | 可选放开定时任务门控。 |
| `i5.b.getMIFY_LLM_BASE_URL()` / `i5.k.getMifyLlmBaseUrl()` | 可选重定向辅助服务。 |

这些名称是 APK 中实际存在的运行时类名，但属于混淆名称，可能随应用版本变化。LSPosed 自身的随机化包名与目标应用的类名混淆无关。

## 故障排查

- 修改模块配置后，需要强制停止并重新启动小爱同学。仅重启模块配置界面不会重新加载 Hook。
- 日志出现 `hook 成功 N/N` 表示当前版本的目标方法已找到。
- HTTPS 后端必须提供完整可信的证书链。Nginx/OpenResty 应配置 `fullchain.pem`，否则 Android 可能报告 `Trust anchor for certification path not found`。
- 自定义模型名建议不要包含 `mimo`，否则小爱可能将其识别为小米模型并启用专用 Responses API 适配。
- 后端响应过慢可能触发小爱的首字或空闲超时。

## 已知限制

- 生图、视频和文档生成使用独立云端服务，不经过本模块的主对话 Hook。
- ASR 与 TTS 使用小米专用接口，不会被重定向。
- 后端必须支持 SSE 流式响应；部分请求还会发送 `stream_options.include_usage`。
- 辅助服务重定向要求后端实现对应的 embeddings 或 rerank 接口。
- API Key 保存在本机 SharedPreferences 中，并需要由 LSPosed 在目标进程读取。请勿提交配置文件或包含凭据的日志。

## 许可证

[MIT](LICENSE)
