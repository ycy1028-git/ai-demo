import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/store'

// 路由懒加载
const Dashboard = () => import('@/views/dashboard/index.vue')
const Login = () => import('@/views/login.vue')
const Layout = () => import('@/views/Layout.vue')

// 知识库相关
const KnowledgeBaseList = () => import('@/views/knowledge/base/index.vue')
const KnowledgeBaseForm = () => import('@/views/knowledge/base/form.vue')
const KnowledgeItemList = () => import('@/views/knowledge/item/index.vue')
const KnowledgeItemForm = () => import('@/views/knowledge/item/form.vue')

// 智能应用
const CustomerAssistant = () => import('@/views/app/customer.vue')
const SearchAssistant = () => import('@/views/app/search.vue')

// 流程管理
const FlowTemplateList = () => import('@/views/flow/template/index.vue')
const FlowTemplateEditor = () => import('@/views/flow/editor/index.vue')

// 系统管理
const UserManagement = () => import('@/views/system/user/index.vue')
const CredentialManagement = () => import('@/views/system/credential/index.vue')
// AI大模型配置
const ModelConfigList = () => import('@/views/system/model/index.vue')
const ModelConfigForm = () => import('@/views/system/model/form.vue')

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: Login,
    meta: { title: '登录', hidden: true }
  },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: Dashboard,
        meta: { title: '工作台', icon: 'Odometer' }
      }
    ]
  },
  {
    path: '/knowledge',
    component: Layout,
    redirect: '/knowledge/base',
    meta: { title: '智能知识库', icon: 'Document' },
    children: [
      {
        path: 'base',
        name: 'KnowledgeBaseList',
        component: KnowledgeBaseList,
        meta: { title: '知识库列表' }
      },
      {
        path: 'base/create',
        name: 'KnowledgeBaseCreate',
        component: KnowledgeBaseForm,
        meta: { title: '创建知识库' }
      },
      {
        path: 'base/edit/:id',
        name: 'KnowledgeBaseEdit',
        component: KnowledgeBaseForm,
        meta: { title: '编辑知识库' }
      },
      {
        path: 'item',
        name: 'KnowledgeItemList',
        component: KnowledgeItemList,
        meta: { title: '知识列表' }
      },
      {
        path: 'item/create',
        name: 'KnowledgeItemCreate',
        component: KnowledgeItemForm,
        meta: { title: '创建知识' }
      },
      {
        path: 'item/edit/:id',
        name: 'KnowledgeItemEdit',
        component: KnowledgeItemForm,
        meta: { title: '编辑知识' }
      }
    ]
  },
  {
    path: '/app',
    component: Layout,
    redirect: '/app/customer',
    meta: { title: '智能应用', icon: 'Grid' },
    children: [
      {
        path: 'customer',
        name: 'CustomerAssistant',
        component: CustomerAssistant,
        meta: { title: '智能助手' }
      },
      {
        path: 'search',
        name: 'SearchAssistant',
        component: SearchAssistant,
        meta: { title: '智能搜索' }
      }
    ]
  },
  {
    path: '/flow',
    component: Layout,
    redirect: '/flow/template',
    meta: { title: '流程管理', icon: 'Operation' },
    children: [
      {
        path: 'template',
        name: 'FlowTemplateList',
        component: FlowTemplateList,
        meta: { title: '流程模板' }
      },
      {
        path: 'editor/:id',
        name: 'FlowTemplateEditor',
        component: FlowTemplateEditor,
        meta: { title: '流程编排' }
      }
    ]
  },
  {
    path: '/system',
    component: Layout,
    redirect: '/system/user',
    meta: { title: '系统管理', icon: 'Setting' },
    children: [
      {
        path: 'user',
        name: 'UserManagement',
        component: UserManagement,
        meta: { title: '用户管理' }
      },
      {
        path: 'credential',
        name: 'CredentialManagement',
        component: CredentialManagement,
        meta: { title: 'API凭证管理' }
      },
      {
        path: 'model',
        name: 'ModelConfigList',
        component: ModelConfigList,
        meta: { title: '大模型配置' }
      },
      {
        path: 'model/create',
        name: 'ModelConfigCreate',
        component: ModelConfigForm,
        meta: { title: '创建模型配置' }
      },
      {
        path: 'model/edit/:id',
        name: 'ModelConfigEdit',
        component: ModelConfigForm,
        meta: { title: '编辑模型配置' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach((to, from, next) => {
  const userStore = useUserStore()
  const token = userStore.token

  if (to.path === '/login') {
    next()
  } else if (!token) {
    next('/login')
  } else {
    next()
  }
})

export default router
