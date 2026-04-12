<template>
  <div class="stat-card-wrapper" :style="{ animationDelay: `${delay}s` }">
    <div class="stat-card">
      <div class="stat-content">
        <div class="stat-info">
          <div class="stat-label">{{ title }}</div>
          <div class="stat-value">
            <span class="value-number">{{ displayValue }}</span>
            <span class="value-unit" v-if="unit">{{ unit }}</span>
          </div>
          <div class="stat-desc" v-if="desc || $slots.desc">
            <slot name="desc">{{ desc }}</slot>
          </div>
        </div>
        <div class="stat-icon-wrapper">
          <div class="stat-icon" :style="{ borderColor: `${iconColor}20` }">
            <el-icon :size="28" :style="{ color: iconColor }">
              <component :is="icon" />
            </el-icon>
          </div>
          <div class="stat-glow" :style="{ background: iconColor }"></div>
        </div>
      </div>
      <div class="stat-decoration" :style="{ background: iconColor }"></div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  // 标题
  title: {
    type: String,
    required: true
  },
  // 数值
  value: {
    type: [Number, String],
    required: true
  },
  // 单位
  unit: {
    type: String,
    default: ''
  },
  // 描述
  desc: {
    type: String,
    default: ''
  },
  // 图标组件
  icon: {
    type: [Object, String],
    required: true
  },
  // 图标颜色
  iconColor: {
    type: String,
    default: '#6366f1'
  },
  // 动画延迟（秒）
  delay: {
    type: Number,
    default: 0
  }
})

// 格式化显示数值
const displayValue = computed(() => {
  const val = Number(props.value)
  if (isNaN(val)) return props.value
  if (val >= 10000000) {
    return (val / 10000000).toFixed(1) + 'kw'
  }
  if (val >= 10000) {
    return (val / 10000).toFixed(1) + 'w'
  }
  if (val >= 1000) {
    return (val / 1000).toFixed(1) + 'k'
  }
  return val.toLocaleString()
})
</script>

<style lang="scss" scoped>
.stat-card-wrapper {
  animation: statFadeIn 0.6s ease-out both;
}

@keyframes statFadeIn {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.stat-card {
  position: relative;
  padding: 24px;
  background: rgba($gray-800, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: $radius-xl;
  overflow: hidden;
  transition: all $transition-base;

  &:hover {
    transform: translateY(-4px);
    border-color: rgba(255, 255, 255, 0.12);
    box-shadow: 0 12px 40px rgba(0, 0, 0, 0.25);
  }
}

.stat-content {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  position: relative;
  z-index: 2;
}

.stat-info {
  flex: 1;
  position: relative;
}

.stat-label {
  font-size: $font-size-sm;
  color: $gray-300;
  margin-bottom: 12px;
  font-weight: 500;
}

.stat-value {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin-bottom: 8px;
}

.value-number {
  font-size: $font-size-3xl;
  font-weight: 700;
  color: #ffffff;
  font-variant-numeric: tabular-nums;
  line-height: 1;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
}

.value-unit {
  font-size: $font-size-sm;
  color: $gray-400;
}

.stat-desc {
  font-size: $font-size-xs;
  color: $gray-400;
  display: flex;
  align-items: center;
  gap: 4px;
}

.stat-icon-wrapper {
  position: relative;
}

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: $radius-lg;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all $transition-base;
  background: rgba(255, 255, 255, 0.08);
}

.stat-glow {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 80px;
  height: 80px;
  border-radius: 50%;
  opacity: 0;
  transform: translate(-50%, -50%);
  filter: blur(30px);
  transition: all $transition-slow;
  pointer-events: none;
}

.stat-card:hover .stat-glow {
  opacity: 0.12;
}

.stat-decoration {
  position: absolute;
  bottom: -20px;
  right: -20px;
  width: 120px;
  height: 120px;
  border-radius: 50%;
  opacity: 0.06;
  pointer-events: none;
  z-index: 1;
}
</style>
