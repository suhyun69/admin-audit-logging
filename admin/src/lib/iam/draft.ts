import type { EffectivePermission, Mutation, OverrideDraft } from "./types.ts";

export function toggleId(ids: number[], id: number): number[] {
  return ids.includes(id) ? ids.filter((value) => value !== id) : [...ids, id];
}
export function sameIds(a: number[], b: number[]): boolean {
  return a.length === b.length && [...a].sort((x, y) => x - y).every((value, i) => value === [...b].sort((x, y) => x - y)[i]);
}
export function overrideMutation(userId: number, groupId: number, permissionId: number, draft: OverrideDraft): Mutation {
  if (draft.effect && !draft.reason.trim()) throw new Error("직접 허용·거부에는 사유가 필요합니다.");
  const expiresAt = draft.expiresAt ? new Date(draft.expiresAt).toISOString() : null;
  if (draft.effect && expiresAt && new Date(expiresAt).getTime() <= Date.now()) throw new Error("만료 시각은 현재 이후여야 합니다.");
  return { path: `/iam/users/${userId}/overrides/${permissionId}`, method: "PUT", body: { groupId, effect: draft.effect, expiresAt: draft.effect ? expiresAt : null, reason: draft.reason.trim() } };
}
export function previewPermission(permission: EffectivePermission, draft: OverrideDraft, context: { enabled: boolean; globalAdmin?: boolean } = { enabled: true }): string {
  if (!context.enabled) return "DENY (비활성 계정 또는 그룹)";
  const unexpired = !draft.expiresAt || new Date(draft.expiresAt).getTime() > Date.now();
  if (draft.effect === "DENY" && unexpired) return "DENY (예상 예외 정책, 서버 재평가)";
  if (context.globalAdmin) return "ALLOW (명시적 전역 관리자, 서버 재평가)";
  if (draft.effect === "ALLOW" && unexpired) return "ALLOW (예상 예외 정책, 서버 재평가)";
  // Other policy prerequisites (inactive account/group, missing membership) remain authoritative on the server.
  return permission.inherited ? "ALLOW (역할 상속, 서버 재평가)" : "DENY (기본 거부, 서버 재평가)";
}
export function errorMessage(error: unknown): string {
  if (error && typeof error === "object" && "status" in error && error.status === 403) return "접근 권한이 없습니다. 변경사항은 저장되지 않았습니다.";
  return error instanceof Error ? error.message : "요청을 처리할 수 없습니다.";
}
