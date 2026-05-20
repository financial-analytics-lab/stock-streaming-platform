import axios from 'axios'
import type { ReasoningRequest, ReasoningResponse } from '../types'

const reasoningClient = axios.create({
  baseURL: 'https://sentiment-analysis-service-production.up.railway.app',
})

export async function postReasoning(req: ReasoningRequest): Promise<ReasoningResponse> {
  const { data } = await reasoningClient.post<ReasoningResponse>('/analyze', req)
  return data
}
