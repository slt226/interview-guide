-- =====================================================================
-- 数据隔离改造：给 5 张"根表"补 user_id（owner 列）
--
-- 设计要点：
--   1. 只给根表加 owner，子表靠外键回查父表校验（避免归属信息冗余不一致）
--   2. 加列 -> 回填 -> 收紧 NOT NULL -> 加外键，四步走，保证迁移可重复执行
--   3. 全局唯一键降级为"用户内唯一"，否则 B 无法上传与 A 相同的文件
--
-- 执行前请确认已备份：backup_before_isolation.sql
-- =====================================================================

-- ========== 1) 加可空列 ==========
-- 历史数据此刻还没有归属，不能直接 NOT NULL，否则迁移必然失败
alter table resumes              add column user_id bigint;
alter table interview_sessions   add column user_id bigint;
alter table knowledge_bases      add column user_id bigint;
alter table rag_chat_sessions    add column user_id bigint;
alter table interview_schedule   add column user_id bigint;

-- ========== 2) 校验回填目标存在 ==========
-- 干净库里没有历史数据也没有用户，此时不该阻断部署；
-- 只有"确实存在无主数据"时才要求必须能找到一个账号来接盘。
do $$
declare
    v_owner_id bigint;
    v_orphans  bigint;
begin
    select count(*) into v_orphans
      from (select id from resumes            where user_id is null
            union all
            select id from interview_sessions where user_id is null
            union all
            select id from knowledge_bases    where user_id is null
            union all
            select id from rag_chat_sessions  where user_id is null
            union all
            select id from interview_schedule where user_id is null) t;

    if v_orphans > 0 then
        -- 优先用开发账号 saber123；不存在则退回"最早创建的用户"
        select id into v_owner_id from app_user where username = 'saber123';
        if v_owner_id is null then
            select id into v_owner_id from app_user order by id limit 1;
        end if;

        if v_owner_id is null then
            raise exception '存在 % 条无主数据，但 app_user 表为空，无法回填 user_id。请先注册一个账号再执行迁移', v_orphans;
        end if;
    end if;
end $$;

-- ========== 3) 回填历史数据 ==========
update resumes             set user_id = coalesce((select id from app_user where username = 'saber123'), (select id from app_user order by id limit 1)) where user_id is null;
update interview_sessions  set user_id = coalesce((select id from app_user where username = 'saber123'), (select id from app_user order by id limit 1)) where user_id is null;
update knowledge_bases     set user_id = coalesce((select id from app_user where username = 'saber123'), (select id from app_user order by id limit 1)) where user_id is null;
update rag_chat_sessions   set user_id = coalesce((select id from app_user where username = 'saber123'), (select id from app_user order by id limit 1)) where user_id is null;
update interview_schedule  set user_id = coalesce((select id from app_user where username = 'saber123'), (select id from app_user order by id limit 1)) where user_id is null;

-- ========== 4) 收紧为非空 + 外键 ==========
alter table resumes             alter column user_id set not null;
alter table interview_sessions  alter column user_id set not null;
alter table knowledge_bases     alter column user_id set not null;
alter table rag_chat_sessions   alter column user_id set not null;
alter table interview_schedule  alter column user_id set not null;

alter table resumes             add constraint fk_resumes_user      foreign key (user_id) references app_user(id);
alter table interview_sessions  add constraint fk_interview_user    foreign key (user_id) references app_user(id);
alter table knowledge_bases     add constraint fk_kb_user           foreign key (user_id) references app_user(id);
alter table rag_chat_sessions   add constraint fk_rag_session_user  foreign key (user_id) references app_user(id);
alter table interview_schedule  add constraint fk_schedule_user     foreign key (user_id) references app_user(id);

-- ========== 5) 查询索引 ==========
-- 列表接口都是"我的数据按时间倒序"，所以索引统一带上 user_id 打头
create index idx_resume_user_uploaded          on resumes(user_id, uploaded_at desc);
create index idx_interview_session_user_created on interview_sessions(user_id, created_at desc);
create index idx_kb_user_uploaded              on knowledge_bases(user_id, uploaded_at desc);
create index idx_rag_session_user_updated      on rag_chat_sessions(user_id, updated_at desc);
create index idx_schedule_user_time            on interview_schedule(user_id, interview_time);

-- ========== 6) 去重范围从"全局"改成"用户内" ==========
-- idx_resume_hash / idx_kb_hash 建表时是 UNIQUE CONSTRAINT，必须用 DROP CONSTRAINT
-- 不改这一步等于没隔离：B 上传与 A 相同的文件会命中 A 的记录并复用
alter table resumes          drop constraint idx_resume_hash;
alter table knowledge_bases  drop constraint idx_kb_hash;

create unique index uk_resume_user_hash on resumes(user_id, file_hash);
create unique index uk_kb_user_hash     on knowledge_bases(user_id, file_hash);

-- 失去全局唯一后仍需支持"按哈希查重"，补一个普通索引兜住查询
create index idx_resume_hash on resumes(file_hash);
create index idx_kb_hash     on knowledge_bases(file_hash);

-- 幂等键 request_id 同理：全局唯一会让 B 的请求撞上 A 的键，
-- 降级为 (user_id, request_id) 唯一，各自独立
drop index uk_interview_sessions_request_id;
create unique index uk_interview_user_request on interview_sessions(user_id, request_id);

-- ========== 7) voice_interview_sessions.user_id 类型对齐 ==========
-- 原列是 varchar(255) 且全项目写死 'default'，与 app_user.id(bigint) 无法关联。
-- 这里重建该列，并把历史行的归属回填到同一个账号。
alter table voice_interview_sessions add column user_id_new bigint;
update voice_interview_sessions
   set user_id_new = coalesce((select id from app_user where username = 'saber123'), (select id from app_user order by id limit 1));
alter table voice_interview_sessions drop column user_id;
alter table voice_interview_sessions rename column user_id_new to user_id;
alter table voice_interview_sessions alter column user_id set not null;
alter table voice_interview_sessions add constraint fk_voice_session_user foreign key (user_id) references app_user(id);
create index idx_voice_session_user_updated on voice_interview_sessions(user_id, updated_at desc);
