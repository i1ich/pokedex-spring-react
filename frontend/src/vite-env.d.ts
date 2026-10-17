/// <reference types="vite/client" />

interface ImportMetaEnv {
  /**
   * Base URL of the backend. Leave it undefined for the local default (http://localhost:8080);
   * set it to an empty string for same-origin calls (Docker/nginx proxy).
   */
  readonly VITE_API_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
