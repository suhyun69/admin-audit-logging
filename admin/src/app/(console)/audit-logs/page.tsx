import { ProtectedContent } from "@/components/auth/protected-content";
import { AuditExplorer } from "@/components/audit/audit-explorer";
export default function AuditLogsPage(){return <ProtectedContent adminOnly><AuditExplorer/></ProtectedContent>;}
