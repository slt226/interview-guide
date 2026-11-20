# 截图目录

存放 README「效果展示」小节引用的界面截图，由 `frontend/scripts/capture-screenshots.mjs` 生成。

| 文件 | 页面 |
|---|---|
| `01-login.png` | 登录页 |
| `02-interview-hub.png` | 面试中心 |
| `03-resume-library.png` | 简历库 |
| `04-knowledgebase.png` | 知识库管理 |
| `05-rag-chat.png` | 知识库问答 |
| `06-interview-history.png` | 面试记录 |
| `07-dead-letter.png` | 死信管理 |
| `08-settings.png` | 设置页 |

重新生成：

```bash
# 先灌演示数据（可重复执行，脚本内部按 payload_identifier 清理）
docker cp scripts/demo-seed.sql interview-postgres:/tmp/demo-seed.sql
docker exec interview-postgres psql -U postgres -d interview_guide -f /tmp/demo-seed.sql

# 再截图（默认复用系统 Chrome；没有 Chrome 时设 PW_CHANNEL=msedge）
cd frontend
DEMO_USER=saber123 DEMO_PASS=saber123 node scripts/capture-screenshots.mjs
```

脚本前置条件：后端 8080 与前端 5173 已启动。截图里的账号是本地演示账号，数据全部由 `scripts/demo-seed.sql` 构造。
