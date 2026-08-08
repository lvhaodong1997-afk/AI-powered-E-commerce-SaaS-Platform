import router from './router'
import type { RouteRecordRaw } from 'vue-router'
import { isRelogin } from '@/config/axios/service'
import { getAccessToken, removeToken } from '@/utils/auth'
import { useTitle } from '@/hooks/web/useTitle'
import { useNProgress } from '@/hooks/web/useNProgress'
import { usePageLoading } from '@/hooks/web/usePageLoading'
import { useDictStoreWithOut } from '@/store/modules/dict'
import { useUserStoreWithOut } from '@/store/modules/user'
import { usePermissionStoreWithOut } from '@/store/modules/permission'
import { parseRouteLocation } from '@/utils/routeParams'
import { deleteUserCache } from '@/hooks/web/useCache'

const { start, done } = useNProgress()

const { loadStart, loadDone } = usePageLoading()

// 路由不重定向白名单
const whiteList = [
  '/',
  '/login',
  '/social-login',
  '/auth-redirect',
  '/bind',
  '/register',
  '/oauthLogin/gitee',
  '/privacy-policy',
  '/terms-of-service',
  '/data-deletion',
  '/review'
]

const publicPageList = ['/', '/privacy-policy', '/terms-of-service', '/data-deletion', '/review']

// 路由加载前
router.beforeEach(async (to, from, next) => {
  start()
  loadStart()
  if (publicPageList.includes(to.path)) {
    next()
    return
  }
  if (getAccessToken()) {
    if (to.path === '/login') {
      next({ path: '/tk/dashboard' })
    } else {
      const dictStore = useDictStoreWithOut()
      const userStore = useUserStoreWithOut()
      const permissionStore = usePermissionStoreWithOut()
      // 异步加载字典
      if (!dictStore.getIsSetDict) {
        dictStore.setDictMap().then()
      }
      if (!userStore.getIsSetUser) {
        try {
          isRelogin.show = true
          await userStore.setUserInfoAction()
          isRelogin.show = false
          // 后端过滤菜单
          await permissionStore.generateRoutes()
          permissionStore.getAddRouters.forEach((route) => {
            router.addRoute(route as unknown as RouteRecordRaw) // 动态添加可访问路由表
          })
          const redirectPath = from.query.redirect
          // 修复跳转时不带参数的问题
          const redirect = typeof redirectPath === 'string' ? redirectPath : to.fullPath
          const redirectLocation = parseRouteLocation(redirect)
          const nextData =
            to.fullPath === redirect
              ? { ...to, replace: true }
              : { ...redirectLocation, replace: true }
          next(nextData)
        } catch (error) {
          isRelogin.show = false
          removeToken()
          deleteUserCache()
          userStore.resetState()
          loadDone()
          done()
          next(`/login?redirect=${encodeURIComponent(to.fullPath)}`)
        }
      } else {
        next()
      }
    }
  } else {
    if (whiteList.indexOf(to.path) !== -1) {
      next()
    } else {
      next(`/login?redirect=${encodeURIComponent(to.fullPath)}`) // 否则全部重定向到登录页
    }
  }
})

router.afterEach((to) => {
  useTitle(to?.meta?.title as string)
  done() // 结束Progress
  loadDone()
})
