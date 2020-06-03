package com.ctrip.framework.apollo.biz.message;

import com.ctrip.framework.apollo.biz.entity.ReleaseMessage;

/**
 * ReleaseMessage 监听器接口
 * @author Jason Song(song_s@ctrip.com)
 */
public interface ReleaseMessageListener {
  /**
   * 处理 ReleaseMessage
   *
   * @param message
   * @param channel 通道（主题）
   */
  void handleMessage(ReleaseMessage message, String channel);
}
