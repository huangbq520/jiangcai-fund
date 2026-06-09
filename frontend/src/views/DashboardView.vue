<template>
  <header class="app-header">
    <div class="logo-wrapper">
      <div class="loader">
        <svg width="100" height="100" viewBox="0 0 100 100">
          <defs>
            <mask id="clipping">
              <polygon points="0,0 100,0 100,100 0,100" fill="black"></polygon>
              <polygon points="25,25 75,25 50,75" fill="white"></polygon>
              <polygon points="50,25 75,75 25,75" fill="white"></polygon>
              <polygon points="35,35 65,35 50,65" fill="white"></polygon>
              <polygon points="35,35 65,35 50,65" fill="white"></polygon>
              <polygon points="35,35 65,35 50,65" fill="white"></polygon>
              <polygon points="35,35 65,35 50,65" fill="white"></polygon>
            </mask>
          </defs>
        </svg>
        <div class="box"></div>
      </div>
      <h1>酱菜养基</h1>
    </div>
    <div class="header-search">
      <SearchFund @add-fund="handleAddFund" :compact="true" />
    </div>
    <div class="header-actions">
      <a
        href="https://github.com/huangbq520/fund-management-system"
        target="_blank"
        rel="noopener noreferrer"
        class="github-btn"
        title="GitHub"
      >
        <svg viewBox="0 0 24 24" class="github-icon">
          <path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z"/>
        </svg>
      </a>
      <UserMenu />
    </div>
  </header>

  <main class="app-main">
    <section class="market-section">
      <MarketIndex @select="openQuote" />
    </section>

    <section class="summary-section">
      <PortfolioSummary @view-detail="handleViewDetail" />
    </section>

    <section class="list-section">
      <HoldingList
        @update="handleHoldingUpdate"
        @view-detail="handleViewDetail"
      />
    </section>

    <FundDetailModal
      v-if="showDetail"
      :fund-code="currentFundCode"
      @close="closeDetail"
    />

    <IndexQuoteModal
      v-if="quoteIndex"
      :visible="!!quoteIndex"
      :index-code="quoteIndex.code"
      :index-name="quoteIndex.name"
      @close="closeQuote"
    />
  </main>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import MarketIndex from '../components/MarketIndex.vue'
import SearchFund from '../components/SearchFund.vue'
import PortfolioSummary from '../components/PortfolioSummary.vue'
import HoldingList from '../components/HoldingList.vue'
import FundDetailModal from '../components/FundDetailModal.vue'
import IndexQuoteModal from '../components/IndexQuoteModal.vue'
import UserMenu from '../components/UserMenu.vue'
import { useFundStore } from '../stores/fundStore'

const router = useRouter()
const route = useRoute()
const fundStore = useFundStore()

const showDetail = computed(() => !!route.params.fundCode)
const currentFundCode = computed(() => route.params.fundCode || '')
const quoteIndex = ref(null)

const openQuote = (index) => {
  quoteIndex.value = index
}

const closeQuote = () => {
  quoteIndex.value = null
}

const handleAddFund = async (fundCode, fundName) => {
  await fundStore.addFund(fundCode, fundName)
}

const handleHoldingUpdate = () => {
  fundStore.fetchHoldings()
  fundStore.fetchSummary()
}

const handleViewDetail = (fundCode) => {
  router.push('/detail/' + fundCode)
}

const closeDetail = () => {
  router.push('/')
}
</script>

<style scoped>
.app-header {
  background: rgba(232, 232, 232, 0.92);
  backdrop-filter: blur(10px);
  padding: 12px 24px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
  display: flex;
  justify-content: space-between;
  align-items: center;
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 100;
}

.logo-wrapper {
  display: flex;
  align-items: center;
  gap: 20px;
  flex-shrink: 0;
}

.header-search {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.github-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: 2px solid rgba(0, 0, 0, 0.1);
  background: rgba(255, 255, 255, 0.6);
  color: #666;
  transition: all 0.25s ease;
  text-decoration: none;
  cursor: pointer;
}

.github-btn:hover {
  border-color: #24292e;
  background: rgba(36, 41, 46, 0.08);
  color: #24292e;
  transform: translateY(-2px);
}

.github-icon {
  width: 18px;
  height: 18px;
  fill: currentColor;
}

