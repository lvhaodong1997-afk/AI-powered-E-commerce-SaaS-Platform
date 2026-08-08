import { Layout } from '@/utils/routerHelper'

const { t } = useI18n()
/**
 * redirect: noredirect        褰撹缃?noredirect 鐨勬椂鍊欒璺敱鍦ㄩ潰鍖呭睉瀵艰埅涓笉鍙鐐瑰嚮
 * name:'router-name'          璁惧畾璺敱鐨勫悕瀛楋紝涓€瀹氳濉啓涓嶇劧浣跨敤<keep-alive>鏃朵細鍑虹幇鍚勭闂
 * meta : {
 hidden: true              褰撹缃?true 鐨勬椂鍊欒璺敱涓嶄細鍐嶄晶杈规爮鍑虹幇 濡?04锛宭ogin绛夐〉闈?榛樿 false)

 alwaysShow: true          褰撲綘涓€涓矾鐢变笅闈㈢殑 children 澹版槑鐨勮矾鐢卞ぇ浜?涓椂锛岃嚜鍔ㄤ細鍙樻垚宓屽鐨勬ā寮忥紝
 鍙湁涓€涓椂锛屼細灏嗛偅涓瓙璺敱褰撳仛鏍硅矾鐢辨樉绀哄湪渚ц竟鏍忥紝
 鑻ヤ綘鎯充笉绠¤矾鐢变笅闈㈢殑 children 澹版槑鐨勪釜鏁伴兘鏄剧ず浣犵殑鏍硅矾鐢憋紝
 浣犲彲浠ヨ缃?alwaysShow: true锛岃繖鏍峰畠灏变細蹇界暐涔嬪墠瀹氫箟鐨勮鍒欙紝
 涓€鐩存樉绀烘牴璺敱(榛樿 false)

 title: 'title'            璁剧疆璇ヨ矾鐢卞湪渚ц竟鏍忓拰闈㈠寘灞戜腑灞曠ず鐨勫悕瀛?

 icon: 'svg-name'          璁剧疆璇ヨ矾鐢辩殑鍥炬爣

 noCache: true             濡傛灉璁剧疆涓簍rue锛屽垯涓嶄細琚?<keep-alive> 缂撳瓨(榛樿 false)

 breadcrumb: false         濡傛灉璁剧疆涓篺alse锛屽垯涓嶄細鍦╞readcrumb闈㈠寘灞戜腑鏄剧ず(榛樿 true)

 affix: true               濡傛灉璁剧疆涓簍rue锛屽垯浼氫竴鐩村浐瀹氬湪tag椤逛腑(榛樿 false)

 noTagsView: true          濡傛灉璁剧疆涓簍rue锛屽垯涓嶄細鍑虹幇鍦╰ag涓?榛樿 false)

 activeMenu: '/dashboard'  鏄剧ず楂樹寒鐨勮矾鐢辫矾寰?

 followAuth: '/dashboard'  璺熼殢鍝釜璺敱杩涜鏉冮檺杩囨护

 canTo: true               璁剧疆涓簍rue鍗充娇hidden涓簍rue锛屼篃渚濈劧鍙互杩涜璺敱璺宠浆(榛樿 false)
 }
 **/
