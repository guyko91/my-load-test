import http from 'k6/http';
import { check, sleep } from 'k6';

// 장시간 시나리오 - 환경변수로 시나리오 선택
const SCENARIO = __ENV.SCENARIO || 'daily_pattern';
const BASE_URL = __ENV.BASE_URL || 'http://localhost:28080';

// 시나리오 설정
const scenarios = {
  // 1. 일반적인 하루 패턴 (8시간)
  daily_pattern: {
    executor: 'ramping-vus',
    startVUs: 1,
    stages: [
      { duration: '30m', target: 5 },   // 새벽 - 저부하
      { duration: '1h', target: 20 },   // 출근 시간 - 부하 증가
      { duration: '2h', target: 30 },   // 오전 업무 시간 - 고부하
      { duration: '1h', target: 15 },   // 점심 시간 - 부하 감소
      { duration: '2h', target: 35 },   // 오후 업무 시간 - 최고 부하
      { duration: '1h', target: 20 },   // 퇴근 시간 - 부하 감소
      { duration: '30m', target: 5 },   // 저녁 - 저부하
    ],
    gracefulRampDown: '5m',
  },

  // 2. 점진적 부하 증가 (4시간)
  gradual_increase: {
    executor: 'ramping-vus',
    startVUs: 5,
    stages: [
      { duration: '30m', target: 10 },
      { duration: '30m', target: 20 },
      { duration: '30m', target: 40 },
      { duration: '30m', target: 60 },
      { duration: '30m', target: 80 },
      { duration: '30m', target: 100 },
      { duration: '30m', target: 60 },
      { duration: '30m', target: 20 },
    ],
    gracefulRampDown: '5m',
  },

  // 3. 스파이크 패턴 (3시간)
  spike_pattern: {
    executor: 'ramping-vus',
    startVUs: 10,
    stages: [
      { duration: '20m', target: 10 },   // 안정 상태
      { duration: '5m', target: 100 },   // 급격한 증가 (스파이크)
      { duration: '10m', target: 100 },  // 고부하 유지
      { duration: '5m', target: 10 },    // 급격한 감소
      { duration: '20m', target: 10 },   // 안정 상태
      { duration: '5m', target: 80 },    // 스파이크 2
      { duration: '10m', target: 80 },
      { duration: '5m', target: 10 },
      { duration: '20m', target: 10 },   // 안정 상태
      { duration: '5m', target: 120 },   // 스파이크 3 (최대)
      { duration: '10m', target: 120 },
      { duration: '5m', target: 10 },
      { duration: '20m', target: 10 },   // 종료
    ],
    gracefulRampDown: '5m',
  },

  // 4. 블랙 프라이데이 패턴 (6시간)
  black_friday: {
    executor: 'ramping-vus',
    startVUs: 20,
    stages: [
      { duration: '30m', target: 50 },   // 이벤트 시작 전
      { duration: '15m', target: 150 },  // 이벤트 시작 (급증)
      { duration: '1h', target: 200 },   // 최고 트래픽
      { duration: '1h', target: 180 },   // 높은 트래픽 유지
      { duration: '1h', target: 150 },   // 서서히 감소
      { duration: '1h', target: 100 },
      { duration: '30m', target: 60 },
      { duration: '30m', target: 30 },   // 안정화
    ],
    gracefulRampDown: '10m',
  },

  // 5. 야간 배치 패턴 (2시간)
  night_batch: {
    executor: 'ramping-vus',
    startVUs: 5,
    stages: [
      { duration: '10m', target: 5 },    // 준비
      { duration: '20m', target: 30 },   // 배치 작업 시작
      { duration: '40m', target: 50 },   // 배치 작업 피크
      { duration: '20m', target: 30 },   // 배치 작업 종료
      { duration: '20m', target: 10 },   // 정리
      { duration: '10m', target: 5 },    // 완료
    ],
    gracefulRampDown: '5m',
  },

  // 6. 스트레스 테스트 (2시간)
  stress_test: {
    executor: 'ramping-vus',
    startVUs: 10,
    stages: [
      { duration: '10m', target: 50 },
      { duration: '10m', target: 100 },
      { duration: '10m', target: 150 },
      { duration: '10m', target: 200 },
      { duration: '10m', target: 250 },
      { duration: '20m', target: 300 },  // 최대 부하 유지
      { duration: '10m', target: 200 },
      { duration: '10m', target: 100 },
      { duration: '10m', target: 50 },
      { duration: '10m', target: 10 },
    ],
    gracefulRampDown: '5m',
  },
};

