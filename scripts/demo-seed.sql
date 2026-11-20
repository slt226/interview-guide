-- ============================================================
-- 演示数据：仅用于 README 截图与本地展示，不代表真实业务数据
--
-- 执行方式（先拷进容器，避免 Windows 管道编码问题）：
--   docker cp scripts/demo-seed.sql interview-postgres:/tmp/demo-seed.sql
--   docker exec interview-postgres psql -U postgres -d interview_guide -f /tmp/demo-seed.sql
--
-- 前置条件：库里已有 saber123 账号（user_id=6）、1 份简历、1 个知识库
-- 清理方式见文件末尾
-- ============================================================

SET client_encoding = 'UTF8';

BEGIN;

-- ------------------------------------------------------------
-- 1. 死信列表：覆盖 5 种任务类型与 3 种状态，让筛选器有真实维度
-- ------------------------------------------------------------
DELETE FROM async_task_dead_letter
WHERE error_message LIKE '%e2e%' OR payload_identifier IN ('kb:2', 'kb:3');

INSERT INTO async_task_dead_letter
  (stream_key, group_name, message_id, task_type, payload, payload_identifier,
   error_message, status, retry_count, replay_count, created_at, last_replay_at, updated_at)
VALUES
  ('knowledgebase:vectorize:stream', 'vectorize-group', '1732091400000-0',
   'knowledgebase:vectorize:stream',
   '{"kbId":3,"retryCount":3}', 'kb:3',
   'Embedding 调用失败：qwen3-embedding 返回 429 Too Many Requests（并发 8，限流 5/s）',
   'DEAD', 3, 0,
   now() - interval '38 minutes', NULL, now() - interval '35 minutes'),

  ('resume:analyze:stream', 'analyze-group', '1732090500000-0',
   'resume:analyze:stream',
   '{"resumeId":7,"retryCount":3}', 'resume:7',
   '结构化输出解析失败：已重试 3 次，返回体缺少 questions 字段',
   'REPLAYED', 3, 1,
   now() - interval '2 hours', now() - interval '90 minutes', now() - interval '90 minutes'),

  ('interview:evaluate:stream', 'evaluate-group', '1732089600000-0',
   'interview:evaluate:stream',
   '{"sessionId":"a3f1c9e2-8b47-4d16-9f3a-2c5e8d7b1a04","retryCount":3}', 'session:a3f1c9e2',
   'LLM 调用超时 30000ms（provider=dashscope, model=qwen3.5-flash）',
   'RESOLVED', 3, 1,
   now() - interval '5 hours', now() - interval '4 hours', now() - interval '4 hours'),

  ('knowledgebase:question-gen:stream', 'question-gen-group', '1732086000000-0',
   'knowledgebase:question-gen:stream',
   '{"kbId":5,"taskId":"task-2f8e","retryCount":3}', 'question-gen:task-2f8e',
   '生成题目数量不足：期望 8 题，实际返回 3 题，草稿已保留避免静默丢失',
   'DEAD', 3, 0,
   now() - interval '1 day', NULL, now() - interval '1 day'),

  ('voice:evaluate:stream', 'voice-evaluate-group', '1732082400000-0',
   'voice:evaluate:stream',
   '{"voiceSessionId":12,"retryCount":3}', 'voice:12',
   'TTS 单句合成超时（上限 8s），已中断本轮评估',
   'RESOLVED', 3, 2,
   now() - interval '2 days', now() - interval '2 days', now() - interval '2 days');

-- ------------------------------------------------------------
-- 2. 简历与知识库：换成可读的名字，补一份像样的分析结果
-- ------------------------------------------------------------
UPDATE resumes
   SET original_filename = '陈明-Java后端-3年.txt'
 WHERE id = 1;

UPDATE resume_analyses
   SET overall_score     = 82,
       structure_score   = 85,
       content_score     = 78,
       expression_score  = 80,
       skill_match_score = 84,
       project_score     = 83,
       summary = '技术栈与 Java 后端岗位匹配度高：Spring Boot 与 Redis 使用有实战痕迹，项目描述能量化。'
                 || '主要短板是缺少 JVM 调优与高并发场景的深度，简历中「负责」类表述偏多，建议补充可验证的结果指标。'
 WHERE resume_id = 1;

UPDATE knowledge_bases
   SET name     = 'Spring 核心机制笔记.md',
       category = '后端基础'
 WHERE id = 3;

