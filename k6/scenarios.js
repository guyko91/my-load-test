import http from 'k6/http';
import { check, sleep } from 'k6';

// 시나리오 설정: 현재 프로젝트의 4가지 시나리오를 K6로 재현
export const options = {
  scenarios: {
    // Scenario 1: High burst every 4 hours (4시간마다 고부하)
    high_burst: {
      executor: 'constant-arrival-rate',
      rate: 20,  // 20 RPS
      timeUnit: '1s',
      duration: '5m',  // 5분 동안
      preAllocatedVUs: 30,
      maxVUs: 50,
      exec: 'highBurstScenario',
      startTime: '0s',
    },

    // Scenario 2: Daily 8am medium load (중간 부하)
    medium_load: {
      executor: 'constant-arrival-rate',
      rate: 10,  // 10 RPS
      timeUnit: '1s',
      duration: '10m',  // 10분 동안
      preAllocatedVUs: 15,
      maxVUs: 25,
      exec: 'mediumLoadScenario',
      startTime: '6m',  // 5분 후 시작
    },

    // Scenario 3: Background low load (저부하 지속)
    background_low: {
      executor: 'constant-arrival-rate',
      rate: 2,  // 2 RPS
      timeUnit: '1s',
      duration: '30m',  // 30분 동안
      preAllocatedVUs: 5,
      maxVUs: 10,
      exec: 'backgroundLowScenario',
      startTime: '0s',
    },

    // Scenario 4: Medium burst (중간 버스트)
    medium_burst: {
      executor: 'constant-arrival-rate',
      rate: 8,  // 8 RPS
      timeUnit: '1s',
      duration: '8m',  // 8분 동안
      preAllocatedVUs: 12,
      maxVUs: 20,
      exec: 'mediumBurstScenario',
      startTime: '10m',  // 10분 후 시작
    },

    // Scenario 5: Memory Hog (메모리 부하)
    memory_hog: {
      executor: 'constant-arrival-rate',
      rate: 2, // 2 RPS
      timeUnit: '1s',
      duration: '5m', // 5분 동안
      preAllocatedVUs: 5,
      maxVUs: 10,
      exec: 'memoryHogScenario',
      startTime: '15m', // 15분 후 시작
    },
  },

  thresholds: {
    http_req_duration: ['p(95)<5000'],  // 95% of requests should be below 5s
    http_req_failed: ['rate<0.1'],      // Error rate should be less than 10%
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://app:28080';

// Scenario 1: High CPU + DB Query
export function highBurstScenario() {
  const cpuRes = http.post(`${BASE_URL}/api/workload/cpu`, JSON.stringify({
    durationMs: 2000,
    cpuPercent: 80
  }), {
    headers: { 'Content-Type': 'application/json' },
  });

  check(cpuRes, {
    'high burst - cpu status is 200': (r) => r.status === 200,
  });

  // DB 복합 쿼리
  const dbRes = http.post(`${BASE_URL}/api/workload/db/complex`);
  check(dbRes, {
    'high burst - db status is 200': (r) => r.status === 200,
  });

  sleep(0.5);
}

// Scenario 2: Realistic workload (CPU + DB)
export function mediumLoadScenario() {
  const realisticRes = http.post(`${BASE_URL}/api/workload/realistic`, JSON.stringify({
    durationMs: 1000,
    cpuPercent: 50
  }), {
    headers: { 'Content-Type': 'application/json' },
  });

  check(realisticRes, {
    'medium load - realistic status is 200': (r) => r.status === 200,
  });

  // 상태별 조회
  const statuses = ['PENDING', 'CONFIRMED', 'SHIPPED', 'DELIVERED'];
  const randomStatus = statuses[Math.floor(Math.random() * statuses.length)];
  const statusRes = http.get(`${BASE_URL}/api/workload/db/status/${randomStatus}`);

  check(statusRes, {
    'medium load - db query status is 200': (r) => r.status === 200,
  });

  sleep(1);
}

// Scenario 3: Background low load (DB 조회만)
export function backgroundLowScenario() {
  const queryRes = http.get(`${BASE_URL}/api/workload/db/query?limit=20`);

  check(queryRes, {
    'background low - query status is 200': (r) => r.status === 200,
  });

  // 고액 주문 조회
  const highValueRes = http.get(`${BASE_URL}/api/workload/db/high-value?minPrice=500000`);

  check(highValueRes, {
    'background low - high value status is 200': (r) => r.status === 200,
  });

  sleep(2);
}

// Scenario 4: Mixed workload
export function mediumBurstScenario() {
  const mixedRes = http.post(`${BASE_URL}/api/workload/mixed`, JSON.stringify({
    durationMs: 1500,
    cpuPercent: 60,
    ioOps: 5
  }), {
    headers: { 'Content-Type': 'application/json' },
  });

  check(mixedRes, {
    'medium burst - mixed status is 200': (r) => r.status === 200,
  });

  // 날짜 범위 조회
  const dateRangeRes = http.get(`${BASE_URL}/api/workload/db/date-range?daysAgo=30`);

  check(dateRangeRes, {
    'medium burst - date range status is 200': (r) => r.status === 200,
  });

  sleep(0.8);
}

// Scenario 5: Memory Hog
export function memoryHogScenario() {
  const memRes = http.post(`${BASE_URL}/api/workload/memory`, JSON.stringify({
    sizeMb: 256, // 256MB 할당
    durationMs: 15000 // 15초 동안 유지
  }), {
    headers: { 'Content-Type': 'application/json' },
  });

  check(memRes, {
    'memory hog - status is 200': (r) => r.status === 200,
  });

  // 다른 요청과 겹치지 않도록 충분히 sleep
  sleep(10);
}
