# text2SQL-ai

渠道反欺诈 Text2SQL AI 工程，包含 Spring Boot 后端与 React/Vite 前端。

## 项目结构

- `channel-antifraud-ai_backend`：Java 17、Spring Boot、MyBatis 后端
- `channel-antifraud-ai-frontend`：React、Vite 前端

头像文件运行时写入前端 `src/asserts`，数据库仅保存 `src/asserts/<文件名>` 相对路径；运行时头像不会提交到 Git。

详细说明见后端目录中的 `README.md`。
