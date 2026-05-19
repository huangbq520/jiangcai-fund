<template>
  <div id="app">
    <router-view />
    <ToastContainer />
  </div>
</template>

<script setup>
import ToastContainer from './components/ToastContainer.vue'
</script>

<style scoped>
#app {
  min-height: 100vh;
  background: #f5f6f8;
}
</style>
