package com.xuecheng.learning.service;

import com.xuecheng.base.model.RestResponse;

/**
 * 在线学习的接口
 */
public interface LearningService {

    /**
     * 获取教学视频
     * @param userId
     * @param teachplanId
     * @param mediaId
     * @return
     */
    public RestResponse<String> getVideo(String userId, Long coursed,Long teachplanId, String mediaId);
}