export const options = {
  scenarios: {
    [SCENARIO]: scenarios[SCENARIO] || scenarios['daily_pattern'],
  },
  thresholds: {
    http_req_duration: ['p(95)<5000', 'p(99)<10000'],
    http_req_failed: ['rate<0.1'],
    http_reqs: ['rate>0'],
  },
};

export default function () {
  // 시나리오에 따라 다양한 워크로드 실행
  const workloads = [
    executeRealisticLoad,
    executeCPULoad,
    executeDBLoad,
    executeMixedLoad,
  ];

  // 랜덤하게 워크로드 선택 (실제 사용자 행동 모방)
  const randomWorkload = workloads[Math.floor(Math.random() * workloads.length)];
  randomWorkload();
}

// 현실적인 워크로드 (DB + CPU)
function executeRealisticLoad() {
  const res = http.post(`${BASE_URL}/api/workload/realistic`, JSON.stringify({
    durationMs: 500 + Math.floor(Math.random() * 1000),  // 500-1500ms
    cpuPercent: 30 + Math.floor(Math.random() * 30)      // 30-60%
  }), {
    headers: { 'Content-Type': 'application/json' },
  });

  check(res, {
    'realistic load is 200': (r) => r.status === 200,
  });

  // 추가 DB 조회
  const statuses = ['PENDING', 'CONFIRMED', 'SHIPPED', 'DELIVERED'];
  const randomStatus = statuses[Math.floor(Math.random() * statuses.length)];
  http.get(`${BASE_URL}/api/workload/db/status/${randomStatus}`);

  sleep(1 + Math.random() * 2);  // 1-3초
}

// CPU 부하
function executeCPULoad() {
  const res = http.post(`${BASE_URL}/api/workload/cpu`, JSON.stringify({
    durationMs: 800 + Math.floor(Math.random() * 700),   // 800-1500ms
    cpuPercent: 50 + Math.floor(Math.random() * 30)       // 50-80%
  }), {
    headers: { 'Content-Type': 'application/json' },
  });

  check(res, {
    'cpu load is 200': (r) => r.status === 200,
  });

  sleep(0.5 + Math.random());  // 0.5-1.5초
}

// DB 조회 부하
function executeDBLoad() {
  const queries = [
    () => http.get(`${BASE_URL}/api/workload/db/query?limit=${10 + Math.floor(Math.random() * 40)}`),
    () => http.post(`${BASE_URL}/api/workload/db/complex`),
    () => http.get(`${BASE_URL}/api/workload/db/high-value?minPrice=${300000 + Math.floor(Math.random() * 500000)}`),
    () => http.get(`${BASE_URL}/api/workload/db/date-range?daysAgo=${7 + Math.floor(Math.random() * 60)}`),
  ];

  const randomQuery = queries[Math.floor(Math.random() * queries.length)];
  const res = randomQuery();

  check(res, {
    'db query is 200': (r) => r.status === 200,
  });

  sleep(1 + Math.random());  // 1-2초
}

// 혼합 부하
function executeMixedLoad() {
  const res = http.post(`${BASE_URL}/api/workload/mixed`, JSON.stringify({
    durationMs: 1000 + Math.floor(Math.random() * 1000),  // 1-2초
    cpuPercent: 40 + Math.floor(Math.random() * 40),       // 40-80%
    ioOps: Math.floor(Math.random() * 10)                  // 0-10 ops
  }), {
    headers: { 'Content-Type': 'application/json' },
  });

  check(res, {
    'mixed load is 200': (r) => r.status === 200,
  });

  sleep(0.8 + Math.random() * 1.2);  // 0.8-2초
}
