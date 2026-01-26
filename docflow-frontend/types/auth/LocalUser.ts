export type LocalUser = {
    id: string;
    tenantId: string;
    email: string;
    firstName: string;
    lastName: string;
    displayName: string;
    jobTitle?: string;
    department?: string;
    businessPhone?: string;
};