import { ErrorResponse } from "./api/ErrorResponse";

export class ApiError extends Error {
  readonly status: number;
  readonly response?: ErrorResponse;

  constructor(message: string, status: number, response?: ErrorResponse) {
    super(message);
    this.status = status;
    this.response = response;
  }
}

export class UnauthenticatedError extends ApiError {
  constructor() {
    super("Unauthenticated", 401);
  }
}

export class ForbiddenError extends ApiError {
  readonly errorCode: string;

  constructor(response: ErrorResponse) {
    super(response.message, response.status, response);
    this.errorCode = response.errorCode;
  }
}
