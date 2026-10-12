import { apiClient, ApiError } from './client';

export interface EnterpriseConfigRequest {
  enterpriseId: string;
  serviceAccountJson: string;
  projectId: string;
  dpcPackageName?: string;
  enabled?: boolean;
}

export interface AndroidEnterpriseConfig {
  id: number;
  customerId: number;
  enterpriseId: string;
  projectId: string;
  dpcPackageName: string;
  enabled: boolean;
  createdAt: number;
  updatedAt: number;
}

export interface EnterpriseEnrollmentTokenRequest {
  configurationId?: number;
  oneTime?: boolean;
  durationMillis?: number;
}

export interface EnrollmentToken {
  name: string;
  value: string;
  duration: string;
  oneTime: boolean;
  policyName: string;
}

export interface EnrollmentTokenResponse {
  token: EnrollmentToken;
  qrCodeData: string;
  configurationId: number;
}

export interface EnterpriseDevice {
  id: number;
  number: string;
  enrollmentMode: 'enterprise';
  enterpriseDeviceId: string;
  configurationId?: number;
  description?: string;
  lastUpdate?: number;
  imei?: string;
  phone?: string;
  publicIp?: string;
  serial?: string;
  androidVersion?: string;
  enrollTime?: number;
  mdmMode?: boolean;
  kioskMode?: boolean;
  launcherVersion?: string;
  statusCode?: string;
  hardwareId?: string;
  custom1?: string;
  custom2?: string;
  custom3?: string;
  oldNumber?: string;
  launcherPkg?: string;
  groups?: { id: number; name: string }[];
}

export interface EnterpriseDeviceListView {
  devices: EnterpriseDevice[];
  totalItemsCount: number;
}

export type EnterpriseCommandType =
  | 'WIPE'
  | 'LOCK'
  | 'INSTALL_APP'
  | 'UNINSTALL_APP'
  | 'CLEAR_APP_DATA'
  | 'REBOOT';

export interface WipePayload {
  userWipe: boolean;
}

export interface LockPayload {
  lockMessage?: string;
}

export interface InstallAppPayload {
  packageName: string;
  versionCode?: number;
}

export interface UninstallAppPayload {
  packageName: string;
}

export interface ClearAppDataPayload {
  packageName: string;
}

export interface RebootPayload {}

export type EnterpriseCommandPayload =
  | WipePayload
  | LockPayload
  | InstallAppPayload
  | UninstallAppPayload
  | ClearAppDataPayload
  | RebootPayload;

export interface EnterpriseCommandRequest {
  type: EnterpriseCommandType;
  payload: EnterpriseCommandPayload;
}

export interface EnterpriseCommandResponse {
  name: string;
  done: boolean;
  response?: Record<string, unknown>;
  error?: { code: number; message: string; status: string };
}

export interface SyncEnterpriseResponse {
  synced: number;
}

export interface EnterpriseEnrollRequest {
  enrollmentToken: string;
  deviceId?: string;
  capabilities?: Record<string, unknown>;
  hardwareId: string;
  androidSdkInt: number;
  androidRelease: string;
  manufacturer: string;
  model: string;
  isDeviceOwner: boolean;
}

export interface EnterpriseEnrollResponse {
  success: boolean;
  data: {
    deviceId: string;
    configurationName: string;
    deviceSecret: string;
  };
}

const ENTERPRISE_ERROR_MESSAGES: Record<string, string> = {
  'error.enterprise.not.configured': 'Enterprise not configured. Please set up your Google Cloud service account first.',
  'error.enterprise.serviceaccount.required': 'Service Account JSON is required.',
  'error.enterprise.serviceaccount.invalid': 'Invalid Service Account JSON. Verify the key is correct and has Android Management API permissions.',
  'error.enterprise.token.create.failed': 'Failed to create enrollment token. Check your enterprise configuration.',
  'error.enterprise.command.unsupported': 'Command type not supported.',
  'error.agent.token.invalid': 'Enrollment token is invalid or expired.',
  'error.agent.enrollment.disabled': 'Enrollment is disabled for this configuration.',
};

export function mapEnterpriseError(error: unknown): string {
  if (error instanceof ApiError) {
    const mapped = ENTERPRISE_ERROR_MESSAGES[error.status];
    if (mapped) return mapped;
    if (error.message) return error.message;
    return `Enterprise error: ${error.status}`;
  }
  if (error instanceof Error) return error.message;
  return 'An unknown error occurred';
}

export async function getEnterpriseConfig(): Promise<AndroidEnterpriseConfig | null> {
  try {
    return await apiClient.get<AndroidEnterpriseConfig>('/private/settings/enterprise');
  } catch (err) {
    if (err instanceof ApiError && (err.httpStatus === 404 || err.status === 'error.enterprise.not.configured')) {
      return null;
    }
    throw err;
  }
}

export async function saveEnterpriseConfig(req: EnterpriseConfigRequest): Promise<AndroidEnterpriseConfig> {
  return apiClient.post<AndroidEnterpriseConfig>('/private/settings/enterprise', req);
}

