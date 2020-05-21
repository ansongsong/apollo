package com.ctrip.framework.apollo.portal.constant;

public class RoleType {
  /**
   * 修改和发布权限
   */
  public static final String MASTER = "Master";
  /**
   * 修改权限
   */
  public static final String MODIFY_NAMESPACE = "ModifyNamespace";
  /**
   * 发布权限
   */
  public static final String RELEASE_NAMESPACE = "ReleaseNamespace";

  public static boolean isValidRoleType(String roleType) {
    return MASTER.equals(roleType) || MODIFY_NAMESPACE.equals(roleType) || RELEASE_NAMESPACE.equals(roleType);
  }

}
