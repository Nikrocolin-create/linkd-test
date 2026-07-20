/**
 * k6 Cache Stampede Test
 *
 * Сценарий: 100 VU одновременно читают один короткий код,
 * которого нет в Redis → 100 запросов уходят в Postgres.
 *
 * Запуск:
 *   k6 run k6-cache-stampede.js
 *
 * Перед запуском:
 *   1. Приложение запущено на localhost:8080
 *   2. Redis запущен и пуст (или ключ удалён вручную)
 *   3. В Postgres есть запись с кодом SHORT_CODE (создать через POST /api/v1/links)
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const BASE_URL = 'http://localhost:8080/api/v1';

// Метрики
const pgHits = new Counter('postgres_hits');          // промахи кэша → запросы в PG
const cacheHits = new Counter('cache_hits');          // попадания в Redis
const responseTime = new Trend('response_time_ms');

export const options = {
    scenarios: {
        stampede: {
            executor: 'shared-iterations',  // все итерации делятся между VU
            vus: 1000,                       // 100 потоков
            iterations: 1000,                // ровно 100 итераций
            maxDuration: '30s',
        },
    },
    thresholds: {
        // Ожидаем: при stampede почти все 100 запросов пойдут в PG
        http_req_duration: ['p(95)<5000'],
    },
};

// SHORT_CODE — код ссылки, созданной БЕЗ прогрева кэша (cacheWarming=false)
// Замените на реальный код после POST /api/v1/links
const SHORT_CODE = __ENV.SHORT_CODE || 'REPLACE_ME';

export function setup() {
    // Шаг 1: создаём ссылку без прогрева кэша
    const payload = JSON.stringify({ url: 'https://example.com/stampede-test' });
    const res = http.post(`${BASE_URL}/links?cacheWarming=false`, payload, {
        headers: { 'Content-Type': 'application/json' },
    });

    check(res, { 'link created': (r) => r.status === 201 });

    const body = JSON.parse(res.body);
    // shortUrl вида http://localhost:8080/api/v1/XXXXXXXX → берём последнюю часть
    const code = body.shortUrl.split('/').pop();

    console.log(`[setup] Created link with code: ${code}`);
    console.log(`[setup] Redis key "${code}" NOT warmed — stampede ready`);

    return { code };
}

export default function (data) {
    const code = SHORT_CODE !== 'REPLACE_ME' ? SHORT_CODE : data.code;

    const start = Date.now();
    const res = http.get(`${BASE_URL}/${code}`, {
        redirects: 0,  // не следуем редиректу — нас интересует время ответа
    });
    const elapsed = Date.now() - start;

    responseTime.add(elapsed);

    // 302 = найдено (cache hit или PG hit — оба успех)
    // 404 = ключ не найден вообще
    const ok = check(res, {
        'status 302 (redirect)': (r) => r.status === 302,
        'redirect has Location': (r) => r.headers['Location'] !== undefined,
    });

    // Определяем cache hit vs PG hit по времени ответа:
    // cache hit обычно < 10 ms, PG hit > 10 ms (эмпирически)
    if (elapsed < 10) {
        cacheHits.add(1);
    } else {
        pgHits.add(1);
    }

    // Никакого sleep — все 100 VU бьют одновременно
}

export function teardown(data) {
    console.log('\n[teardown] Test finished.');
    console.log(`Results: check logs or k6 summary for postgres_hits vs cache_hits`);
    console.log(`\nЧто наблюдать в логах Spring (application):`);
    console.log(`  - "Cache miss for link" должно появиться ~100 раз в первые мс`);
    console.log(`  - "Cache hit for link"  — 0 раз (все промахнулись одновременно)`);
    console.log(`\nЭто и есть Cache Stampede — все 100 потоков ушли в Postgres.`);
}
