import axios from 'axios'
import { getToken, removeToken, removeUser } from './auth'

// 防止重复跳转
let isRedirecting = false

const api = axios.create({
  baseURL: '/api',
  timeout: 30000
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

    // 处理 401 未授权
    if (error.response && error.response.status === 401) {
      if (!isRedirecting) {
        isRedirecting = true

        // 清除本地认证信息
        removeToken()
        removeUser()

        // 跳转到登录页
        window.location.href = '#/auth'

        // 延迟重置标志，避免短时间内重复跳转
        setTimeout(() => {
          isRedirecting = false
        }, 1000)
      }
    }

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

  add: (fundCode, fundName, groupId) => api.post('/fund/add', { fundCode, fundName, groupId }),

  delete: (fundCode, groupId) => {
    const payload = { fundCode }
    if (groupId != null) payload.groupId = groupId
    return api.post('/fund/delete', payload)
  },

  getHoldingList: () => api.get('/fund/holding/list'),

  getWatchlistList: () => api.get('/fund/watchlist/list'),

  getPortfolioSummary: () => api.get('/fund/portfolio/summary'),

  updateHolding: (fundCode, payload) => api.post('/fund/holding/update', { fundCode, ...payload }),

  adjustHolding: (fundCode, type, adjustShare, adjustCost, extra = {}) => api.post('/fund/holding/adjust', {
    fundCode, type, adjustShare, adjustCost, ...extra
  }),

  clearHolding: (fundCode) => api.post('/fund/holding/clear', { fundCode }),

  getNavAt: (code, date) => api.get(`/fund/nav-at?code=${code}&date=${date}`),

  getFundDailyProfit: (fundCode, period = '6month') =>
    api.get(`/fund/daily-profit/${fundCode}?period=${period}`),

  getOverallDailyProfit: (period = '6month') =>
    api.get(`/fund/daily-profit/overall?period=${period}`),

  deleteBatch: (fundCodes, groupId) => {
    const payload = { fundCodes }
    if (groupId != null) payload.groupId = groupId
    return api.post('/fund/delete/batch', payload)
  },

  // 分组管理
  getGroups: () => api.get('/fund/groups'),
  createGroup: (name) => api.post('/fund/group', { name }),
  renameGroup: (id, name) => api.put(`/fund/group/${id}`, { name }),
  deleteGroup: (id) => api.delete(`/fund/group/${id}`),
  reorderGroups: (groupIds) => api.put('/fund/groups/reorder', { groupIds }),
  getGroupFunds: (groupId) => api.get(`/fund/group/${groupId}/funds`)
}

export const marketApi = {
  getIndices: () => api.get('/market/indices'),

  getKline: (code, startDate, endDate, klt = '101') =>
    api.get(`/market/kline?code=${code}&startDate=${startDate}&endDate=${endDate}&klt=${klt}`),

  getRealtime: (code) => api.get(`/market/realtime?code=${code}`)
}

export const ocrApi = {
  recognize: (file) => {
    const formData = new FormData()
    formData.append('file', file)
    return api.post('/ocr/recognize', formData)
  }
}

export default api
