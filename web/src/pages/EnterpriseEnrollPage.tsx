import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';
import { AppShell } from '../ui/AppShell';
import { IconCopy } from '../ui/icons';
import { useToast } from '../ui/toast';
import { QrCanvas } from '../components/QrCanvas';
import { fmtDateTime } from '../ui/format';
import {
  getEnterpriseConfig,
  createEnrollmentToken,
  type EnrollmentTokenResponse,
  mapEnterpriseError,
} from '../api/enterprise';

const DEFAULT_DURATION_MS = 24 * 60 * 60 * 1000;

/** Parses an AM API protobuf duration string (e.g. "86400s") into milliseconds. */
function parseDurationMs(duration: string): number {
  if (duration.endsWith('ms')) return parseInt(duration.slice(0, -2), 10);
  if (duration.endsWith('s')) return Math.round(parseFloat(duration.slice(0, -1)) * 1000);
  const n = Number(duration);
  return Number.isFinite(n) ? n : 0;
}

export function EnterpriseEnrollPage() {
  const navigate = useNavigate();
  const toast = useToast();
  const [config, setConfig] = useState<{ id: number; enterpriseId: string } | null>(null);
  const [tokenData, setTokenData] = useState<EnrollmentTokenResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [generating, setGenerating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    loadConfig();
  }, []);

  async function loadConfig() {
    setLoading(true);
    setError(null);
    try {
      const cfg = await getEnterpriseConfig();
      if (cfg) {
        if (!cfg.enabled) {
          setError('Enterprise configuration exists but is disabled. Please enable it in Enterprise Settings.');
          setConfig({ id: cfg.id, enterpriseId: cfg.enterpriseId });
        } else {
          setConfig({ id: cfg.id, enterpriseId: cfg.enterpriseId });
          await generateToken(cfg.id);
        }
      } else {
        setError('Enterprise not configured. Please configure Android Enterprise first.');
      }
    } catch (err) {
      setError(mapEnterpriseError(err));
    } finally {
      setLoading(false);
    }
  }

  async function generateToken(configurationId: number) {
    setGenerating(true);
    setError(null);
    try {
      const res = await createEnrollmentToken({
        configurationId,
        oneTime: true,
        durationMillis: DEFAULT_DURATION_MS,
      });
      setTokenData(res);
    } catch (err) {
      setError(mapEnterpriseError(err));
    } finally {
      setGenerating(false);
    }
  }

  async function handleRefresh() {
    if (!config) return;
    await generateToken(config.id);
  }

  async function copyToken() {
    if (!tokenData?.token?.value) return;
    try {
      await navigator.clipboard.writeText(tokenData.token.value);
      toast.push('ok', 'Token copied', 'Enrollment token copied to clipboard');
    } catch {
      toast.push('err', 'Copy failed', 'Select and copy the token manually');
    }
  }

  async function copyQrData() {
    if (!tokenData?.qrCodeData) return;
    try {
      await navigator.clipboard.writeText(tokenData.qrCodeData);
      toast.push('ok', 'QR data copied', 'Provisioning JSON copied to clipboard');
    } catch {
      toast.push('err', 'Copy failed', 'Select and copy the QR data manually');
    }
  }

  if (loading) {
    return (
      <AppShell title="Enrollment QR">
        <div className="panel">
          <div className="empty"><span className="spin" /> Loading…</div>
        </div>
      </AppShell>
    );
  }

  if (error && !tokenData) {
    return (
      <AppShell title="Enrollment QR">
        <div className="panel">
          <div className="banner banner-alert">{error}</div>
          <button className="btn btn-primary" onClick={() => navigate('/enterprise/settings')} style={{ marginTop: 16 }}>
            Configure Enterprise
          </button>
        </div>
      </AppShell>
    );
  }

  const token = tokenData?.token;
  const expiresAt = token?.duration
    ? Date.now() + parseDurationMs(token.duration)
    : undefined;

  return (
    <AppShell title="Enrollment QR">
      <div className="enroll">
        <div className="enroll-top">
          <h1 style={{ fontSize: 24, fontWeight: 700, letterSpacing: '-0.02em', margin: 0 }}>
            Enterprise Enrollment
          </h1>
          <div className="sp" />
          {config && (
            <span className="mono" style={{ fontSize: 13, color: 'var(--muted)' }}>
              Enterprise: {config.enterpriseId}
            </span>
          )}
        </div>

        {error && <div className="banner banner-alert">{error}</div>}

        <section className="panel">
          <div className="panel-head">
            <h2 className="panel-title">Scan to Enroll</h2>
            <button className="btn btn-sm" onClick={handleRefresh} disabled={generating}>
              {generating ? 'Generating…' : 'New Token'}
            </button>
          </div>

          <div className="qr-layout">
            <div>
              <div className="qr-frame">
                {tokenData?.qrCodeData ? (
                  <QrCanvas text={tokenData.qrCodeData} size={320} />
                ) : (
                  <div className="empty"><span className="spin" /> Generating QR code…</div>
                )}
              </div>
              <div className="qr-cap">
                Single-use token{expiresAt ? ` · expires ${fmtDateTime(expiresAt)}` : ''}
                {tokenData?.configurationId ? ` · Configuration ID: ${tokenData.configurationId}` : ''}
              </div>
              <details className="qr-details">
                <summary>Token Details</summary>
                <div className="token-details" style={{ marginTop: 12, fontSize: 13 }}>
                  <div style={{ marginBottom: 8 }}>
                    <strong>Token Value:</strong>
                    <div className="token-box" style={{ marginTop: 4, display: 'flex', gap: 8, alignItems: 'center' }}>
                      <code className="tok" style={{ flex: 1, wordBreak: 'break-all' }}>{token?.value || '—'}</code>
                      <button className="btn btn-sm btn-ghost" onClick={copyToken} aria-label="Copy token">
                        <IconCopy className="ico" />
                      </button>
                    </div>
                  </div>
                  <div style={{ marginBottom: 8 }}>
                    <strong>Token Name:</strong>
                    <code className="mono">{token?.name || '—'}</code>
                  </div>
                  <div style={{ marginBottom: 8 }}>
                    <strong>Duration:</strong>
                    <span className="mono">{token?.duration || '—'}</span>
                  </div>
                  <div style={{ marginBottom: 8 }}>
                    <strong>One-time:</strong>
                    <span>{token?.oneTime ? 'Yes' : 'No'}</span>
                  </div>
                  <div style={{ marginBottom: 8 }}>
                    <strong>Policy:</strong>
                    <code className="mono">{token?.policyName || '—'}</code>
                  </div>
                  <div>
                    <strong>Configuration ID:</strong>
                    <span className="mono">{tokenData?.configurationId ?? '—'}</span>
                  </div>
                </div>
              </details>
              <details className="qr-details" style={{ marginTop: 16 }}>
                <summary>QR Code Data (JSON)</summary>
                <div style={{ marginTop: 8 }}>
                  <button className="btn btn-sm btn-ghost" onClick={copyQrData} style={{ marginBottom: 8 }}>
                    <IconCopy className="ico" /> Copy JSON
                  </button>
                  <pre className="mono" style={{ fontSize: 11, maxHeight: 200, overflow: 'auto', whiteSpace: 'pre-wrap', wordBreak: 'break-word', background: 'var(--bg-elevated)', padding: 12, borderRadius: 4 }}>
                    {tokenData?.qrCodeData || '—'}
                  </pre>
                </div>
              </details>
            </div>
            <ol className="qr-steps" style={{ listStyle: 'none', margin: 0, padding: 0 }}>
              <li className="qr-step" style={{ display: 'flex', alignItems: 'flex-start', gap: 12, marginBottom: 16 }}>
                <span className="step-n" style={{ width: 28, height: 28, borderRadius: '50%', background: 'var(--accent)', color: 'var(--accent-contrast)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 700, flexShrink: 0 }}>1</span>
                <span className="st-tx" style={{ display: 'flex', flexDirection: 'column' }}>
                  <strong>Factory reset the device</strong>
                  <span className="sub" style={{ color: 'var(--muted)', fontSize: 13 }}>On the "Hi there" welcome screen, don't sign in yet</span>
                </span>
              </li>
              <li className="qr-step" style={{ display: 'flex', alignItems: 'flex-start', gap: 12, marginBottom: 16 }}>
                <span className="step-n" style={{ width: 28, height: 28, borderRadius: '50%', background: 'var(--accent)', color: 'var(--accent-contrast)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 700, flexShrink: 0 }}>2</span>
                <span className="st-tx" style={{ display: 'flex', flexDirection: 'column' }}>
                  <strong>Tap screen 6 times</strong>
                  <span className="sub" style={{ color: 'var(--muted)', fontSize: 13 }}>Opens the QR provisioning scanner. Connect to Wi-Fi if prompted</span>
                </span>
              </li>
              <li className="qr-step" style={{ display: 'flex', alignItems: 'flex-start', gap: 12, marginBottom: 16 }}>
                <span className="step-n" style={{ width: 28, height: 28, borderRadius: '50%', background: 'var(--accent)', color: 'var(--accent-contrast)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 700, flexShrink: 0 }}>3</span>
                <span className="st-tx" style={{ display: 'flex', flexDirection: 'column' }}>
                  <strong>Scan this QR code</strong>
                  <span className="sub" style={{ color: 'var(--muted)', fontSize: 13 }}>Android downloads the MDMesh agent and sets it as device owner</span>
                </span>
              </li>
              <li className="qr-step" style={{ display: 'flex', alignItems: 'flex-start', gap: 12 }}>
                <span className="step-n" style={{ width: 28, height: 28, borderRadius: '50%', background: 'var(--accent)', color: 'var(--accent-contrast)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 700, flexShrink: 0 }}>4</span>
                <span className="st-tx" style={{ display: 'flex', flexDirection: 'column' }}>
                  <strong>Wait for enrollment</strong>
                  <span className="sub" style={{ color: 'var(--muted)', fontSize: 13 }}>Device appears in Enterprise Devices after first check-in</span>
                </span>
              </li>
            </ol>
          </div>
        </section>
      </div>
    </AppShell>
  );
}