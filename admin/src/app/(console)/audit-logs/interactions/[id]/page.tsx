import {notFound} from "next/navigation";
import {ProtectedContent} from "@/components/auth/protected-content";
import {InteractionTimeline} from "@/components/audit/interaction-timeline";
export default async function InteractionTimelinePage({params}:{params:Promise<{id:string}>}) {
  const {id}=await params;if(!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id))notFound();
  return <ProtectedContent adminOnly><InteractionTimeline key={id} id={id}/></ProtectedContent>;
}
