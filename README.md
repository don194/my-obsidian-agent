# Obsidian Agent

一个基于 Spring AI 构建的、旨在与 Obsidian 知识库进行交互的智能代理项目。

## 🌟 功能简介

- **核心 Agent 能力**: 基于 Spring AI 实现，支持通过 ReAct 模式进行工具调用（如查询时间）。
- **会话管理**: 提供完整的 RESTful API 用于创建、查询、删除会话及历史消息，使用 SQLite 进行持久化存储。
- **流式输出**: 基于 SSE (Server-Sent Events) 实现流畅的流式聊天体验。
- **Obsidian 集成**: 通过 MCP (Model Context Protocol) 连接外部 [mcp-obsidian](https://github.com/MarkusPfundstein/mcp-obsidian) 服务，为操作 Obsidian 笔记提供接口。
- **灵活配置**: 支持多环境配置及通过环境变量设置关键信息。

## ⚠️ 温馨提示

这是一个个人项目，主要目的是为了深入学习和实践 Spring AI 框架。作为练习项目，其中部分代码实现可能还不够成熟或优雅，欢迎任何形式的交流与指正。如果您有任何想法或建议，请随时提出 Issue！

## 🚀 快速开始

### 环境要求

- JDK 17+
- Maven
- （必须）[Obsidian Local REST API 插件](https://github.com/coddingtonbear/obsidian-local-rest-api)
- （必须）[mcp-obsidian](https://github.com/MarkusPfundstein/mcp-obsidian) 服务端

### 运行步骤

1. **克隆项目**

   ```
   git clone https://github.com/your-username/obsidian-agent.git
   cd obsidian-agent
   ```

2. **配置 API Key** 在 `src/main/resources/application.yml` 或 `application-dev.yml` 中配置你的大模型 API Key 和 Base URL。

   ```
   spring:
     ai:
       openai:
         api-key: "sk-..."
         base-url: "https://api.openai.com/v1"
   ```

3. **【重要】配置 MCP 服务** 本项目通过一个外部的 `mcp-server` 来和 Obsidian 通信。您需要： a. 确保 Obsidian 中已安装并启用了 **Local REST API** 插件。 b. 自行准备并运行一个 [mcp-obsidian](https://github.com/MarkusPfundstein/mcp-obsidian) 服务端。 c. 修改 `src/main/resources/mcp-connections.yml` 文件，将其中的 `directory` 路径指向你本地的 `mcp-obsidian` 项目路径，并填入正确的 `OBSIDIAN_API_KEY`。

4. **运行应用**

   ```
   mvn spring-boot:run
   ```

   应用将在 `http://localhost:8080` 启动。

## 🗺️ 未来计划 (TODO)

- [ ] **集成 RAG**: 实现基于 Obsidian 仓库内容的知识库问答。
- [ ] **开发配套前端**: 创建一个美观易用的前端界面。
- [ ] **自研 mcp-obsidian 服务**: 亲自实现一个功能更完备的、对接 Obsidian 的 `mcp-server`。
- [ ] **文件上传与处理**: 支持上传文件，并让 Agent 能够理解和处理文件内容。
- [ ] **Docker 化部署**: 提供 Dockerfile，实现一键部署。

## 致谢 (Acknowledgements)

本项目 Agent 的部分实现思路参考了 [Cunninger/my-ai-agent](https://github.com/Cunninger/my-ai-agent) 项目（该项目基于 Apache License 2.0 开源），并在此基础上进行了一些改动。

## 📜 开源协议

本项目基于 **Apache License 2.0** 开源，详细内容请查看 `LICENSE` 文件。