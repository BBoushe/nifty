export interface BackendHealth {
  status: string
  service: string
  aiProviderMode: string
  timestamp: string
}

export async function fetchBackendHealth(): Promise<BackendHealth> {
  const response = await fetch('/api/v1/health')
  if (!response.ok) {
    throw new Error(`Health check failed with status ${response.status}`)
  }
  return (await response.json()) as BackendHealth
}
