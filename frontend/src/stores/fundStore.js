import { defineStore } from 'pinia'
import { ref, reactive, computed } from 'vue'
import { fundApi } from '../api'

export const useFundStore = defineStore('fund', () => {
  const holdings = ref([])
  const watchlist = ref([])
  const summary = ref({
    totalAsset: 0,
    todayProfit: 0,
    todayProfitRate: 0,
    totalProfit: 0,
    totalProfitRate: 0,
    fundCount: 0
  })
  const searchResults = ref([])
  const loading = reactive({
    holdings: false,
    summary: false,
    search: false,
    watchlist: false,
    groups: false
  })

  // ===================== 分组管理 =====================
  const groups = ref([])
  const activeGroupId = ref(null)
  // 缓存各分组的基金数据: Map<groupId, fundList>
  const groupFunds = reactive({})

  // 查找HOLDING类型的分组
  const holdingGroup = computed(() => {
    return groups.value.find(g => g.groupType === 'HOLDING') || null
  })

  // 查找默认WATCHLIST类型的分组（名为"自选"的）
  const defaultWatchlistGroup = computed(() => {
    return groups.value.find(g => g.groupType === 'WATCHLIST' && g.name === '自选') || null
  })

  async function fetchGroups() {
    loading.groups = true
    try {
      const response = await fundApi.getGroups()
      if (response.code === 200) {
        groups.value = response.data || []
        // 设置默认激活的分组
        if (!activeGroupId.value && groups.value.length > 0) {
          activeGroupId.value = groups.value[0].id
        }
      }
    } catch (err) {
      console.error('Failed to load groups:', err)
    } finally {
      loading.groups = false
    }
  }

  async function createGroup(name) {
    const response = await fundApi.createGroup(name)
    if (response.code === 200 && response.data) {
      groups.value.push(response.data)
      groups.value.sort((a, b) => a.sortOrder - b.sortOrder)
      groupFunds[response.data.id] = []
      activeGroupId.value = response.data.id
    }
    return response
  }

  async function renameGroup(id, name) {
    const response = await fundApi.renameGroup(id, name)
    if (response.code === 200) {
      const idx = groups.value.findIndex(g => g.id === id)
      if (idx !== -1) {
        groups.value[idx] = { ...groups.value[idx], ...response.data }
      }
    }
    return response
  }

  async function deleteGroup(id) {
    const response = await fundApi.deleteGroup(id)
    if (response.code === 200) {
      groups.value = groups.value.filter(g => g.id !== id)
      delete groupFunds[id]
      if (activeGroupId.value === id && groups.value.length > 0) {
        activeGroupId.value = groups.value[0].id
      }
    }
    return response
  }

  async function reorderGroups(orderedIds) {
    const response = await fundApi.reorderGroups(orderedIds)
    if (response.code === 200) {
      await fetchGroups()
    }
    return response
  }

  async function fetchGroupFunds(groupId) {
    try {
      const response = await fundApi.getGroupFunds(groupId)
      if (response.code === 200) {
        groupFunds[groupId] = response.data || []
      }
    } catch (err) {
      console.error('Failed to load group funds:', err)
    }
  }

  async function silentFetchGroupFunds(groupId) {
    try {
      const response = await fundApi.getGroupFunds(groupId)
      if (response.code === 200) {
        groupFunds[groupId] = response.data || []
      }
    } catch (err) {
      console.error('Failed to load group funds:', err)
    }
  }

  // ===================== 持仓（向后兼容） =====================
  async function fetchHoldings() {
    loading.holdings = true
    try {
      const response = await fundApi.getHoldingList()
      if (response.code === 200) {
        holdings.value = response.data || []
        // 同步到 groupFunds
        const hGroup = holdingGroup.value
        if (hGroup) {
          groupFunds[hGroup.id] = response.data || []
        }
      }
    } catch (err) {
      console.error('Failed to load holdings:', err)
    } finally {
      loading.holdings = false
    }
  }

  async function fetchSummary() {
    loading.summary = true
    try {
      const response = await fundApi.getPortfolioSummary()
      if (response.code === 200 && response.data) {
        summary.value = response.data
      }
    } catch (err) {
      console.error('Failed to load portfolio summary:', err)
    } finally {
      loading.summary = false
    }
  }

  async function fetchWatchlist() {
    loading.watchlist = true
    try {
      const response = await fundApi.getWatchlistList()
      if (response.code === 200) {
        watchlist.value = response.data || []
        // 同步到 groupFunds
        const wGroup = defaultWatchlistGroup.value
        if (wGroup) {
          groupFunds[wGroup.id] = response.data || []
        }
      }
    } catch (err) {
      console.error('Failed to load watchlist:', err)
    } finally {
      loading.watchlist = false
    }
  }

  async function searchFunds(keyword) {
    loading.search = true
    try {
      const response = await fundApi.searchFunds(keyword)
      if (response.code === 200 && response.data) {
        searchResults.value = response.data
      } else {
        searchResults.value = []
      }
      return response
    } catch (err) {
      console.error('Search failed:', err)
      return { code: 500, message: '搜索失败' }
    } finally {
      loading.search = false
    }
  }

  async function silentFetchHoldings() {
    try {
      const response = await fundApi.getHoldingList()
      if (response.code === 200) {
        holdings.value = response.data || []
        const hGroup = holdingGroup.value
        if (hGroup) {
          groupFunds[hGroup.id] = response.data || []
        }
      }
    } catch (err) {
      console.error('Failed to load holdings:', err)
    }
  }

  async function silentFetchSummary() {
    try {
      const response = await fundApi.getPortfolioSummary()
      if (response.code === 200 && response.data) {
        summary.value = response.data
      }
    } catch (err) {
      console.error('Failed to load portfolio summary:', err)
    }
  }

  async function silentFetchWatchlist() {
    try {
      const response = await fundApi.getWatchlistList()
      if (response.code === 200) {
        watchlist.value = response.data || []
        const wGroup = defaultWatchlistGroup.value
        if (wGroup) {
          groupFunds[wGroup.id] = response.data || []
        }
      }
    } catch (err) {
      console.error('Failed to load watchlist:', err)
    }
  }

  function updateHoldingInPlace(fundCode, updated) {
    const idx = holdings.value.findIndex(h => h.fundCode === fundCode)
    if (idx !== -1) {
      holdings.value[idx] = { ...holdings.value[idx], ...updated }
    }
    holdings.value = [...holdings.value]
    recalcSummary()
  }

  function recalcSummary() {
    const items = holdings.value.filter(h => {
      const val = h.currentValue || h.holdAmount
      return val && Number(val) > 0
    })
    if (items.length === 0) {
      summary.value = { totalAsset: 0, todayProfit: 0, todayProfitRate: 0, totalProfit: 0, totalProfitRate: 0, fundCount: holdings.value.length }
      return
    }
    let totalAsset = 0, todayProfit = 0, totalCost = 0, totalValue = 0
    items.forEach(h => {
      const cv = Number(h.currentValue || h.holdAmount || 0)
      const tp = Number(h.todayProfit || 0)
      const share = Number(h.shareForTodayProfit || h.holdShare || 0)
      const cost = Number(h.costPrice || 0)
      totalAsset += cv
      todayProfit += tp
      if (share > 0 && cost > 0) {
        totalCost += share * cost
        totalValue += cv
      }
    })
    const todayRate = totalAsset > 0 ? (todayProfit / (totalAsset - todayProfit)) * 100 : 0
    const totalProfit = totalValue - totalCost
    const totalRate = totalCost > 0 ? (totalProfit / totalCost) * 100 : 0
    summary.value = {
      totalAsset: Math.round(totalAsset * 100) / 100,
      todayProfit: Math.round(todayProfit * 100) / 100,
      todayProfitRate: Math.round(todayRate * 100) / 100,
      totalProfit: Math.round(totalProfit * 100) / 100,
      totalProfitRate: Math.round(totalRate * 100) / 100,
      fundCount: holdings.value.length
    }
  }

  async function addFund(fundCode, fundName, groupId) {
    const response = await fundApi.add(fundCode, fundName, groupId)
    if (response.code === 200) {
      const group = groups.value.find(g => g.id === groupId)
      if (group && group.groupType === 'HOLDING') {
        await fetchHoldings()
        await fetchSummary()
      } else {
        await fetchGroupFunds(groupId)
        // 如果添加到了默认自选分组，也刷新watchlist
        if (group && group.groupType === 'WATCHLIST' && group.name === '自选') {
          await fetchWatchlist()
        }
      }
    }
    return response
  }

  async function deleteBatch(fundCodes, groupId) {
    const response = await fundApi.deleteBatch(fundCodes, groupId)
    if (response.code === 200) {
      const group = groups.value.find(g => g.id === groupId)
      if (group && group.groupType === 'HOLDING') {
        await fetchHoldings()
        await fetchSummary()
      } else {
        await fetchGroupFunds(groupId)
        if (group && group.groupType === 'WATCHLIST' && group.name === '自选') {
          await fetchWatchlist()
        }
      }
    }
    return response
  }

  async function deleteWatchlistFund(fundCode) {
    const wGroup = defaultWatchlistGroup.value
    const groupId = wGroup ? wGroup.id : null
    if (!groupId) {
      const response = await fundApi.delete(fundCode, null)
      if (response.code === 200) {
        await fetchWatchlist()
      }
      return response
    }
    const response = await fundApi.delete(fundCode, groupId)
    if (response.code === 200) {
      await fetchGroupFunds(groupId)
      await fetchWatchlist()
    }
    return response
  }

  return {
    // 旧版状态（向后兼容）
    holdings, watchlist, summary, searchResults, loading,
    // 新版分组状态
    groups, activeGroupId, groupFunds,
    holdingGroup, defaultWatchlistGroup,
    // 旧版方法
    fetchHoldings, fetchSummary, fetchWatchlist, searchFunds,
    silentFetchHoldings, silentFetchSummary, silentFetchWatchlist,
    updateHoldingInPlace, recalcSummary, deleteBatch, deleteWatchlistFund,
    // 新版方法
    fetchGroups, createGroup, renameGroup, deleteGroup, reorderGroups,
    fetchGroupFunds, silentFetchGroupFunds, addFund
  }
})
