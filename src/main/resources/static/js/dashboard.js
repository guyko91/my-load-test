// Dashboard State
let isK6Running = false;
let refreshInterval;

// DOM Elements
const startK6Btn = document.getElementById('start-k6-btn');
const stopK6Btn = document.getElementById('stop-k6-btn');
const stopAllBtn = document.getElementById('stop-all-btn');
const refreshDbBtn = document.getElementById('refresh-db-btn');

const scenarioSelect = document.getElementById('scenario-select');
const rpsInput = document.getElementById('rps-input');
const durationInput = document.getElementById('duration-input');
const vusInput = document.getElementById('vus-input');

const k6StatusBadge = document.getElementById('k6-status-badge');
const k6StatusText = document.getElementById('k6-status-text');
const appStatusBadge = document.getElementById('app-status-badge');
const workloadStatusText = document.getElementById('workload-status-text');
const orderCountEl = document.getElementById('order-count');

const k6ResultDiv = document.getElementById('k6-result');
const k6ResultInfo = document.getElementById('k6-result-info');

// Initialize
document.addEventListener('DOMContentLoaded', () => {
    refreshAllStatus();
    startAutoRefresh();

    startK6Btn.addEventListener('click', startK6Test);
    stopK6Btn.addEventListener('click', stopK6Test);
    stopAllBtn.addEventListener('click', stopAllWorkloads);
    refreshDbBtn.addEventListener('click', refreshDatabaseStatus);
});

// Auto Refresh (every 3 seconds)
function startAutoRefresh() {
    refreshInterval = setInterval(() => {
        refreshAllStatus();
    }, 3000);
}

function stopAutoRefresh() {
    if (refreshInterval) {
        clearInterval(refreshInterval);
    }
}

// Refresh All Status
async function refreshAllStatus() {
    await Promise.all([
        refreshK6Status(),
        refreshAppStatus(),
        refreshDatabaseStatus()
    ]);
}

// K6 Status
async function refreshK6Status() {
    try {
        const res = await fetch('/api/dashboard/k6/status');
        const data = await res.json();

        isK6Running = data.running;

        if (data.running) {
            k6StatusBadge.textContent = 'RUNNING';
            k6StatusBadge.className = 'status-badge status-running';
            k6StatusText.textContent = 'Running';

            startK6Btn.style.display = 'none';
            stopK6Btn.style.display = 'block';

            if (data.lastTest) {
                k6ResultDiv.classList.add('show');
                k6ResultInfo.textContent = `Scenario: ${data.lastTest.scenario || 'N/A'}, Status: ${data.lastTest.status || 'N/A'}`;
            }
        } else {
            k6StatusBadge.textContent = 'IDLE';
            k6StatusBadge.className = 'status-badge status-idle';
            k6StatusText.textContent = 'Stopped';

            startK6Btn.style.display = 'block';
            stopK6Btn.style.display = 'none';

            if (data.lastTest && data.lastTest.status) {
                k6ResultDiv.classList.add('show');
                const status = data.lastTest.status;
                const scenario = data.lastTest.scenario || 'N/A';
                k6ResultInfo.textContent = `Last Test: ${scenario}, Status: ${status}`;
            }
        }
    } catch (error) {
        console.error('Failed to refresh K6 status:', error);
    }
}

// App Status
async function refreshAppStatus() {
    try {
        const res = await fetch('/api/dashboard/app/status');
        const data = await res.json();

        if (data.running) {
            appStatusBadge.textContent = 'RUNNING';
            appStatusBadge.className = 'status-badge status-running';
            workloadStatusText.textContent = 'Active';
        } else {
            appStatusBadge.textContent = 'IDLE';
            appStatusBadge.className = 'status-badge status-idle';
            workloadStatusText.textContent = 'Idle';
        }
    } catch (error) {
        console.error('Failed to refresh app status:', error);
    }
}

// Database Status
async function refreshDatabaseStatus() {
    try {
        const res = await fetch('/api/dashboard/db/status');
        const data = await res.json();

        if (data.orderCount !== undefined) {
            orderCountEl.textContent = data.orderCount.toLocaleString();
        }
    } catch (error) {
        console.error('Failed to refresh database status:', error);
    }
}

// Start K6 Test
async function startK6Test() {
    const scenario = scenarioSelect.value;
    const rps = parseInt(rpsInput.value);
    const duration = parseInt(durationInput.value);
    const vus = parseInt(vusInput.value);

    if (!scenario || rps < 1 || duration < 1 || vus < 1) {
        alert('올바른 값을 입력해주세요.');
        return;
    }

    startK6Btn.disabled = true;

    try {
        const res = await fetch('/api/dashboard/k6/start', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ scenario, rps, duration, vus })
        });

        if (res.ok) {
            alert(`K6 테스트 시작!\nScenario: ${scenario}\nRPS: ${rps}\nDuration: ${duration}분\nVUs: ${vus}`);
            await refreshK6Status();
        } else {
            alert('K6 테스트 시작 실패');
        }
    } catch (error) {
        console.error('Failed to start K6:', error);
        alert('K6 테스트 시작 중 오류 발생');
    } finally {
        startK6Btn.disabled = false;
    }
}

// Stop K6 Test
async function stopK6Test() {
    if (!confirm('K6 테스트를 중지하시겠습니까?')) {
        return;
    }

    stopK6Btn.disabled = true;

    try {
        const res = await fetch('/api/dashboard/k6/stop', {
            method: 'POST'
        });

        if (res.ok) {
            alert('K6 테스트 중지됨');
            await refreshK6Status();
        } else {
            alert('K6 테스트 중지 실패');
        }
    } catch (error) {
        console.error('Failed to stop K6:', error);
        alert('K6 테스트 중지 중 오류 발생');
    } finally {
        stopK6Btn.disabled = false;
    }
}

// Stop All Workloads
async function stopAllWorkloads() {
    if (!confirm('모든 워크로드를 중지하시겠습니까?')) {
        return;
    }

    stopAllBtn.disabled = true;

    try {
        const res = await fetch('/api/load/stop', {
            method: 'POST'
        });

        if (res.ok) {
            alert('모든 워크로드가 중지되었습니다.');
            await refreshAppStatus();
        } else {
            alert('워크로드 중지 실패');
        }
    } catch (error) {
        console.error('Failed to stop workloads:', error);
        alert('워크로드 중지 중 오류 발생');
    } finally {
        stopAllBtn.disabled = false;
    }
}

// Quick Test Function (called from HTML)
async function quickTest(scenario, rps, duration, vus) {
    if (isK6Running) {
        alert('이미 K6 테스트가 실행 중입니다.');
        return;
    }

    if (!confirm(`Quick Test 시작\nScenario: ${scenario}\nRPS: ${rps}\nDuration: ${duration}분\nVUs: ${vus}`)) {
        return;
    }

    try {
        const res = await fetch('/api/dashboard/k6/start', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ scenario, rps, duration, vus })
        });

        if (res.ok) {
            alert('Quick Test 시작!');
            await refreshK6Status();
        } else {
            alert('Quick Test 시작 실패');
        }
    } catch (error) {
        console.error('Failed to start quick test:', error);
        alert('Quick Test 시작 중 오류 발생');
    }
}
