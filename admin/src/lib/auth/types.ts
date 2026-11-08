export type Role = "ADMIN" | "OPERATOR" | "VIEWER";
export type Actor = { id: number; username: string; displayName: string; role: Role };

export const roleLabels: Record<Role, string> = { ADMIN: "관리자", OPERATOR: "운영자", VIEWER: "조회 전용" };
export const authQueryKey = (epoch: number) => ["auth", "me", epoch] as const;
export const actorQueryKey = (actor: Actor, ...parts: unknown[]) => ["actor", actor.id, actor.role, ...parts] as const;