export async function deleteEnterpriseConfig(): Promise<void> {
  await apiClient.del('/private/settings/enterprise');
}

export async function createEnrollmentToken(req: EnterpriseEnrollmentTokenRequest = {}): Promise<EnrollmentTokenResponse> {
  return apiClient.post<EnrollmentTokenResponse>('/private/agent/v1/enterprise/enrollment-token', req);
}

export async function getEnterpriseQr(
  configId: number,
  format: 'png' | 'json' = 'json',
  size = 300
): Promise<string | Blob> {
  const url = `/rest/public/qr/enterprise/${configId}?format=${format}&size=${size}`;
  const res = await fetch(url, { credentials: 'include' });
  if (!res.ok) throw new Error('Failed to fetch QR code');
  return format === 'png' ? res.blob() : res.text();
}

export async function listEnterpriseDevices(): Promise<EnterpriseDeviceListView> {
  return apiClient.get<EnterpriseDeviceListView>('/private/agent/v1/enterprise/devices');
}

export async function sendEnterpriseCommand(
  deviceId: string,
  req: EnterpriseCommandRequest
): Promise<EnterpriseCommandResponse> {
  return apiClient.post<EnterpriseCommandResponse>(
    `/private/agent/v1/enterprise/devices/${encodeURIComponent(deviceId)}/command`,
    req
  );
}

export async function deleteEnterpriseDevice(deviceId: string): Promise<void> {
  await apiClient.del(`/private/agent/v1/enterprise/devices/${encodeURIComponent(deviceId)}`);
}

export async function syncEnterpriseDevices(): Promise<SyncEnterpriseResponse> {
  return apiClient.post<SyncEnterpriseResponse>('/private/agent/v1/enterprise/sync', {});
}

export async function enrollEnterpriseDevice(req: EnterpriseEnrollRequest): Promise<EnterpriseEnrollResponse> {
  return apiClient.post<EnterpriseEnrollResponse>('/public/agent/v1/enterprise/enroll', req);
}

export const COMMAND_TYPES: { value: EnterpriseCommandType; label: string; description: string; danger?: boolean }[] = [
  { value: 'WIPE', label: 'Factory Reset (Wipe)', description: 'Erase all data on the device. Optionally preserve user data.', danger: true },
  { value: 'LOCK', label: 'Lock Device', description: 'Lock the device screen with an optional message.', danger: true },
  { value: 'INSTALL_APP', label: 'Install App', description: 'Install an application by package name and optional version code.' },
  { value: 'UNINSTALL_APP', label: 'Uninstall App', description: 'Uninstall an application by package name.' },
  { value: 'CLEAR_APP_DATA', label: 'Clear App Data', description: 'Clear all data for an application by package name.' },
  { value: 'REBOOT', label: 'Reboot Device', description: 'Reboot the device immediately.', danger: true },
];

export function getCommandPayloadFields(type: EnterpriseCommandType): { key: string; label: string; type: 'text' | 'number' | 'checkbox'; required?: boolean; placeholder?: string }[] {
  switch (type) {
    case 'WIPE':
      return [
        { key: 'userWipe', label: 'User Wipe (preserve user data)', type: 'checkbox' },
      ];
    case 'LOCK':
      return [
        { key: 'lockMessage', label: 'Lock Message (optional)', type: 'text', placeholder: 'Device locked by admin' },
      ];
    case 'INSTALL_APP':
      return [
        { key: 'packageName', label: 'Package Name', type: 'text', required: true, placeholder: 'com.example.app' },
        { key: 'versionCode', label: 'Version Code (optional)', type: 'number', placeholder: '123' },
      ];
    case 'UNINSTALL_APP':
    case 'CLEAR_APP_DATA':
      return [
        { key: 'packageName', label: 'Package Name', type: 'text', required: true, placeholder: 'com.example.app' },
      ];
    case 'REBOOT':
    default:
      return [];
  }
}

export function buildCommandPayload(type: EnterpriseCommandType, values: Record<string, string | boolean>): EnterpriseCommandPayload {
  switch (type) {
    case 'WIPE':
      return { userWipe: values.userWipe === true };
    case 'LOCK':
      return { lockMessage: String(values.lockMessage || '') };
    case 'INSTALL_APP':
      return { packageName: String(values.packageName || ''), versionCode: values.versionCode ? Number(values.versionCode) : undefined };
    case 'UNINSTALL_APP':
    case 'CLEAR_APP_DATA':
      return { packageName: String(values.packageName || '') };
    case 'REBOOT':
    default:
      return {};
  }
}

export function validateCommandPayload(type: EnterpriseCommandType, values: Record<string, string | boolean>): string | null {
  const fields = getCommandPayloadFields(type);
  for (const field of fields) {
    if (field.required && (!values[field.key] || String(values[field.key]).trim() === '')) {
      return `${field.label} is required`;
    }
  }
  return null;
}