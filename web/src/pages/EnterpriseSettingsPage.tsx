import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';
import { AppShell } from '../ui/AppShell';
import { useToast } from '../ui/toast';
import {
  getEnterpriseConfig,
  saveEnterpriseConfig,
  deleteEnterpriseConfig,
  type AndroidEnterpriseConfig,
  type EnterpriseConfigRequest,
  mapEnterpriseError,
} from '../api/enterprise';

const DEFAULT_DPC_PACKAGE = 'com.mdmesh.agent';

export function EnterpriseSettingsPage() {
  const navigate = useNavigate();
  const toast = useToast();
  const [config, setConfig] = useState<AndroidEnterpriseConfig | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [form, setForm] = useState<EnterpriseConfigRequest>({
    enterpriseId: '',
    serviceAccountJson: '',
    projectId: '',
    dpcPackageName: DEFAULT_DPC_PACKAGE,
    enabled: true,
  });
  const [saJsonError, setSaJsonError] = useState<string | null>(null);

  useEffect(() => {
    loadConfig();
  }, []);

  async function loadConfig() {
    setLoading(true);
    setError(null);
    try {
      const cfg = await getEnterpriseConfig();
      setConfig(cfg);
      if (cfg) {
        setForm({
          enterpriseId: cfg.enterpriseId,
          serviceAccountJson: '',
          projectId: cfg.projectId,
          dpcPackageName: cfg.dpcPackageName,
          enabled: cfg.enabled,
        });
      }
    } catch (err) {
      setError(mapEnterpriseError(err));
    } finally {
      setLoading(false);
    }
  }

  function validateSaJson(json: string): boolean {
    if (!json.trim()) {
      setSaJsonError('Service Account JSON is required');
      return false;
    }
    try {
      const parsed = JSON.parse(json);
      if (!parsed.type || parsed.type !== 'service_account') {
        setSaJsonError('Invalid Service Account: missing or incorrect "type" field (expected "service_account")');
        return false;
      }
      if (!parsed.project_id) {
        setSaJsonError('Invalid Service Account: missing "project_id" field');
        return false;
      }
      if (!parsed.client_email) {
        setSaJsonError('Invalid Service Account: missing "client_email" field');
        return false;
      }
      if (!parsed.private_key) {
        setSaJsonError('Invalid Service Account: missing "private_key" field');
        return false;
      }
      setSaJsonError(null);
      return true;
    } catch {
      setSaJsonError('Invalid JSON format');
      return false;
    }
  }

  function validateForm(): boolean {
    if (!form.enterpriseId.trim()) {
      setError('Enterprise ID is required');
      return false;
    }
    if (!form.enterpriseId.match(/^enterprises\//)) {
      setError('Enterprise ID must be in format "enterprises/..."');
      return false;
    }
    if (!form.projectId.trim()) {
      setError('Project ID is required');
      return false;
    }
    if (!validateSaJson(form.serviceAccountJson)) {
      return false;
    }
    return true;
  }

  async function handleSave() {
    if (!validateForm()) return;
    setSaving(true);
    setError(null);
    try {
      const saved = await saveEnterpriseConfig(form);
      setConfig(saved);
      setForm((f) => ({ ...f, serviceAccountJson: '' }));
      toast.push('ok', 'Enterprise configuration saved', `Enterprise ${saved.enterpriseId} configured`);
    } catch (err) {
      setError(mapEnterpriseError(err));
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete() {
    if (!window.confirm('Disable Android Enterprise integration? This will remove the configuration and all enrolled devices will need to be re-enrolled.')) return;
    setDeleting(true);
    setError(null);
    try {
      await deleteEnterpriseConfig();
      setConfig(null);
      setForm({
        enterpriseId: '',
        serviceAccountJson: '',
        projectId: '',
        dpcPackageName: DEFAULT_DPC_PACKAGE,
        enabled: true,
      });
      toast.push('ok', 'Enterprise configuration deleted', 'Android Enterprise integration disabled');
    } catch (err) {
      setError(mapEnterpriseError(err));
    } finally {
      setDeleting(false);
    }
  }

  function handleChange<K extends keyof EnterpriseConfigRequest>(key: K, value: EnterpriseConfigRequest[K]) {
    setForm((f) => ({ ...f, [key]: value }));
    if (key === 'serviceAccountJson') {
      validateSaJson(String(value));
    }
    setError(null);
  }

  if (loading) {
    return (
      <AppShell title="Android Enterprise">
        <div className="panel">
          <div className="empty"><span className="spin" /> Loading enterprise configuration…</div>
        </div>
      </AppShell>
    );
  }

  const isConfigured = config !== null;

  return (
    <AppShell title="Android Enterprise">
      <div className="page-head">
        <h1>Android Enterprise</h1>
        <span className="mono">{isConfigured ? 'Configured' : 'Not configured'}</span>
      </div>

      {error && <div className="banner banner-alert">{error}</div>}

      <section className="panel">
        <div className="panel-head">
          <h2 className="panel-title">Enterprise Configuration</h2>
        </div>

        <div className="set-row">
          <span className="k">Enterprise ID</span>
          <span className="v">
            <input
              className="input"
              type="text"
              value={form.enterpriseId}
              onChange={(e) => handleChange('enterpriseId', e.target.value)}
              placeholder="enterprises/LC01abc123"
              disabled={saving}
            />
          </span>
        </div>

        <div className="set-row">
          <span className="k">Project ID</span>
          <span className="v">
            <input
              className="input"
              type="text"
              value={form.projectId}
              onChange={(e) => handleChange('projectId', e.target.value)}
              placeholder="my-gcp-project"
              disabled={saving}
            />
          </span>
        </div>

        <div className="set-row">
          <span className="k">DPC Package Name</span>
          <span className="v">
            <input
              className="input"
              type="text"
              value={form.dpcPackageName}
              onChange={(e) => handleChange('dpcPackageName', e.target.value)}
              placeholder={DEFAULT_DPC_PACKAGE}
              disabled={saving}
            />
          </span>
        </div>

        <div className="set-row">
          <span className="k">Enabled</span>
          <span className="v">
            <label className="switch">
              <input
                type="checkbox"
                checked={form.enabled}
                onChange={(e) => handleChange('enabled', e.target.checked)}
                disabled={saving}
              />
              <span className="slider" />
            </label>
          </span>
        </div>

        <div className="set-row">
          <span className="k">Service Account JSON</span>
          <span className="v">
            <textarea
              className="input mono"
              rows={12}
              value={form.serviceAccountJson}
              onChange={(e) => handleChange('serviceAccountJson', e.target.value)}
              placeholder='{ "type": "service_account", "project_id": "...", "private_key_id": "...", "private_key": "-----BEGIN PRIVATE KEY-----\n...", "client_email": "...", "client_id": "...", "auth_uri": "...", "token_uri": "...", "auth_provider_x509_cert_url": "...", "client_x509_cert_url": "..." }'
              disabled={saving}
              style={{ fontSize: 12, fontFamily: 'monospace', minHeight: 200 }}
            />
            {saJsonError && <p className="note" style={{ color: 'var(--err)', marginTop: 4 }}>{saJsonError}</p>}
            <p className="note" style={{ marginTop: 8 }}>
              Paste the full JSON key file from Google Cloud Console (IAM & Admin → Service Accounts → Keys).
              The private key is stored encrypted on the server and never returned in API responses.
            </p>
          </span>
        </div>

        <div className="panel-actions" style={{ display: 'flex', gap: 8, marginTop: 16 }}>
          <button className="btn btn-primary" onClick={handleSave} disabled={saving}>
            {saving ? 'Saving…' : isConfigured ? 'Update Configuration' : 'Save Configuration'}
          </button>
          {isConfigured && (
            <button className="btn btn-danger" onClick={handleDelete} disabled={deleting}>
              {deleting ? 'Deleting…' : 'Disable Enterprise'}
            </button>
          )}
        </div>
      </section>

      {isConfigured && (
        <section className="panel">
          <div className="panel-head">
            <h2 className="panel-title">Quick Actions</h2>
          </div>
          <div className="set-row">
            <span className="k">Manage Devices</span>
            <span className="v">
              <button className="btn btn-primary" onClick={() => navigate('/enterprise/devices')}>
                View & Manage Devices
              </button>
            </span>
          </div>
          <div className="set-row">
            <span className="k">Generate Enrollment QR</span>
            <span className="v">
              <button className="btn btn-primary" onClick={() => navigate('/enterprise/enroll')}>
                Create Enrollment Token & QR
              </button>
            </span>
          </div>
          <div className="set-row">
            <span className="k">Force Sync</span>
            <span className="v">
              <button className="btn" onClick={async () => {
                try {
                  await (await import('../api/enterprise')).syncEnterpriseDevices();
                  toast.push('ok', 'Sync triggered', 'Enterprise devices sync initiated');
                } catch (err) {
                  toast.push('err', 'Sync failed', mapEnterpriseError(err));
                }
              }}>
                Sync Devices Now
              </button>
            </span>
          </div>
        </section>
      )}

      {!isConfigured && (
        <section className="panel">
          <div className="panel-head">
            <h2 className="panel-title">Getting Started</h2>
          </div>
          <div className="set-row">
            <span className="k" style={{ fontWeight: 400, lineHeight: 1.6 }}>
              <ol style={{ margin: 0, paddingLeft: 20 }}>
                <li>Create a Google Cloud project and enable the Android Management API</li>
                <li>Create a Service Account with the <b>Android Management Admin</b> role</li>
                <li>Download the JSON key file</li>
                <li>Create an Enterprise via the Play EMM API or Android Management API</li>
                <li>Enter the Enterprise ID, Project ID, and Service Account JSON above</li>
              </ol>
            </span>
          </div>
        </section>
      )}
    </AppShell>
  );
}