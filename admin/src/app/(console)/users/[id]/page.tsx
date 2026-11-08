import { notFound } from "next/navigation";
import { UserDetail } from "@/components/users/user-detail";
export default async function UserDetailPage({params}:{params:Promise<{id:string}>}) {
  const {id}=await params;
  if (!/^[1-9]\d{0,18}$/.test(id) || BigInt(id)>BigInt("9223372036854775807")) notFound();
  return <UserDetail userId={id} />;
}
