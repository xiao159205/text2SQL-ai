# AI Agent大数据智能查询系统

面向企业数据分析场景的智能查数系统，支持通过自然语言查询 MySQL/Hive 数据、生成 SQL、展示图表并检索业务知识库。工程包含 Spring Boot 后端与 React/Vite 前端。

## 项目结构

- `channel-antifraud-ai_backend`：Java 17、Spring Boot、MyBatis 后端
- `channel-antifraud-ai-frontend`：React、Vite 前端

头像文件运行时写入前端 `src/asserts`，数据库仅保存 `src/asserts/<文件名>` 相对路径；运行时头像不会提交到 Git。

详细说明见后端目录中的 `README.md`。

项目已支持 MySQL 持久化的多轮上下文和历史会话：每轮保存问题、响应、SQL 与 AST，后续问题会携带最近几轮上下文生成新的完整查询计划。
