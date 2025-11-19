import http from 'k6/http';
import { check, sleep } from 'k6';

// 환경변수로 시나리오 설정 받기
const SCENARIO = __ENV.SCENARIO || 'realistic';
const RPS = parseInt(__ENV.RPS || '10');
const DURATION = __ENV.DURATION || '5m';
const VUS = parseInt(__ENV.VUS || '20');
const BASE_URL = __ENV.BASE_URL || 'http://localhost:28080';

export const options = {
  scenarios: {
    dynamic_load: {
      executor: 'constant-arrival-rate',
      rate: RPS,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: VUS,
      maxVUs: VUS * 2,
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<5000'],
    http_req_failed: ['rate<0.1'],
  },
};

// 시나리오 함수 매핑
const scenarios = {
  cpu: executeCPULoad,
  db: executeDBLoad,
  realistic: executeRealisticLoad,
  mixed: executeMixedLoad,
  high_burst: executeHighBurst,
};

export default function () {
  const scenarioFunc = scenarios[SCENARIO] || executeRealisticLoad;
  scenarioFunc();
}

// CPU 부하 시나리오
function executeCPULoad() {
  const res = http.post(`${BASE_URL}/api/workload/cpu`, JSON.stringify({
    durationMs: 1000,
    cpuPercent: 70
  }), {
    headers: { 'Content-Type': 'application/json' },
  });

  check(res, {
    'cpu load status is 200': (r) => r.status === 200,
  });

  sleep(0.5);
}

// DB 조회 부하 시나리오
function executeDBLoad() {
  const queries = [
    () => http.get(`${BASE_URL}/api/workload/db/query?limit=20`),
    () => http.post(`${BASE_URL}/api/workload/db/complex`),
    () => http.get(`${BASE_URL}/api/workload/db/high-value?minPrice=500000`),
    () => http.get(`${BASE_URL}/api/workload/db/date-range?daysAgo=30`),
  ];

  const randomQuery = queries[Math.floor(Math.random() * queries.length)];
  const res = randomQuery();

  check(res, {
    'db query status is 200': (r) => r.status === 200,
  });

  sleep(1);
}

// 현실적인 워크로드 (DB + CPU)
function executeRealisticLoad() {
  // 1. 기존의 realistic 워크로드 (DB 조회 + CPU 부하)
  const realisticRes = http.post(`${BASE_URL}/api/workload/realistic`, JSON.stringify({
    durationMs: 800,
    cpuPercent: 40
  }), {
    headers: { 'Content-Type': 'application/json' },
  });

  check(realisticRes, {
    'realistic load status is 200': (r) => r.status === 200,
  });

  // 2. '주문 처리' 워크로드 추가 (DB Read & Write)
  const customerNames = [
      "김철수", "이영희", "박민수", "최지원", "정현우",
      "강서연", "윤태영", "임수진", "한지훈", "오민지"
  ];
  const randomCustomer = customerNames[Math.floor(Math.random() * customerNames.length)];

  const processRes = http.post(`${BASE_URL}/api/workload/process-order`, JSON.stringify({
    customerName: randomCustomer
  }), {
    headers: { 'Content-Type': 'application/json' },
  });

  check(processRes, {
    'process order status is 200': (r) => r.status === 200,
  });


  sleep(0.8);
}

// 혼합 부하 시나리오
function executeMixedLoad() {
  const res = http.post(`${BASE_URL}/api/workload/mixed`, JSON.stringify({
    durationMs: 1500,
    cpuPercent: 60,
    ioOps: 5
  }), {
    headers: { 'Content-Type': 'application/json' },
  });

  check(res, {
    'mixed load status is 200': (r) => r.status === 200,
  });

  sleep(0.5);
}

// 고부하 버스트
function executeHighBurst() {
  // CPU 고부하
  const cpuRes = http.post(`${BASE_URL}/api/workload/cpu`, JSON.stringify({
    durationMs: 2000,
    cpuPercent: 80
  }), {
    headers: { 'Content-Type': 'application/json' },
  });

  check(cpuRes, {
    'high burst cpu status is 200': (r) => r.status === 200,
  });

  // DB 복합 쿼리
  const dbRes = http.post(`${BASE_URL}/api/workload/db/complex`);

  check(dbRes, {
    'high burst db status is 200': (r) => r.status === 200,
  });

  sleep(0.3);
}
