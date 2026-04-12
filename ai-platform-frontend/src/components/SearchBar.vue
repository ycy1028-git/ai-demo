<template>
  <div class="search-bar">
    <div class="search-container">
      <div class="search-icon">
        <el-icon :size="18"><Search /></el-icon>
      </div>

      <el-form :inline="true" :model="form" class="search-form">
        <el-form-item
          v-for="item in searchFields"
          :key="item.prop"
          class="search-field"
        >
          <label class="field-label" v-if="item.label">{{ item.label }}</label>

          <!-- 输入框 -->
          <el-input
            v-if="item.type === 'input' || !item.type"
            v-model="form[item.prop]"
            :placeholder="item.placeholder || `请输入${item.label}`"
            clearable
            class="search-input"
          />

          <!-- 下拉选择 -->
          <el-select
            v-else-if="item.type === 'select'"
            v-model="form[item.prop]"
            :placeholder="item.placeholder || `请选择${item.label}`"
            clearable
            class="search-select"
          >
            <el-option
              v-for="option in item.options"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>

          <!-- 日期选择 -->
          <el-date-picker
            v-else-if="item.type === 'date'"
            v-model="form[item.prop]"
            type="date"
            :placeholder="item.placeholder || `请选择${item.label}`"
            value-format="YYYY-MM-DD"
            class="search-date"
          />

          <!-- 日期范围 -->
          <el-date-picker
            v-else-if="item.type === 'daterange'"
            v-model="form[item.prop]"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            class="search-date-range"
          />
        </el-form-item>
      </el-form>

      <div class="search-actions">
        <el-button type="primary" class="search-btn" @click="handleSearch">
          <el-icon :size="16"><Search /></el-icon>
          <span>搜索</span>
        </el-button>
        <el-button class="reset-btn" @click="handleReset">
          <el-icon :size="16"><Refresh /></el-icon>
          <span>重置</span>
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'

const props = defineProps({
  // 搜索字段配置
  fields: {
    type: Array,
    required: true
  }
})

const emit = defineEmits(['search', 'reset'])

// 初始化表单数据
const form = reactive({})
props.fields.forEach(field => {
  form[field.prop] = field.defaultValue || null
})

// 搜索处理
function handleSearch() {
  emit('search', { ...form })
}

// 重置处理
function handleReset() {
  props.fields.forEach(field => {
    form[field.prop] = field.defaultValue || null
  })
  emit('reset')
}

// 获取表单数据
function getFormData() {
  return { ...form }
}

defineExpose({ getFormData })
</script>

<style lang="scss" scoped>
.search-bar {
  margin-bottom: 20px;
}

.search-container {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px 24px;
  background: $gray-50;
  border: 1px solid $gray-200;
  border-radius: $radius-xl;
  box-shadow: $shadow-sm;
}

.search-icon {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba($primary-color, 0.15);
  border-radius: $radius-md;
  color: $primary-light;
  flex-shrink: 0;
}

.search-form {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 0;
}

.search-field {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  padding: 0;

  :deep(.el-form-item__content) {
    display: flex;
    align-items: center;
    gap: 8px;
  }
}

.field-label {
  font-size: $font-size-sm;
  color: $gray-400;
  white-space: nowrap;
  font-weight: 500;
}

.search-input,
.search-select,
.search-date,
.search-date-range {
  :deep(.el-input__wrapper) {
    background: $gray-50 !important;
    border: 1px solid $gray-300 !important;
    border-radius: $radius-md !important;
    box-shadow: none !important;
    height: 38px;

    &:hover {
      border-color: $gray-400 !important;
    }

    &.is-focus {
      border-color: $primary-color !important;
      box-shadow: 0 0 0 3px rgba($primary-color, 0.15) !important;
    }
  }

  :deep(.el-input__inner) {
    color: $gray-200;

    &::placeholder {
      color: $gray-500;
    }
  }

  :deep(.el-input__prefix) {
    color: $gray-500;
  }
}

.search-actions {
  display: flex;
  gap: 10px;
  flex-shrink: 0;
}

.search-btn {
  background: linear-gradient(135deg, $primary-color, $primary-dark) !important;
  border: none !important;
  border-radius: $radius-md !important;
  height: 38px;
  padding: 0 18px;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 6px;
  transition: all $transition-base;

  &:hover {
    transform: translateY(-1px);
    box-shadow: 0 4px 12px $primary-glow;
  }
}

.reset-btn {
  background: rgba($gray-700, 0.5) !important;
  border: 1px solid rgba(255, 255, 255, 0.08) !important;
  border-radius: $radius-md !important;
  height: 38px;
  padding: 0 18px;
  color: $gray-300 !important;
  display: flex;
  align-items: center;
  gap: 6px;
  transition: all $transition-base;

  &:hover {
    background: rgba($gray-700, 0.7) !important;
    border-color: rgba(255, 255, 255, 0.12) !important;
    color: $gray-200 !important;
  }
}
</style>
