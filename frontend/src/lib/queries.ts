import { useQuery } from '@tanstack/react-query'
import { api } from './api'

// Shared queries: every component asking for the same key shares one cache entry and one poller.

export const useStats = () => useQuery({ queryKey: ['stats'], queryFn: api.stats, refetchInterval: 2000 })

export const useRecentAttempts = () =>
  useQuery({ queryKey: ['attempts', 'recent'], queryFn: api.recentAttempts, refetchInterval: 2000 })

export const useChaos = () => useQuery({ queryKey: ['chaos'], queryFn: api.chaos, refetchInterval: 5000 })
