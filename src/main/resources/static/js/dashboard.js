// Toast Notification Function
function showToast(message, type = 'info') {
    const container = document.getElementById('toast-container');
    if (!container) return;
    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.textContent = message;
    container.appendChild(toast);
    setTimeout(() => toast.classList.add('show'), 100);
    setTimeout(() => {
        toast.classList.remove('show');
        toast.addEventListener('transitionend', () => toast.remove());
    }, 4000);
}

document.addEventListener('DOMContentLoaded', () => {
    // --- Element Selectors ---
    const elements = {
        baseline: {
            statusBadge: document.getElementById('baseline-status-badge'),
            resultDiv: document.getElementById('baseline-result'),
            resultInfo: document.getElementById('baseline-result-info'),
            rpsInput: document.getElementById('baseline-rps-input'),
            vusInput: document.getElementById('baseline-vus-input'),
            scenarioInput: document.getElementById('baseline-scenario-select'),
            startBtn: document.getElementById('start-baseline-btn'),
            stopBtn: document.getElementById('stop-baseline-btn'),
        },
        scenario: {
            statusBadge: document.getElementById('scenario-status-badge'),
            resultDiv: document.getElementById('scenario-result'),
            resultInfo: document.getElementById('scenario-result-info'),
            select: document.getElementById('scenario-select'),
            rpsInput: document.getElementById('scenario-rps-input'),
            durationInput: document.getElementById('scenario-duration-input'),
            vusInput: document.getElementById('scenario-vus-input'),
            startBtn: document.getElementById('start-scenario-btn'),
            stopBtn: document.getElementById('stop-scenario-btn'),
        },
        global: {
            stopAllBtn: document.getElementById('stop-all-k6-btn'),
            appStatusBadge: document.getElementById('app-status-badge'),
            orderCount: document.getElementById('order-count'),
        },
        dbPool: {
            value: document.getElementById('pool-size-value'),
            input: document.getElementById('pool-size-input'),
            setBtn: document.getElementById('set-pool-size-btn'),
            activeValue: document.getElementById('pool-active-value'),
            idleValue: document.getElementById('pool-idle-value'),
            pendingValue: document.getElementById('pool-pending-value'),
        },
        longScenarioGrid: document.getElementById('long-scenario-grid'),
    };

    // --- Event Listeners ---
    elements.baseline.startBtn.addEventListener('click', startBaselineTest);
    elements.baseline.stopBtn.addEventListener('click', () => stopTest(elements.baseline.stopBtn.dataset.testId));
    elements.scenario.startBtn.addEventListener('click', startScenarioTest);
    elements.scenario.stopBtn.addEventListener('click', () => stopTest(elements.scenario.stopBtn.dataset.testId));
    elements.global.stopAllBtn.addEventListener('click', stopAllK6Tests);
    elements.dbPool.setBtn.addEventListener('click', setPoolSize);

    // --- Initialization ---
    populateLongScenarios();
    startAutoRefresh();

    // --- Functions ---
    function startAutoRefresh() {
        refreshAllStatus();
        setInterval(refreshAllStatus, 3000);
    }

    async function refreshAllStatus() {
        await Promise.all([
            refreshK6Status(),
            refreshAppStatus(),
            refreshDatabaseStatus(),
            refreshPoolSize(),
            refreshPoolLiveStatus()
        ]);
    }

    async function refreshK6Status() {
        try {
            const res = await fetch('/api/dashboard/k6/status');
            const data = await res.json();

            const runningBaseline = data.runningTests.find(t => t.type === 'baseline');
            const runningScenario = data.runningTests.find(t => t.type === 'scenario');

            updateTestCardUI('baseline', runningBaseline, data.lastFinishedTests.baseline);
            updateTestCardUI('scenario', runningScenario, data.lastFinishedTests.scenario);

            const anyTestRunning = runningBaseline || runningScenario;
            elements.global.stopAllBtn.style.display = anyTestRunning ? 'block' : 'none';

        } catch (error) {
            console.error('Failed to refresh K6 status:', error);
        }
    }

    function updateTestCardUI(type, runningTest, lastFinishedTest) {
        const ui = elements[type];
        if (runningTest) {
            ui.statusBadge.textContent = 'RUNNING';
            ui.statusBadge.className = 'status-badge status-running';
            ui.startBtn.style.display = 'none';
            ui.stopBtn.style.display = 'block';
            ui.stopBtn.dataset.testId = runningTest.id;
            ui.resultDiv.classList.add('show');
            ui.resultInfo.textContent = `Scenario: ${runningTest.scenario}, Status: ${runningTest.status}`;
        } else {
            ui.statusBadge.textContent = 'IDLE';
            ui.statusBadge.className = 'status-badge status-idle';
            ui.startBtn.style.display = 'block';
            ui.stopBtn.style.display = 'none';
            delete ui.stopBtn.dataset.testId;
            if (lastFinishedTest) {
                ui.resultDiv.classList.add('show');
                ui.resultInfo.textContent = `Last: ${lastFinishedTest.scenario}, Status: ${lastFinishedTest.status} (Code: ${lastFinishedTest.exitCode})`;
            } else {
                ui.resultDiv.classList.remove('show');
            }
        }
    }

    async function startBaselineTest() {
        const payload = {
            testType: 'baseline',
            scenario: elements.baseline.scenarioInput.value,
            rps: parseInt(elements.baseline.rpsInput.value),
            vus: parseInt(elements.baseline.vusInput.value),
            duration: 99999, // "Infinite"
            script: 'dynamic.js'
        };
        if (payload.rps < 1 || payload.vus < 1) {
            showToast('Please enter valid values for Baseline test.', 'error');
            return;
        }
        await startTest(payload, 'baseline');
    }

    async function startScenarioTest() {
        const payload = {
            testType: 'scenario',
            scenario: elements.scenario.select.value,
            rps: parseInt(elements.scenario.rpsInput.value),
            vus: parseInt(elements.scenario.vusInput.value),
            duration: parseInt(elements.scenario.durationInput.value),
            script: 'dynamic.js'
        };
        if (payload.rps < 1 || payload.vus < 1 || payload.duration < 1) {
            showToast('Please enter valid values for Scenario test.', 'error');
            return;
        }
        await startTest(payload, 'scenario');
    }

    async function startTest(payload, type) {
        elements[type].startBtn.disabled = true;
        try {
            const res = await fetch('/api/dashboard/k6/start', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            if (res.ok) {
                showToast(`${type.charAt(0).toUpperCase() + type.slice(1)} test started!`, 'success');
                await refreshK6Status();
            } else {
                const errorData = await res.json();
                showToast(`Failed to start test: ${errorData.message}`, 'error');
                elements[type].startBtn.disabled = false;
            }
        } catch (error) {
            console.error(`Failed to start ${type} test:`, error);
            showToast('An error occurred while starting the test.', 'error');
            elements[type].startBtn.disabled = false;
        }
    }

    async function stopTest(testId) {
        if (!testId) return;
        const btn = document.querySelector(`[data-test-id="${testId}"]`);
        if(btn) btn.disabled = true;

        try {
            const res = await fetch(`/api/dashboard/k6/stop/${testId}`, { method: 'POST' });
            if (res.ok) {
                showToast(`Test ${testId} stopped.`, 'info');
                await refreshK6Status();
            } else {
                showToast('Failed to stop test.', 'error');
            }
        } catch (error) {
            console.error('Failed to stop test:', error);
            showToast('An error occurred while stopping the test.', 'error');
        } finally {
            if(btn) btn.disabled = false;
        }
    }

    async function stopAllK6Tests() {
        if (!confirm('Are you sure you want to stop ALL running K6 tests?')) return;
        elements.global.stopAllBtn.disabled = true;
        try {
            const res = await fetch('/api/dashboard/k6/stop-all', { method: 'POST' });
            if (res.ok) {
                showToast('All K6 tests stopped.', 'info');
                await refreshK6Status();
            } else {
                showToast('Failed to stop K6 tests.', 'error');
            }
        } catch (error) {
            console.error('Failed to stop K6 tests:', error);
            showToast('An error occurred while stopping tests.', 'error');
        } finally {
            elements.global.stopAllBtn.disabled = false;
        }
    }

    // --- Other Functions ---
    async function refreshAppStatus() {
        try {
            const res = await fetch('/api/dashboard/app/status');
            const data = await res.json();
            elements.global.appStatusBadge.textContent = data.running ? 'Active' : 'Idle';
        } catch (error) {
            console.error('Failed to refresh app status:', error);
            elements.global.appStatusBadge.textContent = 'Error';
        }
    }

    async function refreshDatabaseStatus() {
        try {
            const res = await fetch('/api/dashboard/db/status');
            const data = await res.json();
            if (data.orderCount !== undefined) {
                elements.global.orderCount.textContent = data.orderCount.toLocaleString();
            }
        } catch (error) {
            console.error('Failed to refresh database status:', error);
        }
    }

    async function refreshPoolSize() {
        try {
            const res = await fetch('/api/workload/db/pool-size');
            const data = await res.json();
            if (data.maxPoolSize !== undefined && data.maxPoolSize > 0) {
                elements.dbPool.value.textContent = data.maxPoolSize;
                if (document.activeElement !== elements.dbPool.input) {
                    elements.dbPool.input.value = data.maxPoolSize;
                }
            } else {
                elements.dbPool.value.textContent = 'N/A';
            }
        } catch (error) {
            console.error('Failed to refresh pool size:', error);
            elements.dbPool.value.textContent = 'Error';
        }
    }
    
    async function refreshPoolLiveStatus() {
        try {
            const res = await fetch('/api/dashboard/db/pool-status');
            const data = await res.json();
            elements.dbPool.activeValue.textContent = data.active ?? '-';
            elements.dbPool.idleValue.textContent = data.idle ?? '-';
            elements.dbPool.pendingValue.textContent = data.pending ?? '-';
        } catch (error) {
            console.error('Failed to refresh pool live status:', error);
            elements.dbPool.activeValue.textContent = 'Err';
            elements.dbPool.idleValue.textContent = 'Err';
            elements.dbPool.pendingValue.textContent = 'Err';
        }
    }

    async function setPoolSize() {
        const newSize = parseInt(elements.dbPool.input.value);
        if (isNaN(newSize) || newSize < 1) {
            showToast('Please enter a valid pool size.', 'error');
            return;
        }
        elements.dbPool.setBtn.disabled = true;
        try {
            const res = await fetch('/api/workload/db/pool-size', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ maxPoolSize: newSize })
            });
            if (res.ok) {
                const data = await res.json();
                showToast(`Max pool size set to ${data.maxPoolSize}`, 'success');
                await refreshPoolSize();
            } else {
                showToast('Failed to set pool size.', 'error');
            }
        } catch (error) {
            console.error('Failed to set pool size:', error);
            showToast('An error occurred.', 'error');
        } finally {
            elements.dbPool.setBtn.disabled = false;
        }
    }

    function populateLongScenarios() {
        const scenarios = {
            'daily_pattern': { name: '📅 Daily Pattern', desc: '8시간 - 일반 하루 패턴' },
            'gradual_increase': { name: '📈 Gradual Increase', desc: '4시간 - 점진적 부하 증가' },
            'spike_pattern': { name: '⚡ Spike Pattern', desc: '3시간 - 트래픽 스파이크' },
            'black_friday': { name: '🛒 Black Friday', desc: '6시간 - 대규모 이벤트' },
            'night_batch': { name: '🌙 Night Batch', desc: '2시간 - 야간 배치' },
            'stress_test': { name: '💪 Stress Test', desc: '2시간 - 최대 부하' }
        };
        for (const key in scenarios) {
            const scenario = scenarios[key];
            const btn = document.createElement('div');
            btn.className = 'quick-action-btn';
            btn.innerHTML = `<div class="title">${scenario.name}</div><div class="desc">${scenario.desc}</div>`;
            btn.onclick = () => startLongScenario(key);
            elements.longScenarioGrid.appendChild(btn);
        }
    }

    async function startLongScenario(scenarioName) {
        const payload = {
            testType: 'scenario',
            scenario: scenarioName,
            rps: 1, vus: 1, duration: 1,
            script: 'long-scenarios.js'
        };
        if (confirm(`Start Long Scenario: ${scenarioName}?`)) {
            await startTest(payload, 'scenario');
        }
    }
});
