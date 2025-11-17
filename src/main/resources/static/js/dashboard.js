// Toast Notification Function
function showToast(message, type = 'info') {
    const container = document.getElementById('toast-container');
    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.textContent = message;

    container.appendChild(toast);

    // Show the toast
    setTimeout(() => {
        toast.classList.add('show');
    }, 100);

    // Hide and remove the toast after 4 seconds
    setTimeout(() => {
        toast.classList.remove('show');
        toast.classList.add('hide');
        toast.addEventListener('transitionend', () => toast.remove());
    }, 4000);
}


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
    refreshDbBtn.addEventListener('click', () => {
        refreshDatabaseStatus();
        showToast('Database status refreshed!', 'info');
    });
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
        showToast('Please enter valid values for the test.', 'error');
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
            showToast(`K6 test started: ${scenario}`, 'success');
            await refreshK6Status();
        } else {
            showToast('Failed to start K6 test.', 'error');
        }
    } catch (error) {
        console.error('Failed to start K6:', error);
        showToast('An error occurred while starting the K6 test.', 'error');
    } finally {
        startK6Btn.disabled = false;
    }
}

// Stop K6 Test
async function stopK6Test() {
    if (!confirm('Are you sure you want to stop the K6 test?')) {
        return;
    }

    stopK6Btn.disabled = true;

    try {
        const res = await fetch('/api/dashboard/k6/stop', {
            method: 'POST'
        });

        if (res.ok) {
            showToast('K6 test stopped.', 'info');
            await refreshK6Status();
        } else {
            showToast('Failed to stop K6 test.', 'error');
        }
    } catch (error) {
        console.error('Failed to stop K6:', error);
        showToast('An error occurred while stopping the K6 test.', 'error');
    } finally {
        stopK6Btn.disabled = false;
    }
}

// Stop All Workloads
async function stopAllWorkloads() {
    if (!confirm('Are you sure you want to stop all workloads?')) {
        return;
    }

    stopAllBtn.disabled = true;

    try {
        const res = await fetch('/api/load/stop', {
            method: 'POST'
        });

        if (res.ok) {
            showToast('All workloads have been stopped.', 'info');
            await refreshAppStatus();
        } else {
            showToast('Failed to stop workloads.', 'error');
        }
    } catch (error) {
        console.error('Failed to stop workloads:', error);
        showToast('An error occurred while stopping workloads.', 'error');
    } finally {
        stopAllBtn.disabled = false;
    }
}

// Quick Test Function (called from HTML)
async function quickTest(scenario, rps, duration, vus) {
    if (isK6Running) {
        showToast('A K6 test is already running.', 'info');
        return;
    }

    if (!confirm(`Start Quick Test?\nScenario: ${scenario}, RPS: ${rps}, Duration: ${duration}min, VUs: ${vus}`)) {
        return;
    }

    try {
        const res = await fetch('/api/dashboard/k6/start', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ scenario, rps, duration, vus })
        });

        if (res.ok) {
            showToast('Quick Test started!', 'success');
            await refreshK6Status();
        } else {
            showToast('Failed to start Quick Test.', 'error');
        }
    } catch (error) {
        console.error('Failed to start quick test:', error);
        showToast('An error occurred while starting the Quick Test.', 'error');
    }
}

// Long Running Scenario Function (called from HTML)
async function longScenario(scenarioName) {
    if (isK6Running) {
        showToast('A K6 test is already running.', 'info');
        return;
    }

    const scenarios = {
        'daily_pattern': { name: 'Daily Pattern', duration: '8 hours', desc: 'Simulates a typical business day (dawn→morning→lunch→afternoon→evening)' },
        'gradual_increase': { name: 'Gradual Increase', duration: '4 hours', desc: 'Gradually increases load (5 VUs → 100 VUs)' },
        'spike_pattern': { name: 'Spike Pattern', duration: '3 hours', desc: 'Generates 3 sudden traffic spikes' },
        'black_friday': { name: 'Black Friday', duration: '6 hours', desc: 'Simulates a large-scale event (up to 200 VUs)' },
        'night_batch': { name: 'Night Batch', duration: '2 hours', desc: 'Simulates a nightly batch job pattern' },
        'stress_test': { name: 'Stress Test', duration: '2 hours', desc: 'Gradual stress test (up to 300 VUs)' }
    };

    const scenario = scenarios[scenarioName];
    if (!scenario) {
        showToast('Invalid scenario selected.', 'error');
        return;
    }

    const confirmMsg = `Start ${scenario.name}?\n\n` +
                      `⏱️ Estimated Duration: ${scenario.duration}\n` +
                      `📋 Description: ${scenario.desc}\n\n` +
                      `⚠️ This is a long-running test. Monitor system resources.`;

    if (!confirm(confirmMsg)) {
        return;
    }

    try {
        const res = await fetch('/api/dashboard/k6/long-scenario', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ scenario: scenarioName })
        });

        if (res.ok) {
            showToast(`${scenario.name} started!`, 'success');
            await refreshK6Status();
        } else {
            showToast('Failed to start long-running test.', 'error');
        }
    } catch (error) {
        console.error('Failed to start long scenario:', error);
        showToast('An error occurred while starting the long-running test.', 'error');
    }
}
