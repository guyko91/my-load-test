document.addEventListener('DOMContentLoaded', () => {
    const statusEl = document.getElementById('status');
    const stopBtn = document.getElementById('stop');
    const refreshSchedulesBtn = document.getElementById('refresh-schedules');
    const scheduledListEl = document.getElementById('scheduled-list');

    async function refreshStatus() {
        try {
            const res = await fetch('/api/load/status');
            const json = await res.json();
            statusEl.textContent = `Running: ${json.running}`;
        } catch (e) {
            statusEl.textContent = 'Error fetching status';
        }
    }

    async function refreshScheduledList() {
        try {
            const res = await fetch('/api/load/schedules');
            const json = await res.json();
            if (json.scheduled && json.scheduled.length > 0) {
                scheduledListEl.innerHTML = json.scheduled.map(name => `<span class="scheduled-item">${name}</span>`).join(', ');
            } else {
                scheduledListEl.textContent = 'No scheduled scenarios';
            }
        } catch (e) {
            scheduledListEl.textContent = 'Error fetching schedules';
        }
    }

    document.querySelectorAll('button.trigger').forEach(btn => {
        btn.addEventListener('click', async (ev) => {
            const name = ev.target.getAttribute('data-name');
            ev.target.disabled = true;
            try {
                await fetch(`/api/load/trigger/${encodeURIComponent(name)}`, { method: 'POST' });
                await refreshStatus();
            } catch (e) {
                alert('Trigger failed');
            } finally {
                ev.target.disabled = false;
            }
        });
    });

    document.querySelectorAll('button.cancel-schedule').forEach(btn => {
        btn.addEventListener('click', async (ev) => {
            const name = ev.target.getAttribute('data-name');
            if (!confirm(`Cancel schedule for ${name}?`)) return;
            ev.target.disabled = true;
            try {
                const res = await fetch(`/api/load/schedule/${encodeURIComponent(name)}`, { method: 'DELETE' });
                if (res.ok) {
                    alert(`Schedule cancelled for ${name}`);
                    await refreshScheduledList();
                } else {
                    alert('Cancel failed or not found');
                }
            } catch (e) {
                alert('Cancel schedule failed');
            } finally {
                ev.target.disabled = false;
            }
        });
    });

    document.querySelectorAll('button.reschedule').forEach(btn => {
        btn.addEventListener('click', async (ev) => {
            const name = ev.target.getAttribute('data-name');
            if (!confirm(`Reschedule ${name}?`)) return;
            ev.target.disabled = true;
            try {
                const res = await fetch(`/api/load/schedule/${encodeURIComponent(name)}`, { method: 'POST' });
                if (res.ok) {
                    alert(`Rescheduled ${name}`);
                    await refreshScheduledList();
                } else {
                    alert('Reschedule failed or not found');
                }
            } catch (e) {
                alert('Reschedule failed');
            } finally {
                ev.target.disabled = false;
            }
        });
    });

    stopBtn.addEventListener('click', async () => {
        stopBtn.disabled = true;
        try {
            await fetch('/api/load/stop', { method: 'POST' });
            await refreshStatus();
        } catch (e) {
            alert('Stop failed');
        } finally {
            stopBtn.disabled = false;
        }
    });

    refreshSchedulesBtn.addEventListener('click', async () => {
        await refreshScheduledList();
    });

    // refresh status every 5s
    refreshStatus();
    refreshScheduledList();
    setInterval(refreshStatus, 5000);
});

