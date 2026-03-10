"use client";

import { useEffect, useState } from "react";
import { apiFetch } from "@/lib/apiFetch";
import { ApiError } from "@/lib/apiErrors";
import { useAuth } from "@/lib/auth/useAuth";
import { fetchManagedTenants } from "@/lib/admin/adminTenants";
import type { AdminTenant } from "@/types/admin/Tenant";

import PageContainer from "@/components/layout/PageContainer";
import PageHeader from "@/components/layout/PageHeader";
import InviteCreateCard from "@/components/invites/InviteCreateCard";
import InvitesTableCard from "@/components/invites/InvitesTableCard";

import {
  InvitePage,
  InviteRow,
  InviteStatusFilter,
  TenantRole,
} from "@/types/invites/types";

const INVITE_PAGE_SIZE = 10;

export default function InvitesPage() {
  const { status, identity } = useAuth();
  const isAdmin = status === "AUTH" && identity?.roles.includes("ADMIN");

  const [tenants, setTenants] = useState<AdminTenant[]>([]);
  const [selectedTenantId, setSelectedTenantId] = useState("");

  const [email, setEmail] = useState("");
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [jobTitle, setJobTitle] = useState("");
  const [department, setDepartment] = useState("");
  const [tenantRole, setTenantRole] = useState<TenantRole>("MEMBER");

  const [filterEmail, setFilterEmail] = useState("");
  const [filterStatus, setFilterStatus] = useState<InviteStatusFilter>("");
  const [appliedEmail, setAppliedEmail] = useState("");
  const [appliedStatus, setAppliedStatus] = useState<InviteStatusFilter>("");

  const [invites, setInvites] = useState<InviteRow[]>([]);
  const [invitePage, setInvitePage] = useState(0);
  const [inviteTotalPages, setInviteTotalPages] = useState(0);
  const [inviteTotalElements, setInviteTotalElements] = useState(0);

  const [loading, setLoading] = useState(false);
  const [invitesLoading, setInvitesLoading] = useState(false);
  const [cleanupLoading, setCleanupLoading] = useState(false);
  const [revokeLoadingId, setRevokeLoadingId] = useState<string | null>(null);

  const [success, setSuccess] = useState<string | null>(null);
  const [tableSuccess, setTableSuccess] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [invitesError, setInvitesError] = useState<string | null>(null);

  useEffect(() => {
    if (!isAdmin) return;
    void loadTenants();
  }, [isAdmin]);

  useEffect(() => {
    if (!selectedTenantId) {
      setInvites([]);
      setInvitePage(0);
      setInviteTotalPages(0);
      setInviteTotalElements(0);
      return;
    }

    void loadInvites({
      tenantId: selectedTenantId,
      page: invitePage,
      email: appliedEmail,
      status: appliedStatus,
    });
  }, [selectedTenantId, invitePage, appliedEmail, appliedStatus]);

  async function loadTenants() {
    try {
      const tenants = await fetchManagedTenants();
      setTenants(tenants);
    } catch {
      setError("Failed to load tenants.");
    }
  }

  async function loadInvites(params: {
    tenantId: string;
    page: number;
    email: string;
    status: InviteStatusFilter;
  }) {
    setInvitesLoading(true);
    setInvitesError(null);

    try {
      const query = new URLSearchParams({
        page: String(params.page),
        size: String(INVITE_PAGE_SIZE),
      });

      if (params.email.trim()) {
        query.set("email", params.email.trim());
      }

      if (params.status) {
        query.set("status", params.status);
      }

      const res = await apiFetch<InvitePage>(
        `/api/tenants/${params.tenantId}/invites?${query.toString()}`,
      );

      setInvites(res.content);
      setInviteTotalPages(res.totalPages);
      setInviteTotalElements(res.totalElements);
    } catch (err) {
      if (err instanceof ApiError) {
        setInvitesError(err.message);
      } else if (err instanceof Error) {
        setInvitesError(err.message);
      } else {
        setInvitesError("Failed to load invites.");
      }
    } finally {
      setInvitesLoading(false);
    }
  }

  async function onSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();

    if (!selectedTenantId) {
      setError("Select a tenant first.");
      return;
    }

    setLoading(true);
    setError(null);
    setSuccess(null);

    try {
      await apiFetch<void>(`/api/tenants/${selectedTenantId}/invites`, {
        method: "POST",
        body: JSON.stringify({
          email,
          firstName,
          lastName,
          jobTitle: jobTitle || null,
          department: department || null,
          tenantRole,
        }),
      });

      setSuccess("Invite sent successfully.");
      setInvitePage(0);

      setEmail("");
      setFirstName("");
      setLastName("");
      setJobTitle("");
      setDepartment("");
      setTenantRole("MEMBER");

      await loadInvites({
        tenantId: selectedTenantId,
        page: 0,
        email: appliedEmail,
        status: appliedStatus,
      });
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else if (err instanceof Error) {
        setError(err.message);
      } else {
        setError("Unexpected error occurred.");
      }
    } finally {
      setLoading(false);
    }
  }

  function onFilterSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();

    if (!selectedTenantId) {
      setInvitesError("Select a tenant first.");
      return;
    }

    setInvitePage(0);
    setAppliedEmail(filterEmail);
    setAppliedStatus(filterStatus);
    setTableSuccess(null);
  }

  function onResetFilters() {
    setFilterEmail("");
    setFilterStatus("");
    setAppliedEmail("");
    setAppliedStatus("");
    setInvitePage(0);
    setTableSuccess(null);
  }

  function onSelectedTenantIdChange(value: string) {
    setSelectedTenantId(value);
    setFilterEmail("");
    setFilterStatus("");
    setAppliedEmail("");
    setAppliedStatus("");
    setInvitePage(0);
    setInvites([]);
    setInviteTotalPages(0);
    setInviteTotalElements(0);
    setInvitesError(null);
    setTableSuccess(null);
    setError(null);
    setSuccess(null);
  }

  async function onCleanupInvites() {
    if (!selectedTenantId) return;

    setCleanupLoading(true);
    setInvitesError(null);
    setTableSuccess(null);

    try {
      const result = await apiFetch<{
        deletedInvites: number;
        deletedUsers: number;
      }>(`/api/tenants/${selectedTenantId}/invites/cleanup`, {
        method: "POST",
      });

      setTableSuccess(
        `Cleanup completed. Deleted ${result.deletedInvites} expired invites and ${result.deletedUsers} orphaned users.`,
      );

      await loadInvites({
        tenantId: selectedTenantId,
        page: invitePage,
        email: appliedEmail,
        status: appliedStatus,
      });
    } catch (err) {
      if (err instanceof ApiError) {
        setInvitesError(err.message);
      } else if (err instanceof Error) {
        setInvitesError(err.message);
      } else {
        setInvitesError("Failed to clean up expired invites.");
      }
    } finally {
      setCleanupLoading(false);
    }
  }

  async function onRevokeInvite(inviteId: string) {
    if (!selectedTenantId) return;

    setRevokeLoadingId(inviteId);
    setInvitesError(null);
    setTableSuccess(null);

    try {
      await apiFetch<void>(
        `/api/tenants/${selectedTenantId}/invites/${inviteId}/revoke`,
        {
          method: "POST",
        },
      );

      setTableSuccess("Invite revoked successfully.");

      await loadInvites({
        tenantId: selectedTenantId,
        page: invitePage,
        email: appliedEmail,
        status: appliedStatus,
      });
    } catch (err) {
      if (err instanceof ApiError) {
        setInvitesError(err.message);
      } else if (err instanceof Error) {
        setInvitesError(err.message);
      } else {
        setInvitesError("Failed to revoke invite.");
      }
    } finally {
      setRevokeLoadingId(null);
    }
  }

  if (!isAdmin) {
    return (
      <PageContainer>
        <p className="text-(--color-text-secondary)">Access denied</p>
      </PageContainer>
    );
  }

  return (
    <PageContainer>
      <PageHeader
        title="Tenant Invites"
        description="Create, search, revoke, and clean up tenant invites."
      />

      <div className="grid gap-6 lg:grid-cols-[minmax(0,24rem)_minmax(0,1fr)]">
        <InviteCreateCard
          tenants={tenants}
          selectedTenantId={selectedTenantId}
          onSelectedTenantIdChange={onSelectedTenantIdChange}
          email={email}
          onEmailChange={setEmail}
          firstName={firstName}
          onFirstNameChange={setFirstName}
          lastName={lastName}
          onLastNameChange={setLastName}
          jobTitle={jobTitle}
          onJobTitleChange={setJobTitle}
          department={department}
          onDepartmentChange={setDepartment}
          tenantRole={tenantRole}
          onTenantRoleChange={setTenantRole}
          loading={loading}
          success={success}
          error={error}
          onSubmit={onSubmit}
        />

        <InvitesTableCard
          tenants={tenants}
          selectedTenantId={selectedTenantId}
          onSelectedTenantIdChange={onSelectedTenantIdChange}
          filterEmail={filterEmail}
          onFilterEmailChange={setFilterEmail}
          filterStatus={filterStatus}
          onFilterStatusChange={setFilterStatus}
          onFilterSubmit={onFilterSubmit}
          onResetFilters={onResetFilters}
          onCleanupInvites={() => void onCleanupInvites()}
          invites={invites}
          invitesLoading={invitesLoading}
          cleanupLoading={cleanupLoading}
          revokeLoadingId={revokeLoadingId}
          tableSuccess={tableSuccess}
          invitesError={invitesError}
          invitePage={invitePage}
          inviteTotalPages={inviteTotalPages}
          inviteTotalElements={inviteTotalElements}
          onPreviousPage={() =>
            setInvitePage((current) => Math.max(current - 1, 0))
          }
          onNextPage={() =>
            setInvitePage((current) =>
              inviteTotalPages === 0 || current >= inviteTotalPages - 1
                ? current
                : current + 1,
            )
          }
          onRevokeInvite={(inviteId) => void onRevokeInvite(inviteId)}
        />
      </div>
    </PageContainer>
  );
}
