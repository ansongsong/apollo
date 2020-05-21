package com.ctrip.framework.apollo.portal.listener;

import com.google.common.base.Preconditions;

import com.ctrip.framework.apollo.common.entity.App;

import org.springframework.context.ApplicationEvent;

/**
 * App 创建事件
 */
public class AppCreationEvent extends ApplicationEvent {
  /**
   * 将 App 对象作为方法参数传入。
   * @param source
   */
  public AppCreationEvent(Object source) {
    super(source);
  }

  /**
   * 获得事件对应的 App 对象。
   * @return
   */
  public App getApp() {
    Preconditions.checkState(source != null);
    return (App) this.source;
  }
}
