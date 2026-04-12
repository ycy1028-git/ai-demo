import { ref, onMounted, onUnmounted } from 'vue'

/**
 * 响应式窗口尺寸组合式函数
 * @returns {Object} 窗口尺寸信息
 */
export function useWindowSize() {
  const width = ref(window.innerWidth)
  const height = ref(window.innerHeight)

  function handleResize() {
    width.value = window.innerWidth
    height.value = window.innerHeight
  }

  onMounted(() => {
    window.addEventListener('resize', handleResize)
  })

  onUnmounted(() => {
    window.removeEventListener('resize', handleResize)
  })

  return { width, height }
}

/**
 * 防抖组合式函数
 * @param {Function} fn - 待防抖的函数
 * @param {number} delay - 延迟时间
 * @returns {Object} { debouncedFn, cancel }
 */
export function useDebounce(fn, delay = 300) {
  let timeout = null

  function debounced(...args) {
    if (timeout) clearTimeout(timeout)
    timeout = setTimeout(() => fn(...args), delay)
  }

  function cancel() {
    if (timeout) {
      clearTimeout(timeout)
      timeout = null
    }
  }

  return { debouncedFn: debounced, cancel }
}

/**
 * 分页组合式函数
 * @param {Object} options - 配置选项
 * @returns {Object} 分页相关状态和方法
 */
export function usePagination(options = {}) {
  const {
    currentPage = 1,
    pageSize = 10,
    total = 0
  } = options

  const pagination = ref({
    current: currentPage,
    pageSize: pageSize,
    total: total
  })

  function handleCurrentChange(val) {
    pagination.value.current = val
  }

  function handleSizeChange(val) {
    pagination.value.pageSize = val
    pagination.value.current = 1
  }

  function setTotal(val) {
    pagination.value.total = val
  }

  function reset() {
    pagination.value.current = 1
    pagination.value.pageSize = 10
    pagination.value.total = 0
  }

  return {
    pagination,
    handleCurrentChange,
    handleSizeChange,
    setTotal,
    reset
  }
}
