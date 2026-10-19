import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router';
import { AppShell } from '../ui/AppShell';
import { DeviceGlyph } from '../ui/DeviceGlyph';
import { isOnline as isOnlineByRecency } from '../ui/status';
import { useToast } from '../ui/toast';
import { fmtRelative, orDash } from '../ui/format';
import {
  listEnterpriseDevices,
  sendEnterpriseCommand,
  deleteEnterpriseDevice,
  syncEnterpriseDevices,
  type EnterpriseDevice,
  type EnterpriseCommandType,
  type EnterpriseCommandRequest,
  COMMAND_TYPES,
  getCommandPayloadFields,
  buildCommandPayload,
  validateCommandPayload,
  mapEnterpriseError,
} from '../api/enterprise';

type View = 'grid' | 'list';
type StatusFilter = 'all' | 'online' | 'offline';

const isOnline = (d: EnterpriseDevice, now?: number) => isOnlineByRecency(d.lastUpdate, now);

function IconSearch() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <circle cx="11" cy="11" r="7" />
      <path d="M21 21l-4-4" />
    </svg>
  );
}
function IconGrid() {
  return (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
      <rect x="3" y="3" width="7" height="7" rx="1.5" />
      <rect x="14" y="3" width="7" height="7" rx="1.5" />
      <rect x="3" y="14" width="7" height="7" rx="1.5" />
      <rect x="14" y="14" width="7" height="7" rx="1.5" />
    </svg>
  );
}
function IconList() {
  return (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
      <line x1="4" y1="6" x2="20" y2="6" />
      <line x1="4" y1="12" x2="20" y2="12" />
      <line x1="4" y1="18" x2="20" y2="18" />
    </svg>
  );
}

interface CommandModalState {
  open: boolean;
  deviceIds: string[];
  commandType: EnterpriseCommandType;
  payloadValues: Record<string, string | boolean>;
}

