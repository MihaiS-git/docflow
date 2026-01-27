import { apiFetch } from "../apiFetch";

export async function fetchAdminRoles(): Promise<string[]>{
        return await apiFetch<string[]>("/api/admin/users/roles");
}