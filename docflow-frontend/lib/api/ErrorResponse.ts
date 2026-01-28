export interface ErrorResponse {
  status: number;
  error: string;
  errorCode: string;
  message: string;
  path: string;
}