export function EnterpriseDevicesPage() {
  const navigate = useNavigate();
  const toast = useToast();
  const [devices, setDevices] = useState<EnterpriseDevice[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [view, setView] = useState<View>('grid');
  const [status, setStatus] = useState<StatusFilter>('all');
  const [q, setQ] = useState('');
  const [now, setNow] = useState(() => Date.now());

  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [commandModal, setCommandModal] = useState<CommandModalState>({
    open: false,
    deviceIds: [],
    commandType: 'LOCK',
    payloadValues: {},
  });
  const [delOpen, setDelOpen] = useState(false);
  const [syncing, setSyncing] = useState(false);

  const loadDevices = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await listEnterpriseDevices();
      setDevices(res?.devices ?? []);
      setTotal(res?.totalItemsCount ?? 0);
    } catch (err) {
      setError(mapEnterpriseError(err));
      setDevices([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadDevices();
  }, [loadDevices]);

  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 30000);
    return () => clearInterval(t);
  }, []);

  const onlineCount = useMemo(
    () => devices.filter((d) => isOnline(d, now)).length,
    [devices, now],
  );

  const filtered = useMemo(() => {
    const needle = q.trim().toLowerCase();
    return devices.filter((d) => {
      if (status === 'online' && !isOnline(d, now)) return false;
      if (status === 'offline' && isOnline(d, now)) return false;
      if (needle) {
        const hay = `${d.number ?? ''} ${d.description ?? ''} ${d.enterpriseDeviceId ?? ''}`.toLowerCase();
        if (!hay.includes(needle)) return false;
      }
      return true;
    });
  }, [devices, status, q, now]);

  const go = (d: EnterpriseDevice) => navigate(`/enterprise/devices/${encodeURIComponent(d.number)}`);

  const toggle = (id: number) =>
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  const clearSel = () => setSelected(new Set());
  const selectAllFiltered = () => setSelected(new Set(filtered.map((d) => d.id)));
  const selectionActive = selected.size > 0;
  const allFilteredSelected = filtered.length > 0 && filtered.every((d) => selected.has(d.id));
  const toggleAll = () => (allFilteredSelected ? clearSel() : selectAllFiltered());

  function openCommandModal(type: EnterpriseCommandType, deviceIds: string[]) {
    const fields = getCommandPayloadFields(type);
    const initialValues: Record<string, string | boolean> = {};
    for (const f of fields) {
      if (f.type === 'checkbox') initialValues[f.key] = false;
      else initialValues[f.key] = '';
    }
    setCommandModal({ open: true, deviceIds, commandType: type, payloadValues: initialValues });
  }

  function closeCommandModal() {
    setCommandModal({ open: false, deviceIds: [], commandType: 'LOCK', payloadValues: {} });
  }

  async function executeCommand() {
    const { deviceIds, commandType, payloadValues } = commandModal;
    const validationError = validateCommandPayload(commandType, payloadValues);
    if (validationError) {
      toast.push('err', 'Validation error', validationError);
      return;
    }
    const payload = buildCommandPayload(commandType, payloadValues);
    const req: EnterpriseCommandRequest = { type: commandType, payload };
    let successCount = 0;
    let failCount = 0;
    for (const deviceId of deviceIds) {
      try {
        await sendEnterpriseCommand(deviceId, req);
        successCount++;
      } catch (err) {
        failCount++;
        console.error('Command failed for', deviceId, err);
      }
    }
    if (successCount > 0) {
      toast.push('ok', 'Command sent', `${successCount} device(s) queued${failCount > 0 ? `, ${failCount} failed` : ''}`);
    } else if (failCount > 0) {
      toast.push('err', 'Command failed', `${failCount} device(s) failed`);
    }
    closeCommandModal();
    await loadDevices();
  }

  async function handleSync() {
    setSyncing(true);
    try {
      const res = await syncEnterpriseDevices();
      toast.push('ok', 'Sync triggered', `${res.synced} device(s) synced`);
      await loadDevices();
    } catch (err) {
      toast.push('err', 'Sync failed', mapEnterpriseError(err));
    } finally {
      setSyncing(false);
    }
  }

  async function handleDelete() {
    setDelOpen(false);
    try {
      for (const id of selected) {
        const device = devices.find((d) => d.id === id);
        if (device) await deleteEnterpriseDevice(device.enterpriseDeviceId);
      }
      toast.push('ok', 'Devices deleted', `${selected.size} device(s) removed`);
      clearSel();
      await loadDevices();
    } catch (err) {
      toast.push('err', 'Delete failed', mapEnterpriseError(err));
    }
  }

  function handlePayloadChange(key: string, value: string | boolean) {
    setCommandModal((m) => ({ ...m, payloadValues: { ...m.payloadValues, [key]: value } }));
  }

  return (
    <AppShell title="Enterprise Devices">
      <div className="dv-head">
        <h1>Enterprise Devices</h1>
        <span className="dv-count">
          {total > devices.length ? `${devices.length} of ${total}` : devices.length} total · {onlineCount} online
        </span>
        <div className="dv-spacer" />
        <div className="dv-search">
          <IconSearch />
          <input
            type="search"
            placeholder="Search devices"
            value={q}
            onChange={(e) => setQ(e.target.value)}
          />
        </div>
        <div className="toggle" role="group" aria-label="View">
          <button className={view === 'grid' ? 'on' : ''} onClick={() => setView('grid')} aria-label="Grid view" title="Grid">
            <IconGrid />
          </button>
          <button className={view === 'list' ? 'on' : ''} onClick={() => setView('list')} aria-label="List view" title="List">
            <IconList />
          </button>
        </div>
        <button className="btn btn-primary" onClick={handleSync} disabled={syncing}>
          {syncing ? 'Syncing…' : 'Sync All'}
        </button>
        <button className="btn btn-dark" onClick={() => navigate('/enterprise/enroll')}>
          Enroll Device
        </button>
      </div>

      <div className="filters">
        <button className={`filter-chip ${status === 'all' ? 'on' : ''}`} onClick={() => setStatus('all')}>
          All <b>{devices.length}</b>
        </button>
        <button className={`filter-chip ${status === 'online' ? 'on' : ''}`} onClick={() => setStatus('online')}>
          Online <b>{onlineCount}</b>
        </button>
        <button className={`filter-chip ${status === 'offline' ? 'on' : ''}`} onClick={() => setStatus('offline')}>
          Offline <b>{devices.length - onlineCount}</b>
        </button>
        <span className="filter-div" />
        <button className="btn btn-sm" onClick={handleSync} disabled={syncing}>
          {syncing ? 'Syncing…' : 'Sync Now'}
        </button>
      </div>

      {selected.size > 0 && (
        <div className="bulk-bar">
          <span className="bulk-count">{selected.size} selected</span>
          <button className="btn btn-sm" onClick={() => openCommandModal('LOCK', [...selected].map((id) => devices.find((d) => d.id === id)?.enterpriseDeviceId).filter(Boolean) as string[])}>
            Lock
          </button>
          <button className="btn btn-sm" onClick={() => openCommandModal('REBOOT', [...selected].map((id) => devices.find((d) => d.id === id)?.enterpriseDeviceId).filter(Boolean) as string[])}>
            Reboot
          </button>
          <button className="btn btn-sm" onClick={() => openCommandModal('WIPE', [...selected].map((id) => devices.find((d) => d.id === id)?.enterpriseDeviceId).filter(Boolean) as string[])}>
            Wipe
          </button>
          <button className="btn btn-sm" onClick={() => openCommandModal('CLEAR_APP_DATA', [...selected].map((id) => devices.find((d) => d.id === id)?.enterpriseDeviceId).filter(Boolean) as string[])}>
            Clear App Data
          </button>
          <div style={{ flex: 1 }} />
          {selected.size < filtered.length && (
            <button className="btn btn-sm btn-ghost" onClick={selectAllFiltered}>
              Select all {filtered.length}
            </button>
          )}
          <button className="btn btn-sm btn-ghost" onClick={clearSel}>
            Clear
          </button>
          <button className="btn btn-sm btn-danger" onClick={() => setDelOpen(true)}>
            Delete
          </button>
        </div>
      )}

      {error && <div className="banner banner-alert">{error}</div>}

      {loading ? (
        <div className="panel">
          <div className="empty">
            <span className="spin" /> Loading devices…
          </div>
        </div>
      ) : filtered.length === 0 ? (
        <div className="panel">
          <div className="empty">
            <span className="label">No devices</span>
            {devices.length === 0 ? 'No enterprise devices enrolled yet.' : 'No devices match these filters.'}
          </div>
        </div>
      ) : (
        <>
          <div className="select-all">
            <input
              type="checkbox"
              className="dev-check"
              checked={allFilteredSelected}
              ref={(el) => {
                if (el) el.indeterminate = selectionActive && !allFilteredSelected;
              }}
              onChange={toggleAll}
              aria-label="Select all devices"
            />
            <span onClick={toggleAll} style={{ cursor: 'pointer' }}>
              {allFilteredSelected ? 'Clear selection' : 'Select all'} · {filtered.length} device{filtered.length === 1 ? '' : 's'}
            </span>
          </div>
          {view === 'grid' ? (
            <div className="dev-grid">
              {filtered.map((d) => (
                <EnterpriseDeviceCard
                  key={d.id}
                  d={d}
                  now={now}
                  selected={selected.has(d.id)}
                  selectionActive={selectionActive}
                  onToggle={() => toggle(d.id)}
                  onOpen={() => go(d)}
                  onCommand={(type) => openCommandModal(type, [d.enterpriseDeviceId])}
                  onDelete={() => { setSelected(new Set([d.id])); setDelOpen(true); }}
                />
              ))}
            </div>
          ) : (
            <div className="dev-list">
              {filtered.map((d) => (
                <EnterpriseDeviceRow
                  key={d.id}
                  d={d}
                  now={now}
                  selected={selected.has(d.id)}
                  selectionActive={selectionActive}
                  onToggle={() => toggle(d.id)}
                  onOpen={() => go(d)}
                  onCommand={(type) => openCommandModal(type, [d.enterpriseDeviceId])}
                  onDelete={() => { setSelected(new Set([d.id])); setDelOpen(true); }}
                />
              ))}
            </div>
          )}
        </>
      )}

      {commandModal.open && (
        <CommandModal
          modal={commandModal}
          onClose={closeCommandModal}
          onExecute={executeCommand}
          onPayloadChange={handlePayloadChange}
        />
      )}

      {delOpen && (
        <div className="modal-backdrop" role="dialog" aria-modal="true" onClick={() => setDelOpen(false)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <h3>Delete devices</h3>
            <p className="muted" style={{ marginTop: 2 }}>
              Permanently remove {selected.size} device{selected.size === 1 ? '' : 's'} from enterprise?
              The device(s) will need to be re-enrolled.
            </p>
            <div className="modal-actions">
              <button className="btn" onClick={() => setDelOpen(false)}>Cancel</button>
              <button className="btn btn-danger" onClick={() => void handleDelete()}>
                Delete {selected.size}
              </button>
            </div>
          </div>
        </div>
      )}
    </AppShell>
  );
}

