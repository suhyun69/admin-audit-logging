import {notFound} from "next/navigation";
import {ProtectedContent} from "@/components/auth/protected-content";
import {AuditDetailView} from "@/components/audit/audit-detail-view";
export default async function AuditDetailPage({params}:{params:Promise<{id:string}>}) {
  const {id}=await params;if(!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id))notFound();
  return <ProtectedContent adminOnly><AuditDetailView id={id}/></ProtectedContent>;
}
