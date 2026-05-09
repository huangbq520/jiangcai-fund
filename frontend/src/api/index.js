import axios from 'axios'
import { getToken } from './auth'

const api = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

api.interceptors.request.use(
  config => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

api.interceptors.response.use(
  response => response.data,
  error => {
    console.error('API Error:', error)
    return Promise.reject(error)
  }
)

export const fundApi = {
  searchFunds: (keyword) => api.get(`/fund/search?keyword=${encodeURIComponent(keyword)}`),

  search: (code) => api.get(`/fund/search?keyword=${encodeURIComponent(code)}`),

  list: () => api.get('/fund/list'),

  detail: (code) => api.get(`/fund/detail?code=${code}`),

  getFundData: (code) => api.get(`/fund/data?code=${code}`),

  getPerformanceData: (code, period) => api.get(`/fund/performance?code=${code}&period=${period}`),

  add: (fundCode, fundName) => api.post('/fund/add', { fundCode, fundName }),

  delete: (fundCode) => api.post('/fund/delete', { fundCode }),

  getHoldingList: () => api.get('/fund/holding/list'),

  getPortfolioSummary: () => api.get('/fund/portfolio/summary'),

  updateHolding: (fundCode, holdShare, costPrice, buyDate) => api.post('/fund/holding/update', {
    fundCode,
    holdShare,
    costPrice,
    buyDate
  })
}

export default api
