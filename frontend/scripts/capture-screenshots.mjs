// pnpm 只在 .pnpm 里装了 playwright，没有提升到顶层；@playwright/test 已经 re-export 了 chromium
import { chromium } from '@playwright/test';
import { mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

/**
 * 截图脚本：登录后逐页截取真实界面，输出到 assets/screenshots/。
 *
 * 前置条件：
 *   1. 后端 http://localhost:8080 已启动
 *   2. 前端 http://localhost:5173 已启动
 *   3. 数据库里已有演示数据（否则列表页是空的）
 *
 * 用法（在 frontend 目录执行）：
 *   node scripts/capture-screenshots.mjs
 *
 * 可用环境变量覆盖：BASE_URL / API_URL / DEMO_USER / DEMO_PASS
 */

const BASE_URL = process.env.BASE_URL ?? 'http://localhost:5173';
const API_URL = process.env.API_URL ?? 'http://localhost:8080';
const DEMO_USER = process.env.DEMO_USER ?? 'demo_review';
const DEMO_PASS = process.env.DEMO_PASS ?? 'Demo@12345';

const HERE = dirname(fileURLToPath(import.meta.url));
const OUT_DIR = resolve(HERE, '../../assets/screenshots');

/** 页面清单：name 决定文件名，ready 是可选的等待选择器 */
const PAGES = [
  { name: '01-login', path: '/login', auth: false, settle: 900 },
  { name: '02-interview-hub', path: '/interview-hub', ready: 'text=开始文字面试' },
  { name: '03-resume-library', path: '/history' },
  { name: '04-knowledgebase', path: '/knowledgebase' },
  {
    name: '05-rag-chat',
    path: '/knowledgebase/chat',
    // 问答页默认是空态，点开历史会话才能截到「提问 + 回答 + 引用」的真实形态
    action: async (page) => {
      const item = page.getByText('Spring Bean 生命周期与循环依赖').first();
      await item.waitFor({ timeout: 6000 }).catch(() => {});
      await item.click().catch(() => {});
      await page.waitForTimeout(1500);
    },
  },
  { name: '06-interview-history', path: '/interviews' },
  { name: '07-dead-letter', path: '/dead-letter' },
  { name: '08-settings', path: '/settings' },
];

async function api(path, init) {
  const res = await fetch(`${API_URL}${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init?.headers ?? {}) },
  });
  const body = await res.json().catch(() => ({}));
  return { status: res.status, body };
}

async function ensureAccount() {
  const reg = await api('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify({ username: DEMO_USER, password: DEMO_PASS }),
  });
  if (reg.body?.code === 200) {
    console.log(`注册新账号 ${DEMO_USER}`);
  } else {
    console.log(`账号已存在或注册被拒（code=${reg.body?.code}），直接登录`);
  }

  const login = await api('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username: DEMO_USER, password: DEMO_PASS }),
  });
  const data = login.body?.data;
  if (!data?.token) {
    throw new Error(`登录失败：${JSON.stringify(login.body)}`);
  }
  console.log(`登录成功 userId=${data.userId}`);
  return data;
}

async function main() {
  mkdirSync(OUT_DIR, { recursive: true });
  const auth = await ensureAccount();

  // 没下载 Playwright 自带浏览器时，直接复用系统 Chrome（channel 可切换为 msedge）
  const browser = await chromium.launch({ channel: process.env.PW_CHANNEL ?? 'chrome' });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    deviceScaleFactor: 2,
    locale: 'zh-CN',
  });

  const page = await context.newPage();
  let failed = 0;

  for (const item of PAGES) {
    try {
      if (item.auth === false) {
        await context.clearCookies();
        await page.goto(`${BASE_URL}${item.path}`, { waitUntil: 'domcontentloaded' });
        await page.evaluate(() => localStorage.clear());
        await page.goto(`${BASE_URL}${item.path}`, { waitUntil: 'domcontentloaded' });
      } else {
        // 先落到同源页面，注入凭证后再进入目标路由，避免 axios 抢跑
        await page.goto(`${BASE_URL}/login`, { waitUntil: 'domcontentloaded' });
        await page.evaluate(
          ([token, user]) => {
            localStorage.setItem('ig_token', token);
            localStorage.setItem('ig_user', JSON.stringify(user));
          },
          [auth.token, { userId: auth.userId, username: auth.username }],
        );
        await page.goto(`${BASE_URL}${item.path}`, { waitUntil: 'domcontentloaded' });
      }

      if (item.ready) {
        await page.locator(item.ready).first().waitFor({ timeout: 8000 }).catch(() => {
          console.warn(`  ! ${item.name} 未等到 "${item.ready}"，仍按当前状态截图`);
        });
      }

      if (item.action) {
        await item.action(page);
      }

      // 等首屏动画与懒加载 chunk 落位
      await page.waitForTimeout(item.settle ?? 1600);
      const target = resolve(OUT_DIR, `${item.name}.png`);
      await page.screenshot({ path: target });
      console.log(`✓ ${item.name}.png`);
    } catch (err) {
      failed += 1;
      console.error(`✗ ${item.name} 失败：${err.message}`);
    }
  }

  await browser.close();
  console.log(`\n完成：${PAGES.length - failed}/${PAGES.length} 张，输出目录 ${OUT_DIR}`);
  if (failed > 0) process.exitCode = 1;
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
