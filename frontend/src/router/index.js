import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import LoginView from '../views/LoginView.vue'
import HomeView from '../views/HomeView.vue'
import ProductsView from '../views/ProductsView.vue'
import ProcessesView from '../views/ProcessesView.vue'
import OrdersView from '../views/OrdersView.vue'
import PlansView from '../views/PlansView.vue'
import WorkOrdersView from '../views/WorkOrdersView.vue'
import ReportsView from '../views/ReportsView.vue'
import QualityView from '../views/QualityView.vue'
import EquipmentView from '../views/EquipmentView.vue'
import StatisticsView from '../views/StatisticsView.vue'
import SystemView from '../views/SystemView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView, meta: { guestOnly: true } },
    { path: '/', name: 'home', component: HomeView, meta: { requiresAuth: true } },
    { path: '/statistics', name: 'statistics', component: StatisticsView, meta: { requiresAuth: true, permissions: ['statistics:read'] } },
    { path: '/products', name: 'products', component: ProductsView, meta: { requiresAuth: true, permissions: ['product:read', 'product:manage'] } },
    { path: '/processes', name: 'processes', component: ProcessesView, meta: { requiresAuth: true, permissions: ['process:read', 'process:manage'] } },
    { path: '/orders', name: 'orders', component: OrdersView, meta: { requiresAuth: true, permissions: ['order:read', 'order:manage'] } },
    { path: '/plans', name: 'plans', component: PlansView, meta: { requiresAuth: true, permissions: ['plan:read', 'plan:manage'] } },
    { path: '/work-orders', name: 'work-orders', component: WorkOrdersView, meta: { requiresAuth: true, permissions: ['workorder:read', 'workorder:manage'] } },
    { path: '/reports', name: 'reports', component: ReportsView, meta: { requiresAuth: true, permissions: ['report:create', 'report:read:own', 'report:read'] } },
    { path: '/quality', name: 'quality', component: QualityView, meta: { requiresAuth: true, permissions: ['quality:read', 'quality:create', 'quality:dispose'] } },
    { path: '/equipment', name: 'equipment', component: EquipmentView, meta: { requiresAuth: true, permissions: ['equipment:read', 'equipment:manage', 'equipment:maintain'] } },
    { path: '/system', name: 'system', component: SystemView, meta: { requiresAuth: true, permissions: ['system:user:manage', 'system:role:manage', 'system:permission:read'] } },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  await auth.initialize()
  if (to.meta.requiresAuth && !auth.authenticated) return { name: 'login' }
  if (to.meta.permissions && !to.meta.permissions.some((permission) => auth.user?.permissions?.includes(permission))) return { name: 'home' }
  if (to.meta.guestOnly && auth.authenticated) return { name: 'home' }
  return true
})

export default router
