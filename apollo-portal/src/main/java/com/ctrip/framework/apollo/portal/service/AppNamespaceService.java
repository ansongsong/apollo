package com.ctrip.framework.apollo.portal.service;

import com.ctrip.framework.apollo.common.entity.App;
import com.ctrip.framework.apollo.common.entity.AppNamespace;
import com.ctrip.framework.apollo.common.exception.BadRequestException;
import com.ctrip.framework.apollo.core.ConfigConsts;
import com.ctrip.framework.apollo.core.enums.ConfigFileFormat;
import com.ctrip.framework.apollo.core.utils.StringUtils;
import com.ctrip.framework.apollo.portal.repository.AppNamespaceRepository;
import com.ctrip.framework.apollo.portal.spi.UserInfoHolder;
import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class AppNamespaceService {

  private static final int PRIVATE_APP_NAMESPACE_NOTIFICATION_COUNT = 5;
  private static final Joiner APP_NAMESPACE_JOINER = Joiner.on(",").skipNulls();

  private final UserInfoHolder userInfoHolder;
  private final AppNamespaceRepository appNamespaceRepository;
  private final RoleInitializationService roleInitializationService;
  private final AppService appService;
  private final RolePermissionService rolePermissionService;

  public AppNamespaceService(
      final UserInfoHolder userInfoHolder,
      final AppNamespaceRepository appNamespaceRepository,
      final RoleInitializationService roleInitializationService,
      final @Lazy AppService appService,
      final RolePermissionService rolePermissionService) {
    this.userInfoHolder = userInfoHolder;
    this.appNamespaceRepository = appNamespaceRepository;
    this.roleInitializationService = roleInitializationService;
    this.appService = appService;
    this.rolePermissionService = rolePermissionService;
  }

  /**
   * 公共的app ns,能被其它项目关联到的app ns
   */
  public List<AppNamespace> findPublicAppNamespaces() {
    return appNamespaceRepository.findByIsPublicTrue();
  }

  public AppNamespace findPublicAppNamespace(String namespaceName) {
    List<AppNamespace> appNamespaces = appNamespaceRepository.findByNameAndIsPublic(namespaceName, true);

    if (CollectionUtils.isEmpty(appNamespaces)) {
      return null;
    }

    return appNamespaces.get(0);
  }

  private List<AppNamespace> findAllPrivateAppNamespaces(String namespaceName) {
    return appNamespaceRepository.findByNameAndIsPublic(namespaceName, false);
  }

  public AppNamespace findByAppIdAndName(String appId, String namespaceName) {
    return appNamespaceRepository.findByAppIdAndName(appId, namespaceName);
  }

  public List<AppNamespace> findByAppId(String appId) {
    return appNamespaceRepository.findByAppId(appId);
  }

  @Transactional
  public void createDefaultAppNamespace(String appId) {
    if (!isAppNamespaceNameUnique(appId, ConfigConsts.NAMESPACE_APPLICATION)) {
      throw new BadRequestException(String.format("App already has application namespace. AppId = %s", appId));
    }

    AppNamespace appNs = new AppNamespace();
    appNs.setAppId(appId);
    appNs.setName(ConfigConsts.NAMESPACE_APPLICATION);
    appNs.setComment("default app namespace");
    appNs.setFormat(ConfigFileFormat.Properties.getValue());
    String userId = userInfoHolder.getUser().getUserId();
    appNs.setDataChangeCreatedBy(userId);
    appNs.setDataChangeLastModifiedBy(userId);

    appNamespaceRepository.save(appNs);
  }

  public boolean isAppNamespaceNameUnique(String appId, String namespaceName) {
    Objects.requireNonNull(appId, "AppId must not be null");
    Objects.requireNonNull(namespaceName, "Namespace must not be null");
    return Objects.isNull(appNamespaceRepository.findByAppIdAndName(appId, namespaceName));
  }

  public AppNamespace createAppNamespaceInLocal(AppNamespace appNamespace) {
    return createAppNamespaceInLocal(appNamespace, true);
  }

  @Transactional
  public AppNamespace createAppNamespaceInLocal(AppNamespace appNamespace, boolean appendNamespacePrefix) {
    String appId = appNamespace.getAppId();
    // 校验对应的 App 是否存在。若不存在，抛出 BadRequestException 异常
    //add app org id as prefix
    App app = appService.load(appId);
    if (app == null) {
      throw new BadRequestException("App not exist. AppId = " + appId);
    }
    // 拼接 AppNamespace 的 `name` 属性。
    StringBuilder appNamespaceName = new StringBuilder();
    //add prefix postfix
    appNamespaceName
        .append(appNamespace.isPublic() && appendNamespacePrefix ? app.getOrgId() + "." : "") // 公用类型，拼接组织编号
        .append(appNamespace.getName())
        .append(appNamespace.formatAsEnum() == ConfigFileFormat.Properties ? "" : "." + appNamespace.getFormat());
    appNamespace.setName(appNamespaceName.toString());
    // 设置 AppNamespace 的 `comment` 属性为空串，若为 null 。
    if (appNamespace.getComment() == null) {
      appNamespace.setComment("");
    }
    // 校验 AppNamespace 的 `format` 是否合法
    if (!ConfigFileFormat.isValidFormat(appNamespace.getFormat())) {
     throw new BadRequestException("Invalid namespace format. format must be properties、json、yaml、yml、xml");
    }
    // 设置 AppNamespace 的创建和修改人
    String operator = appNamespace.getDataChangeCreatedBy();
    if (StringUtils.isEmpty(operator)) {
      operator = userInfoHolder.getUser().getUserId();
      appNamespace.setDataChangeCreatedBy(operator);
    }

    appNamespace.setDataChangeLastModifiedBy(operator);
    // 公用类型，校验 `name` 在全局唯一
    // globally uniqueness check for public app namespace
    if (appNamespace.isPublic()) {
      checkAppNamespaceGlobalUniqueness(appNamespace);
    } else {
      // 私有类型，校验 `name` 在 App 下唯一
      // check private app namespace
      if (appNamespaceRepository.findByAppIdAndName(appNamespace.getAppId(), appNamespace.getName()) != null) {
        throw new BadRequestException("Private AppNamespace " + appNamespace.getName() + " already exists!");
      }
      // should not have the same with public app namespace
      checkPublicAppNamespaceGlobalUniqueness(appNamespace);
    }

    AppNamespace createdAppNamespace = appNamespaceRepository.save(appNamespace);
    // 初始化 Namespace 的 Role 们
    roleInitializationService.initNamespaceRoles(appNamespace.getAppId(), appNamespace.getName(), operator);
    roleInitializationService.initNamespaceEnvRoles(appNamespace.getAppId(), appNamespace.getName(), operator);

    return createdAppNamespace;
  }

  private void checkAppNamespaceGlobalUniqueness(AppNamespace appNamespace) {
    checkPublicAppNamespaceGlobalUniqueness(appNamespace);

    List<AppNamespace> privateAppNamespaces = findAllPrivateAppNamespaces(appNamespace.getName());

    if (!CollectionUtils.isEmpty(privateAppNamespaces)) {
      Set<String> appIds = Sets.newHashSet();
      for (AppNamespace ans : privateAppNamespaces) {
        appIds.add(ans.getAppId());
        if (appIds.size() == PRIVATE_APP_NAMESPACE_NOTIFICATION_COUNT) {
          break;
        }
      }

      throw new BadRequestException(
          "Public AppNamespace " + appNamespace.getName() + " already exists as private AppNamespace in appId: "
              + APP_NAMESPACE_JOINER.join(appIds) + ", etc. Please select another name!");
    }
  }

  private void checkPublicAppNamespaceGlobalUniqueness(AppNamespace appNamespace) {
    AppNamespace publicAppNamespace = findPublicAppNamespace(appNamespace.getName());
    if (publicAppNamespace != null) {
      throw new BadRequestException("AppNamespace " + appNamespace.getName() + " already exists as public namespace in appId: " + publicAppNamespace.getAppId() + "!");
    }
  }


  @Transactional
  public AppNamespace deleteAppNamespace(String appId, String namespaceName) {
    AppNamespace appNamespace = appNamespaceRepository.findByAppIdAndName(appId, namespaceName);
    if (appNamespace == null) {
      throw new BadRequestException(
          String.format("AppNamespace not exists. AppId = %s, NamespaceName = %s", appId, namespaceName));
    }

    String operator = userInfoHolder.getUser().getUserId();

    // this operator is passed to com.ctrip.framework.apollo.portal.listener.DeletionListener.onAppNamespaceDeletionEvent
    appNamespace.setDataChangeLastModifiedBy(operator);

    // delete app namespace in portal db
    appNamespaceRepository.delete(appId, namespaceName, operator);

    // delete Permission and Role related data
    rolePermissionService.deleteRolePermissionsByAppIdAndNamespace(appId, namespaceName, operator);

    return appNamespace;
  }

  public void batchDeleteByAppId(String appId, String operator) {
    appNamespaceRepository.batchDeleteByAppId(appId, operator);
  }
}