function SelectBox({ selected, onToggle }: { selected: boolean; onToggle: () => void }) {
  return (
    <input
      type="checkbox"
      className="dev-check"
      checked={selected}
      onClick={(e) => e.stopPropagation()}
      onChange={onToggle}
      aria-label="Select device"
    />
  );
}

function EnterpriseDeviceCard({
  d,
  now,
  selected,
  selectionActive,
  onToggle,
  onOpen,
  onCommand,
  onDelete,
}: {
  d: EnterpriseDevice;
  now: number;
  selected: boolean;
  selectionActive: boolean;
  onToggle: () => void;
  onOpen: () => void;
  onCommand: (type: EnterpriseCommandType) => void;
  onDelete: () => void;
}) {
  const online = isOnline(d, now);
  const act = selectionActive ? onToggle : onOpen;
  return (
    <div
      className={`dev ${selected ? 'sel' : ''}`}
      role="button"
      tabIndex={0}
      onClick={act}
      onKeyDown={(e) => e.key === 'Enter' && act()}
    >
      <div className="h">
        <SelectBox selected={selected} onToggle={onToggle} />
        <span className={`dot ${online ? 'on' : 'off'}`} />
        <span className="nm">{orDash(d.number)}</span>
        <DeviceGlyph className="ico" name={d.description || d.number} size={16} />
      </div>
      {d.description && <div className="sub">{d.description}</div>}
      <div className="kv">
        <div>
          <div className="k">Android</div>
          <div className="v">{orDash(d.androidVersion)}</div>
        </div>
        <div>
          <div className="k">Enterprise ID</div>
          <div className="v mono" style={{ fontSize: 11, wordBreak: 'break-all' }}>{d.enterpriseDeviceId}</div>
        </div>
        <div>
          <div className="k">Seen</div>
          <div className="v">{fmtRelative(d.lastUpdate)}</div>
        </div>
      </div>
      <div className="card-actions" style={{ display: 'flex', gap: 4, marginTop: 8, paddingTop: 8, borderTop: '1px solid var(--border)' }}>
        <button className="btn btn-sm" onClick={(e) => { e.stopPropagation(); onCommand('LOCK'); }} title="Lock">🔒</button>
        <button className="btn btn-sm" onClick={(e) => { e.stopPropagation(); onCommand('REBOOT'); }} title="Reboot">⟳</button>
        <button className="btn btn-sm btn-danger" onClick={(e) => { e.stopPropagation(); onCommand('WIPE'); }} title="Wipe">🗑</button>
        <button className="btn btn-sm" onClick={(e) => { e.stopPropagation(); onCommand('CLEAR_APP_DATA'); }} title="Clear App Data">🧹</button>
        <button className="btn btn-sm" onClick={(e) => { e.stopPropagation(); onDelete(); }} title="Delete">✕</button>
      </div>
    </div>
  );
}