const remainingRouter: AppRouteRecordRaw[] = [
  {
    path: '/redirect',
    component: Layout,
    name: 'RedirectRoot',
    children: [
      {
        path: '/redirect/:path(.*)',
        name: 'Redirect',
        component: () => import('@/views/Redirect/Redirect.vue'),
        meta: {}
      }
    ],
    meta: {
      hidden: true,
      noTagsView: true
    }
  },
  {
    path: '/',
    component: () => import('@/views/Public/TkReview.vue'),
    name: 'PublicHome',
    meta: {
      hidden: true,
      title: 'ClipForge Studio',
      noTagsView: true
    }
  },
  {
    path: '/index',
    redirect: '/tk/dashboard',
    name: 'Index',
    meta: {
      hidden: true,
      noTagsView: true
    }
  },
  {
    path: '/tk/dashboard',
    component: Layout,
    name: 'TkDashboardDirectRoot',
    meta: {
      hidden: true,
      noTagsView: true
    },
    children: [
      {
        path: '',
        component: () => import('@/views/tk/dashboard/index.vue'),
        name: 'TkDashboardDirect',
        meta: {
          title: 'Home',
          hidden: true,
          noTagsView: false,
          activeMenu: '/tk/dashboard',
          icon: 'ep:home-filled'
        }
      }
    ]
  },
  {
    path: '/tk/data-dashboard',
    component: Layout,
    name: 'TkDataDashboardDirectRoot',
    meta: {
      hidden: true,
      noTagsView: true
    },
    children: [
      {
        path: '',
        component: () => import('@/views/tk/data-dashboard/index.vue'),
        name: 'TkDataDashboardDirect',
        meta: {
          title: 'Data Dashboard',
          hidden: true,
          noTagsView: false,
          activeMenu: '/tk/dashboard',
          icon: 'ep:data-analysis'
        }
      }
    ]
  },
  {
    path: '/tk/generation-batch',
    component: Layout,
    name: 'TkGenerationBatchDirectRoot',
    meta: {
      hidden: true,
      noTagsView: true
    },
    children: [
      {
        path: '',
        component: () => import('@/views/tk/generation-batch/index.vue'),
        name: 'TkGenerationBatchDirect',
        meta: {
          title: 'Generation Batch Queue',
          hidden: true,
          noTagsView: false,
          activeMenu: '/tk/dashboard',
          icon: 'ep:operation'
        }
      }
    ]
  },
  {
    path: '/tk/generation-route',
    component: Layout,
    name: 'TkGenerationRouteDirectRoot',
    meta: {
      hidden: true,
      noTagsView: true
    },
    children: [
      {
        path: '',
        component: () => import('@/views/tk/generation-route/index.vue'),
        name: 'TkGenerationRouteDirect',
        meta: {
          title: 'Generation Route',
          hidden: true,
          noTagsView: false,
          activeMenu: '/tk/dashboard',
          icon: 'ep:connection'
        }
      }
    ]
  },
  {
    path: '/user',
    component: Layout,
    name: 'UserInfo',
    meta: {
      hidden: true
    },
    children: [
      {
        path: 'profile',
        component: () => import('@/views/Profile/Index.vue'),
        name: 'Profile',
        meta: {
          canTo: true,
          hidden: true,
          noTagsView: false,
          icon: 'ep:user',
          title: t('common.profile')
        }
      },
      {
        path: 'notify-message',
        component: () => import('@/views/system/notify/my/index.vue'),
        name: 'MyNotifyMessage',
        meta: {
          canTo: true,
          hidden: true,
          noTagsView: false,
          icon: 'ep:message',
          title: 'My Messages'
        }
      }
    ]
  },
  {
    path: '/dict',
    component: Layout,
    name: 'dict',
    meta: {
      hidden: true
    },
    children: [
      {
        path: 'type/data/:dictType',
        component: () => import('@/views/system/dict/data/index.vue'),
        name: 'SystemDictData',
        meta: {
          title: 'Dictionary Data',
          noCache: true,
          hidden: true,
          canTo: true,
          icon: '',
          activeMenu: '/system/dict'
        }
      }
    ]
  },
  {
    path: '/login',
    component: () => import('@/views/Login/Login.vue'),
    name: 'Login',
    meta: {
      hidden: true,
      title: 'router.login',
      noTagsView: true
    }
  },
  {
    path: '/sso',
    component: () => import('@/views/Login/Login.vue'),
    name: 'SSOLogin',
    meta: {
      hidden: true,
      title: 'router.login',
      noTagsView: true
    }
  },
  {
    path: '/social-login',
    component: () => import('@/views/Login/SocialLogin.vue'),
    name: 'SocialLogin',
    meta: {
      hidden: true,
      title: 'router.socialLogin',
      noTagsView: true
    }
  },
  {
    path: '/privacy-policy',
    component: () => import('@/views/Public/PrivacyPolicy.vue'),
    name: 'PrivacyPolicy',
    meta: {
      hidden: true,
      title: 'Privacy Policy',
      noTagsView: true
    }
  },
  {
    path: '/terms-of-service',
    component: () => import('@/views/Public/TermsOfService.vue'),
    name: 'TermsOfService',
    meta: {
      hidden: true,
      title: 'Terms of Service',
      noTagsView: true
    }
  },
  {
    path: '/data-deletion',
    component: () => import('@/views/Public/DataDeletion.vue'),
    name: 'DataDeletion',
    meta: {
      hidden: true,
      title: 'Data Deletion',
      noTagsView: true
    }
  },
  {
    path: '/review',
    component: () => import('@/views/Public/TkReview.vue'),
    name: 'TkReview',
    meta: {
      hidden: true,
      title: 'ClipForge Studio',
      noTagsView: true
    }
  },
  {
    path: '/403',
    component: () => import('@/views/Error/403.vue'),
    name: 'NoAccess',
    meta: {
      hidden: true,
      title: '403',
      noTagsView: true
    }
  },
  {
    path: '/404',
    component: () => import('@/views/Error/404.vue'),
    name: 'NoFound',
    meta: {
      hidden: true,
      title: '404',
      noTagsView: true
    }
  },
  {
    path: '/500',
    component: () => import('@/views/Error/500.vue'),
    name: 'Error',
    meta: {
      hidden: true,
      title: '500',
      noTagsView: true
    }
  },
  {
    path: '/:pathMatch(.*)*',
    component: () => import('@/views/Error/404.vue'),
    name: '',
    meta: {
      title: '404',
      hidden: true,
      breadcrumb: false
    }
  }
]

export default remainingRouter
