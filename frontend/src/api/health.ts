import { apiFetch } from './client'

export interface BackendHealth {
  status: string
  service: string
  aiProviderMode: string
  timestamp: string
}

export const fetchBackendHealth = () => apiFetch<BackendHealth>('/api/v1/health')