function EnterpriseDeviceRow({
  d,
  now,
  selected,
  selectionActive,
  onToggle,
  onOpen,
  onCommand,
  onDelete,
}: {
  d: EnterpriseDevice;
  now: number;
  selected: boolean;
  selectionActive: boolean;
  onToggle: () => void;
  onOpen: () => void;
  onCommand: (type: EnterpriseCommandType) => void;
  onDelete: () => void;
}) {
  const online = isOnline(d, now);
  const act = selectionActive ? onToggle : onOpen;
  return (
    <div
      className={`dev-row ${selected ? 'sel' : ''}`}
      role="button"
      tabIndex={0}
      onClick={act}
      onKeyDown={(e) => e.key === 'Enter' && act()}
    >
      <div className="id">
        <SelectBox selected={selected} onToggle={onToggle} />
        <span className={`dot ${online ? 'on' : 'off'}`} />
        <DeviceGlyph className="ico" name={d.description || d.number} size={15} />
        <div style={{ minWidth: 0 }}>
          <div className="nm">{orDash(d.number)}</div>
          {d.description && <div className="sub">{d.description}</div>}
        </div>
      </div>
      <div className="lc">
        <span className="lk">Android</span>
        <span className="lv">{orDash(d.androidVersion)}</span>
      </div>
      <div className="lc">
        <span className="lk">Enterprise ID</span>
        <span className="lv mono" style={{ fontSize: 11, wordBreak: 'break-all' }}>{d.enterpriseDeviceId}</span>
      </div>
      <div className="lc">
        <span className="lk">Seen</span>
        <span className="lv">{fmtRelative(d.lastUpdate)}</span>
      </div>
      <div className="lc" style={{ display: 'flex', gap: 4 }}>
        <button className="btn btn-sm" onClick={(e) => { e.stopPropagation(); onCommand('LOCK'); }} title="Lock">🔒</button>
        <button className="btn btn-sm" onClick={(e) => { e.stopPropagation(); onCommand('REBOOT'); }} title="Reboot">⟳</button>
        <button className="btn btn-sm btn-danger" onClick={(e) => { e.stopPropagation(); onCommand('WIPE'); }} title="Wipe">🗑</button>
        <button className="btn btn-sm" onClick={(e) => { e.stopPropagation(); onCommand('CLEAR_APP_DATA'); }} title="Clear App Data">🧹</button>
        <button className="btn btn-sm" onClick={(e) => { e.stopPropagation(); onDelete(); }} title="Delete">✕</button>
      </div>
    </div>
  );
}