.logo-wrapper h1 {
  margin: 0;
  color: #333;
  font-size: 20px;
  font-weight: 700;
  background: #1677ff;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.loader {
  --color-one: #ffbf48;
  --color-two: #be4a1d;
  --color-three: #ffbf4780;
  --color-four: #bf4a1d80;
  --color-five: #ffbf4740;
  --time-animation: 2s;
  --size: 0.5;
  width: calc(100px * var(--size));
  height: calc(100px * var(--size));
  position: relative;
  border-radius: 50%;
  transform: scale(var(--size));
  transform-origin: top left;
  box-shadow:
    0 0 25px 0 var(--color-three),
    0 20px 50px 0 var(--color-four);
  animation: colorize calc(var(--time-animation) * 3) ease-in-out infinite;
}

.loader::before {
  content: "";
  position: absolute;
  top: 0;
  left: 0;
  width: 100px;
  height: 100px;
  border-radius: 50%;
  border-top: solid 1px var(--color-one);
  border-bottom: solid 1px var(--color-two);
  background: linear-gradient(180deg, var(--color-five), var(--color-four));
  box-shadow:
    inset 0 10px 10px 0 var(--color-three),
    inset 0 -10px 10px 0 var(--color-four);
}

.loader .box {
  width: 100px;
  height: 100px;
  background: linear-gradient(
    180deg,
    var(--color-one) 30%,
    var(--color-two) 70%
  );
  mask: url(#clipping);
  -webkit-mask: url(#clipping);
}

.loader svg {
  position: absolute;
}

.loader :deep(svg #clipping) {
  filter: contrast(15);
  animation: roundness calc(var(--time-animation) / 2) linear infinite;
}

.loader :deep(svg #clipping polygon) {
  filter: blur(7px);
}

.loader :deep(svg #clipping polygon:nth-child(1)) {
  transform-origin: 75% 25%;
  transform: rotate(90deg);
}

.loader :deep(svg #clipping polygon:nth-child(2)) {
  transform-origin: 50% 50%;
  animation: rotation var(--time-animation) linear infinite reverse;
}

.loader :deep(svg #clipping polygon:nth-child(3)) {
  transform-origin: 50% 60%;
  animation: rotation var(--time-animation) linear infinite;
  animation-delay: calc(var(--time-animation) / -3);
}

.loader :deep(svg #clipping polygon:nth-child(4)) {
  transform-origin: 40% 40%;
  animation: rotation var(--time-animation) linear infinite reverse;
}

.loader :deep(svg #clipping polygon:nth-child(5)) {
  transform-origin: 40% 40%;
  animation: rotation var(--time-animation) linear infinite reverse;
  animation-delay: calc(var(--time-animation) / -2);
}

.loader :deep(svg #clipping polygon:nth-child(6)) {
  transform-origin: 60% 40%;
  animation: rotation var(--time-animation) linear infinite;
}

.loader :deep(svg #clipping polygon:nth-child(7)) {
  transform-origin: 60% 40%;
  animation: rotation var(--time-animation) linear infinite;
  animation-delay: calc(var(--time-animation) / -1.5);
}

@keyframes rotation {
  0% {
    transform: rotate(0deg);
  }
  100% {
    transform: rotate(360deg);
  }
}

@keyframes roundness {
  0% {
    filter: contrast(15);
  }
  20% {
    filter: contrast(3);
  }
  40% {
    filter: contrast(3);
  }
  60% {
    filter: contrast(15);
  }
  100% {
    filter: contrast(15);
  }
}

@keyframes colorize {
  0% {
    filter: hue-rotate(0deg);
  }
  20% {
    filter: hue-rotate(-30deg);
  }
  40% {
    filter: hue-rotate(-60deg);
  }
  60% {
    filter: hue-rotate(-90deg);
  }
  80% {
    filter: hue-rotate(-45deg);
  }
  100% {
    filter: hue-rotate(0deg);
  }
}

.app-main {
  max-width: 1200px;
  margin: 0 auto;
  padding: 82px 20px 30px;
  position: relative;
}

.market-section {
  margin-bottom: 16px;
}

.summary-section {
  margin-bottom: 20px;
}

.list-section {
  margin-bottom: 20px;
}
</style>
