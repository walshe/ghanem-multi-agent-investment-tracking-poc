import { CheckoutsApi, Configuration } from '../api';

export const DEFAULT_API_BASE_URL = 'http://localhost:8080';

/** Backend URL from VITE_API_BASE_URL, defaulting to the local backend. */
export const API_BASE_URL: string =
  import.meta.env.VITE_API_BASE_URL || DEFAULT_API_BASE_URL;

export function createCheckoutsApi(basePath: string = API_BASE_URL): CheckoutsApi {
  return new CheckoutsApi(new Configuration({ basePath }));
}