function CommandModal({
  modal,
  onClose,
  onExecute,
  onPayloadChange,
}: {
  modal: CommandModalState;
  onClose: () => void;
  onExecute: () => void;
  onPayloadChange: (key: string, value: string | boolean) => void;
}) {
  const { commandType, payloadValues, deviceIds } = modal;
  const fields = getCommandPayloadFields(commandType);
  const cmdTypeInfo = COMMAND_TYPES.find((c) => c.value === commandType);
  const isDanger = cmdTypeInfo?.danger;

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()} style={{ maxWidth: 480 }}>
        <h3>{cmdTypeInfo?.label || commandType}</h3>
        <p className="muted" style={{ margin: '2px 0 14px' }}>
          Apply to {deviceIds.length} device{deviceIds.length === 1 ? '' : 's'}
        </p>
        {fields.length === 0 ? (
          <p className="muted">No additional parameters required.</p>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {fields.map((field) => (
              <label key={field.key} className="field" style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                <span>{field.label}{field.required ? ' *' : ''}</span>
                {field.type === 'checkbox' ? (
                  <input
                    type="checkbox"
                    checked={payloadValues[field.key] === true}
                    onChange={(e) => onPayloadChange(field.key, e.target.checked)}
                  />
                ) : field.type === 'number' ? (
                  <input
                    className="input"
                    type="number"
                    placeholder={field.placeholder}
                    value={String(payloadValues[field.key] ?? '')}
                    onChange={(e) => onPayloadChange(field.key, e.target.value)}
                    required={field.required}
                  />
                ) : (
                  <input
                    className="input"
                    type="text"
                    placeholder={field.placeholder}
                    value={String(payloadValues[field.key] ?? '')}
                    onChange={(e) => onPayloadChange(field.key, e.target.value)}
                    required={field.required}
                  />
                )}
              </label>
            ))}
          </div>
        )}
        <div className="modal-actions">
          <button className="btn" onClick={onClose}>Cancel</button>
          <button className={`btn btn-primary ${isDanger ? 'btn-danger' : ''}`} onClick={() => void onExecute()}>
            Execute
          </button>
        </div>
      </div>
    </div>
  );
}