-- ------------------------------------------------------------
-- 3. 面试记录：两份已完成出分，其余保留「已创建」作为进行中态
-- ------------------------------------------------------------
UPDATE interview_sessions
   SET status           = 'EVALUATED',
       evaluate_status  = 'COMPLETED',
       overall_score    = 82,
       completed_at     = now() - interval '3 days',
       overall_feedback = '基础扎实，能讲清 Spring Bean 生命周期与 AOP 代理选择；'
                          || '回答偏结论化，缺少踩坑细节。建议按「现象—定位—方案—验证」四段式展开。'
 WHERE id = 5;

UPDATE interview_sessions
   SET status           = 'EVALUATED',
       evaluate_status  = 'COMPLETED',
       overall_score    = 76,
       completed_at     = now() - interval '6 days',
       overall_feedback = '对 Redis 缓存三兄弟（穿透/击穿/雪崩）区分清楚，但分布式锁的续期与误删场景回答不完整。'
 WHERE id = 3;

-- ------------------------------------------------------------
-- 4. 语音面试：补全评估结果，避免列表出现 0 分
-- ------------------------------------------------------------
UPDATE voice_interview_sessions
   SET actual_duration = 1102,
       end_time        = now() - interval '1 day'
 WHERE id = 1;

UPDATE voice_interview_evaluations
   SET overall_score      = 86,
       interviewer_role   = 'java-backend',
       overall_feedback   = '表达流畅，项目介绍有层次。追问到 Redis 持久化时能说出 RDB/AOF 取舍，'
                            || '但对 AOF 重写期间的阻塞影响回答含糊，建议补充 fork 与写时复制的细节。',
       strengths_json     = '["表达清晰，语速稳定","项目背景交代完整，能讲清个人贡献","缓存与消息队列的使用场景区分准确"]',
       improvements_json  = '["补充 AOF 重写与 fsync 策略的具体影响","回答技术选型时给出量化依据","减少「大概/可能」这类模糊表述"]'
 WHERE id = 1;

-- ------------------------------------------------------------
-- 5. RAG 问答：一条真实形态的多轮对话（含知识库引用）
-- ------------------------------------------------------------
DELETE FROM rag_chat_messages WHERE session_id = 1001;
DELETE FROM rag_session_knowledge_bases WHERE session_id = 1001;
DELETE FROM rag_chat_sessions WHERE id = 1001;

INSERT INTO rag_chat_sessions (id, title, status, message_count, is_pinned, created_at, updated_at, user_id)
VALUES (1001, 'Spring Bean 生命周期与循环依赖', 'ACTIVE', 2, true,
        now() - interval '3 hours', now() - interval '3 hours', 6);

INSERT INTO rag_session_knowledge_bases (session_id, knowledge_base_id) VALUES (1001, 3);

INSERT INTO rag_chat_messages (session_id, type, content, message_order, completed, created_at, updated_at)
VALUES
  (1001, 'USER',
   'Spring 里单例 Bean 的循环依赖是怎么解决的？三级缓存每一级分别解决什么问题？',
   1, true, now() - interval '3 hours', now() - interval '3 hours'),

  (1001, 'ASSISTANT',
   '根据《Spring 核心机制笔记》，单例循环依赖靠三级缓存解决，每一级的职责不同：' || chr(10) || chr(10) ||
   '1）singletonObjects（一级）：成品 Bean，可以直接用。' || chr(10) ||
   '2）earlySingletonObjects（二级）：提前暴露的半成品，用于解决「多个 Bean 同时依赖同一个早期引用」的问题。' || chr(10) ||
   '3）singletonFactories（三级）：ObjectFactory，只有在发生循环依赖时才会被调用，用于生成早期引用（必要时套 AOP 代理）。' || chr(10) || chr(10) ||
   '流程是：A 实例化后先把 ObjectFactory 放进三级缓存 → 填充属性时发现依赖 B → B 又依赖 A → B 从三级缓存拿到 A 的早期引用并升到二级 → B 完成初始化后 A 继续。' || chr(10) || chr(10) ||
   '限制有三点：只对单例生效；构造器注入的循环依赖无法解决（此时还没实例化，谈不上提前暴露）；' ||
   '原型（prototype）Bean 也不支持。Spring Boot 2.6 之后默认禁止循环依赖，需要显式开启 spring.main.allow-circular-references。',
   2, true, now() - interval '3 hours', now() - interval '3 hours');

COMMIT;

-- ============================================================
-- 清理演示数据（需要时手动执行）
-- ============================================================
-- DELETE FROM rag_chat_messages WHERE session_id = 1001;
-- DELETE FROM rag_session_knowledge_bases WHERE session_id = 1001;
-- DELETE FROM rag_chat_sessions WHERE id = 1001;
-- DELETE FROM async_task_dead_letter WHERE payload_identifier IN
--   ('kb:3', 'resume:7', 'session:a3f1c9e2', 'question-gen:task-2f8e', 'voice:12');
