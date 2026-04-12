<template>
  <el-dialog
    v-model="visible"
    :title="title"
    :width="width"
    :close-on-click-modal="false"
    :show-close="showClose"
    class="custom-dialog"
    @close="handleClose"
  >
    <template #header>
      <div class="dialog-header">
        <div class="header-icon" v-if="showIcon">
          <el-icon :size="20"><component :is="icon" /></el-icon>
        </div>
        <span class="header-title">{{ title }}</span>
      </div>
    </template>

    <slot />

    <template #footer v-if="showFooter">
      <slot name="footer">
        <div class="dialog-footer">
          <el-button @click="handleClose" :disabled="loading">
            取消
          </el-button>
          <el-button
            type="primary"
            :loading="loading"
            @click="handleConfirm"
          >
            {{ confirmText }}
          </el-button>
        </div>
      </slot>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed } from 'vue'
import { Warning } from '@element-plus/icons-vue'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  title: {
    type: String,
    default: ''
  },
  width: {
    type: String,
    default: '500px'
  },
  showFooter: {
    type: Boolean,
    default: true
  },
  showClose: {
    type: Boolean,
    default: true
  },
  showIcon: {
    type: Boolean,
    default: false
  },
  icon: {
    type: [Object, String],
    default: () => Warning
  },
  loading: {
    type: Boolean,
    default: false
  },
  confirmText: {
    type: String,
    default: '确定'
  }
})

const emit = defineEmits(['update:modelValue', 'close', 'confirm'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

function handleClose() {
  emit('close')
}

function handleConfirm() {
  emit('confirm')
}
</script>

<style lang="scss" scoped>
.dialog-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-icon {
  width: 36px;
  height: 36px;
  border-radius: $radius-md;
  background: rgba($primary-color, 0.15);
  color: $primary-light;
  display: flex;
  align-items: center;
  justify-content: center;
}

.header-title {
  font-size: $font-size-md;
  font-weight: 600;
  color: #fff;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>

<style lang="scss">
// 全局对话框样式覆盖（非 scoped）
.custom-dialog {
  --el-dialog-bg-color: #{$gray-800};
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: $radius-xl !important;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);

  .el-dialog__header {
    padding: 20px 24px;
    border-bottom: 1px solid rgba(255, 255, 255, 0.06);
    margin-right: 0;
  }

  .el-dialog__body {
    padding: 24px;
    color: $gray-300;
  }

  .el-dialog__footer {
    padding: 16px 24px;
    border-top: 1px solid rgba(255, 255, 255, 0.06);
  }

  // 遮罩层优化
  &.el-dialog {
    backdrop-filter: blur(10px);
  }
}

// 遮罩层
.el-overlay {
  backdrop-filter: blur(4px);
}
</style>
