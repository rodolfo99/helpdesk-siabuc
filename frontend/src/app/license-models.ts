export interface LicenseDefaults { emailMinutes: number; remoteMinutes: number; configured: boolean; revision: number; }
export interface SupportLicense {
  id: number; institutionId: number; institutionName: string; number: string; defaultsRevision: number;
  initialEmailMinutes: number; initialRemoteMinutes: number;
  emailAllocatedMinutes: number; remoteAllocatedMinutes: number;
  emailUsedMinutes: number; remoteUsedMinutes: number;
  emailRemainingMinutes: number; remoteRemainingMinutes: number;
}
export interface LicenseMovement {
  id: number; kind: string; emailMinutes: number; remoteMinutes: number; reason: string;
  paymentId: number | null; entryId: number | null; createdBy: string; createdAt: string;
}
