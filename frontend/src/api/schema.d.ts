/**
 * OpenAPI TypeScript schema stub.
 * Start the backend and run `npm run api:generate` to regenerate types
 * from http://localhost:8080/v3/api-docs.
 */
export interface paths {
  '/api/v1/health': {
    get: {
      responses: {
        200: {
          content: {
            'application/json': {
              status: string
              service: string
              aiProviderMode: string
              timestamp: string
            }
          }
        }
      }
    }
  }
}
