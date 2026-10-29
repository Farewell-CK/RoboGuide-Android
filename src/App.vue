<template>
  <div class="flex flex-col h-screen w-screen bg-deaios-dark overflow-hidden font-sans">
    <!-- Top Header Bar -->
    <HeaderBar @trigger-estop="triggerEmergencyStop" />

    <!-- Main Content Area (Tabs) -->
    <main class="flex-1 relative overflow-hidden">
      <!-- Tab 1: Spatial Map Canvas -->
      <div v-show="activeTab === 'map'" class="w-full h-full">
        <SpatialCanvas />
      </div>

      <!-- Tab 2: Heterogeneous Device Mesh -->
      <div v-show="activeTab === 'devices'" class="w-full h-full">
        <DeviceMesh @select-device="openDeviceDetail" />
      </div>

      <!-- Tab 3: Natural Language Mission Commander -->
      <div v-show="activeTab === 'mission'" class="w-full h-full">
        <MissionCommander />
      </div>
    </main>

    <!-- Bottom Mobile Tab Bar -->
    <nav class="bg-deaios-card/95 backdrop-blur-md border-t border-deaios-border px-6 py-2 flex items-center justify-around z-30 select-none pb-safe">
      <button 
        @click="activeTab = 'map'" 
        :class="[
          'flex flex-col items-center space-y-1 transition text-xs font-medium',
          activeTab === 'map' ? 'text-blue-400 font-bold' : 'text-slate-400 hover:text-slate-200'
        ]"
      >
        <span class="text-lg">🗺️</span>
        <span>全景空间</span>
      </button>

      <button 
        @click="activeTab = 'devices'" 
        :class="[
          'flex flex-col items-center space-y-1 transition text-xs font-medium',
          activeTab === 'devices' ? 'text-blue-400 font-bold' : 'text-slate-400 hover:text-slate-200'
        ]"
      >
        <span class="text-lg">🧩</span>
        <span>异构集群</span>
      </button>

      <button 
        @click="activeTab = 'mission'" 
        :class="[
          'flex flex-col items-center space-y-1 transition text-xs font-medium',
          activeTab === 'mission' ? 'text-blue-400 font-bold' : 'text-slate-400 hover:text-slate-200'
        ]"
      >
        <span class="text-lg">🗣️</span>
        <span>任务指挥</span>
      </button>
    </nav>

    <!-- Fullscreen Emergency Stop Modal -->
    <EStopModal />

    <!-- Device Details Modal -->
    <DeviceDetailModal 
      :device-id="selectedDeviceId" 
      @close="selectedDeviceId = null" 
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import HeaderBar from './components/HeaderBar.vue';
import SpatialCanvas from './components/SpatialCanvas.vue';
import DeviceMesh from './components/DeviceMesh.vue';
import MissionCommander from './components/MissionCommander.vue';
import EStopModal from './components/EStopModal.vue';
import DeviceDetailModal from './components/DeviceDetailModal.vue';
import { startTelemetryPolling, triggerEmergencyStop } from './services/telemetryService';

const activeTab = ref<'map' | 'devices' | 'mission'>('map');
const selectedDeviceId = ref<string | null>(null);

function openDeviceDetail(id: string) {
  selectedDeviceId.value = id;
}

onMounted(() => {
  startTelemetryPolling();
  window.addEventListener('hardware-estop', () => {
    triggerEmergencyStop();
  });
});
</script>

<style scoped>
.pb-safe {
  padding-bottom: max(0.5rem, env(safe-area-inset-bottom));
}
</style>
