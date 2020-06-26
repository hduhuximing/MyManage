import Vue from 'vue'

import {hasAnyPermission, hasAnyRole, hasNoPermission, hasPermission, hasRole} from 'utils/permissionDirect'

const Plugins = [
  hasPermission,
  hasNoPermission,
  hasAnyPermission,
  hasRole,
  hasAnyRole
]

Plugins.map((plugin) => {
  Vue.use(plugin)
})

export default Vue
