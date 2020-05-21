package com.ctrip.framework.apollo.portal.api;


import com.ctrip.framework.apollo.portal.component.RetryableRestTemplate;

import org.springframework.beans.factory.annotation.Autowired;

/**
 * 提供统一的 restTemplate 的属性注入
 */
public abstract class API {
  /**
   * 重试 RestTemplate
   */
  @Autowired
  protected RetryableRestTemplate restTemplate;

}
