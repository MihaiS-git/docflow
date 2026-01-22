export class ApiError extends Error {
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.status = status;
  }
}

export class UnauthenticatedError extends ApiError {
  constructor() {
    super("Unauthenticated", 401);
  }
}

export class ForbiddenError extends ApiError {
  readonly errorCode: string;

  constructor(errorCode: string) {
    super("Forbidden", 403);
    this.errorCode = errorCode;
  }
}
