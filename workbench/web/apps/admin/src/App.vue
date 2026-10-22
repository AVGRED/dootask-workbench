<template>
  <el-config-provider :locale="zhCn">
    <div class="wb-galaxy" aria-hidden="true">
      <span class="band band-a"></span>
      <span class="band band-b"></span>
      <span class="milky"></span>
      <span class="stars"></span>
    </div>
    <router-view />
  </el-config-provider>
</template>

<script setup lang="ts">
import zhCn from "element-plus/es/locale/lang/zh-cn";
</script>
